package org.dynamisengine.light.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import org.dynamisengine.light.api.config.QualityTier;

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
        boolean subsurfaceScattering = safe.subsurfaceScatteringEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean thinFilmIridescence = safe.thinFilmIridescenceEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean sheen = safe.sheenEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean parallaxOcclusion = safe.parallaxOcclusionEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean tessellation = safe.tessellationEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.ULTRA.ordinal();
        boolean decals = safe.decalsEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean eyeShader = safe.eyeShaderEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean hairShader = safe.hairShaderEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
        boolean clothShader = safe.clothShaderEnabled()
                && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal();
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
        if (subsurfaceScattering) {
            active.add("vulkan.pbr.subsurface_scattering");
        }
        if (thinFilmIridescence) {
            active.add("vulkan.pbr.thin_film_iridescence");
        }
        if (sheen) {
            active.add("vulkan.pbr.sheen");
        }
        if (parallaxOcclusion) {
            active.add("vulkan.pbr.parallax_occlusion");
        }
        if (tessellation) {
            active.add("vulkan.pbr.tessellation");
        }
        if (decals) {
            active.add("vulkan.pbr.decals");
        }
        if (eyeShader) {
            active.add("vulkan.pbr.eye_shader");
        }
        if (hairShader) {
            active.add("vulkan.pbr.hair_shader");
        }
        if (clothShader) {
            active.add("vulkan.pbr.cloth_shader");
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
        if (!safe.subsurfaceScatteringEnabled()) {
            pruned.add("vulkan.pbr.subsurface_scattering (disabled)");
        } else if (!subsurfaceScattering) {
            pruned.add("vulkan.pbr.subsurface_scattering (quality tier too low)");
        }
        if (!safe.thinFilmIridescenceEnabled()) {
            pruned.add("vulkan.pbr.thin_film_iridescence (disabled)");
        } else if (!thinFilmIridescence) {
            pruned.add("vulkan.pbr.thin_film_iridescence (quality tier too low)");
        }
        if (!safe.sheenEnabled()) {
            pruned.add("vulkan.pbr.sheen (disabled)");
        } else if (!sheen) {
            pruned.add("vulkan.pbr.sheen (quality tier too low)");
        }
        if (!safe.parallaxOcclusionEnabled()) {
            pruned.add("vulkan.pbr.parallax_occlusion (disabled)");
        } else if (!parallaxOcclusion) {
            pruned.add("vulkan.pbr.parallax_occlusion (quality tier too low)");
        }
        if (!safe.tessellationEnabled()) {
            pruned.add("vulkan.pbr.tessellation (disabled)");
        } else if (!tessellation) {
            pruned.add("vulkan.pbr.tessellation (quality tier too low)");
        }
        if (!safe.decalsEnabled()) {
            pruned.add("vulkan.pbr.decals (disabled)");
        } else if (!decals) {
            pruned.add("vulkan.pbr.decals (quality tier too low)");
        }
        if (!safe.eyeShaderEnabled()) {
            pruned.add("vulkan.pbr.eye_shader (disabled)");
        } else if (!eyeShader) {
            pruned.add("vulkan.pbr.eye_shader (quality tier too low)");
        }
        if (!safe.hairShaderEnabled()) {
            pruned.add("vulkan.pbr.hair_shader (disabled)");
        } else if (!hairShader) {
            pruned.add("vulkan.pbr.hair_shader (quality tier too low)");
        }
        if (!safe.clothShaderEnabled()) {
            pruned.add("vulkan.pbr.cloth_shader (disabled)");
        } else if (!clothShader) {
            pruned.add("vulkan.pbr.cloth_shader (quality tier too low)");
        }

        boolean advancedStack = clearCoat || anisotropic || transmission || refraction;
        boolean cinematicStack = subsurfaceScattering || thinFilmIridescence || sheen
                || parallaxOcclusion || tessellation || decals || eyeShader || hairShader || clothShader;
        String modeId = resolveMode(specGloss, detailMaps, materialLayering, advancedStack, cinematicStack);
        List<String> signals = List.of(
                "resolvedMode=" + modeId,
                "specularGlossinessEnabled=" + specGloss,
                "detailMapsEnabled=" + detailMaps,
                "materialLayeringEnabled=" + materialLayering,
                "clearCoatEnabled=" + clearCoat,
                "anisotropicEnabled=" + anisotropic,
                "transmissionEnabled=" + transmission,
                "refractionEnabled=" + refraction,
                "subsurfaceScatteringEnabled=" + subsurfaceScattering,
                "thinFilmIridescenceEnabled=" + thinFilmIridescence,
                "sheenEnabled=" + sheen,
                "parallaxOcclusionEnabled=" + parallaxOcclusion,
                "tessellationEnabled=" + tessellation,
                "decalsEnabled=" + decals,
                "eyeShaderEnabled=" + eyeShader,
                "hairShaderEnabled=" + hairShader,
                "clothShaderEnabled=" + clothShader,
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
                subsurfaceScattering,
                thinFilmIridescence,
                sheen,
                parallaxOcclusion,
                tessellation,
                decals,
                eyeShader,
                hairShader,
                clothShader,
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
            boolean advancedStack,
            boolean cinematicStack
    ) {
        if (cinematicStack) {
            return VulkanPbrCapabilityDescriptorV2.MODE_CINEMATIC_SURFACE_STACK.id();
        }
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
            boolean subsurfaceScatteringEnabled,
            boolean thinFilmIridescenceEnabled,
            boolean sheenEnabled,
            boolean parallaxOcclusionEnabled,
            boolean tessellationEnabled,
            boolean decalsEnabled,
            boolean eyeShaderEnabled,
            boolean hairShaderEnabled,
            boolean clothShaderEnabled,
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
