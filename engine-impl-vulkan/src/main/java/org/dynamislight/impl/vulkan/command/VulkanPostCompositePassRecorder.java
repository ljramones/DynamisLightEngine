package org.dynamislight.impl.vulkan.command;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder facade for post composite execution.
 *
 * This is a Phase A extraction wrapper with no behavior changes.
 */
final class VulkanPostCompositePassRecorder {
    VulkanRenderCommandRecorder.PostCompositeState record(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.PostCompositeInputs inputs
    ) {
        return VulkanRenderCommandRecorder.executePostCompositePass(
                stack,
                commandBuffer,
                inputs
        );
    }
}
