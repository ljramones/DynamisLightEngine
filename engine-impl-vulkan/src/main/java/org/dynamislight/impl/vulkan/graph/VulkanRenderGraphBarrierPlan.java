package org.dynamislight.impl.vulkan.graph;

import java.util.List;

/**
 * Metadata-only barrier plan derived from a compiled render graph.
 */
public record VulkanRenderGraphBarrierPlan(List<VulkanRenderGraphBarrier> barriers) {
    public VulkanRenderGraphBarrierPlan {
        barriers = barriers == null ? List.of() : List.copyOf(barriers);
    }

    public String debugDump() {
        StringBuilder out = new StringBuilder();
        for (VulkanRenderGraphBarrier barrier : barriers) {
            out.append(barrier.sourceAccessId())
                    .append(" -> ")
                    .append(barrier.destinationAccessId())
                    .append(" | ")
                    .append(barrier.resourceName())
                    .append(" | ")
                    .append(barrier.hazardType())
                    .append(" | src=")
                    .append(barrier.srcStageMask()).append('/').append(barrier.srcAccessMask())
                    .append(" dst=")
                    .append(barrier.dstStageMask()).append('/').append(barrier.dstAccessMask())
                    .append(" layout=")
                    .append(barrier.oldLayout()).append("->").append(barrier.newLayout())
                    .append(" execOnly=")
                    .append(barrier.executionDependencyOnly())
                    .append('\n');
        }
        return out.toString();
    }
}
