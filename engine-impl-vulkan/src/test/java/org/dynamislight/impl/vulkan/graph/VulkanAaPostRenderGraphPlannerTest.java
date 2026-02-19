package org.dynamislight.impl.vulkan.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityMode;
import org.dynamislight.impl.vulkan.capability.VulkanAaPostCapabilityPlanner;
import org.junit.jupiter.api.Test;

class VulkanAaPostRenderGraphPlannerTest {
    private final VulkanAaPostRenderGraphPlanner planner = new VulkanAaPostRenderGraphPlanner();

    @Test
    void compilesDefaultAaPostPlanWithoutValidationErrors() {
        VulkanAaPostRenderGraphPlanner.VulkanAaPostRenderGraphCompilation compilation = planner.compile(
                VulkanAaPostCapabilityPlanner.PlanInput.defaults()
        );

        assertFalse(compilation.graphPlan().hasErrors());
        assertFalse(compilation.graphPlan().orderedNodes().isEmpty());
        assertTrue(compilation.externalInputs().contains("scene_color"));
        assertTrue(compilation.externalInputs().contains("velocity"));
    }

    @Test
    void lowTierPlanCompilesAndKeepsSsaoPruned() {
        var input = new VulkanAaPostCapabilityPlanner.PlanInput(
                QualityTier.LOW,
                VulkanAaCapabilityMode.TAA,
                true,
                true,
                true,
                true,
                true,
                true,
                true
        );

        VulkanAaPostRenderGraphPlanner.VulkanAaPostRenderGraphCompilation compilation = planner.compile(input);

        assertFalse(compilation.graphPlan().hasErrors());
        assertTrue(compilation.capabilityPlan().prunedCapabilities().stream().anyMatch(s -> s.contains("vulkan.post.ssao")));
        assertTrue(compilation.graphPlan().orderedNodes().stream().noneMatch(n -> n.featureId().equals("vulkan.post.ssao")));
    }

    @Test
    void missingExternalInputsSurfaceValidationErrors() {
        var input = new VulkanAaPostCapabilityPlanner.PlanInput(
                QualityTier.HIGH,
                VulkanAaCapabilityMode.TAA,
                true,
                true,
                true,
                true,
                true,
                false,
                true
        );

        VulkanAaPostRenderGraphPlanner.VulkanAaPostRenderGraphCompilation compilation = planner.compile(
                input,
                java.util.Set.of("scene_color")
        );

        assertTrue(compilation.graphPlan().hasErrors());
        assertTrue(compilation.graphPlan().validationIssues().stream()
                .anyMatch(i -> i.code().equals("MISSING_PRODUCER") && i.message().contains("velocity")));
    }
}
