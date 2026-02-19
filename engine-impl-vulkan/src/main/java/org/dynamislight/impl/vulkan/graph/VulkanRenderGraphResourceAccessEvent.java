package org.dynamislight.impl.vulkan.graph;

/**
 * Ordered resource access event within the compiled node stream.
 */
public record VulkanRenderGraphResourceAccessEvent(
        String resourceName,
        String nodeId,
        int nodeIndex,
        VulkanRenderGraphResourceAccessType accessType
) {
    public VulkanRenderGraphResourceAccessEvent {
        resourceName = resourceName == null ? "" : resourceName.trim();
        nodeId = nodeId == null ? "" : nodeId.trim();
        nodeIndex = Math.max(0, nodeIndex);
        accessType = accessType == null ? VulkanRenderGraphResourceAccessType.READ : accessType;
    }
}
