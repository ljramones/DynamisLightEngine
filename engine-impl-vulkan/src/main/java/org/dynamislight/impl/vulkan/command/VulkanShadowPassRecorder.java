package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder for shadow pass execution.
 */
final class VulkanShadowPassRecorder {
    void record(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.ShadowPassInputs inputs,
            List<VulkanRenderCommandRecorder.MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanRenderCommandRecorder.recordShadowPasses(
                stack,
                commandBuffer,
                inputs,
                meshes,
                dynamicUniformOffset
        );
    }
}
