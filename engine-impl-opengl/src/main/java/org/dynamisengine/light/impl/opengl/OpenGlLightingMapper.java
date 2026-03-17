package org.dynamisengine.light.impl.opengl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.scene.LightDesc;
import org.dynamisengine.light.api.scene.LightType;
import org.dynamisengine.light.api.scene.ShadowDesc;
import org.dynamisengine.light.impl.common.shadow.ShadowAtlasPlanner;

/**
 * Maps scene light descriptors into shadow and lighting render configs.
 */
final class OpenGlLightingMapper {

    private OpenGlLightingMapper() {
    }

    // ── records ──

    record LightingConfig(
            float[] directionalDirection,
            float[] directionalColor,
            float directionalIntensity,
            float[] shadowPointPosition,
            float[] shadowPointDirection,
            boolean shadowPointIsSpot,
            float shadowPointOuterCos,
            float shadowPointRange,
            boolean shadowPointCastsShadows,
            int localLightCount,
            float[] localLightPosRange,
            float[] localLightColorIntensity,
            float[] localLightDirInner,
            float[] localLightOuterTypeShadow
    ) {
    }

    record ShadowRenderConfig(
            boolean enabled,
            float strength,
            float bias,
            float normalBiasScale,
            float slopeBiasScale,
            int pcfRadius,
            int cascadeCount,
            int mapResolution,
            int maxShadowedLocalLights,
            int selectedLocalShadowLights,
            String primaryShadowType,
            String primaryShadowLightId,
            int atlasCapacityTiles,
            int atlasAllocatedTiles,
            float atlasUtilization,
            int atlasEvictions,
            long atlasMemoryBytesD16,
            long atlasMemoryBytesD32,
            long shadowUpdateBytesEstimate,
            boolean degraded
    ) {
    }

    static final ShadowRenderConfig SHADOWS_DISABLED =
            new ShadowRenderConfig(false, 0.45f, 0.0015f, 1.0f, 1.0f, 1, 1, 1024,
                    0, 0, "none", "none", 0, 0, 0.0f, 0, 0L, 0L, 0L, false);

    // ── shadow mapping ──

    static ShadowRenderConfig mapShadows(List<LightDesc> lights, QualityTier qualityTier) {
        if (lights == null || lights.isEmpty()) {
            return SHADOWS_DISABLED;
        }
        int maxShadowedLocalLights = switch (qualityTier) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case ULTRA -> 4;
        };
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
        LightDesc primary = primaryDirectional != null ? primaryDirectional : bestLocal;
        if (primary == null) {
            return new ShadowRenderConfig(false, 0.45f, 0.0015f, 1.0f, 1.0f, 1, 1, 1024, maxShadowedLocalLights, 0, "none", "none", 0, 0, 0.0f, 0, 0L, 0L, 0L, false);
        }

