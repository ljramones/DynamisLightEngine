package org.dynamislight.impl.vulkan.graph;

import org.dynamislight.spi.render.RenderResourceType;

/**
 * Vulkan resource binding mapped from a logical graph resource name.
 */
public sealed interface VulkanResourceBinding permits VulkanImageResourceBinding, VulkanBufferResourceBinding {
    String resourceName();

    RenderResourceType resourceType();
}
