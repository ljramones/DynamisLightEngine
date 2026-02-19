package org.dynamislight.impl.vulkan.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executable Vulkan graph plan (metadata order + per-node callback mapping).
 */
public record VulkanExecutableRenderGraphPlan(
        VulkanRenderGraphPlan metadataPlan,
        VulkanRenderGraphBarrierPlan barrierPlan,
        Map<String, Runnable> executeCallbackByNodeId
) {
    public VulkanExecutableRenderGraphPlan {
        metadataPlan = metadataPlan == null
                ? new VulkanRenderGraphPlan(List.of(), List.of(), List.of(), List.of())
                : metadataPlan;
        barrierPlan = barrierPlan == null ? new VulkanRenderGraphBarrierPlan(List.of()) : barrierPlan;
        executeCallbackByNodeId = executeCallbackByNodeId == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(executeCallbackByNodeId));
    }

    public Runnable executeCallback(String nodeId) {
        Runnable callback = executeCallbackByNodeId.get(nodeId);
        return callback == null ? () -> { } : callback;
    }
}
