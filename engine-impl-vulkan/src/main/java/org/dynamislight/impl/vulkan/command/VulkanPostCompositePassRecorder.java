package org.dynamislight.impl.vulkan.command;

import java.util.ArrayList;
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
    private volatile VulkanPostModulePlan lastModulePlan = new VulkanPostModulePlan(List.of(), List.of());

    VulkanRenderCommandRecorder.PostCompositeState record(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderCommandRecorder.PostCompositeInputs inputs
    ) {
        lastModulePlan = planModules(inputs);
        return VulkanRenderCommandRecorder.executePostCompositePass(
                stack,
                commandBuffer,
                inputs
        );
    }

    VulkanPostModulePlan lastModulePlan() {
        return lastModulePlan;
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
        builder.addPass(
                FEATURE_ID,
                new RenderPassContribution(
                        PASS_ID,
                        RenderPassPhase.POST_MAIN,
                        List.of("scene_color", "velocity", "history_color", "history_velocity", "planar_capture"),
                        List.of("resolved_color", "history_color_next", "history_velocity_next"),
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

    VulkanPostModulePlan planModules(VulkanRenderCommandRecorder.PostCompositeInputs inputs) {
        if (inputs == null) {
            return new VulkanPostModulePlan(List.of(), List.of("post:missing-inputs"));
        }
        List<String> active = new ArrayList<>();
        List<String> pruned = new ArrayList<>();
        addModule(active, pruned, "post.tonemap", inputs.tonemapEnabled(), "disabled");
        addModule(active, pruned, "post.bloom", inputs.bloomEnabled(), "disabled");
        addModule(active, pruned, "post.ssao", inputs.ssaoEnabled(), "disabled");
        addModule(active, pruned, "post.smaa", inputs.smaaEnabled(), "disabled");
        addModule(active, pruned, "post.aa.taa_resolve", inputs.taaEnabled(), "disabled");
        addModule(active, pruned, "post.reflections.resolve", inputs.reflectionsEnabled(), "disabled");
        return new VulkanPostModulePlan(active, pruned);
    }

    private static void addModule(List<String> active, List<String> pruned, String id, boolean enabled, String reason) {
        if (enabled) {
            active.add(id);
        } else {
            pruned.add(id + " (" + reason + ")");
        }
    }
}
