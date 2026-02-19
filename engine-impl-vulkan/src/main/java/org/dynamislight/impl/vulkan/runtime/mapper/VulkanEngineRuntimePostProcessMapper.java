package org.dynamislight.impl.vulkan.runtime.mapper;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.AntiAliasingDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.ReflectionAdvancedDesc;
import org.dynamislight.api.scene.ReflectionDesc;
import org.dynamislight.impl.vulkan.runtime.config.AaMode;
import org.dynamislight.impl.vulkan.runtime.config.AaPreset;
import org.dynamislight.impl.vulkan.runtime.config.ReflectionProfile;
import org.dynamislight.impl.vulkan.runtime.config.TsrControls;
import org.dynamislight.impl.vulkan.runtime.config.UpscalerMode;
import org.dynamislight.impl.vulkan.runtime.config.UpscalerQuality;
import org.dynamislight.impl.vulkan.runtime.model.PostProcessRenderConfig;

final class VulkanEngineRuntimePostProcessMapper {
    private VulkanEngineRuntimePostProcessMapper() {
    }

    static PostProcessRenderConfig mapPostProcess(
            PostProcessDesc desc,
            QualityTier qualityTier,
            boolean taaLumaClipEnabledDefault,
            AaPreset aaPreset,
            AaMode aaMode,
            UpscalerMode upscalerMode,
            UpscalerQuality upscalerQuality,
            TsrControls tsrControls,
            ReflectionProfile reflectionProfile
    ) {
        if (desc == null || !desc.enabled()) {
            return new PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.8f, false, 0f, 1.0f, 0.02f, 1.0f, false, 0f, false, 0f, 1.0f, false, 0.12f, 1.0f, false, 0, 0.6f, 0.78f, 1.0f, 0.80f, 0.35f, 0.0f);
        }
        float tierExposureScale = switch (qualityTier) {
            case LOW -> 0.9f;
            case MEDIUM -> 1.0f;
            case HIGH -> 1.05f;
            case ULTRA -> 1.1f;
        };
        float exposure = Math.max(0.25f, Math.min(4.0f, desc.exposure() * tierExposureScale));
        float gamma = Math.max(1.6f, Math.min(2.6f, desc.gamma()));
        float bloomThreshold = Math.max(0.2f, Math.min(2.5f, desc.bloomThreshold()));
        float bloomStrength = Math.max(0f, Math.min(1.6f, desc.bloomStrength()));
        boolean bloomEnabled = desc.bloomEnabled() && qualityTier != QualityTier.LOW;
        boolean ssaoEnabled = desc.ssaoEnabled() && qualityTier != QualityTier.LOW;
        float ssaoStrength = Math.max(0f, Math.min(1.0f, desc.ssaoStrength()));
        float ssaoRadius = Math.max(0.2f, Math.min(3.0f, desc.ssaoRadius()));
        float ssaoBias = Math.max(0f, Math.min(0.2f, desc.ssaoBias()));
        float ssaoPower = Math.max(0.5f, Math.min(4.0f, desc.ssaoPower()));
        boolean smaaEnabled = desc.smaaEnabled() && qualityTier != QualityTier.LOW;
        float smaaStrength = Math.max(0f, Math.min(1.0f, desc.smaaStrength()));
        boolean taaEnabled = desc.taaEnabled() && qualityTier != QualityTier.LOW;
        float taaBlend = Math.max(0f, Math.min(0.95f, desc.taaBlend()));
        float taaClipScale = switch (qualityTier) {
            case LOW -> 1.35f;
            case MEDIUM -> 1.10f;
            case HIGH -> 0.92f;
            case ULTRA -> 0.78f;
        };
        float taaSharpenStrength = switch (qualityTier) {
            case LOW -> 0.08f;
            case MEDIUM -> 0.12f;
            case HIGH -> 0.16f;
            case ULTRA -> 0.20f;
        };
        float taaRenderScale = 1.0f;
        boolean taaLumaClipEnabled = desc.taaLumaClipEnabled() || taaLumaClipEnabledDefault;
        if (qualityTier == QualityTier.MEDIUM) {
            ssaoStrength *= 0.8f;
            ssaoRadius *= 0.9f;
            smaaStrength *= 0.8f;
            taaBlend *= 0.85f;
        }
        if (aaPreset != null) {
            switch (aaPreset) {
                case PERFORMANCE -> {
                    smaaStrength *= 0.80f;
                    taaBlend *= 0.82f;
                    taaClipScale = Math.min(1.6f, taaClipScale * 1.12f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.70f);
                }
                case QUALITY -> {
                    smaaStrength = Math.min(1.0f, smaaStrength * 1.12f);
                    taaBlend = Math.min(0.95f, taaBlend + 0.05f);
                    taaClipScale = Math.max(0.5f, taaClipScale * 0.94f);
                    taaSharpenStrength = Math.min(0.35f, taaSharpenStrength * 1.10f);
                    taaLumaClipEnabled = true;
                }
                case STABILITY -> {
                    smaaStrength = Math.min(1.0f, smaaStrength * 0.90f);
                    taaBlend = Math.min(0.95f, taaBlend + 0.08f);
                    taaClipScale = Math.min(1.6f, taaClipScale * 1.08f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.82f);
                    taaLumaClipEnabled = true;
                }
                case BALANCED -> {
                }
            }
        }
        if (aaMode != null) {
            switch (aaMode) {
                case TSR -> {
                    taaEnabled = qualityTier != QualityTier.LOW;
                    smaaEnabled = false;
                    smaaStrength = 0f;
                    taaRenderScale = qualityTier == QualityTier.LOW
                            ? 1.0f
                            : Math.max(0.5f, Math.min(1.0f, tsrControls.tsrRenderScale()));
                    float historyInfluence = clamp01(
                            tsrControls.historyWeight() * tsrControls.reprojectionConfidence() * (1.0f - tsrControls.responsiveMask() * 0.22f)
                    );
                    taaBlend = Math.max(taaBlend, Math.min(0.95f, 0.78f + 0.17f * historyInfluence));
                    taaClipScale = Math.max(0.5f, Math.min(1.6f, taaClipScale * (1.0f - (tsrControls.neighborhoodClamp() - 0.5f) * 0.45f)));
                    float antiRingingAttenuation = 1.0f - (0.35f * tsrControls.antiRinging());
                    taaSharpenStrength = Math.max(0f, Math.min(0.35f,
                            (tsrControls.sharpen() * antiRingingAttenuation) + (taaSharpenStrength * 0.22f)));
                    taaLumaClipEnabled = tsrControls.antiRinging() >= 0.35f;
                }
                case TUUA -> {
                    taaEnabled = qualityTier != QualityTier.LOW;
                    smaaEnabled = false;
                    smaaStrength = 0f;
                    taaRenderScale = qualityTier == QualityTier.LOW
                            ? 1.0f
                            : Math.max(0.5f, Math.min(1.0f, tsrControls.tuuaRenderScale()));
                    taaBlend = Math.min(0.95f, taaBlend + 0.10f);
                    taaClipScale = Math.max(0.5f, taaClipScale * 0.86f);
                    taaSharpenStrength = Math.min(0.35f, taaSharpenStrength * 1.16f);
                    taaLumaClipEnabled = true;
                }
                case MSAA_SELECTIVE -> {
                    smaaEnabled = qualityTier != QualityTier.LOW;
                    smaaStrength = Math.min(1.0f, smaaStrength + 0.12f);
                    taaBlend = Math.max(0.0f, taaBlend * 0.72f);
                    taaClipScale = Math.min(1.6f, taaClipScale * 1.10f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.75f);
                }
                case HYBRID_TUUA_MSAA -> {
                    taaEnabled = qualityTier != QualityTier.LOW;
                    smaaEnabled = qualityTier != QualityTier.LOW;
                    smaaStrength = Math.min(1.0f, smaaStrength * 1.05f);
                    taaBlend = Math.min(0.95f, taaBlend + 0.06f);
                    taaClipScale = Math.max(0.5f, taaClipScale * 0.90f);
                    taaSharpenStrength = Math.min(0.35f, taaSharpenStrength * 0.95f);
                    taaLumaClipEnabled = true;
                }
                case DLAA -> {
                    taaEnabled = qualityTier != QualityTier.LOW;
                    smaaEnabled = qualityTier != QualityTier.LOW;
                    smaaStrength = Math.min(1.0f, smaaStrength * 0.55f);
                    taaBlend = Math.max(taaBlend, 0.90f);
                    taaClipScale = Math.max(0.5f, taaClipScale * 0.88f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.70f);
                    taaLumaClipEnabled = true;
                }
                case FXAA_LOW -> {
                    taaEnabled = false;
                    taaBlend = 0f;
                    smaaEnabled = qualityTier != QualityTier.LOW;
                    smaaStrength = Math.min(1.0f, Math.max(0.45f, smaaStrength * 0.90f));
                    taaClipScale = Math.min(1.6f, taaClipScale * 1.15f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.60f);
                    taaLumaClipEnabled = false;
                }
                case TAA -> {
                }
            }
        }
        if ((aaMode == AaMode.TSR || aaMode == AaMode.TUUA)
                && upscalerMode != UpscalerMode.NONE) {
            float qualityScale = switch (upscalerQuality) {
                case PERFORMANCE -> 0.88f;
                case BALANCED -> 0.94f;
                case QUALITY -> 1.0f;
                case ULTRA_QUALITY -> 1.05f;
            };
            switch (upscalerMode) {
                case FSR -> {
                    taaSharpenStrength = Math.min(0.35f, taaSharpenStrength + 0.05f * qualityScale);
                    taaBlend = Math.max(0.0f, taaBlend - 0.02f);
                    taaRenderScale = Math.max(taaRenderScale, 0.60f * qualityScale);
                }
                case XESS -> {
                    taaBlend = Math.min(0.95f, taaBlend + 0.03f * qualityScale);
                    taaClipScale = Math.max(0.5f, taaClipScale * (0.96f - ((qualityScale - 1.0f) * 0.05f)));
                    taaRenderScale = Math.max(taaRenderScale, 0.64f * qualityScale);
                }
                case DLSS -> {
                    taaBlend = Math.min(0.95f, taaBlend + 0.05f * qualityScale);
                    taaClipScale = Math.max(0.5f, taaClipScale * (0.92f - ((qualityScale - 1.0f) * 0.05f)));
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.82f);
                    taaRenderScale = Math.max(taaRenderScale, 0.67f * qualityScale);
                }
                case NONE -> {
                }
            }
        }
        if (desc.antiAliasing() != null) {
            AntiAliasingDesc aa = desc.antiAliasing();
            taaBlend = clamp01(Math.min(0.95f, Math.max(0f, aa.blend())));
            taaClipScale = Math.max(0.5f, Math.min(1.6f, aa.clipScale()));
            taaLumaClipEnabled = aa.lumaClipEnabled();
            taaSharpenStrength = Math.max(0f, Math.min(0.35f, aa.sharpenStrength()));
            taaRenderScale = Math.max(0.5f, Math.min(1.0f, aa.renderScale()));
        }
        ReflectionDesc reflectionDesc = desc.reflections();
        ReflectionAdvancedDesc reflectionAdvancedDesc = desc.reflectionAdvanced();
        boolean reflectionsEnabled = false;
        int reflectionsMode = 0;
        float reflectionsSsrStrength = 0.6f;
        float reflectionsSsrMaxRoughness = 0.78f;
        float reflectionsSsrStepScale = 1.0f;
        float reflectionsTemporalWeight = 0.80f;
        float reflectionsPlanarStrength = 0.35f;
        float reflectionsPlanarPlaneHeight = 0.0f;
        if (reflectionDesc != null) {
            reflectionsMode = parseReflectionMode(reflectionDesc.mode());
            reflectionsEnabled = reflectionDesc.enabled() && reflectionsMode != 0;
            reflectionsSsrStrength = Math.max(0f, Math.min(1.0f, reflectionDesc.ssrStrength()));
            reflectionsSsrMaxRoughness = Math.max(0f, Math.min(1.0f, reflectionDesc.ssrMaxRoughness()));
            reflectionsSsrStepScale = Math.max(0.5f, Math.min(3.0f, reflectionDesc.ssrStepScale()));
            reflectionsTemporalWeight = Math.max(0f, Math.min(0.98f, reflectionDesc.temporalWeight()));
            reflectionsPlanarStrength = Math.max(0f, Math.min(1.0f, reflectionDesc.planarStrength()));
            if (qualityTier == QualityTier.LOW) {
                reflectionsEnabled = false;
            } else if (qualityTier == QualityTier.MEDIUM) {
                reflectionsSsrStrength *= 0.85f;
                reflectionsSsrStepScale = Math.min(3.0f, reflectionsSsrStepScale * 1.15f);
                reflectionsPlanarStrength *= 0.9f;
            }
        }
        if (reflectionAdvancedDesc != null && reflectionsEnabled) {
            if (!reflectionAdvancedDesc.hiZEnabled()) {
                reflectionsSsrStepScale = Math.max(0.5f, Math.min(3.0f, reflectionsSsrStepScale * 1.08f));
            } else {
                reflectionsSsrStepScale = Math.max(0.5f, Math.min(3.0f, reflectionsSsrStepScale * 0.92f));
            }
            int denoisePasses = Math.max(0, Math.min(6, reflectionAdvancedDesc.denoisePasses()));
            reflectionsTemporalWeight = Math.max(0f, Math.min(0.98f, reflectionsTemporalWeight + (denoisePasses * 0.02f)));
            if (reflectionAdvancedDesc.planarClipPlaneEnabled()) {
                reflectionsPlanarStrength = Math.max(0f, Math.min(1.0f, reflectionsPlanarStrength + 0.05f));
                reflectionsPlanarPlaneHeight = reflectionAdvancedDesc.planarPlaneHeight();
            }
            if (reflectionAdvancedDesc.probeVolumeEnabled()) {
                reflectionsPlanarStrength = Math.max(0f, Math.min(1.0f, reflectionsPlanarStrength + 0.03f));
                reflectionsTemporalWeight = Math.max(0f, Math.min(0.98f, reflectionsTemporalWeight + 0.02f));
            }
            if (reflectionAdvancedDesc.rtEnabled()) {
                int fallbackMode = parseReflectionMode(reflectionAdvancedDesc.rtFallbackMode());
                reflectionsMode = fallbackMode == 0 ? 3 : fallbackMode;
                reflectionsSsrMaxRoughness = Math.max(0f, Math.min(1.0f, Math.max(reflectionsSsrMaxRoughness, reflectionAdvancedDesc.rtMaxRoughness())));
                reflectionsTemporalWeight = Math.max(0f, Math.min(0.98f, reflectionsTemporalWeight + 0.04f));
            }
        }
        if (reflectionsEnabled && reflectionProfile != null) {
            switch (reflectionProfile) {
                case PERFORMANCE -> {
                    reflectionsSsrStrength = Math.max(0f, Math.min(1.0f, reflectionsSsrStrength * 0.80f));
                    reflectionsSsrStepScale = Math.max(0.5f, Math.min(3.0f, reflectionsSsrStepScale * 1.20f));
                    reflectionsTemporalWeight = Math.max(0f, Math.min(0.98f, reflectionsTemporalWeight * 0.75f));
                    reflectionsPlanarStrength = Math.max(0f, Math.min(1.0f, reflectionsPlanarStrength * 0.90f));
                }
                case QUALITY -> {
                    reflectionsSsrStrength = Math.max(0f, Math.min(1.0f, reflectionsSsrStrength * 1.10f));
                    reflectionsSsrStepScale = Math.max(0.5f, Math.min(3.0f, reflectionsSsrStepScale * 0.90f));
                    reflectionsTemporalWeight = Math.max(0f, Math.min(0.98f, reflectionsTemporalWeight + 0.08f));
                    reflectionsPlanarStrength = Math.max(0f, Math.min(1.0f, reflectionsPlanarStrength + 0.05f));
                }
                case STABILITY -> {
                    reflectionsSsrStrength = Math.max(0f, Math.min(1.0f, reflectionsSsrStrength * 0.95f));
                    reflectionsSsrStepScale = Math.max(0.5f, Math.min(3.0f, reflectionsSsrStepScale * 1.05f));
                    reflectionsTemporalWeight = Math.max(0f, Math.min(0.98f, reflectionsTemporalWeight + 0.12f));
                }
                case BALANCED -> {
                }
            }
        }
        return new PostProcessRenderConfig(
                desc.tonemapEnabled(),
                exposure,
                gamma,
                bloomEnabled,
                bloomThreshold,
                bloomStrength,
                ssaoEnabled,
                ssaoStrength,
                ssaoRadius,
                ssaoBias,
                ssaoPower,
                smaaEnabled,
                smaaStrength,
                taaEnabled,
                taaBlend,
                taaClipScale,
                taaLumaClipEnabled,
                taaSharpenStrength,
                taaRenderScale,
                reflectionsEnabled,
                packReflectionMode(reflectionsMode, reflectionAdvancedDesc),
                reflectionsSsrStrength,
                reflectionsSsrMaxRoughness,
                reflectionsSsrStepScale,
                reflectionsTemporalWeight,
                reflectionsPlanarStrength,
                reflectionsPlanarPlaneHeight
        );
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int parseReflectionMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return 0;
        }
        return switch (rawMode.trim().toLowerCase()) {
            case "ssr" -> 1;
            case "planar" -> 2;
            case "hybrid" -> 3;
            case "rt_hybrid", "rt" -> 4;
            default -> 0;
        };
    }

    private static int packReflectionMode(int baseMode, ReflectionAdvancedDesc advanced) {
        int packed = baseMode & 0x7;
        if (advanced == null) {
            return packed;
        }
        if (advanced.hiZEnabled()) {
            packed |= 1 << 3;
        }
        int denoisePasses = Math.max(0, Math.min(6, advanced.denoisePasses()));
        packed |= (denoisePasses & 0x7) << 4;
        if (advanced.planarClipPlaneEnabled()) {
            packed |= 1 << 7;
        }
        if (advanced.probeVolumeEnabled()) {
            packed |= 1 << 8;
        }
        if (advanced.probeBoxProjectionEnabled()) {
            packed |= 1 << 9;
        }
        if (advanced.rtEnabled()) {
            packed |= 1 << 10;
        }
        return packed;
    }
}
