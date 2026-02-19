package org.dynamislight.impl.vulkan.command;

import java.util.List;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphBuilder;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder facade for post composite execution.
 *
 * This is a Phase A extraction wrapper with no behavior changes.
 */
final class VulkanPostCompositePassRecorder {
    static final String FEATURE_ID = "vulkan.post";
    static final String PASS_ID = "post_composite";
    private volatile VulkanPostExecutionPlan lastExecutionPlan = new VulkanPostExecutionPlan(List.of(), List.of(), List.of());

    VulkanRenderCommandRecorder.PostCompositeState record(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.PostCompositeInputs inputs
    ) {
        lastExecutionPlan = planModules(inputs);
        return VulkanRenderCommandRecorder.executePostCompositePass(
                stack,
                commandBuffer,
                inputs
        );
    }

    VulkanPostExecutionPlan lastExecutionPlan() {
        return lastExecutionPlan;
    }

    void declarePasses(
            VulkanExecutableRenderGraphBuilder builder,
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.PostCompositeInputs inputs,
            java.util.function.Consumer<Boolean> postIntermediateInitializedSink,
            java.util.function.Consumer<Boolean> taaHistoryInitializedSink
    ) {
        if (builder == null || inputs == null) {
            return;
        }
        VulkanPostExecutionPlan executionPlan = planModules(inputs);
        builder.addPass(
                FEATURE_ID,
                new RenderPassContribution(
                        PASS_ID,
                        RenderPassPhase.POST_MAIN,
                        VulkanPostExecutionPlanner.readsForPass(executionPlan),
                        VulkanPostExecutionPlanner.writesForPass(executionPlan),
                        false
                ),
                () -> {
                    VulkanRenderCommandRecorder.PostCompositeState state = record(stack, commandBuffer, inputs);
                    if (postIntermediateInitializedSink != null) {
                        postIntermediateInitializedSink.accept(state.postIntermediateInitialized());
                    }
                    if (taaHistoryInitializedSink != null) {
                        taaHistoryInitializedSink.accept(state.taaHistoryInitialized());
                    }
                }
        );
    }

    VulkanPostExecutionPlan planModules(VulkanRenderCommandRecorder.PostCompositeInputs inputs) {
        return VulkanPostExecutionPlanner.plan(inputs);
    }
}
