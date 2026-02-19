package org.dynamislight.impl.vulkan.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds module-owned post execution contracts from runtime inputs.
 */
final class VulkanPostExecutionPlanner {
    private VulkanPostExecutionPlanner() {
    }

    static VulkanPostExecutionPlan plan(VulkanRenderCommandRecorder.PostCompositeInputs inputs) {
        if (inputs == null) {
            return new VulkanPostExecutionPlan(List.of(), List.of("post:missing-inputs"), List.of());
        }
        List<VulkanPostExecutionModuleContract> contracts = List.of(
                module("post.tonemap", "vulkan.post", inputs.tonemapEnabled(), "disabled",
                        List.of("scene_color"), List.of("resolved_color")),
                module("post.bloom", "vulkan.post", inputs.bloomEnabled(), "disabled",
                        List.of("scene_color"), List.of("resolved_color")),
                module("post.ssao", "vulkan.post", inputs.ssaoEnabled(), "disabled",
                        List.of("scene_color", "scene_depth"), List.of("resolved_color")),
                module("post.smaa", "vulkan.post", inputs.smaaEnabled(), "disabled",
                        List.of("scene_color"), List.of("resolved_color")),
                module("post.aa.taa_resolve", "vulkan.aa", inputs.taaEnabled(), "disabled",
                        List.of("scene_color", "velocity", "history_color", "history_velocity"),
                        List.of("resolved_color", "history_color_next", "history_velocity_next")),
                module("post.reflections.resolve", "vulkan.reflections", inputs.reflectionsEnabled(), "disabled",
                        List.of("scene_color", "planar_capture"), List.of("resolved_color"))
        );
        List<String> active = contracts.stream()
                .filter(VulkanPostExecutionModuleContract::enabled)
                .map(VulkanPostExecutionModuleContract::moduleId)
                .toList();
        List<String> pruned = contracts.stream()
                .filter(contract -> !contract.enabled())
                .map(contract -> contract.moduleId() + " (" + contract.reason() + ")")
                .toList();
        return new VulkanPostExecutionPlan(active, pruned, contracts);
    }

    static List<String> readsForPass(VulkanPostExecutionPlan plan) {
        if (plan == null || plan.moduleContracts().isEmpty()) {
            return List.of("scene_color");
        }
        Set<String> reads = new LinkedHashSet<>();
        for (VulkanPostExecutionModuleContract contract : plan.moduleContracts()) {
            if (!contract.enabled()) {
                continue;
            }
            reads.addAll(contract.reads());
        }
        if (reads.isEmpty()) {
            reads.add("scene_color");
        }
        return List.copyOf(reads);
    }

    static List<String> writesForPass(VulkanPostExecutionPlan plan) {
        if (plan == null || plan.moduleContracts().isEmpty()) {
            return List.of("resolved_color");
        }
        Set<String> writes = new LinkedHashSet<>();
        for (VulkanPostExecutionModuleContract contract : plan.moduleContracts()) {
            if (!contract.enabled()) {
                continue;
            }
            writes.addAll(contract.writes());
        }
        if (writes.isEmpty()) {
            writes.add("resolved_color");
        } else if (!writes.contains("resolved_color")) {
            // post composite always resolves final color even when only temporal/history lanes are active.
            List<String> mutable = new ArrayList<>(writes);
            mutable.add("resolved_color");
            writes = new LinkedHashSet<>(mutable);
        }
        return List.copyOf(writes);
    }

    private static VulkanPostExecutionModuleContract module(
            String moduleId,
            String ownerFeatureId,
            boolean enabled,
            String reason,
            List<String> reads,
            List<String> writes
    ) {
        return new VulkanPostExecutionModuleContract(
                moduleId,
                ownerFeatureId,
                VulkanPostCompositePassRecorder.PASS_ID,
                enabled,
                reason,
                reads,
                writes
        );
    }
}
