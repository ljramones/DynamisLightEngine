package org.dynamislight.impl.vulkan.capability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.impl.vulkan.runtime.config.GiMode;
import org.junit.jupiter.api.Test;

class VulkanGiCapabilityPlannerTest {
    @Test
    void disabledGiPrunesRequestedMode() {
        VulkanGiCapabilityPlan plan = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(QualityTier.HIGH, GiMode.SSGI, false, true)
        );
        assertFalse(plan.giEnabled());
        assertTrue(plan.activeCapabilities().isEmpty());
        assertTrue(plan.prunedCapabilities().stream().anyMatch(s -> s.contains("gi disabled")));
    }

    @Test
    void hybridModeFallsBackWhenRtUnavailable() {
        VulkanGiCapabilityPlan plan = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(QualityTier.ULTRA, GiMode.HYBRID_PROBE_SSGI_RT, true, false)
        );
        assertTrue(plan.giEnabled());
        assertTrue(plan.activeCapabilities().contains("vulkan.gi.probe_grid"));
        assertTrue(plan.activeCapabilities().contains("vulkan.gi.ssgi"));
        assertFalse(plan.activeCapabilities().contains("vulkan.gi.rt_detail"));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(s -> s.contains("rt unavailable")));
    }

    @Test
    void rtgiMultiFallsBackToSsgiWhenRtUnavailable() {
        VulkanGiCapabilityPlan plan = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(QualityTier.ULTRA, GiMode.RTGI_MULTI, true, false)
        );
        assertTrue(plan.giEnabled());
        assertTrue(plan.activeCapabilities().contains("vulkan.gi.ssgi"));
        assertFalse(plan.activeCapabilities().contains("vulkan.gi.rtgi_multi"));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(s -> s.contains("vulkan.gi.rtgi_multi")));
    }

    @Test
    void dedicatedNonRtModesActivateExpectedCapability() {
        VulkanGiCapabilityPlan emissive = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(QualityTier.HIGH, GiMode.EMISSIVE_GI, true, false)
        );
        VulkanGiCapabilityPlan dynamicSky = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(QualityTier.HIGH, GiMode.DYNAMIC_SKY_GI, true, false)
        );
        VulkanGiCapabilityPlan indirectSpecular = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(QualityTier.HIGH, GiMode.INDIRECT_SPECULAR_GI, true, false)
        );
        VulkanGiCapabilityPlan staticLightmaps = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(QualityTier.HIGH, GiMode.STATIC_LIGHTMAPS, true, false)
        );
        VulkanGiCapabilityPlan lightProbes = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(QualityTier.HIGH, GiMode.LIGHT_PROBES_SH, true, false)
        );
        VulkanGiCapabilityPlan irradianceVolumes = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(QualityTier.HIGH, GiMode.IRRADIANCE_VOLUMES, true, false)
        );

        assertTrue(emissive.activeCapabilities().contains("vulkan.gi.emissive"));
        assertTrue(dynamicSky.activeCapabilities().contains("vulkan.gi.dynamic_sky"));
        assertTrue(indirectSpecular.activeCapabilities().contains("vulkan.gi.indirect_specular"));
        assertTrue(staticLightmaps.activeCapabilities().contains("vulkan.gi.static_lightmaps"));
        assertTrue(lightProbes.activeCapabilities().contains("vulkan.gi.light_probes_sh"));
        assertTrue(irradianceVolumes.activeCapabilities().contains("vulkan.gi.irradiance_volumes"));
    }
}
