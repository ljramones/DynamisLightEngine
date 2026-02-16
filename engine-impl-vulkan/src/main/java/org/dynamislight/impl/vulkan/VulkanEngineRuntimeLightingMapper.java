package org.dynamislight.impl.vulkan;

import java.util.List;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;

final class VulkanEngineRuntimeLightingMapper {
    private VulkanEngineRuntimeLightingMapper() {
    }

    static VulkanEngineRuntime.PostProcessRenderConfig mapPostProcess(PostProcessDesc desc, QualityTier qualityTier) {
        if (desc == null || !desc.enabled()) {
            return new VulkanEngineRuntime.PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.8f, false, 0f);
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
        if (qualityTier == QualityTier.MEDIUM) {
            ssaoStrength *= 0.8f;
        }
        return new VulkanEngineRuntime.PostProcessRenderConfig(
                desc.tonemapEnabled(),
                exposure,
                gamma,
                bloomEnabled,
                bloomThreshold,
                bloomStrength,
                ssaoEnabled,
                ssaoStrength
        );
    }

    static VulkanEngineRuntime.LightingConfig mapLighting(List<LightDesc> lights) {
        float[] dir = new float[]{0.35f, -1.0f, 0.25f};
        float[] dirColor = new float[]{1.0f, 0.98f, 0.95f};
        float dirIntensity = 1.0f;
        float[] pointPos = new float[]{0f, 1.3f, 1.8f};
        float[] pointColor = new float[]{0.95f, 0.62f, 0.22f};
        float pointIntensity = 1.0f;
        float[] pointDir = new float[]{0f, -1f, 0f};
        float pointInnerCos = 1.0f;
        float pointOuterCos = 1.0f;
        boolean pointIsSpot = false;
        float pointRange = 15f;
        boolean pointCastsShadows = false;
        if (lights == null || lights.isEmpty()) {
            return new VulkanEngineRuntime.LightingConfig(
                    dir, dirColor, dirIntensity,
                    pointPos, pointColor, pointIntensity,
                    pointDir, pointInnerCos, pointOuterCos, pointIsSpot, pointRange, pointCastsShadows
            );
        }
        LightDesc directional = null;
        LightDesc pointLike = null;
        for (LightDesc light : lights) {
            if (light == null) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            if (directional == null && type == LightType.DIRECTIONAL) {
                directional = light;
            }
            if (pointLike == null && (type == LightType.POINT || type == LightType.SPOT)) {
                pointLike = light;
            }
        }
        if (directional == null) {
            directional = lights.getFirst();
        }
        if (pointLike == null && lights.size() > 1) {
            pointLike = lights.get(1);
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
        if (pointLike != null && pointLike.color() != null) {
            pointColor = new float[]{
                    clamp01(pointLike.color().x()),
                    clamp01(pointLike.color().y()),
                    clamp01(pointLike.color().z())
            };
        }
        if (pointLike != null) {
            pointIntensity = Math.max(0f, pointLike.intensity());
            pointRange = pointLike.range() > 0f ? pointLike.range() : 15f;
            if (pointLike.position() != null) {
                pointPos = new float[]{pointLike.position().x(), pointLike.position().y(), pointLike.position().z()};
            }
            LightType pointType = pointLike.type() == null ? LightType.DIRECTIONAL : pointLike.type();
            if (pointType == LightType.SPOT) {
                pointIsSpot = true;
                if (pointLike.direction() != null) {
                    pointDir = VulkanEngineRuntimeCameraMath.normalize3(new float[]{
                            pointLike.direction().x(),
                            pointLike.direction().y(),
                            pointLike.direction().z()
                    });
                }
                float inner = VulkanEngineRuntimeCameraMath.cosFromDegrees(pointLike.innerConeDegrees());
                float outer = VulkanEngineRuntimeCameraMath.cosFromDegrees(pointLike.outerConeDegrees());
                pointInnerCos = Math.max(inner, outer);
                pointOuterCos = Math.min(inner, outer);
            }
            pointCastsShadows = pointType == LightType.POINT && pointLike.castsShadows();
        }
        return new VulkanEngineRuntime.LightingConfig(
                dir, dirColor, dirIntensity,
                pointPos, pointColor, pointIntensity,
                pointDir, pointInnerCos, pointOuterCos, pointIsSpot, pointRange, pointCastsShadows
        );
    }

    static boolean hasNonDirectionalShadowRequest(List<LightDesc> lights) {
        return false;
    }

    static VulkanEngineRuntime.ShadowRenderConfig mapShadows(List<LightDesc> lights, QualityTier qualityTier) {
        if (lights == null || lights.isEmpty()) {
            return new VulkanEngineRuntime.ShadowRenderConfig(false, 0.45f, 0.0015f, 1, 1, 1024, false);
        }
        for (LightDesc light : lights) {
            if (light == null || !light.castsShadows()) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            ShadowDesc shadow = light.shadow();
            int kernel = shadow == null ? 3 : Math.max(1, shadow.pcfKernelSize());
            int cascades = shadow == null ? 1 : Math.max(1, shadow.cascadeCount());
            if (type == LightType.SPOT) {
                cascades = 1;
            } else if (type == LightType.POINT) {
                cascades = 6;
            }
            int resolution = shadow == null ? 1024 : Math.max(256, Math.min(4096, shadow.mapResolution()));
            float bias = shadow == null ? 0.0015f : Math.max(0.00002f, shadow.depthBias());
            int maxKernel = switch (qualityTier) {
                case LOW -> 3;
                case MEDIUM -> 5;
                case HIGH -> 7;
                case ULTRA -> 9;
            };
            int kernelClamped = Math.min(kernel, maxKernel);
            int radius = Math.max(0, (kernelClamped - 1) / 2);
            int maxCascades = switch (qualityTier) {
                case LOW -> type == LightType.POINT ? 6 : 1;
                case MEDIUM -> type == LightType.POINT ? 6 : 2;
                case HIGH -> type == LightType.POINT ? 6 : 3;
                case ULTRA -> type == LightType.POINT ? 6 : 4;
            };
            int cascadesClamped = Math.min(cascades, maxCascades);
            float biasScale = 1.0f + (radius * 0.15f) + (Math.max(0, cascadesClamped - 1) * 0.05f);
            bias = Math.max(0.00002f, Math.min(0.02f, bias * biasScale));
            float base = Math.min(0.9f, 0.25f + (kernelClamped * 0.04f) + (cascadesClamped * 0.05f));
            float tierScale = switch (qualityTier) {
                case LOW -> 0.55f;
                case MEDIUM -> 0.75f;
                case HIGH -> 1.0f;
                case ULTRA -> 1.15f;
            };
            boolean degraded = kernelClamped != kernel || cascadesClamped != cascades
                    || qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM;
            return new VulkanEngineRuntime.ShadowRenderConfig(
                    true,
                    Math.max(0.2f, Math.min(0.9f, base * tierScale)),
                    bias,
                    radius,
                    cascadesClamped,
                    resolution,
                    degraded
            );
        }
        return new VulkanEngineRuntime.ShadowRenderConfig(false, 0.45f, 0.0015f, 1, 1, 1024, false);
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

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
