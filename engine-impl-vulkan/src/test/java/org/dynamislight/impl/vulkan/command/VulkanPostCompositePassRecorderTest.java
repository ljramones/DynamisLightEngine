package org.dynamislight.impl.vulkan.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VulkanPostCompositePassRecorderTest {
    @Test
    void planModulesMarksAaAndReflectionBoundaries() {
        VulkanPostCompositePassRecorder recorder = new VulkanPostCompositePassRecorder();
        VulkanPostExecutionPlan plan = recorder.planModules(baseInputs(
                true,   // tonemap
                true,   // bloom
                true,   // ssao
                true,   // smaa
                true,   // taa
                true    // reflections
        ));

        assertTrue(plan.activeModules().contains("post.aa.taa_resolve"));
        assertTrue(plan.activeModules().contains("post.reflections.resolve"));
        assertFalse(plan.prunedModules().stream().anyMatch(s -> s.startsWith("post.aa.taa_resolve")));
        assertFalse(plan.prunedModules().stream().anyMatch(s -> s.startsWith("post.reflections.resolve")));
        assertTrue(plan.moduleContracts().stream().anyMatch(c ->
                "post.aa.taa_resolve".equals(c.moduleId()) && "vulkan.aa".equals(c.ownerFeatureId())));
        assertTrue(plan.moduleContracts().stream().anyMatch(c ->
                "post.reflections.resolve".equals(c.moduleId()) && "vulkan.reflections".equals(c.ownerFeatureId())));
        assertTrue(VulkanPostExecutionPlanner.readsForPass(plan).contains("history_color"));
        assertTrue(VulkanPostExecutionPlanner.writesForPass(plan).contains("resolved_color"));
    }

    @Test
    void planModulesPrunesDisabledAaAndReflectionBoundaries() {
        VulkanPostCompositePassRecorder recorder = new VulkanPostCompositePassRecorder();
        VulkanPostExecutionPlan plan = recorder.planModules(baseInputs(
                true,   // tonemap
                false,  // bloom
                false,  // ssao
                false,  // smaa
                false,  // taa
                false   // reflections
        ));

        assertTrue(plan.prunedModules().stream().anyMatch(s -> s.startsWith("post.aa.taa_resolve")));
        assertTrue(plan.prunedModules().stream().anyMatch(s -> s.startsWith("post.reflections.resolve")));
        assertFalse(VulkanPostExecutionPlanner.readsForPass(plan).contains("history_color"));
        assertFalse(VulkanPostExecutionPlanner.readsForPass(plan).contains("planar_capture"));
    }

    private static VulkanRenderCommandRecorder.PostCompositeInputs baseInputs(
            boolean tonemapEnabled,
            boolean bloomEnabled,
            boolean ssaoEnabled,
            boolean smaaEnabled,
            boolean taaEnabled,
            boolean reflectionsEnabled
    ) {
        return new VulkanRenderCommandRecorder.PostCompositeInputs(
                0,
                1,
                1,
                false,
                tonemapEnabled,
                1.0f,
                2.2f,
                ssaoEnabled,
                bloomEnabled,
                1.0f,
                0.8f,
                0.3f,
                1.0f,
                0.02f,
                1.0f,
                smaaEnabled,
                0.5f,
                taaEnabled,
                0.75f,
                false,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f,
                1.0f,
                false,
                0.0f,
                reflectionsEnabled,
                0,
                0.0f,
                0.8f,
                1.0f,
                0.75f,
                0.0f,
                0.65f,
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                new long[1]
        );
    }
}
