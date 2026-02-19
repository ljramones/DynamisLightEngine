package org.dynamislight.impl.vulkan.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.spi.render.RenderCapabilityValidationIssue;
import org.dynamislight.spi.render.RenderFeatureCapability;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;

/**
 * Metadata compiler for capability pass declarations into graph plans.
 */
public final class VulkanRenderGraphCompiler {
    public VulkanRenderGraphPlan compile(List<RenderFeatureCapability> capabilities, Set<String> externalInputs) {
        List<VulkanRenderGraphNode> nodes = toNodes(capabilities);
        List<RenderCapabilityValidationIssue> issues = new ArrayList<>();
        Set<String> externals = externalInputs == null ? Set.of() : Set.copyOf(externalInputs);

        validateDuplicateWriters(nodes, issues);
        validateMissingProducers(nodes, externals, issues);

        List<VulkanRenderGraphNode> ordered = order(nodes);
        List<VulkanRenderGraphResourceLifetime> lifetimes = lifetimes(ordered);
        return new VulkanRenderGraphPlan(ordered, issues, lifetimes);
    }

    private static List<VulkanRenderGraphNode> toNodes(List<RenderFeatureCapability> capabilities) {
        List<VulkanRenderGraphNode> out = new ArrayList<>();
        if (capabilities == null) {
            return out;
        }
        for (RenderFeatureCapability capability : capabilities) {
            if (capability == null || capability.contract() == null) {
                continue;
            }
            String featureId = capability.contract().featureId();
            List<RenderPassContribution> contributions = capability.contract().passContributions();
            for (int i = 0; i < contributions.size(); i++) {
                RenderPassContribution c = contributions.get(i);
                String nodeId = featureId + ":" + c.passId() + "#" + i;
                out.add(new VulkanRenderGraphNode(
                        nodeId,
                        featureId,
                        c.passId(),
                        c.phase(),
                        c.reads(),
                        c.writes(),
                        c.optional()
                ));
            }
        }
        return out;
    }

    private static void validateDuplicateWriters(
            List<VulkanRenderGraphNode> nodes,
            List<RenderCapabilityValidationIssue> issues
    ) {
        Map<String, WriterRef> writerByResource = new HashMap<>();
        for (VulkanRenderGraphNode node : nodes) {
            for (String writes : node.writes()) {
                String resource = normalizeResourceName(writes);
                if (resource.isBlank()) {
                    continue;
                }
                WriterRef current = new WriterRef(node.nodeId(), new PassGroupKey(node.phase(), node.passId()));
                WriterRef previous = writerByResource.putIfAbsent(resource, current);
                if (previous != null && !previous.groupKey().equals(current.groupKey())) {
                    issues.add(new RenderCapabilityValidationIssue(
                            "DUPLICATE_WRITER",
                            "Resource '" + resource + "' written by both '" + previous.nodeId() + "' and '" + node.nodeId() + "'",
                            RenderCapabilityValidationIssue.Severity.ERROR
                    ));
                }
            }
        }
    }

    private static void validateMissingProducers(
            List<VulkanRenderGraphNode> nodes,
            Set<String> externalInputs,
            List<RenderCapabilityValidationIssue> issues
    ) {
        Set<String> produced = new HashSet<>(externalInputs);
        List<VulkanRenderGraphNode> sorted = order(nodes);

        int index = 0;
        while (index < sorted.size()) {
            VulkanRenderGraphNode seed = sorted.get(index);
            PassGroupKey group = new PassGroupKey(seed.phase(), seed.passId());
            int end = index;
            Set<String> groupWrites = new HashSet<>();
            while (end < sorted.size()) {
                VulkanRenderGraphNode candidate = sorted.get(end);
                if (!group.equals(new PassGroupKey(candidate.phase(), candidate.passId()))) {
                    break;
                }
                for (String write : candidate.writes()) {
                    String normalizedWrite = normalizeResourceName(write);
                    if (!normalizedWrite.isBlank()) {
                        groupWrites.add(normalizedWrite);
                    }
                }
                end++;
            }

            for (int i = index; i < end; i++) {
                VulkanRenderGraphNode node = sorted.get(i);
                for (String read : node.reads()) {
                    String normalizedRead = normalizeResourceName(read);
                    if (normalizedRead.isBlank()) {
                        continue;
                    }
                    if (!produced.contains(normalizedRead) && !groupWrites.contains(normalizedRead)) {
                        issues.add(new RenderCapabilityValidationIssue(
                                "MISSING_PRODUCER",
                                "Node '" + node.nodeId() + "' reads '" + normalizedRead + "' with no producer",
                                RenderCapabilityValidationIssue.Severity.ERROR
                        ));
                    }
                }
            }

            produced.addAll(groupWrites);
            index = end;
        }
    }

    private static List<VulkanRenderGraphNode> order(List<VulkanRenderGraphNode> nodes) {
        List<VulkanRenderGraphNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator
                .comparing((VulkanRenderGraphNode n) -> phaseRank(n.phase()))
                .thenComparing(VulkanRenderGraphNode::passId)
                .thenComparing(VulkanRenderGraphNode::featureId)
                .thenComparing(VulkanRenderGraphNode::nodeId));
        return ordered;
    }

    private static int phaseRank(RenderPassPhase phase) {
        return switch (phase) {
            case PRE_MAIN -> 0;
            case MAIN -> 1;
            case POST_MAIN -> 2;
            case AUXILIARY -> 3;
        };
    }

    private static List<VulkanRenderGraphResourceLifetime> lifetimes(List<VulkanRenderGraphNode> ordered) {
        Map<String, int[]> span = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            final int nodeIndex = i;
            VulkanRenderGraphNode node = ordered.get(i);
            for (String resource : node.reads()) {
                String normalized = normalizeResourceName(resource);
                if (!normalized.isBlank()) {
                    span.compute(normalized, (k, v) -> updateSpan(v, nodeIndex));
                }
            }
            for (String resource : node.writes()) {
                String normalized = normalizeResourceName(resource);
                if (!normalized.isBlank()) {
                    span.compute(normalized, (k, v) -> updateSpan(v, nodeIndex));
                }
            }
        }
        List<VulkanRenderGraphResourceLifetime> out = new ArrayList<>();
        for (Map.Entry<String, int[]> e : span.entrySet()) {
            out.add(new VulkanRenderGraphResourceLifetime(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        return out;
    }

    private static int[] updateSpan(int[] current, int index) {
        if (current == null) {
            return new int[]{index, index};
        }
        current[0] = Math.min(current[0], index);
        current[1] = Math.max(current[1], index);
        return current;
    }

    private static String normalizeResourceName(String resource) {
        return resource == null ? "" : resource.trim();
    }

    private record PassGroupKey(RenderPassPhase phase, String passId) {
        private PassGroupKey {
            phase = phase == null ? RenderPassPhase.AUXILIARY : phase;
            passId = passId == null ? "" : passId.trim();
        }
    }

    private record WriterRef(String nodeId, PassGroupKey groupKey) {
        private WriterRef {
            nodeId = nodeId == null ? "" : nodeId.trim();
        }
    }
}
