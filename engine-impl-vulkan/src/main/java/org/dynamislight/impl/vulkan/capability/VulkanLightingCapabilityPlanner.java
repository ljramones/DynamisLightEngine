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
        double loadRatio = (double) localLightCount / (double) Math.max(1, safe.localLightBudget());
        boolean budgetEnvelopeBreached = loadRatio > safe.budgetWarnRatioThreshold();
        boolean physUnitsActive = safe.physicallyBasedUnitsEnabled();
        boolean emissiveActive = safe.emissiveMeshEnabled() && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean areaApproxActive = safe.areaApproxEnabled() && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean iesActive = safe.iesProfilesEnabled() && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean cookiesActive = safe.cookiesEnabled() && safe.qualityTier().ordinal() >= QualityTier.MEDIUM.ordinal();
        boolean volumetricShaftsActive = safe.volumetricShaftsEnabled() && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean clusteringActive = safe.clusteringEnabled() && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean lightLayersActive = safe.lightLayersEnabled() && safe.qualityTier().ordinal() >= QualityTier.MEDIUM.ordinal();

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
        if (areaApproxActive) {
            active.add("vulkan.lighting.area_approx");
        }
        if (iesActive) {
            active.add("vulkan.lighting.ies_profiles");
        }
        if (cookiesActive) {
            active.add("vulkan.lighting.cookies");
        }
        if (volumetricShaftsActive) {
            active.add("vulkan.lighting.volumetric_shafts");
        }
        if (clusteringActive) {
            active.add("vulkan.lighting.clustering");
        }
        if (lightLayersActive) {
            active.add("vulkan.lighting.light_layers");
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
        if (!safe.areaApproxEnabled()) {
            pruned.add("vulkan.lighting.area_approx (disabled)");
        } else if (!areaApproxActive) {
            pruned.add("vulkan.lighting.area_approx (quality tier too low)");
        }
        if (!safe.iesProfilesEnabled()) {
            pruned.add("vulkan.lighting.ies_profiles (disabled)");
        } else if (!iesActive) {
            pruned.add("vulkan.lighting.ies_profiles (quality tier too low)");
        }
        if (!safe.cookiesEnabled()) {
            pruned.add("vulkan.lighting.cookies (disabled)");
        } else if (!cookiesActive) {
            pruned.add("vulkan.lighting.cookies (quality tier too low)");
        }
        if (!safe.volumetricShaftsEnabled()) {
            pruned.add("vulkan.lighting.volumetric_shafts (disabled)");
        } else if (!volumetricShaftsActive) {
            pruned.add("vulkan.lighting.volumetric_shafts (quality tier too low)");
        }
        if (!safe.clusteringEnabled()) {
            pruned.add("vulkan.lighting.clustering (disabled)");
        } else if (!clusteringActive) {
            pruned.add("vulkan.lighting.clustering (quality tier too low)");
        }
        if (!safe.lightLayersEnabled()) {
            pruned.add("vulkan.lighting.light_layers (disabled)");
        } else if (!lightLayersActive) {
            pruned.add("vulkan.lighting.light_layers (quality tier too low)");
        }

        boolean anyAdvancedActive = areaApproxActive
                || iesActive
                || cookiesActive
                || volumetricShaftsActive
                || clusteringActive
                || lightLayersActive;
        String mode = resolveMode(budgetActive, physUnitsActive, emissiveActive, anyAdvancedActive);
        List<String> signals = List.of(
                "resolvedMode=" + mode,
                "directional=" + directional,
                "point=" + point,
                "spot=" + spot,
                "localLights=" + localLightCount,
                "localLightBudget=" + safe.localLightBudget(),
                "localLightLoadRatio=" + loadRatio,
                "budgetActive=" + budgetActive,
                "budgetEnvelopeBreached=" + budgetEnvelopeBreached,
                "physicallyBasedUnitsEnabled=" + physUnitsActive,
                "emissiveMeshEnabled=" + emissiveActive,
                "areaApproxEnabled=" + areaApproxActive,
                "iesProfilesEnabled=" + iesActive,
                "cookiesEnabled=" + cookiesActive,
                "volumetricShaftsEnabled=" + volumetricShaftsActive,
                "clusteringEnabled=" + clusteringActive,
                "lightLayersEnabled=" + lightLayersActive
        );
        return new VulkanLightingCapabilityPlan(
                mode,
                directional,
                point,
                spot,
                localLightCount,
                safe.localLightBudget(),
                loadRatio,
                budgetEnvelopeBreached,
                physUnitsActive,
                budgetActive,
                emissiveActive,
                areaApproxActive,
                iesActive,
                cookiesActive,
                volumetricShaftsActive,
                clusteringActive,
                lightLayersActive,
                active,
                pruned,
                signals
        );
    }

    private static String resolveMode(
            boolean budgetActive,
            boolean physUnitsActive,
            boolean emissiveActive,
            boolean anyAdvancedActive
    ) {
        if (emissiveActive && physUnitsActive && budgetActive && anyAdvancedActive) {
            return VulkanLightingCapabilityDescriptorV2.MODE_PHYS_UNITS_BUDGET_EMISSIVE_ADVANCED.id();
        }
        if (emissiveActive && physUnitsActive && budgetActive) {
            return VulkanLightingCapabilityDescriptorV2.MODE_PHYS_UNITS_BUDGET_EMISSIVE.id();
        }
        if (anyAdvancedActive) {
            return VulkanLightingCapabilityDescriptorV2.MODE_ADVANCED_POLICY_STACK.id();
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
            boolean areaApproxEnabled,
            boolean iesProfilesEnabled,
            boolean cookiesEnabled,
            boolean volumetricShaftsEnabled,
            boolean clusteringEnabled,
            boolean lightLayersEnabled,
            int localLightBudget,
            double budgetWarnRatioThreshold
    ) {
        public PlanInput {
            qualityTier = qualityTier == null ? QualityTier.MEDIUM : qualityTier;
            lights = lights == null ? List.of() : List.copyOf(lights);
            localLightBudget = Math.max(1, localLightBudget);
            budgetWarnRatioThreshold = Math.max(1.0, budgetWarnRatioThreshold);
        }

        public static PlanInput defaults() {
            return new PlanInput(
                    QualityTier.MEDIUM,
                    List.of(),
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    8,
                    1.0
            );
        }
    }
}
