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
}
