package org.dynamislight.impl.vulkan.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import org.dynamislight.spi.render.RenderCapabilityValidationIssue;
import org.dynamislight.spi.render.RenderFeatureCapability;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.dynamislight.spi.render.RenderResourceType;

/**
 * Metadata compiler for capability pass declarations into graph plans.
 */
public final class VulkanRenderGraphCompiler {
    public VulkanRenderGraphPlan compile(List<RenderFeatureCapability> capabilities, Set<String> externalInputs) {
        List<VulkanImportedResource> imports = externalInputs == null
                ? List.of()
                : externalInputs.stream()
                .map(name -> new VulkanImportedResource(
                        name,
                        RenderResourceType.ATTACHMENT,
                        VulkanImportedResource.ResourceLifetime.PER_FRAME,
                        VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
                ))
                .toList();
        return compile(capabilities, imports);
    }

    public VulkanRenderGraphPlan compile(
            List<RenderFeatureCapability> capabilities,
            List<VulkanImportedResource> importedResources
    ) {
        List<VulkanRenderGraphNode> nodes = toNodes(capabilities);
        List<Group> groups = toGroups(nodes);
        List<RenderCapabilityValidationIssue> issues = new ArrayList<>();
        List<VulkanImportedResource> imports = normalizeImports(importedResources);
        Set<String> importNames = imports.stream()
                .map(VulkanImportedResource::resourceName)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, GroupKey> writerByResource = validateDuplicateWriters(groups, issues);
        validateMissingProducers(groups, writerByResource, importNames, issues);

        List<Group> orderedGroups = orderGroups(groups, writerByResource, issues);
        List<VulkanRenderGraphNode> orderedNodes = flatten(orderedGroups);
        List<VulkanRenderGraphResourceLifetime> lifetimes = lifetimes(orderedNodes);

        return new VulkanRenderGraphPlan(orderedNodes, imports, issues, lifetimes);
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

    private static List<Group> toGroups(List<VulkanRenderGraphNode> nodes) {
        Map<GroupKey, GroupBuilder> byKey = new LinkedHashMap<>();
        int sequence = 0;
        for (VulkanRenderGraphNode node : nodes) {
            GroupKey key = new GroupKey(node.phase(), node.passId());
            GroupBuilder builder = byKey.get(key);
            if (builder == null) {
                builder = new GroupBuilder(key, sequence++);
                byKey.put(key, builder);
            }
            builder.addNode(node);
        }
        return byKey.values().stream().map(GroupBuilder::build).toList();
    }

    private static List<VulkanImportedResource> normalizeImports(List<VulkanImportedResource> importedResources) {
        if (importedResources == null) {
            return List.of();
        }
        Map<String, VulkanImportedResource> byName = new LinkedHashMap<>();
        for (VulkanImportedResource resource : importedResources) {
            if (resource == null) {
                continue;
            }
            String name = normalizeResourceName(resource.resourceName());
            if (name.isBlank()) {
                continue;
            }
            byName.putIfAbsent(name, new VulkanImportedResource(
                    name,
                    resource.resourceType(),
                    resource.lifetime(),
                    resource.provider()
            ));
        }
        return List.copyOf(byName.values());
    }

    private static Map<String, GroupKey> validateDuplicateWriters(
            List<Group> groups,
            List<RenderCapabilityValidationIssue> issues
    ) {
        Map<String, GroupKey> writerByResource = new LinkedHashMap<>();
        Map<String, String> writerNodeByResource = new LinkedHashMap<>();
        for (Group group : groups) {
            String writerNodeId = group.nodes().isEmpty() ? group.key().passId() : group.nodes().getFirst().nodeId();
            for (String resource : group.writes()) {
                GroupKey previousGroup = writerByResource.putIfAbsent(resource, group.key());
                String previousNode = writerNodeByResource.putIfAbsent(resource, writerNodeId);
                if (previousGroup != null && !previousGroup.equals(group.key())) {
                    issues.add(new RenderCapabilityValidationIssue(
                            "DUPLICATE_WRITER",
                            "Resource '" + resource + "' written by both '" + previousNode + "' and '" + writerNodeId + "'",
                            RenderCapabilityValidationIssue.Severity.ERROR
                    ));
                }
            }
        }
        return writerByResource;
    }

    private static void validateMissingProducers(
            List<Group> groups,
            Map<String, GroupKey> writerByResource,
            Set<String> importNames,
            List<RenderCapabilityValidationIssue> issues
    ) {
        for (Group group : groups) {
            for (String read : group.reads()) {
                if (importNames.contains(read)) {
                    continue;
                }
                if (group.writes().contains(read)) {
                    continue;
                }
                if (!writerByResource.containsKey(read)) {
                    String nodeId = group.nodes().isEmpty() ? group.key().passId() : group.nodes().getFirst().nodeId();
                    issues.add(new RenderCapabilityValidationIssue(
                            "MISSING_PRODUCER",
                            "Node '" + nodeId + "' reads '" + read + "' with no producer",
                            RenderCapabilityValidationIssue.Severity.ERROR
                    ));
                }
            }
        }
    }

    private static List<Group> orderGroups(
            List<Group> groups,
            Map<String, GroupKey> writerByResource,
            List<RenderCapabilityValidationIssue> issues
    ) {
        Map<GroupKey, Group> byKey = groups.stream().collect(Collectors.toMap(
                Group::key,
                g -> g,
                (a, b) -> a,
                LinkedHashMap::new
        ));

        Map<GroupKey, Set<GroupKey>> outgoing = new LinkedHashMap<>();
        Map<GroupKey, Integer> inDegree = new LinkedHashMap<>();
        for (Group group : groups) {
            outgoing.put(group.key(), new LinkedHashSet<>());
            inDegree.put(group.key(), 0);
        }

        for (Group reader : groups) {
            for (String resource : reader.reads()) {
                GroupKey writerKey = writerByResource.get(resource);
                if (writerKey == null || writerKey.equals(reader.key())) {
                    continue;
                }
                if (outgoing.get(writerKey).add(reader.key())) {
                    inDegree.compute(reader.key(), (k, v) -> v == null ? 1 : v + 1);
                }
            }
        }

        Comparator<Group> readyOrder = Comparator
                .comparingInt((Group g) -> phaseRank(g.key().phase()))
                .thenComparingInt(Group::sequenceIndex);

        PriorityQueue<Group> ready = new PriorityQueue<>(readyOrder);
        for (Group group : groups) {
            if (inDegree.get(group.key()) == 0) {
                ready.add(group);
            }
        }

        List<Group> ordered = new ArrayList<>(groups.size());
        while (!ready.isEmpty()) {
            Group current = ready.poll();
            ordered.add(current);
            for (GroupKey nextKey : outgoing.get(current.key())) {
                int nextDegree = inDegree.get(nextKey) - 1;
                inDegree.put(nextKey, nextDegree);
                if (nextDegree == 0) {
                    ready.add(byKey.get(nextKey));
                }
            }
        }

        if (ordered.size() != groups.size()) {
            List<Group> cycleGroups = groups.stream()
                    .filter(group -> inDegree.get(group.key()) > 0)
                    .sorted(readyOrder)
                    .toList();
            String cycleNames = cycleGroups.stream()
                    .map(g -> g.key().phase() + ":" + g.key().passId())
                    .collect(Collectors.joining(" -> "));
            issues.add(new RenderCapabilityValidationIssue(
                    "CYCLE_DETECTED",
                    "Render graph cycle detected across pass groups: " + cycleNames,
                    RenderCapabilityValidationIssue.Severity.ERROR
            ));
            for (Group group : cycleGroups) {
                if (!ordered.contains(group)) {
                    ordered.add(group);
                }
            }
        }

        return ordered;
    }

    private static List<VulkanRenderGraphNode> flatten(List<Group> orderedGroups) {
        List<VulkanRenderGraphNode> ordered = new ArrayList<>();
        for (Group group : orderedGroups) {
            ordered.addAll(group.nodes());
        }
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

    private record GroupKey(RenderPassPhase phase, String passId) {
        private GroupKey {
            phase = phase == null ? RenderPassPhase.AUXILIARY : phase;
            passId = passId == null ? "" : passId.trim();
        }
    }

    private record Group(GroupKey key, int sequenceIndex, List<VulkanRenderGraphNode> nodes, Set<String> reads, Set<String> writes) {
        private Group {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            reads = reads == null ? Set.of() : Set.copyOf(reads);
            writes = writes == null ? Set.of() : Set.copyOf(writes);
        }
    }

    private static final class GroupBuilder {
        private final GroupKey key;
        private final int sequenceIndex;
        private final List<VulkanRenderGraphNode> nodes = new ArrayList<>();
        private final Set<String> reads = new LinkedHashSet<>();
        private final Set<String> writes = new LinkedHashSet<>();

        private GroupBuilder(GroupKey key, int sequenceIndex) {
            this.key = key;
            this.sequenceIndex = sequenceIndex;
        }

        private void addNode(VulkanRenderGraphNode node) {
            nodes.add(node);
            for (String read : node.reads()) {
                String normalized = normalizeResourceName(read);
                if (!normalized.isBlank()) {
                    reads.add(normalized);
                }
            }
            for (String write : node.writes()) {
                String normalized = normalizeResourceName(write);
                if (!normalized.isBlank()) {
                    writes.add(normalized);
                }
            }
        }

        private Group build() {
            return new Group(key, sequenceIndex, nodes, reads, writes);
        }
    }
}
