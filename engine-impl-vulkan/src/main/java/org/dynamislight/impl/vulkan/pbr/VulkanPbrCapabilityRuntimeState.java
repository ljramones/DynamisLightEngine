package org.dynamislight.impl.vulkan.pbr;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.PbrCapabilityDiagnostics;
import org.dynamislight.api.runtime.PbrPromotionDiagnostics;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;
import org.dynamislight.impl.vulkan.capability.VulkanPbrCapabilityPlan;
import org.dynamislight.impl.vulkan.capability.VulkanPbrCapabilityPlanner;

/**
 * Runtime holder for PBR capability mode telemetry and typed diagnostics.
 */
public final class VulkanPbrCapabilityRuntimeState {
    private boolean specularGlossinessEnabled;
    private boolean detailMapsEnabled;
    private boolean materialLayeringEnabled;
    private boolean clearCoatEnabled;
    private boolean anisotropicEnabled;
    private boolean transmissionEnabled;
    private boolean refractionEnabled;
    private boolean subsurfaceScatteringEnabled;
    private boolean thinFilmIridescenceEnabled;
    private boolean sheenEnabled;
    private boolean parallaxOcclusionEnabled;
    private boolean tessellationEnabled;
    private boolean decalsEnabled;
    private boolean eyeShaderEnabled;
    private boolean hairShaderEnabled;
    private boolean clothShaderEnabled;
    private boolean vertexColorBlendEnabled;
    private boolean emissiveBloomControlEnabled;
    private boolean energyConservationValidationEnabled;
    private int promotionReadyMinFrames = 6;
    private int warnMinFrames = 3;
    private int warnCooldownFrames = 120;
    private int advancedWarnMinFeatureCount = 2;
    private int stableStreak;
    private int highStreak;
    private int warnCooldownRemaining;
    private int cinematicStableStreak;
    private int cinematicHighStreak;
    private int cinematicWarnCooldownRemaining;
    private boolean envelopeBreachedLastFrame;
    private boolean cinematicEnvelopeBreachedLastFrame;
    private String modeLastFrame = "metallic_roughness_baseline";
    private List<String> activeCapabilitiesLastFrame = List.of();
    private List<String> prunedCapabilitiesLastFrame = List.of();
    private List<String> signalsLastFrame = List.of();
    private boolean promotionReadyLastFrame;
    private boolean cinematicPromotionReadyLastFrame;

