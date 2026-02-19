package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder facade for shadow + main geometry pass execution.
 *
 * This is a Phase A extraction wrapper with no behavior changes.
 */
final class VulkanShadowMainPassRecorder {
    void record(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.RenderPassInputs inputs,
            List<VulkanRenderCommandRecorder.MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanRenderCommandRecorder.recordShadowAndMainPasses(
                stack,
                commandBuffer,
                inputs,
                meshes,
                dynamicUniformOffset
        );
    }
}