        LightType type = primary.type() == null ? LightType.DIRECTIONAL : primary.type();
        ShadowDesc shadow = primary.shadow();
        int kernel = shadow == null ? 3 : Math.max(1, shadow.pcfKernelSize());
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
        int cascades = shadow == null ? 1 : Math.max(1, shadow.cascadeCount());
        if (type == LightType.SPOT || type == LightType.POINT) {
            cascades = 1;
        }
        int maxCascades = switch (qualityTier) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case ULTRA -> 4;
        };
        cascades = Math.min(cascades, maxCascades);
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
        float biasScale = 1.0f + (radius * 0.15f) + (Math.max(0, cascades - 1) * 0.05f);
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
        float base = Math.min(0.9f, 0.25f + (kernelClamped * 0.04f) + (cascades * 0.05f));
        float tierScale = switch (qualityTier) {
            case LOW -> 0.55f;
            case MEDIUM -> 0.75f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.15f;
        };
        boolean degraded = kernelClamped != kernel
                || resolution != requestedResolution
                || qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM;
        return new ShadowRenderConfig(
                true,
                Math.max(0.2f, Math.min(0.9f, base * tierScale)),
                bias,
                normalBiasScale,
                slopeBiasScale,
                radius,
                cascades,
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
                degraded
        );
    }

    // ── lighting mapping ──

    static LightingConfig mapLighting(List<LightDesc> lights) {
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
        float[] localLightPosRange = new float[OpenGlContext.MAX_LOCAL_LIGHTS * 4];
        float[] localLightColorIntensity = new float[OpenGlContext.MAX_LOCAL_LIGHTS * 4];
        float[] localLightDirInner = new float[OpenGlContext.MAX_LOCAL_LIGHTS * 4];
        float[] localLightOuterTypeShadow = new float[OpenGlContext.MAX_LOCAL_LIGHTS * 4];
        if (lights == null || lights.isEmpty()) {
            return new LightingConfig(
                    dir, dirColor, dirIntensity,
                    shadowPointPos, shadowPointDir, shadowPointIsSpot, shadowPointOuterCos, shadowPointRange, shadowPointCastsShadows,
                    localLightCount, localLightPosRange, localLightColorIntensity, localLightDirInner, localLightOuterTypeShadow
            );
        }
        LightDesc directional = null;
        List<LightDesc> localLights = new ArrayList<>();
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
                dir = normalize3(new float[]{
                        directional.direction().x(),
                        directional.direction().y(),
                        directional.direction().z()
                });
            }
        }
        if (!localLights.isEmpty()) {
            localLights.sort((a, b) -> Float.compare(localLightPriority(b), localLightPriority(a)));
            localLightCount = Math.min(OpenGlContext.MAX_LOCAL_LIGHTS, localLights.size());
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
                        : normalize3(new float[]{light.direction().x(), light.direction().y(), light.direction().z()});
                float inner = 1.0f;
                float outer = 1.0f;
                float isSpot = 0f;
                if (type == LightType.SPOT) {
                    float innerCos = cosFromDegrees(light.innerConeDegrees());
                    float outerCos = cosFromDegrees(light.outerConeDegrees());
                    inner = Math.max(innerCos, outerCos);
                    outer = Math.min(innerCos, outerCos);
                    isSpot = 1f;
                }
                float castsShadows = light.castsShadows() ? 1f : 0f;
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
                localLightOuterTypeShadow[offset + 2] = castsShadows;
            }

            LightDesc shadowLight = localLights.stream().filter(LightDesc::castsShadows).findFirst().orElse(localLights.getFirst());
            if (shadowLight.position() != null) {
                shadowPointPos = new float[]{shadowLight.position().x(), shadowLight.position().y(), shadowLight.position().z()};
            }
            shadowPointRange = shadowLight.range() > 0f ? shadowLight.range() : 15f;
            LightType shadowType = shadowLight.type() == null ? LightType.DIRECTIONAL : shadowLight.type();
            shadowPointIsSpot = shadowType == LightType.SPOT;
            shadowPointCastsShadows = shadowLight.castsShadows();
            if (shadowLight.direction() != null) {
                shadowPointDir = normalize3(new float[]{shadowLight.direction().x(), shadowLight.direction().y(), shadowLight.direction().z()});
            }
            if (shadowPointIsSpot) {
                float innerCos = cosFromDegrees(shadowLight.innerConeDegrees());
                float outerCos = cosFromDegrees(shadowLight.outerConeDegrees());
                shadowPointOuterCos = Math.min(innerCos, outerCos);
            }
        }
        return new LightingConfig(
                dir, dirColor, dirIntensity,
                shadowPointPos, shadowPointDir, shadowPointIsSpot, shadowPointOuterCos, shadowPointRange, shadowPointCastsShadows,
                localLightCount, localLightPosRange, localLightColorIntensity, localLightDirInner, localLightOuterTypeShadow
        );
    }

    static float directionalLightRange(List<LightDesc> lights) {
        if (lights == null || lights.isEmpty()) {
            return 0f;
        }
        for (LightDesc light : lights) {
            if (light == null) continue;
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            if (type == LightType.DIRECTIONAL && light.range() > 0f) {
                return light.range();
            }
        }
        return 0f;
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

    static float localLightPriority(LightDesc light) {
        if (light == null) {
            return Float.NEGATIVE_INFINITY;
        }
        float intensity = Math.max(0f, light.intensity());
        float range = Math.max(0f, light.range());
        float shadowBoost = light.castsShadows() ? 1.15f : 1.0f;
        float spotBoost = (light.type() == LightType.SPOT) ? 1.05f : 1.0f;
        return intensity * (1.0f + (range * 0.08f)) * shadowBoost * spotBoost;
    }

    static float[] normalize3(float[] v) {
        if (v == null || v.length != 3) {
            return new float[]{0f, -1f, 0f};
        }
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1.0e-6f) {
            return new float[]{0f, -1f, 0f};
        }
        return new float[]{v[0] / len, v[1] / len, v[2] / len};
    }

    static float cosFromDegrees(float degrees) {
        float clamped = Math.max(0f, Math.min(89.9f, degrees));
        return (float) Math.cos(Math.toRadians(clamped));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
