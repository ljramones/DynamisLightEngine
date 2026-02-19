package org.dynamislight.impl.vulkan.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamislight.api.config.QualityTier;
import org.junit.jupiter.api.Test;

class VulkanShadowCapabilityPlannerTest {
    @Test
    void mapsFilterPathToCoreModesWhenNoHigherPrioritySignal() {
        assertEquals(
                VulkanShadowCapabilityDescriptorV2.MODE_PCF,
                plan("pcf", "off", false).mode()
        );
        assertEquals(
                VulkanShadowCapabilityDescriptorV2.MODE_PCSS,
                plan("pcss", "off", false).mode()
        );
        assertEquals(
                VulkanShadowCapabilityDescriptorV2.MODE_VSM,
                plan("vsm", "off", false).mode()
        );
        assertEquals(
                VulkanShadowCapabilityDescriptorV2.MODE_EVSM,
                plan("evsm", "off", false).mode()
        );
    }

    @Test
    void rtDenoisedOrHybridTakesPriorityOverFilterPath() {
        VulkanShadowCapabilityPlanner.PlanInput denoised = new VulkanShadowCapabilityPlanner.PlanInput(
                QualityTier.ULTRA,
                "evsm",
                false,
                "bvh_dedicated",
                0,
                0,
                0,
                true,
                false,
                false,
                false,
                false,
                false
        );
        VulkanShadowCapabilityPlanner.Plan denoisedPlan = VulkanShadowCapabilityPlanner.plan(denoised);
        assertEquals(VulkanShadowCapabilityDescriptorV2.MODE_RT_DENOISED, denoisedPlan.mode());

        VulkanShadowCapabilityPlanner.PlanInput hybrid = new VulkanShadowCapabilityPlanner.PlanInput(
                QualityTier.ULTRA,
                "evsm",
                true,
                "rt_native_denoised",
                0,
                0,
                0,
                true,
                false,
                false,
                false,
                false,
                false
        );
        VulkanShadowCapabilityPlanner.Plan hybridPlan = VulkanShadowCapabilityPlanner.plan(hybrid);
        assertEquals(VulkanShadowCapabilityDescriptorV2.MODE_HYBRID_CASCADE_CONTACT_RT, hybridPlan.mode());
    }

    @Test
    void specializationModesResolveWithExpectedPrecedence() {
        VulkanShadowCapabilityPlanner.PlanInput input = new VulkanShadowCapabilityPlanner.PlanInput(
                QualityTier.HIGH,
                "pcss",
                false,
                "off",
                4,
                8,
                12,
                true,
                true,
                true,
                true,
                true,
                true
        );
        VulkanShadowCapabilityPlanner.Plan plan = VulkanShadowCapabilityPlanner.plan(input);
        assertEquals(VulkanShadowCapabilityDescriptorV2.MODE_DISTANCE_FIELD_SOFT, plan.mode());
        assertTrue(plan.signals().stream().anyMatch(s -> s.equals("resolvedMode=distance_field_soft")));
    }

    @Test
    void fallsBackToAtlasCadenceWhenSchedulerOrLayerBudgetActive() {
        VulkanShadowCapabilityPlanner.Plan plan = VulkanShadowCapabilityPlanner.plan(new VulkanShadowCapabilityPlanner.PlanInput(
                QualityTier.MEDIUM,
                "pcf",
                false,
                "off",
                2,
                6,
                0,
                true,
                false,
                false,
                false,
                false,
                false
        ));
        assertEquals(VulkanShadowCapabilityDescriptorV2.MODE_LOCAL_ATLAS_CADENCE, plan.mode());
    }

    private static VulkanShadowCapabilityPlanner.Plan plan(String filterPath, String rtMode, boolean contactShadows) {
        return VulkanShadowCapabilityPlanner.plan(new VulkanShadowCapabilityPlanner.PlanInput(
                QualityTier.HIGH,
                filterPath,
                contactShadows,
                rtMode,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                false
        ));
    }
}
