package org.dynamislight.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.config.QualityTier;

/**
 * Deterministic planner for Vulkan PBR/shading capability mode/telemetry.
 */
public final class VulkanPbrCapabilityPlanner {
    private VulkanPbrCapabilityPlanner() {
    }

    public static VulkanPbrCapabilityPlan plan(PlanInput input) {
        PlanInput safe = input == null ? PlanInput.defaults() : input;
        boolean specGloss = safe.specularGlossinessEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.MEDIUM.ordinal();
        boolean detailMaps = safe.detailMapsEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean materialLayering = safe.materialLayeringEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean clearCoat = safe.clearCoatEnabled();
        boolean anisotropic = safe.anisotropicEnabled();
        boolean transmission = safe.transmissionEnabled();
        boolean refraction = safe.refractionEnabled();
        boolean vertexBlend = safe.vertexColorBlendEnabled();
        boolean emissiveBloomControl = safe.emissiveBloomControlEnabled();
        boolean energyConservationValidation = safe.energyConservationValidationEnabled();

        List<String> active = new ArrayList<>();
        active.add("vulkan.pbr.metallic_roughness");
        if (specGloss) {
            active.add("vulkan.pbr.specular_glossiness");
        }
        if (detailMaps) {
            active.add("vulkan.pbr.detail_maps");
        }
        if (materialLayering) {
            active.add("vulkan.pbr.material_layering");
        }
        if (clearCoat) {
            active.add("vulkan.pbr.clear_coat");
        }
        if (anisotropic) {
            active.add("vulkan.pbr.anisotropic");
        }
        if (transmission) {
            active.add("vulkan.pbr.transmission");
        }
        if (refraction) {
            active.add("vulkan.pbr.refraction");
        }
        if (vertexBlend) {
            active.add("vulkan.pbr.vertex_color_blend");
        }
        if (emissiveBloomControl) {
            active.add("vulkan.pbr.emissive_bloom_control");
        }
        if (energyConservationValidation) {
            active.add("vulkan.pbr.energy_conservation_validation");
        }

        List<String> pruned = new ArrayList<>();
        if (!safe.specularGlossinessEnabled()) {
            pruned.add("vulkan.pbr.specular_glossiness (disabled)");
        } else if (!specGloss) {
            pruned.add("vulkan.pbr.specular_glossiness (quality tier too low)");
        }
        if (!safe.detailMapsEnabled()) {
            pruned.add("vulkan.pbr.detail_maps (disabled)");
        } else if (!detailMaps) {
            pruned.add("vulkan.pbr.detail_maps (quality tier too low)");
        }
        if (!safe.materialLayeringEnabled()) {
            pruned.add("vulkan.pbr.material_layering (disabled)");
        } else if (!materialLayering) {
            pruned.add("vulkan.pbr.material_layering (quality tier too low)");
        }

        boolean advancedStack = clearCoat || anisotropic || transmission || refraction;
        String modeId = resolveMode(specGloss, detailMaps, materialLayering, advancedStack);
        List<String> signals = List.of(
                "resolvedMode=" + modeId,
                "specularGlossinessEnabled=" + specGloss,
                "detailMapsEnabled=" + detailMaps,
                "materialLayeringEnabled=" + materialLayering,
                "clearCoatEnabled=" + clearCoat,
                "anisotropicEnabled=" + anisotropic,
                "transmissionEnabled=" + transmission,
                "refractionEnabled=" + refraction,
                "vertexColorBlendEnabled=" + vertexBlend,
                "emissiveBloomControlEnabled=" + emissiveBloomControl,
                "energyConservationValidationEnabled=" + energyConservationValidation
        );
        return new VulkanPbrCapabilityPlan(
                modeId,
                specGloss,
                detailMaps,
                materialLayering,
                clearCoat,
                anisotropic,
                transmission,
                refraction,
                vertexBlend,
                emissiveBloomControl,
                energyConservationValidation,
                active,
                pruned,
                signals
        );
    }

    private static String resolveMode(
            boolean specGloss,
            boolean detailMaps,
            boolean materialLayering,
            boolean advancedStack
    ) {
        if (specGloss && detailMaps && materialLayering) {
            return VulkanPbrCapabilityDescriptorV2.MODE_SPECULAR_GLOSSINESS_DETAIL_LAYERING.id();
        }
        if (specGloss && detailMaps) {
            return VulkanPbrCapabilityDescriptorV2.MODE_SPECULAR_GLOSSINESS_DETAIL.id();
        }
        if (advancedStack) {
            return VulkanPbrCapabilityDescriptorV2.MODE_ADVANCED_SURFACE_STACK.id();
        }
        if (specGloss) {
            return VulkanPbrCapabilityDescriptorV2.MODE_SPECULAR_GLOSSINESS.id();
        }
        return VulkanPbrCapabilityDescriptorV2.MODE_METALLIC_ROUGHNESS_BASELINE.id();
    }

    public record PlanInput(
            QualityTier qualityTier,
            boolean specularGlossinessEnabled,
            boolean detailMapsEnabled,
            boolean materialLayeringEnabled,
            boolean clearCoatEnabled,
            boolean anisotropicEnabled,
            boolean transmissionEnabled,
            boolean refractionEnabled,
            boolean vertexColorBlendEnabled,
            boolean emissiveBloomControlEnabled,
            boolean energyConservationValidationEnabled
    ) {
        public PlanInput {
            qualityTier = qualityTier == null ? QualityTier.MEDIUM : qualityTier;
        }

        public static PlanInput defaults() {
            return new PlanInput(
                    QualityTier.MEDIUM,
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
            );
        }
    }
}
