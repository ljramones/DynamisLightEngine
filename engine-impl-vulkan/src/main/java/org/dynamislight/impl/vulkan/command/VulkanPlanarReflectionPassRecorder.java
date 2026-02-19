package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder for planar reflection selective capture pass.
 */
final class VulkanPlanarReflectionPassRecorder {
    void record(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.PlanarReflectionPassInputs inputs,
            List<VulkanRenderCommandRecorder.MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanRenderCommandRecorder.recordPlanarReflectionPass(
                stack,
                commandBuffer,
                inputs,
                meshes,
                dynamicUniformOffset
        );
    }
}
