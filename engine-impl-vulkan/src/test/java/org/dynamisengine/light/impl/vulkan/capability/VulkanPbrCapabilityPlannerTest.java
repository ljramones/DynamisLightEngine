package org.dynamisengine.light.impl.vulkan.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamisengine.light.api.config.QualityTier;
import org.junit.jupiter.api.Test;

class VulkanPbrCapabilityPlannerTest {
    @Test
    void resolvesBaselineWhenOptionalModesDisabled() {
        VulkanPbrCapabilityPlan plan = VulkanPbrCapabilityPlanner.plan(
                new VulkanPbrCapabilityPlanner.PlanInput(
                        QualityTier.MEDIUM,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true
                )
        );
        assertEquals(VulkanPbrCapabilityDescriptorV2.MODE_ADVANCED_SURFACE_STACK.id(), plan.modeId());
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.metallic_roughness"));
    }

    @Test
    void resolvesSpecGlossDetailLayeringWhenAllSignalsActive() {
        VulkanPbrCapabilityPlan plan = VulkanPbrCapabilityPlanner.plan(
                new VulkanPbrCapabilityPlanner.PlanInput(
                        QualityTier.ULTRA,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true
                )
        );
        assertEquals(VulkanPbrCapabilityDescriptorV2.MODE_SPECULAR_GLOSSINESS_DETAIL_LAYERING.id(), plan.modeId());
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.specular_glossiness"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.detail_maps"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.material_layering"));
    }

    @Test
    void prunesDetailAndLayeringOnLowTier() {
        VulkanPbrCapabilityPlan plan = VulkanPbrCapabilityPlanner.plan(
                new VulkanPbrCapabilityPlanner.PlanInput(
                        QualityTier.LOW,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );
        assertEquals(VulkanPbrCapabilityDescriptorV2.MODE_METALLIC_ROUGHNESS_BASELINE.id(), plan.modeId());
        assertTrue(plan.prunedCapabilities().stream().anyMatch(value -> value.contains("vulkan.pbr.detail_maps")));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(value -> value.contains("vulkan.pbr.material_layering")));
    }

    @Test
    void resolvesCinematicStackWhenBacklogFeaturesEnabled() {
        VulkanPbrCapabilityPlan plan = VulkanPbrCapabilityPlanner.plan(
                new VulkanPbrCapabilityPlanner.PlanInput(
                        QualityTier.ULTRA,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true
                )
        );
        assertEquals(VulkanPbrCapabilityDescriptorV2.MODE_CINEMATIC_SURFACE_STACK.id(), plan.modeId());
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.subsurface_scattering"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.thin_film_iridescence"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.sheen"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.parallax_occlusion"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.tessellation"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.decals"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.eye_shader"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.hair_shader"));
        assertTrue(plan.activeCapabilities().contains("vulkan.pbr.cloth_shader"));
    }
}