    public void reset() {
        stableStreak = 0;
        modeLastFrame = "metallic_roughness_baseline";
        activeCapabilitiesLastFrame = List.of();
        prunedCapabilitiesLastFrame = List.of();
        signalsLastFrame = List.of();
        promotionReadyLastFrame = false;
        highStreak = 0;
        warnCooldownRemaining = 0;
        cinematicStableStreak = 0;
        cinematicHighStreak = 0;
        cinematicWarnCooldownRemaining = 0;
        envelopeBreachedLastFrame = false;
        cinematicEnvelopeBreachedLastFrame = false;
        cinematicPromotionReadyLastFrame = false;
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        specularGlossinessEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.specularGlossinessEnabled", "false")
        );
        detailMapsEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.detailMapsEnabled", "false")
        );
        materialLayeringEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.materialLayeringEnabled", "false")
        );
        clearCoatEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.clearCoatEnabled", "true")
        );
        anisotropicEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.anisotropicEnabled", "true")
        );
        transmissionEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.transmissionEnabled", "true")
        );
        refractionEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.refractionEnabled", "true")
        );
        subsurfaceScatteringEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.subsurfaceScatteringEnabled", "false")
        );
        thinFilmIridescenceEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.thinFilmIridescenceEnabled", "false")
        );
        sheenEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.sheenEnabled", "false")
        );
        parallaxOcclusionEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.parallaxOcclusionEnabled", "false")
        );
        tessellationEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.tessellationEnabled", "false")
        );
        decalsEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.decalsEnabled", "false")
        );
        eyeShaderEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.eyeShaderEnabled", "false")
        );
        hairShaderEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.hairShaderEnabled", "false")
        );
        clothShaderEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.clothShaderEnabled", "false")
        );
        vertexColorBlendEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.vertexColorBlendEnabled", "true")
        );
        emissiveBloomControlEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.emissiveBloomControlEnabled", "true")
        );
        energyConservationValidationEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.pbr.energyConservationValidationEnabled", "true")
        );
        promotionReadyMinFrames = parseInt(
                safe.get("vulkan.pbr.promotionReadyMinFrames"),
                promotionReadyMinFrames,
                1,
                100_000
        );
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.pbr.warnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.pbr.warnCooldownFrames", warnCooldownFrames, 0, 100_000);
        advancedWarnMinFeatureCount = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.pbr.advancedWarnMinFeatureCount", advancedWarnMinFeatureCount, 0, 100_000);
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions != null && backendOptions.containsKey("vulkan.pbr.promotionReadyMinFrames")) {
            return;
        }
        QualityTier safeTier = tier == null ? QualityTier.MEDIUM : tier;
        promotionReadyMinFrames = switch (safeTier) {
            case LOW -> 3;
            case MEDIUM -> 4;
            case HIGH -> 5;
            case ULTRA -> 6;
        };
    }

    public void emitFrameWarning(QualityTier qualityTier, List<EngineWarning> warnings) {
        VulkanPbrCapabilityPlan plan = VulkanPbrCapabilityPlanner.plan(
                new VulkanPbrCapabilityPlanner.PlanInput(
                        qualityTier,
                        specularGlossinessEnabled,
                        detailMapsEnabled,
                        materialLayeringEnabled,
                        clearCoatEnabled,
                        anisotropicEnabled,
                        transmissionEnabled,
                        refractionEnabled,
                        subsurfaceScatteringEnabled,
                        thinFilmIridescenceEnabled,
                        sheenEnabled,
                        parallaxOcclusionEnabled,
                        tessellationEnabled,
                        decalsEnabled,
                        eyeShaderEnabled,
                        hairShaderEnabled,
                        clothShaderEnabled,
                        vertexColorBlendEnabled,
                        emissiveBloomControlEnabled,
                        energyConservationValidationEnabled
                )
        );
        modeLastFrame = plan.modeId();
        activeCapabilitiesLastFrame = plan.activeCapabilities();
        prunedCapabilitiesLastFrame = plan.prunedCapabilities();
        signalsLastFrame = plan.signals();

        warnings.add(new EngineWarning(
                "PBR_CAPABILITY_MODE_ACTIVE",
                "PBR capability mode active (mode=" + plan.modeId()
                        + ", active=[" + String.join(", ", plan.activeCapabilities()) + "]"
                        + ", pruned=[" + String.join(", ", plan.prunedCapabilities()) + "]"
                        + ", signals=[" + String.join(", ", plan.signals()) + "])"
        ));
        warnings.add(new EngineWarning(
                "PBR_POLICY",
                "PBR policy (specularGlossiness=" + plan.specularGlossinessEnabled()
                        + ", detailMaps=" + plan.detailMapsEnabled()
                        + ", materialLayering=" + plan.materialLayeringEnabled()
                        + ", clearCoat=" + plan.clearCoatEnabled()
                        + ", anisotropic=" + plan.anisotropicEnabled()
                        + ", transmission=" + plan.transmissionEnabled()
                        + ", refraction=" + plan.refractionEnabled()
                        + ", subsurfaceScattering=" + plan.subsurfaceScatteringEnabled()
                        + ", thinFilmIridescence=" + plan.thinFilmIridescenceEnabled()
                        + ", sheen=" + plan.sheenEnabled()
                        + ", parallaxOcclusion=" + plan.parallaxOcclusionEnabled()
                        + ", tessellation=" + plan.tessellationEnabled()
                        + ", decals=" + plan.decalsEnabled()
                        + ", eyeShader=" + plan.eyeShaderEnabled()
                        + ", hairShader=" + plan.hairShaderEnabled()
                        + ", clothShader=" + plan.clothShaderEnabled()
                        + ", vertexColorBlend=" + plan.vertexColorBlendEnabled()
                        + ", emissiveBloomControl=" + plan.emissiveBloomControlEnabled()
                        + ", energyConservationValidation=" + plan.energyConservationValidationEnabled() + ")"
        ));

        int activeAdvancedFeatureCount = 0;
        if (plan.specularGlossinessEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.detailMapsEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.materialLayeringEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.clearCoatEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.anisotropicEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.transmissionEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.refractionEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.subsurfaceScatteringEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.thinFilmIridescenceEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.sheenEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.parallaxOcclusionEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.tessellationEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.decalsEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.eyeShaderEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.hairShaderEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.clothShaderEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.vertexColorBlendEnabled()) activeAdvancedFeatureCount += 1;
        if (plan.emissiveBloomControlEnabled()) activeAdvancedFeatureCount += 1;

        boolean risk = activeAdvancedFeatureCount < advancedWarnMinFeatureCount
                || !plan.energyConservationValidationEnabled();
        if (risk) {
            highStreak += 1;
            stableStreak = 0;
            if (warnCooldownRemaining > 0) {
                warnCooldownRemaining -= 1;
            }
        } else {
            highStreak = 0;
            stableStreak += 1;
            if (warnCooldownRemaining > 0) {
                warnCooldownRemaining -= 1;
            }
        }
        envelopeBreachedLastFrame = risk && highStreak >= warnMinFrames;
        promotionReadyLastFrame = !risk && stableStreak >= promotionReadyMinFrames;

        warnings.add(new EngineWarning(
                "PBR_PROMOTION_POLICY_ACTIVE",
                "PBR promotion policy (advancedWarnMinFeatureCount=" + advancedWarnMinFeatureCount
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        warnings.add(new EngineWarning(
                "PBR_PROMOTION_ENVELOPE",
                "PBR promotion envelope (risk=" + risk
                        + ", activeAdvancedFeatureCount=" + activeAdvancedFeatureCount
                        + ", energyConservationValidation=" + plan.energyConservationValidationEnabled()
                        + ", highStreak=" + highStreak
                        + ", stableStreak=" + stableStreak + ")"
        ));

        int expectedCinematicFeatureCount = 0;
        if (subsurfaceScatteringEnabled) expectedCinematicFeatureCount += 1;
        if (thinFilmIridescenceEnabled) expectedCinematicFeatureCount += 1;
        if (sheenEnabled) expectedCinematicFeatureCount += 1;
        if (parallaxOcclusionEnabled) expectedCinematicFeatureCount += 1;
        if (tessellationEnabled) expectedCinematicFeatureCount += 1;
        if (decalsEnabled) expectedCinematicFeatureCount += 1;
        if (eyeShaderEnabled) expectedCinematicFeatureCount += 1;
        if (hairShaderEnabled) expectedCinematicFeatureCount += 1;
        if (clothShaderEnabled) expectedCinematicFeatureCount += 1;
        int activeCinematicFeatureCount = 0;
        if (plan.subsurfaceScatteringEnabled()) activeCinematicFeatureCount += 1;
        if (plan.thinFilmIridescenceEnabled()) activeCinematicFeatureCount += 1;
        if (plan.sheenEnabled()) activeCinematicFeatureCount += 1;
        if (plan.parallaxOcclusionEnabled()) activeCinematicFeatureCount += 1;
        if (plan.tessellationEnabled()) activeCinematicFeatureCount += 1;
        if (plan.decalsEnabled()) activeCinematicFeatureCount += 1;
        if (plan.eyeShaderEnabled()) activeCinematicFeatureCount += 1;
        if (plan.hairShaderEnabled()) activeCinematicFeatureCount += 1;
        if (plan.clothShaderEnabled()) activeCinematicFeatureCount += 1;
        boolean cinematicRisk = expectedCinematicFeatureCount > 0
                && activeCinematicFeatureCount < expectedCinematicFeatureCount;
        if (cinematicRisk) {
            cinematicHighStreak += 1;
            cinematicStableStreak = 0;
            if (cinematicWarnCooldownRemaining > 0) {
                cinematicWarnCooldownRemaining -= 1;
            }
        } else {
            cinematicHighStreak = 0;
            cinematicStableStreak += 1;
            if (cinematicWarnCooldownRemaining > 0) {
                cinematicWarnCooldownRemaining -= 1;
            }
        }
        cinematicEnvelopeBreachedLastFrame = cinematicRisk && cinematicHighStreak >= warnMinFrames;
        cinematicPromotionReadyLastFrame = !cinematicRisk && cinematicStableStreak >= promotionReadyMinFrames;
        warnings.add(new EngineWarning(
                "PBR_CINEMATIC_POLICY_ACTIVE",
                "PBR cinematic policy (expectedCount=" + expectedCinematicFeatureCount
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        warnings.add(new EngineWarning(
                "PBR_CINEMATIC_ENVELOPE",
                "PBR cinematic envelope (risk=" + cinematicRisk
                        + ", expectedCount=" + expectedCinematicFeatureCount
                        + ", activeCount=" + activeCinematicFeatureCount
                        + ", highStreak=" + cinematicHighStreak
                        + ", stableStreak=" + cinematicStableStreak + ")"
        ));
        if (cinematicEnvelopeBreachedLastFrame && cinematicWarnCooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "PBR_CINEMATIC_ENVELOPE_BREACH",
                    "PBR cinematic envelope breach (highStreak=" + cinematicHighStreak
                            + ", cooldown=" + cinematicWarnCooldownRemaining + ")"
            ));
            cinematicWarnCooldownRemaining = warnCooldownFrames;
        }
        if (cinematicPromotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "PBR_CINEMATIC_PROMOTION_READY",
                    "PBR cinematic promotion-ready envelope satisfied (mode=" + plan.modeId()
                            + ", stableStreak=" + cinematicStableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }

        if (envelopeBreachedLastFrame && warnCooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "PBR_PROMOTION_ENVELOPE_BREACH",
                    "PBR promotion envelope breach (highStreak=" + highStreak
                            + ", cooldown=" + warnCooldownRemaining + ")"
            ));
            warnCooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "PBR_PROMOTION_READY",
                    "PBR promotion-ready envelope satisfied (mode=" + plan.modeId()
                            + ", stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public PbrCapabilityDiagnostics diagnostics() {
        return new PbrCapabilityDiagnostics(
                true,
                modeLastFrame,
                activeCapabilitiesLastFrame.contains("vulkan.pbr.specular_glossiness"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.detail_maps"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.material_layering"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.clear_coat"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.anisotropic"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.transmission"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.refraction"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.subsurface_scattering"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.thin_film_iridescence"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.sheen"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.parallax_occlusion"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.tessellation"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.decals"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.eye_shader"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.hair_shader"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.cloth_shader"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.vertex_color_blend"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.emissive_bloom_control"),
                activeCapabilitiesLastFrame.contains("vulkan.pbr.energy_conservation_validation"),
                activeCapabilitiesLastFrame,
                prunedCapabilitiesLastFrame,
                signalsLastFrame
        );
    }

    public PbrPromotionDiagnostics promotionDiagnostics() {
        int activeAdvancedFeatureCount = 0;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.specular_glossiness")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.detail_maps")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.material_layering")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.clear_coat")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.anisotropic")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.transmission")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.refraction")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.subsurface_scattering")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.thin_film_iridescence")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.sheen")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.parallax_occlusion")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.tessellation")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.decals")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.eye_shader")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.hair_shader")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.cloth_shader")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.vertex_color_blend")) activeAdvancedFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.emissive_bloom_control")) activeAdvancedFeatureCount += 1;
        int expectedCinematicFeatureCount = 0;
        if (subsurfaceScatteringEnabled) expectedCinematicFeatureCount += 1;
        if (thinFilmIridescenceEnabled) expectedCinematicFeatureCount += 1;
        if (sheenEnabled) expectedCinematicFeatureCount += 1;
        if (parallaxOcclusionEnabled) expectedCinematicFeatureCount += 1;
        if (tessellationEnabled) expectedCinematicFeatureCount += 1;
        if (decalsEnabled) expectedCinematicFeatureCount += 1;
        if (eyeShaderEnabled) expectedCinematicFeatureCount += 1;
        if (hairShaderEnabled) expectedCinematicFeatureCount += 1;
        if (clothShaderEnabled) expectedCinematicFeatureCount += 1;
        int activeCinematicFeatureCount = 0;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.subsurface_scattering")) activeCinematicFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.thin_film_iridescence")) activeCinematicFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.sheen")) activeCinematicFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.parallax_occlusion")) activeCinematicFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.tessellation")) activeCinematicFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.decals")) activeCinematicFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.eye_shader")) activeCinematicFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.hair_shader")) activeCinematicFeatureCount += 1;
        if (activeCapabilitiesLastFrame.contains("vulkan.pbr.cloth_shader")) activeCinematicFeatureCount += 1;
        boolean energyConservationValidationEnabled =
                activeCapabilitiesLastFrame.contains("vulkan.pbr.energy_conservation_validation");
        return new PbrPromotionDiagnostics(
                true,
                modeLastFrame,
                activeAdvancedFeatureCount,
                advancedWarnMinFeatureCount,
                expectedCinematicFeatureCount,
                activeCinematicFeatureCount,
                energyConservationValidationEnabled,
                envelopeBreachedLastFrame,
                promotionReadyLastFrame,
                cinematicEnvelopeBreachedLastFrame,
                cinematicPromotionReadyLastFrame,
                stableStreak,
                highStreak,
                cinematicStableStreak,
                cinematicHighStreak,
                warnCooldownRemaining,
                cinematicWarnCooldownRemaining,
                warnMinFrames,
                warnCooldownFrames,
                promotionReadyMinFrames
        );
    }

    private static int parseInt(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
