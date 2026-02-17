package org.dynamislight.impl.vulkan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.AntiAliasingDesc;
import org.dynamislight.api.scene.ReflectionAdvancedDesc;
import org.dynamislight.api.scene.ReflectionDesc;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.impl.common.shadow.ShadowAtlasPlanner;

final class VulkanEngineRuntimeLightingMapper {
    private static final int VULKAN_MAX_SHADOW_MATRICES = 12;

    private VulkanEngineRuntimeLightingMapper() {
    }

    static VulkanEngineRuntime.PostProcessRenderConfig mapPostProcess(
            PostProcessDesc desc,
            QualityTier qualityTier,
            boolean taaLumaClipEnabledDefault,
            VulkanEngineRuntime.AaPreset aaPreset,
            VulkanEngineRuntime.AaMode aaMode,
            VulkanEngineRuntime.UpscalerMode upscalerMode,
            VulkanEngineRuntime.UpscalerQuality upscalerQuality,
            VulkanEngineRuntime.TsrControls tsrControls,
            VulkanEngineRuntime.ReflectionProfile reflectionProfile
    ) {
        if (desc == null || !desc.enabled()) {
            return new VulkanEngineRuntime.PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.8f, false, 0f, 1.0f, 0.02f, 1.0f, false, 0f, false, 0f, 1.0f, false, 0.12f, 1.0f, false, 0, 0.6f, 0.78f, 1.0f, 0.80f, 0.35f);
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
        if ((aaMode == VulkanEngineRuntime.AaMode.TSR || aaMode == VulkanEngineRuntime.AaMode.TUUA)
                && upscalerMode != VulkanEngineRuntime.UpscalerMode.NONE) {
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
        return new VulkanEngineRuntime.PostProcessRenderConfig(
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
                reflectionsPlanarStrength
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

    static VulkanEngineRuntime.LightingConfig mapLighting(
            List<LightDesc> lights,
            QualityTier qualityTier,
            int shadowMaxLocalLayers
    ) {
        float[] dir = new float[]{0.35f, -1.0f, 0.25f};
        float[] dirColor = new float[]{1.0f, 0.98f, 0.95f};
        float dirIntensity = 1.0f;
        float[] shadowPointPos = new float[]{0f, 1.3f, 1.8f};
        float[] shadowPointDir = new float[]{0f, -1f, 0f};
        boolean shadowPointIsSpot = false;
        float shadowPointOuterCos = 1.0f;
        float shadowPointRange = 15f;
        boolean shadowPointCastsShadows = false;
        int localLightCount = 0;
        float[] localLightPosRange = new float[VulkanContext.MAX_LOCAL_LIGHTS * 4];
        float[] localLightColorIntensity = new float[VulkanContext.MAX_LOCAL_LIGHTS * 4];
        float[] localLightDirInner = new float[VulkanContext.MAX_LOCAL_LIGHTS * 4];
        float[] localLightOuterTypeShadow = new float[VulkanContext.MAX_LOCAL_LIGHTS * 4];
        if (lights == null || lights.isEmpty()) {
            return new VulkanEngineRuntime.LightingConfig(
                    dir, dirColor, dirIntensity,
                    shadowPointPos, shadowPointDir, shadowPointIsSpot, shadowPointOuterCos, shadowPointRange, shadowPointCastsShadows,
                    localLightCount, localLightPosRange, localLightColorIntensity, localLightDirInner, localLightOuterTypeShadow
            );
        }
        LightDesc directional = null;
        List<LightDesc> localLights = new java.util.ArrayList<>();
        for (LightDesc light : lights) {
            if (light == null) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            if (directional == null && type == LightType.DIRECTIONAL) {
                directional = light;
            }
            if (type == LightType.SPOT || type == LightType.POINT) {
                localLights.add(light);
            }
        }
        if (directional == null) {
            directional = lights.getFirst();
        }
        if (directional != null && directional.color() != null) {
            dirColor = new float[]{
                    clamp01(directional.color().x()),
                    clamp01(directional.color().y()),
                    clamp01(directional.color().z())
            };
        }
        if (directional != null) {
            dirIntensity = Math.max(0f, directional.intensity());
            if (directional.direction() != null) {
                dir = VulkanEngineRuntimeCameraMath.normalize3(new float[]{
                        directional.direction().x(),
                        directional.direction().y(),
                        directional.direction().z()
                });
            }
        }
        if (!localLights.isEmpty()) {
            localLights.sort((a, b) -> Float.compare(localLightPriority(b), localLightPriority(a)));
            localLightCount = Math.min(VulkanContext.MAX_LOCAL_LIGHTS, localLights.size());
            int maxShadowedLocalLights = switch (qualityTier) {
                case LOW -> 1;
                case MEDIUM -> 2;
                case HIGH -> 3;
                case ULTRA -> 4;
            };
            int tierShadowLayers = switch (qualityTier) {
                case LOW -> 4;
                case MEDIUM -> 6;
                case HIGH -> 8;
                case ULTRA -> 12;
            };
            int maxShadowLayers = shadowMaxLocalLayers > 0
                    ? Math.min(VULKAN_MAX_SHADOW_MATRICES, shadowMaxLocalLayers)
                    : tierShadowLayers;
            int assignedShadowLights = 0;
            int assignedShadowLayers = 0;
            for (int i = 0; i < localLightCount; i++) {
                LightDesc light = localLights.get(i);
                int offset = i * 4;
                LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
                float[] pos = light.position() == null
                        ? new float[]{0f, 1.3f, 1.8f}
                        : new float[]{light.position().x(), light.position().y(), light.position().z()};
                float range = light.range() > 0f ? light.range() : 15f;
                float[] color = light.color() == null
                        ? new float[]{0.95f, 0.62f, 0.22f}
                        : new float[]{clamp01(light.color().x()), clamp01(light.color().y()), clamp01(light.color().z())};
                float intensity = Math.max(0f, light.intensity());
                float[] direction = light.direction() == null
                        ? new float[]{0f, -1f, 0f}
                        : VulkanEngineRuntimeCameraMath.normalize3(new float[]{light.direction().x(), light.direction().y(), light.direction().z()});
                float inner = 1.0f;
                float outer = 1.0f;
                float isSpot = 0f;
                if (type == LightType.SPOT) {
                    float innerCos = VulkanEngineRuntimeCameraMath.cosFromDegrees(light.innerConeDegrees());
                    float outerCos = VulkanEngineRuntimeCameraMath.cosFromDegrees(light.outerConeDegrees());
                    inner = Math.max(innerCos, outerCos);
                    outer = Math.min(innerCos, outerCos);
                    isSpot = 1f;
                }
                float castsShadows = light.castsShadows() ? 1f : 0f;
                float layerBase = 0f;
                if (castsShadows > 0.5f && assignedShadowLights < maxShadowedLocalLights) {
                    int layerCost = isSpot > 0.5f ? 1 : 6;
                    if (assignedShadowLayers + layerCost <= maxShadowLayers) {
                        layerBase = assignedShadowLayers + 1;
                        assignedShadowLayers += layerCost;
                        assignedShadowLights++;
                    }
                }
                localLightPosRange[offset] = pos[0];
                localLightPosRange[offset + 1] = pos[1];
                localLightPosRange[offset + 2] = pos[2];
                localLightPosRange[offset + 3] = range;
                localLightColorIntensity[offset] = color[0];
                localLightColorIntensity[offset + 1] = color[1];
                localLightColorIntensity[offset + 2] = color[2];
                localLightColorIntensity[offset + 3] = intensity;
                localLightDirInner[offset] = direction[0];
                localLightDirInner[offset + 1] = direction[1];
                localLightDirInner[offset + 2] = direction[2];
                localLightDirInner[offset + 3] = inner;
                localLightOuterTypeShadow[offset] = outer;
                localLightOuterTypeShadow[offset + 1] = isSpot;
                localLightOuterTypeShadow[offset + 2] = layerBase > 0f ? 1f : 0f;
                localLightOuterTypeShadow[offset + 3] = layerBase;
            }
            LightDesc shadowLight = localLights.stream().filter(LightDesc::castsShadows).findFirst().orElse(localLights.getFirst());
            if (shadowLight.position() != null) {
                shadowPointPos = new float[]{shadowLight.position().x(), shadowLight.position().y(), shadowLight.position().z()};
            }
            shadowPointRange = shadowLight.range() > 0f ? shadowLight.range() : 15f;
            LightType shadowType = shadowLight.type() == null ? LightType.DIRECTIONAL : shadowLight.type();
            shadowPointIsSpot = shadowType == LightType.SPOT;
            shadowPointCastsShadows = shadowLight.castsShadows();
            if (assignedShadowLayers > 0) {
                shadowPointCastsShadows = false;
            }
            if (shadowLight.direction() != null) {
                shadowPointDir = VulkanEngineRuntimeCameraMath.normalize3(new float[]{
                        shadowLight.direction().x(),
                        shadowLight.direction().y(),
                        shadowLight.direction().z()
                });
            }
            if (shadowPointIsSpot) {
                float innerCos = VulkanEngineRuntimeCameraMath.cosFromDegrees(shadowLight.innerConeDegrees());
                float outerCos = VulkanEngineRuntimeCameraMath.cosFromDegrees(shadowLight.outerConeDegrees());
                shadowPointOuterCos = Math.min(innerCos, outerCos);
            }
        }
        return new VulkanEngineRuntime.LightingConfig(
                dir, dirColor, dirIntensity,
                shadowPointPos, shadowPointDir, shadowPointIsSpot, shadowPointOuterCos, shadowPointRange, shadowPointCastsShadows,
                localLightCount, localLightPosRange, localLightColorIntensity, localLightDirInner, localLightOuterTypeShadow
        );
    }

    static boolean hasNonDirectionalShadowRequest(List<LightDesc> lights) {
        if (lights == null || lights.isEmpty()) {
            return false;
        }
        for (LightDesc light : lights) {
            if (light == null || !light.castsShadows()) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            if (type == LightType.POINT || type == LightType.SPOT) {
                return true;
            }
        }
        return false;
    }

    private static float localLightPriority(LightDesc light) {
        if (light == null) {
            return Float.NEGATIVE_INFINITY;
        }
        float intensity = Math.max(0f, light.intensity());
        float range = Math.max(0f, light.range());
        float shadowBoost = light.castsShadows() ? 1.15f : 1.0f;
        float spotBoost = (light.type() == LightType.SPOT) ? 1.05f : 1.0f;
        return intensity * (1.0f + (range * 0.08f)) * shadowBoost * spotBoost;
    }

    static VulkanEngineRuntime.ShadowRenderConfig mapShadows(
            List<LightDesc> lights,
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame
    ) {
        String filterPath = shadowFilterPath == null || shadowFilterPath.isBlank() ? "pcf" : shadowFilterPath.trim().toLowerCase(java.util.Locale.ROOT);
        String rtMode = shadowRtMode == null || shadowRtMode.isBlank() ? "off" : shadowRtMode.trim().toLowerCase(java.util.Locale.ROOT);
        if (lights == null || lights.isEmpty()) {
            return new VulkanEngineRuntime.ShadowRenderConfig(false, 0.45f, 0.0015f, 1.0f, 1.0f, 1, 1, 1024, 0, 0, "none", "none", 0, 0, 0.0f, 0, 0L, 0L, 0L, 0, 0, 0, filterPath, shadowContactShadows, rtMode, false, false);
        }
        int maxShadowedLocalLights = switch (qualityTier) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case ULTRA -> 4;
        };
        int tierShadowLayers = switch (qualityTier) {
            case LOW -> 4;
            case MEDIUM -> 6;
            case HIGH -> 8;
            case ULTRA -> 12;
        };
        int maxShadowLayers = shadowMaxLocalLayers > 0
                ? Math.min(VULKAN_MAX_SHADOW_MATRICES, shadowMaxLocalLayers)
                : tierShadowLayers;
        List<LightDesc> localShadowCandidates = new ArrayList<>();
        LightDesc primaryDirectional = null;
        LightDesc bestLocal = null;
        for (LightDesc light : lights) {
            if (light == null || !light.castsShadows()) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            if (type == LightType.POINT || type == LightType.SPOT) {
                localShadowCandidates.add(light);
                if (bestLocal == null || localLightPriority(light) > localLightPriority(bestLocal)) {
                    bestLocal = light;
                }
            }
            if (primaryDirectional == null && type == LightType.DIRECTIONAL) {
                primaryDirectional = light;
            }
        }
        localShadowCandidates.sort((a, b) -> Float.compare(localLightPriority(b), localLightPriority(a)));
        int selectedLocalShadowLights = Math.min(maxShadowedLocalLights, localShadowCandidates.size());
        selectedLocalShadowLights = Math.min(maxShadowedLocalLights, selectedLocalShadowLights);
        int selectedSpotShadowLights = 0;
        int selectedPointShadowLights = 0;
        for (int i = 0; i < selectedLocalShadowLights; i++) {
            LightDesc candidate = localShadowCandidates.get(i);
            LightType localType = candidate.type() == null ? LightType.DIRECTIONAL : candidate.type();
            if (localType == LightType.SPOT) {
                selectedSpotShadowLights++;
            } else if (localType == LightType.POINT) {
                selectedPointShadowLights++;
            }
        }
        LightDesc primary = primaryDirectional != null ? primaryDirectional : bestLocal;
        if (primary == null) {
            return new VulkanEngineRuntime.ShadowRenderConfig(false, 0.45f, 0.0015f, 1.0f, 1.0f, 1, 1, 1024, maxShadowedLocalLights, 0, "none", "none", 0, 0, 0.0f, 0, 0L, 0L, 0L, 0, 0, 0, filterPath, shadowContactShadows, rtMode, false, false);
        }
        LightType type = primary.type() == null ? LightType.DIRECTIONAL : primary.type();
        ShadowDesc shadow = primary.shadow();
        int kernel = shadow == null ? 3 : Math.max(1, shadow.pcfKernelSize());
        int cascades = shadow == null ? 1 : Math.max(1, shadow.cascadeCount());
        int faceBudget = shadowMaxFacesPerFrame > 0
                ? Math.min(VULKAN_MAX_SHADOW_MATRICES, shadowMaxFacesPerFrame)
                : 0;
        if (type == LightType.SPOT) {
            cascades = Math.max(1, Math.min(4, selectedSpotShadowLights));
            if (faceBudget > 0) {
                cascades = Math.max(1, Math.min(cascades, faceBudget));
            }
        } else if (type == LightType.POINT) {
            int maxPointCubemaps = Math.max(1, maxShadowLayers / 6);
            cascades = 6 * Math.max(1, Math.min(maxPointCubemaps, selectedPointShadowLights));
            if (faceBudget > 0) {
                int normalizedFaceBudget = Math.max(6, (faceBudget / 6) * 6);
                cascades = Math.max(6, Math.min(cascades, normalizedFaceBudget));
            }
        }
        int requestedResolution = shadow == null ? 1024 : Math.max(256, Math.min(4096, shadow.mapResolution()));
        float typeResolutionScale = switch (type) {
            case DIRECTIONAL -> 1.0f;
            case SPOT -> 0.85f;
            case POINT -> 0.75f;
        };
        int resolution = Math.max(256, Math.min(4096, Math.round(requestedResolution * typeResolutionScale)));
        int atlasCapacityTiles = Math.max(1, (resolution / 256) * (resolution / 256));
        List<ShadowAtlasPlanner.Request> atlasRequests = new ArrayList<>();
        for (int i = 0; i < selectedLocalShadowLights; i++) {
            LightDesc local = localShadowCandidates.get(i);
            LightType localType = local.type() == null ? LightType.DIRECTIONAL : local.type();
            if (localType != LightType.SPOT) {
                continue;
            }
            int tileSize = Math.max(256, Math.round(resolution * 0.5f));
            atlasRequests.add(new ShadowAtlasPlanner.Request(
                    local.id() == null || local.id().isBlank() ? ("local-shadow-" + i) : local.id(),
                    tileSize,
                    0
            ));
        }
        ShadowAtlasPlanner.PlanResult atlasPlan = ShadowAtlasPlanner.plan(resolution, atlasRequests, Map.of());
        long atlasMemoryBytesD16 = (long) resolution * (long) resolution * 2L;
        long atlasMemoryBytesD32 = (long) resolution * (long) resolution * 4L;
        long shadowUpdateBytesEstimate = 0L;
        for (ShadowAtlasPlanner.Allocation allocation : atlasPlan.allocations()) {
            long tilePixels = (long) allocation.tileSizePx() * (long) allocation.tileSizePx();
            shadowUpdateBytesEstimate += tilePixels * 2L;
        }
        float bias = shadow == null ? 0.0015f : Math.max(0.00002f, shadow.depthBias());
        int maxKernel = switch (type) {
            case DIRECTIONAL -> switch (qualityTier) {
                case LOW -> 3;
                case MEDIUM -> 5;
                case HIGH -> 7;
                case ULTRA -> 9;
            };
            case SPOT -> switch (qualityTier) {
                case LOW -> 3;
                case MEDIUM -> 5;
                case HIGH -> 5;
                case ULTRA -> 7;
            };
            case POINT -> switch (qualityTier) {
                case LOW -> 3;
                case MEDIUM -> 3;
                case HIGH -> 5;
                case ULTRA -> 5;
            };
        };
        int kernelClamped = Math.min(kernel, maxKernel);
        int radius = Math.max(0, (kernelClamped - 1) / 2);
        int maxPointCubemaps = Math.max(1, maxShadowLayers / 6);
        int maxPointCascades = maxPointCubemaps * 6;
        if (type == LightType.POINT && faceBudget > 0) {
            int normalizedFaceBudget = Math.max(6, (faceBudget / 6) * 6);
            maxPointCascades = Math.min(maxPointCascades, normalizedFaceBudget);
        }
        int maxCascades = switch (qualityTier) {
            case LOW -> type == LightType.POINT ? maxPointCascades : 1;
            case MEDIUM -> type == LightType.POINT ? maxPointCascades : 2;
            case HIGH -> type == LightType.POINT ? maxPointCascades : 3;
            case ULTRA -> type == LightType.POINT ? maxPointCascades : 4;
        };
        int cascadesClamped = Math.min(cascades, maxCascades);
        float biasScale = 1.0f + (radius * 0.15f) + (Math.max(0, cascadesClamped - 1) * 0.05f);
        bias = Math.max(0.00002f, Math.min(0.02f, bias * biasScale));
        float normalBiasScale = switch (type) {
            case DIRECTIONAL -> 1.0f;
            case SPOT -> 1.2f;
            case POINT -> 1.35f;
        };
        float slopeBiasScale = switch (type) {
            case DIRECTIONAL -> 1.0f;
            case SPOT -> 1.15f;
            case POINT -> 1.30f;
        };
        float base = Math.min(0.9f, 0.25f + (kernelClamped * 0.04f) + (cascadesClamped * 0.05f));
        float tierScale = switch (qualityTier) {
            case LOW -> 0.55f;
            case MEDIUM -> 0.75f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.15f;
        };
        int renderedSpotShadowLights = type == LightType.SPOT
                ? Math.max(1, Math.min(cascadesClamped, selectedSpotShadowLights))
                : 0;
        int renderedPointShadowCubemaps = type == LightType.POINT
                ? Math.max(1, Math.min(Math.min(cascadesClamped / 6, selectedPointShadowLights), maxPointCubemaps))
                : 0;
        int renderedLocalShadowLights = renderedSpotShadowLights + renderedPointShadowCubemaps;
        boolean rtShadowActive = false;
        boolean degraded = kernelClamped != kernel || cascadesClamped != cascades || resolution != requestedResolution
                || qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM;
        return new VulkanEngineRuntime.ShadowRenderConfig(
                true,
                Math.max(0.2f, Math.min(0.9f, base * tierScale)),
                bias,
                normalBiasScale,
                slopeBiasScale,
                radius,
                cascadesClamped,
                resolution,
                maxShadowedLocalLights,
                selectedLocalShadowLights,
                type.name().toLowerCase(java.util.Locale.ROOT),
                primary.id() == null ? "unnamed" : primary.id(),
                atlasCapacityTiles,
                atlasPlan.allocations().size(),
                atlasPlan.utilization(),
                atlasPlan.evictedIds().size(),
                atlasMemoryBytesD16,
                atlasMemoryBytesD32,
                shadowUpdateBytesEstimate,
                renderedLocalShadowLights,
                renderedSpotShadowLights,
                renderedPointShadowCubemaps,
                filterPath,
                shadowContactShadows,
                rtMode,
                rtShadowActive,
                degraded
        );
    }

    static VulkanEngineRuntime.FogRenderConfig mapFog(FogDesc fogDesc, QualityTier qualityTier) {
        if (fogDesc == null || !fogDesc.enabled() || fogDesc.mode() == FogMode.NONE) {
            return new VulkanEngineRuntime.FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0, false);
        }
        float tierDensityScale = switch (qualityTier) {
            case LOW -> 0.55f;
            case MEDIUM -> 0.75f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.2f;
        };
        int tierSteps = switch (qualityTier) {
            case LOW -> 4;
            case MEDIUM -> 8;
            case HIGH -> 16;
            case ULTRA -> 0;
        };
        float density = Math.max(0f, fogDesc.density() * tierDensityScale);
        return new VulkanEngineRuntime.FogRenderConfig(
                true,
                fogDesc.color() == null ? 0.5f : fogDesc.color().x(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().y(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().z(),
                density,
                tierSteps,
                qualityTier == QualityTier.LOW
        );
    }

    static VulkanEngineRuntime.SmokeRenderConfig mapSmoke(List<SmokeEmitterDesc> emitters, QualityTier qualityTier) {
        if (emitters == null || emitters.isEmpty()) {
            return new VulkanEngineRuntime.SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }
        int enabledCount = 0;
        float densityAccum = 0f;
        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (SmokeEmitterDesc emitter : emitters) {
            if (!emitter.enabled()) {
                continue;
            }
            enabledCount++;
            densityAccum += Math.max(0f, emitter.density());
            r += emitter.albedo() == null ? 0.6f : emitter.albedo().x();
            g += emitter.albedo() == null ? 0.6f : emitter.albedo().y();
            b += emitter.albedo() == null ? 0.6f : emitter.albedo().z();
        }
        if (enabledCount == 0) {
            return new VulkanEngineRuntime.SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }
        float avgR = r / enabledCount;
        float avgG = g / enabledCount;
        float avgB = b / enabledCount;
        float baseIntensity = Math.min(0.85f, densityAccum / enabledCount);
        float tierScale = switch (qualityTier) {
            case LOW -> 0.45f;
            case MEDIUM -> 0.7f;
            case HIGH -> 0.9f;
            case ULTRA -> 1.0f;
        };
        return new VulkanEngineRuntime.SmokeRenderConfig(
                true,
                avgR,
                avgG,
                avgB,
                Math.min(0.85f, baseIntensity * tierScale),
                qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM
        );
    }

}
