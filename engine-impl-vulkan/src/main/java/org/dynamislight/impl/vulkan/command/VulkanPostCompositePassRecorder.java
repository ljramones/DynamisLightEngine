package org.dynamislight.impl.vulkan.command;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Feature-owned recorder facade for post composite execution.
 *
 * This is a Phase A extraction wrapper with no behavior changes.
 */
final class VulkanPostCompositePassRecorder {
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
