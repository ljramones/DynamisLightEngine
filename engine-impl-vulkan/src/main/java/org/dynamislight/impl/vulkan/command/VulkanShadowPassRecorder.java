package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphBuilder;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder for shadow pass execution.
 */
final class VulkanShadowPassRecorder {
    static final String FEATURE_ID = "vulkan.shadow";
    static final String PASS_ID = "shadow_passes";

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

    void declarePasses(
            VulkanExecutableRenderGraphBuilder builder,
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.ShadowPassInputs inputs,
            List<VulkanRenderCommandRecorder.MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        if (builder == null) {
            return;
        }
        List<String> writes = inputs.shadowMomentPipelineRequested()
                ? List.of("shadow_depth", "shadow_moment_atlas")
                : List.of("shadow_depth");
        builder.addPass(
                FEATURE_ID,
                new RenderPassContribution(
                        PASS_ID,
                        RenderPassPhase.PRE_MAIN,
                        List.of(),
                        writes,
                        false
                ),
                () -> record(stack, commandBuffer, inputs, meshes, dynamicUniformOffset)
        );
    }
}
