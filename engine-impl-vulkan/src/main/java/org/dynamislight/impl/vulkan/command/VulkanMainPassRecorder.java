package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder for main geometry pass execution.
 */
final class VulkanMainPassRecorder {
    void record(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.MainPassInputs inputs,
            List<VulkanRenderCommandRecorder.MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanRenderCommandRecorder.recordMainPass(
                stack,
                commandBuffer,
                inputs,
                meshes,
                dynamicUniformOffset
        );
    }
}
