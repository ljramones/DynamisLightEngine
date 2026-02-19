package org.dynamislight.impl.vulkan.graph;

/**
 * Planned barrier edge between two access points for a resource.
 */
public record VulkanRenderGraphBarrier(
        String resourceName,
        String sourceAccessId,
        String destinationAccessId,
        VulkanRenderGraphBarrierHazardType hazardType,
        int srcStageMask,
        int dstStageMask,
        int srcAccessMask,
        int dstAccessMask,
        int oldLayout,
        int newLayout,
        boolean executionDependencyOnly
) {
    public VulkanRenderGraphBarrier {
        resourceName = resourceName == null ? "" : resourceName.trim();
        sourceAccessId = sourceAccessId == null ? "" : sourceAccessId.trim();
        destinationAccessId = destinationAccessId == null ? "" : destinationAccessId.trim();
        hazardType = hazardType == null ? VulkanRenderGraphBarrierHazardType.READ_AFTER_WRITE : hazardType;
    }

    public String signature() {
        return srcStageMask + ":" + dstStageMask + ":" + srcAccessMask + ":" + dstAccessMask + ":" + oldLayout + ":" + newLayout;
    }
}
