package org.dynamislight.impl.vulkan.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class VulkanLightingCapabilityPlannerTest {
    @Test
    void resolvesBaselineWhenOptionalModesDisabled() {
        VulkanLightingCapabilityPlan plan = VulkanLightingCapabilityPlanner.plan(
                new VulkanLightingCapabilityPlanner.PlanInput(
                        QualityTier.MEDIUM,
                        List.of(
                                light("sun", LightType.DIRECTIONAL),
                                light("point0", LightType.POINT)
                        ),
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        8,
                        1.0
                )
        );
        assertEquals(VulkanLightingCapabilityDescriptorV2.MODE_BASELINE_DIRECTIONAL_POINT_SPOT.id(), plan.modeId());
        assertEquals(1, plan.directionalLights());
        assertEquals(1, plan.pointLights());
        assertEquals(0, plan.spotLights());
    }

    @Test
    void resolvesCompositeModeWhenAllHighValueSignalsActive() {
        VulkanLightingCapabilityPlan plan = VulkanLightingCapabilityPlanner.plan(
                new VulkanLightingCapabilityPlanner.PlanInput(
                        QualityTier.ULTRA,
                        List.of(
                                light("sun", LightType.DIRECTIONAL),
                                light("point0", LightType.POINT),
                                light("point1", LightType.POINT),
                                light("point2", LightType.POINT),
                                light("point3", LightType.POINT)
                        ),
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        2,
                        1.0
                )
        );
        assertEquals(VulkanLightingCapabilityDescriptorV2.MODE_PHYS_UNITS_BUDGET_EMISSIVE.id(), plan.modeId());
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.directional_point_spot"));
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.light_budget_priority"));
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.physically_based_units"));
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.emissive_mesh"));
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.area_approx"));
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.ies_profiles"));
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.cookies"));
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.volumetric_shafts"));
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.clustering"));
        assertTrue(plan.activeCapabilities().contains("vulkan.lighting.light_layers"));
        assertTrue(plan.signals().stream().anyMatch(signal -> signal.equals("resolvedMode=phys_units_budget_emissive")));
    }

    @Test
    void emissiveModePrunedOnLowTier() {
        VulkanLightingCapabilityPlan plan = VulkanLightingCapabilityPlanner.plan(
                new VulkanLightingCapabilityPlanner.PlanInput(
                        QualityTier.MEDIUM,
                        List.of(light("sun", LightType.DIRECTIONAL)),
                        false,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        8,
                        1.0
                )
        );
        assertEquals(VulkanLightingCapabilityDescriptorV2.MODE_LIGHT_BUDGET_PRIORITY.id(), plan.modeId());
        assertTrue(plan.prunedCapabilities().stream().anyMatch(value -> value.contains("quality tier too low")));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(value -> value.contains("vulkan.lighting.area_approx")));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(value -> value.contains("vulkan.lighting.ies_profiles")));
        assertTrue(plan.prunedCapabilities().stream().anyMatch(value -> value.contains("vulkan.lighting.volumetric_shafts")));
    }

    private static LightDesc light(String id, LightType type) {
        return new LightDesc(
                id,
                new Vec3(0f, 2f, 0f),
                new Vec3(1f, 1f, 1f),
                1.0f,
                8.0f,
                false,
                null,
                type,
                new Vec3(0f, -1f, 0f),
                15.0f,
                30.0f
        );
    }
}
