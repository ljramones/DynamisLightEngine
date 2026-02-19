package org.dynamislight.impl.vulkan.graph;

import org.dynamislight.spi.render.RenderResourceType;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

/**
 * Buffer resource binding.
 */
public final class VulkanBufferResourceBinding implements VulkanResourceBinding {
    private final String resourceName;
    private final RenderResourceType resourceType;
    private final long vkBufferHandle;
    private final long sizeBytes;

    public VulkanBufferResourceBinding(
            String resourceName,
            RenderResourceType resourceType,
            long vkBufferHandle,
            long sizeBytes
    ) {
        this.resourceName = resourceName == null ? "" : resourceName.trim();
        this.resourceType = resourceType == null ? RenderResourceType.STORAGE_BUFFER : resourceType;
        this.vkBufferHandle = vkBufferHandle;
        this.sizeBytes = sizeBytes;
    }

    @Override
    public String resourceName() {
        return resourceName;
    }

    @Override
    public RenderResourceType resourceType() {
        return resourceType;
    }

    public long vkBufferHandle() {
        return vkBufferHandle;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public boolean isValid() {
        return !resourceName.isBlank()
                && vkBufferHandle != VK_NULL_HANDLE
                && sizeBytes >= 0L;
    }
}
