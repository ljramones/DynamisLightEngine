package org.dynamisengine.light.impl.vulkan.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamisengine.light.api.config.QualityTier;
import org.junit.jupiter.api.Test;

class VulkanSkyCapabilityPlannerTest {
    @Test
    void resolvesHdriModeWhenIblIsAvailable() {
        VulkanSkyCapabilityPlan plan = VulkanSkyCapabilityPlanner.plan(
                new VulkanSkyCapabilityPlanner.PlanInput(
                        QualityTier.HIGH,
                        "hdri",
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );

        assertEquals("hdri", plan.modeId());
        assertTrue(plan.hdriExpected());
        assertTrue(plan.hdriActive());
        assertTrue(plan.activeCapabilities().contains("vulkan.sky.hdri_skybox"));
    }

    @Test
    void prunesProceduralAndAtmosphereWhenRequestedButUnavailable() {
        VulkanSkyCapabilityPlan plan = VulkanSkyCapabilityPlanner.plan(
                new VulkanSkyCapabilityPlanner.PlanInput(
                        QualityTier.ULTRA,
                        "atmosphere",
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true
                )
        );

        assertEquals("atmosphere", plan.modeId());
        assertTrue(plan.prunedCapabilities().contains("vulkan.sky.procedural_sky"));
        assertTrue(plan.prunedCapabilities().contains("vulkan.sky.atmosphere"));
        assertTrue(plan.prunedCapabilities().contains("vulkan.sky.dynamic_time_of_day"));
        assertTrue(plan.prunedCapabilities().contains("vulkan.sky.volumetric_clouds"));
        assertTrue(plan.prunedCapabilities().contains("vulkan.sky.cloud_shadow_projection"));
        assertTrue(plan.prunedCapabilities().contains("vulkan.sky.aerial_perspective"));
    }
}
