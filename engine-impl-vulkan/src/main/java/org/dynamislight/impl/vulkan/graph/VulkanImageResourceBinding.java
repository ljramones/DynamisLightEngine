package org.dynamislight.impl.vulkan.graph;

import org.dynamislight.spi.render.RenderResourceType;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

/**
 * Image resource binding with mutable tracked layout state.
 */
public final class VulkanImageResourceBinding implements VulkanResourceBinding {
    private final String resourceName;
    private final RenderResourceType resourceType;
    private final long vkImageHandle;
    private final int format;
    private final int aspectMask;
    private int currentLayout;

    public VulkanImageResourceBinding(
            String resourceName,
            RenderResourceType resourceType,
            long vkImageHandle,
            int format,
            int aspectMask,
            int currentLayout
    ) {
        this.resourceName = resourceName == null ? "" : resourceName.trim();
        this.resourceType = resourceType == null ? RenderResourceType.ATTACHMENT : resourceType;
        this.vkImageHandle = vkImageHandle;
        this.format = format;
        this.aspectMask = aspectMask;
        this.currentLayout = currentLayout;
    }

    @Override
    public String resourceName() {
        return resourceName;
    }

    @Override
    public RenderResourceType resourceType() {
        return resourceType;
    }

    public long vkImageHandle() {
        return vkImageHandle;
    }

    public int format() {
        return format;
    }

    public int aspectMask() {
        return aspectMask;
    }

    public int currentLayout() {
        return currentLayout;
    }

    public void updateLayout(int newLayout) {
        currentLayout = newLayout;
    }

    public boolean isValid() {
        return !resourceName.isBlank()
                && vkImageHandle != VK_NULL_HANDLE
                && aspectMask != 0;
    }
}
