package org.dynamislight.impl.vulkan.vfx;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.command.VulkanIndirectDrawBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;

/**
 * Dedicated indirect command resources for VFX draws.
 */
public final class VulkanVfxIndirectResources {
    private static final int MAX_VFX_DRAW_CALLS = 4096;

    private final VulkanIndirectDrawBuffer indirectBuffer;
    private final int maxDrawCalls;

    private VulkanVfxIndirectResources(VulkanIndirectDrawBuffer indirectBuffer, int maxDrawCalls) {
        this.indirectBuffer = indirectBuffer;
        this.maxDrawCalls = maxDrawCalls;
    }

    public static VulkanVfxIndirectResources create(VkDevice device, VkPhysicalDevice physicalDevice)
            throws EngineException {
        VulkanIndirectDrawBuffer buffer = VulkanIndirectDrawBuffer.create(device, physicalDevice, MAX_VFX_DRAW_CALLS);
        return new VulkanVfxIndirectResources(buffer, MAX_VFX_DRAW_CALLS);
    }

    public VulkanIndirectDrawBuffer indirectBuffer() {
        return indirectBuffer;
    }

    public int maxDrawCalls() {
        return maxDrawCalls;
    }

    public void destroy() {
        indirectBuffer.destroy();
    }
}
