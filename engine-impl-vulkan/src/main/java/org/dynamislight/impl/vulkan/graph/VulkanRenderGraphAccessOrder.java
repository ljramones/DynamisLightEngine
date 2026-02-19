package org.dynamislight.impl.vulkan.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility for resource-centric access order inspection.
 */
public final class VulkanRenderGraphAccessOrder {
    private VulkanRenderGraphAccessOrder() {
    }

    public static Map<String, List<VulkanRenderGraphResourceAccessEvent>> byResource(List<VulkanRenderGraphNode> orderedNodes) {
        Map<String, List<VulkanRenderGraphResourceAccessEvent>> result = new LinkedHashMap<>();
        if (orderedNodes == null) {
            return result;
        }

        for (int nodeIndex = 0; nodeIndex < orderedNodes.size(); nodeIndex++) {
            VulkanRenderGraphNode node = orderedNodes.get(nodeIndex);
            if (node == null) {
                continue;
            }

            Set<String> readSet = normalize(node.reads());
            Set<String> writeSet = normalize(node.writes());
            Set<String> all = new LinkedHashSet<>();
            all.addAll(readSet);
            all.addAll(writeSet);

            for (String resource : all) {
                VulkanRenderGraphResourceAccessType type;
                boolean read = readSet.contains(resource);
                boolean write = writeSet.contains(resource);
                if (read && write) {
                    type = VulkanRenderGraphResourceAccessType.READ_WRITE;
                } else if (write) {
                    type = VulkanRenderGraphResourceAccessType.WRITE;
                } else {
                    type = VulkanRenderGraphResourceAccessType.READ;
                }
                result.computeIfAbsent(resource, ignored -> new ArrayList<>())
                        .add(new VulkanRenderGraphResourceAccessEvent(resource, node.nodeId(), nodeIndex, type));
            }
        }

        return result;
    }

    private static Set<String> normalize(List<String> names) {
        Set<String> out = new LinkedHashSet<>();
        if (names == null) {
            return out;
        }
        for (String name : names) {
            String normalized = name == null ? "" : name.trim();
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }
}
