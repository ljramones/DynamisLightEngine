package org.dynamislight.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;

/**
 * Deterministic planner for Vulkan lighting capability mode/telemetry.
 */
public final class VulkanLightingCapabilityPlanner {
    private VulkanLightingCapabilityPlanner() {
    }

    public static VulkanLightingCapabilityPlan plan(PlanInput input) {
        PlanInput safe = input == null ? PlanInput.defaults() : input;
        int directional = 0;
        int point = 0;
        int spot = 0;
        for (LightDesc light : safe.lights()) {
            if (light == null) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            switch (type) {
                case DIRECTIONAL -> directional++;
                case POINT -> point++;
                case SPOT -> spot++;
            }
        }

        int localLightCount = point + spot;
        boolean budgetActive = safe.prioritizationEnabled() || localLightCount > safe.localLightBudget();
        boolean physUnitsActive = safe.physicallyBasedUnitsEnabled();
        boolean emissiveActive = safe.emissiveMeshEnabled() && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();

        List<String> active = new ArrayList<>();
        active.add("vulkan.lighting.directional_point_spot");
        if (budgetActive) {
            active.add("vulkan.lighting.light_budget_priority");
        }
        if (physUnitsActive) {
            active.add("vulkan.lighting.physically_based_units");
        }
        if (emissiveActive) {
            active.add("vulkan.lighting.emissive_mesh");
        }

        List<String> pruned = new ArrayList<>();
        if (!budgetActive) {
            pruned.add("vulkan.lighting.light_budget_priority (within local light budget)");
        }
        if (!physUnitsActive) {
            pruned.add("vulkan.lighting.physically_based_units (disabled)");
        }
        if (!safe.emissiveMeshEnabled()) {
            pruned.add("vulkan.lighting.emissive_mesh (disabled)");
        } else if (!emissiveActive) {
            pruned.add("vulkan.lighting.emissive_mesh (quality tier too low)");
        }

        String mode = resolveMode(budgetActive, physUnitsActive, emissiveActive);
        List<String> signals = List.of(
                "resolvedMode=" + mode,
                "directional=" + directional,
                "point=" + point,
                "spot=" + spot,
                "localLights=" + localLightCount,
                "localLightBudget=" + safe.localLightBudget(),
                "budgetActive=" + budgetActive,
                "physicallyBasedUnitsEnabled=" + physUnitsActive,
                "emissiveMeshEnabled=" + emissiveActive
        );
        return new VulkanLightingCapabilityPlan(
                mode,
                directional,
                point,
                spot,
                physUnitsActive,
                budgetActive,
                emissiveActive,
                active,
                pruned,
                signals
        );
    }

    private static String resolveMode(boolean budgetActive, boolean physUnitsActive, boolean emissiveActive) {
        if (emissiveActive && physUnitsActive && budgetActive) {
            return VulkanLightingCapabilityDescriptorV2.MODE_PHYS_UNITS_BUDGET_EMISSIVE.id();
        }
        if (emissiveActive) {
            return VulkanLightingCapabilityDescriptorV2.MODE_EMISSIVE_MESH.id();
        }
        if (physUnitsActive) {
            return VulkanLightingCapabilityDescriptorV2.MODE_PHYSICALLY_BASED_UNITS.id();
        }
        if (budgetActive) {
            return VulkanLightingCapabilityDescriptorV2.MODE_LIGHT_BUDGET_PRIORITY.id();
        }
        return VulkanLightingCapabilityDescriptorV2.MODE_BASELINE_DIRECTIONAL_POINT_SPOT.id();
    }

    public record PlanInput(
            QualityTier qualityTier,
            List<LightDesc> lights,
            boolean physicallyBasedUnitsEnabled,
            boolean prioritizationEnabled,
            boolean emissiveMeshEnabled,
            int localLightBudget
    ) {
        public PlanInput {
            qualityTier = qualityTier == null ? QualityTier.MEDIUM : qualityTier;
            lights = lights == null ? List.of() : List.copyOf(lights);
            localLightBudget = Math.max(1, localLightBudget);
        }

        public static PlanInput defaults() {
            return new PlanInput(
                    QualityTier.MEDIUM,
                    List.of(),
                    false,
                    true,
                    false,
                    8
            );
        }
    }
}
