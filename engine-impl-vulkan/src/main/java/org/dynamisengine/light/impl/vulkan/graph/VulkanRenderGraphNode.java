package org.dynamisengine.light.impl.vulkan.graph;

import java.util.List;
import org.dynamisengine.light.spi.render.RenderPassPhase;

/**
 * Graph node compiled from a feature pass contribution.
 */
public record VulkanRenderGraphNode(
        String nodeId,
        String featureId,
        String passId,
        RenderPassPhase phase,
        List<String> reads,
        List<String> writes,
        boolean optional
) {
    public VulkanRenderGraphNode {
        nodeId = nodeId == null ? "" : nodeId.trim();
        featureId = featureId == null ? "" : featureId.trim();
        passId = passId == null ? "" : passId.trim();
        phase = phase == null ? RenderPassPhase.AUXILIARY : phase;
        reads = reads == null ? List.of() : List.copyOf(reads);
        writes = writes == null ? List.of() : List.copyOf(writes);
    }
}
