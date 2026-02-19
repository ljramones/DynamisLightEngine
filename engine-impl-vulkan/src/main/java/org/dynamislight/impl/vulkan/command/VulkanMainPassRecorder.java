package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphBuilder;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder for main geometry pass execution.
 */
final class VulkanMainPassRecorder {
    static final String FEATURE_ID = "vulkan.main";
    static final String PASS_ID = "main_geometry";

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

    void declarePasses(
            VulkanExecutableRenderGraphBuilder builder,
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.MainPassInputs inputs,
            List<VulkanRenderCommandRecorder.MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        if (builder == null) {
            return;
        }
        builder.addPass(
                FEATURE_ID,
                new RenderPassContribution(
                        PASS_ID,
                        RenderPassPhase.MAIN,
                        List.of("shadow_depth", "shadow_moment_atlas", "planar_capture"),
                        List.of("scene_color", "velocity", "depth"),
                        false
                ),
                () -> record(stack, commandBuffer, inputs, meshes, dynamicUniformOffset)
        );
    }
}
