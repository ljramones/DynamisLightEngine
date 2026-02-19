package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphBuilder;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder for planar reflection selective capture pass.
 */
final class VulkanPlanarReflectionPassRecorder {
    static final String FEATURE_ID = "vulkan.reflections.planar";
    static final String PASS_ID = "planar_capture";

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

    void declarePasses(
            VulkanExecutableRenderGraphBuilder builder,
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.PlanarReflectionPassInputs inputs,
            List<VulkanRenderCommandRecorder.MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        if (builder == null) {
            return;
        }
        if (!VulkanRenderCommandRecorder.isPlanarReflectionPassRequested(inputs.reflectionsMode(), inputs.planarCaptureImage())) {
            return;
        }
        builder.addPass(
                FEATURE_ID,
                new RenderPassContribution(
                        PASS_ID,
                        RenderPassPhase.PRE_MAIN,
                        List.of("scene_color"),
                        List.of("planar_capture"),
                        false
                ),
                () -> record(stack, commandBuffer, inputs, meshes, dynamicUniformOffset)
        );
    }
}
