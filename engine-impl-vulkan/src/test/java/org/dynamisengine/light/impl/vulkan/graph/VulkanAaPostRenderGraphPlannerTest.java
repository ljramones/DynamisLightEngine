package org.dynamisengine.light.impl.vulkan.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.impl.vulkan.capability.VulkanAaCapabilityMode;
import org.dynamisengine.light.impl.vulkan.capability.VulkanAaPostCapabilityPlanner;
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
        assertFalse(compilation.barrierPlan().barriers().isEmpty());
        assertTrue(compilation.externalInputs().contains("scene_color"));
        assertTrue(compilation.externalInputs().contains("velocity"));
        assertTrue(compilation.importedResources().stream().anyMatch(r ->
                r.resourceName().equals("history_color")
                        && r.lifetime() == VulkanImportedResource.ResourceLifetime.PERSISTENT
                        && r.provider() == VulkanImportedResource.ResourceProvider.PREVIOUS_FRAME));
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
                true,
                false, false, false, false, false, false, false, false, false, false, false, false, false
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
                true,
                false, false, false, false, false, false, false, false, false, false, false, false, false
        );

        VulkanAaPostRenderGraphPlanner.VulkanAaPostRenderGraphCompilation compilation = planner.compile(
                input,
                java.util.Set.of("scene_color")
        );

        assertTrue(compilation.graphPlan().hasErrors());
        assertTrue(compilation.graphPlan().validationIssues().stream()
                .anyMatch(i -> i.code().equals("MISSING_PRODUCER") && i.message().contains("velocity")));
    }

    @Test
    void compileWithTypedImportsUsesProvidedMetadata() {
        var input = VulkanAaPostCapabilityPlanner.PlanInput.defaults();
        var imports = java.util.List.of(
                new VulkanImportedResource(
                        "scene_color",
                        org.dynamisengine.light.spi.render.RenderResourceType.SAMPLED_IMAGE,
                        VulkanImportedResource.ResourceLifetime.PER_FRAME,
                        VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
                ),
                new VulkanImportedResource(
                        "history_color",
                        org.dynamisengine.light.spi.render.RenderResourceType.SAMPLED_IMAGE,
                        VulkanImportedResource.ResourceLifetime.PERSISTENT,
                        VulkanImportedResource.ResourceProvider.PREVIOUS_FRAME
                ),
                new VulkanImportedResource(
                        "history_velocity",
                        org.dynamisengine.light.spi.render.RenderResourceType.SAMPLED_IMAGE,
                        VulkanImportedResource.ResourceLifetime.PERSISTENT,
                        VulkanImportedResource.ResourceProvider.PREVIOUS_FRAME
                ),
                new VulkanImportedResource(
                        "velocity",
                        org.dynamisengine.light.spi.render.RenderResourceType.SAMPLED_IMAGE,
                        VulkanImportedResource.ResourceLifetime.PER_FRAME,
                        VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
                ),
                new VulkanImportedResource(
                        "depth",
                        org.dynamisengine.light.spi.render.RenderResourceType.SAMPLED_IMAGE,
                        VulkanImportedResource.ResourceLifetime.PER_FRAME,
                        VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
                )
        );

        VulkanAaPostRenderGraphPlanner.VulkanAaPostRenderGraphCompilation compilation = planner.compile(input, imports);

        assertFalse(compilation.graphPlan().hasErrors());
        assertEquals(5, compilation.importedResources().size());
    }
}
