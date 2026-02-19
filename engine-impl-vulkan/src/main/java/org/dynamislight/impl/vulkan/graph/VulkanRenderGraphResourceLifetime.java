package org.dynamislight.impl.vulkan.graph;

/**
 * Resource lifetime boundaries across compiled graph nodes.
 *
 * @param resourceName symbolic resource name
 * @param firstNodeIndex first usage index (inclusive)
 * @param lastNodeIndex last usage index (inclusive)
 */
public record VulkanRenderGraphResourceLifetime(
        String resourceName,
        int firstNodeIndex,
        int lastNodeIndex
) {
    public VulkanRenderGraphResourceLifetime {
        resourceName = resourceName == null ? "" : resourceName.trim();
        firstNodeIndex = Math.max(0, firstNodeIndex);
        lastNodeIndex = Math.max(firstNodeIndex, lastNodeIndex);
    }
}
