package org.dynamislight.impl.vulkan.capability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.dynamislight.api.config.QualityTier;
import org.junit.jupiter.api.Test;

class VulkanAaPostCapabilityPlannerTest {
    @Test
    void lowTierPrunesSsaoButKeepsCoreAaResolve() {
        var plan = VulkanAaPostCapabilityPlanner.plan(new VulkanAaPostCapabilityPlanner.PlanInput(
                QualityTier.LOW,
                VulkanAaCapabilityMode.TAA,
                true,
                true,
                true,
                true,
                true,
                true,
                true
        ));
        Set<String> active = plan.activeCapabilities().stream()
                .map(c -> c.contract().featureId())
                .collect(Collectors.toSet());

        assertFalse(active.contains("vulkan.post.ssao"));
        assertTrue(active.contains("vulkan.aa.taa"));
        assertTrue(active.contains("vulkan.post.taa_resolve"));
    }

    @Test
    void fxaaModeDisablesTemporalResolve() {
        var plan = VulkanAaPostCapabilityPlanner.plan(new VulkanAaPostCapabilityPlanner.PlanInput(
                QualityTier.HIGH,
                VulkanAaCapabilityMode.FXAA_LOW,
                true,
                true,
                false,
                true,
                false,
                false,
                false
        ));
        Set<String> active = plan.activeCapabilities().stream()
                .map(c -> c.contract().featureId())
                .collect(Collectors.toSet());

        assertTrue(active.contains("vulkan.aa.fxaa_low"));
        assertFalse(active.contains("vulkan.post.taa_resolve"));
    }

    @Test
    void disabledAaPrunesAaCapability() {
        var plan = VulkanAaPostCapabilityPlanner.plan(new VulkanAaPostCapabilityPlanner.PlanInput(
                QualityTier.ULTRA,
                VulkanAaCapabilityMode.TSR,
                false,
                true,
                true,
                true,
                true,
                true,
                true
        ));
        Set<String> active = plan.activeCapabilities().stream()
                .map(c -> c.contract().featureId())
                .collect(Collectors.toSet());

        assertFalse(active.contains("vulkan.aa.tsr"));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(s -> s.contains("vulkan.aa.tsr")));
    }
}
