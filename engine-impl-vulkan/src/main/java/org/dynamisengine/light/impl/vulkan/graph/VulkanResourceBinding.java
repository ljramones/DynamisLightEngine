package org.dynamisengine.light.impl.vulkan.graph;

import org.dynamisengine.light.spi.render.RenderResourceType;

/**
 * Vulkan resource binding mapped from a logical graph resource name.
 */
public sealed interface VulkanResourceBinding permits VulkanImageResourceBinding, VulkanBufferResourceBinding {
    String resourceName();

    RenderResourceType resourceType();
}
