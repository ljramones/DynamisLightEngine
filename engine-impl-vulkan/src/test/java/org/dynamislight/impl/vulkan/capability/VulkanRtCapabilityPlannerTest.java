package org.dynamislight.impl.vulkan.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamislight.api.config.QualityTier;
import org.junit.jupiter.api.Test;

class VulkanRtCapabilityPlannerTest {
    @Test
    void resolvesFullStackWhenAllSignalsActive() {
        VulkanRtCapabilityPlan plan = VulkanRtCapabilityPlanner.plan(
                new VulkanRtCapabilityPlanner.PlanInput(
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
                        true
                )
        );

        assertEquals(VulkanRtCapabilityDescriptorV2.MODE_FULL_STACK.id(), plan.modeId());
        assertTrue(plan.activeCapabilities().contains("vulkan.rt.ao"));
        assertTrue(plan.activeCapabilities().contains("vulkan.rt.translucency_caustics"));
        assertTrue(plan.activeCapabilities().contains("vulkan.rt.bvh_compaction"));
        assertTrue(plan.activeCapabilities().contains("vulkan.rt.denoiser_framework"));
        assertTrue(plan.activeCapabilities().contains("vulkan.rt.hybrid_composition"));
        assertTrue(plan.activeCapabilities().contains("vulkan.rt.quality_tiers"));
        assertTrue(plan.activeCapabilities().contains("vulkan.rt.inline_ray_query"));
        assertTrue(plan.activeCapabilities().contains("vulkan.rt.dedicated_raygen"));
    }

    @Test
    void prunesExpectedFeaturesWhenRtSupportUnavailable() {
        VulkanRtCapabilityPlan plan = VulkanRtCapabilityPlanner.plan(
                new VulkanRtCapabilityPlanner.PlanInput(
                        QualityTier.MEDIUM,
                        false,
                        false,
                        false,
                        false,
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

        assertEquals(VulkanRtCapabilityDescriptorV2.MODE_QUALITY_TIERS.id(), plan.modeId());
        assertTrue(plan.activeCapabilities().contains("vulkan.rt.quality_tiers"));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(v -> v.contains("vulkan.rt.ao")));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(v -> v.contains("vulkan.rt.translucency_caustics")));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(v -> v.contains("vulkan.rt.bvh_compaction")));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(v -> v.contains("vulkan.rt.inline_ray_query")));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(v -> v.contains("vulkan.rt.dedicated_raygen")));
    }
}
