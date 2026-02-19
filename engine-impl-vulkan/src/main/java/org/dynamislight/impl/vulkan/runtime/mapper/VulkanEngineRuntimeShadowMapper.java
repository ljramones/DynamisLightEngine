package org.dynamislight.impl.vulkan.runtime.mapper;

import org.dynamislight.impl.vulkan.runtime.math.VulkanEngineRuntimeCameraMath;
import org.dynamislight.impl.vulkan.VulkanContext;
import org.dynamislight.impl.vulkan.runtime.model.LightingConfig;
import org.dynamislight.impl.vulkan.runtime.model.ShadowRenderConfig;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.impl.common.shadow.ShadowAtlasPlanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class VulkanEngineRuntimeShadowMapper {
    private static final int VULKAN_MAX_SHADOW_MATRICES = 24;

    private VulkanEngineRuntimeShadowMapper() {
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static LightingConfig mapLighting(
            List<LightDesc> lights,
            QualityTier qualityTier,
            int shadowMaxLocalLayers
    ) {
        return mapLighting(lights, qualityTier, 0, shadowMaxLocalLayers, 0, false, 1, 2, 4, 0L, Map.of(), Map.of());
    }

    static LightingConfig mapLighting(
            List<LightDesc> lights,
            QualityTier qualityTier,
            int shadowMaxShadowedLocalLights,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame,
            boolean shadowSchedulerEnabled,
            int shadowSchedulerHeroPeriod,
            int shadowSchedulerMidPeriod,
            int shadowSchedulerDistantPeriod,
            long shadowSchedulerFrameTick,
            Map<String, Long> shadowSchedulerLastRenderedTicks,
            Map<String, Integer> shadowLayerAssignments
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
            return new LightingConfig(
                    dir, dirColor, dirIntensity,
                    shadowPointPos, shadowPointDir, shadowPointIsSpot, shadowPointOuterCos, shadowPointRange, shadowPointCastsShadows,
                    localLightCount, localLightPosRange, localLightColorIntensity, localLightDirInner, localLightOuterTypeShadow,
                    Map.of(), 0, 0, 0
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
            localLights.sort(Comparator.comparingDouble((LightDesc light) ->
                    localShadowScore(light, shadowSchedulerLastRenderedTicks, shadowSchedulerFrameTick)).reversed());
            localLightCount = Math.min(VulkanContext.MAX_LOCAL_LIGHTS, localLights.size());
            int tierMaxShadowedLocalLights = switch (qualityTier) {
                case LOW -> 1;
                case MEDIUM -> 2;
                case HIGH -> 3;
                case ULTRA -> 4;
            };
            int maxShadowedLocalLights = shadowMaxShadowedLocalLights > 0
                    ? Math.min(VulkanContext.MAX_LOCAL_LIGHTS, shadowMaxShadowedLocalLights)
                    : tierMaxShadowedLocalLights;
            int tierShadowLayers = switch (qualityTier) {
                case LOW -> 4;
                case MEDIUM -> 6;
                case HIGH -> 8;
                case ULTRA -> 12;
            };
            int maxShadowLayers = shadowMaxLocalLayers > 0
                    ? Math.min(VULKAN_MAX_SHADOW_MATRICES, shadowMaxLocalLayers)
                    : tierShadowLayers;
            int maxShadowFacesPerFrameClamped = shadowMaxFacesPerFrame > 0
                    ? Math.min(VULKAN_MAX_SHADOW_MATRICES, shadowMaxFacesPerFrame)
                    : 0;
            int maxFaceBudget = maxShadowFacesPerFrameClamped > 0
                    ? Math.min(maxShadowLayers, maxShadowFacesPerFrameClamped)
                    : maxShadowLayers;
            int assignedShadowLights = 0;
            int assignedShadowLayers = 0;
            int shadowCandidateRank = 0;
            int allocatorReused = 0;
            int allocatorEvictions = 0;
            boolean[] usedLayers = new boolean[maxShadowLayers + 1];
            java.util.Map<String, Integer> newAssignments = new java.util.HashMap<>();
            boolean hasSpotCandidates = false;
            boolean hasPointCandidates = false;
            for (LightDesc local : localLights) {
                if (local == null || !local.castsShadows()) {
                    continue;
                }
                LightType localType = local.type() == null ? LightType.DIRECTIONAL : local.type();
                if (localType == LightType.SPOT) {
                    hasSpotCandidates = true;
                } else if (localType == LightType.POINT) {
                    hasPointCandidates = true;
                }
            }
            boolean reserveMixedTypeParity = hasSpotCandidates
                    && hasPointCandidates
                    && maxShadowedLocalLights >= 2
                    && maxFaceBudget >= 7;
            int assignedPointShadowLights = 0;
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
                    int cadencePeriod = cadencePeriodForRank(
                            shadowCandidateRank,
                            shadowSchedulerHeroPeriod,
                            shadowSchedulerMidPeriod,
                            shadowSchedulerDistantPeriod
                    );
                    boolean cadenceDue = !shadowSchedulerEnabled
                            || isCadenceDue(shadowSchedulerFrameTick, shadowCandidateRank, cadencePeriod);
                    int layerCost = isSpot > 0.5f ? 1 : 6;
                    if (cadenceDue) {
                        if (reserveMixedTypeParity && isSpot > 0.5f
                                && assignedPointShadowLights == 0
                                && hasRemainingPointCandidate(localLights, localLightCount, i + 1)) {
                            int remainingFaceBudget = maxFaceBudget - (assignedShadowLayers + 1);
                            int remainingLightBudget = maxShadowedLocalLights - (assignedShadowLights + 1);
                            if (remainingFaceBudget < 6 || remainingLightBudget < 1) {
                                shadowCandidateRank++;
                                continue;
                            }
                        }
                        if (assignedShadowLayers + layerCost > maxFaceBudget) {
                            shadowCandidateRank++;
                            continue;
                        }
                        String lightId = shadowLightId(light);
                        Integer previousLayerBase = shadowLayerAssignments == null ? null : shadowLayerAssignments.get(lightId);
                        int selectedLayerBase = 0;
                        if (previousLayerBase != null && layerRangeFits(previousLayerBase, layerCost, maxShadowLayers, usedLayers)) {
                            selectedLayerBase = previousLayerBase;
                            allocatorReused++;
                        } else {
                            if (previousLayerBase != null) {
                                allocatorEvictions++;
                            }
                            selectedLayerBase = firstFitLayerBase(layerCost, maxShadowLayers, usedLayers);
                        }
                        if (selectedLayerBase > 0) {
                            markLayerRange(selectedLayerBase, layerCost, usedLayers, true);
                            layerBase = selectedLayerBase;
                            assignedShadowLayers += layerCost;
                            assignedShadowLights++;
                            if (isSpot <= 0.5f) {
                                assignedPointShadowLights++;
                            }
                            newAssignments.put(lightId, selectedLayerBase);
                        }
                    }
                    shadowCandidateRank++;
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
            if (assignedShadowLayers > 0 || (shadowSchedulerEnabled && !localLights.isEmpty())) {
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
            return new LightingConfig(
                    dir, dirColor, dirIntensity,
                    shadowPointPos, shadowPointDir, shadowPointIsSpot, shadowPointOuterCos, shadowPointRange, shadowPointCastsShadows,
                    localLightCount, localLightPosRange, localLightColorIntensity, localLightDirInner, localLightOuterTypeShadow,
                    java.util.Map.copyOf(newAssignments), assignedShadowLights, allocatorReused, allocatorEvictions
            );
        }
        return new LightingConfig(
                dir, dirColor, dirIntensity,
                shadowPointPos, shadowPointDir, shadowPointIsSpot, shadowPointOuterCos, shadowPointRange, shadowPointCastsShadows,
                localLightCount, localLightPosRange, localLightColorIntensity, localLightDirInner, localLightOuterTypeShadow,
                Map.of(), 0, 0, 0
        );
    }

    public static boolean hasNonDirectionalShadowRequest(List<LightDesc> lights) {
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

    public static ShadowRenderConfig mapShadows(
            List<LightDesc> lights,
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame
    ) {
        return mapShadows(
                lights,
                qualityTier,
                shadowFilterPath,
                shadowContactShadows,
                shadowRtMode,
                0,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame,
                false
        );
    }

    public static ShadowRenderConfig mapShadows(
            List<LightDesc> lights,
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            int shadowMaxShadowedLocalLights,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame
    ) {
        return mapShadows(
                lights,
                qualityTier,
                shadowFilterPath,
                shadowContactShadows,
                shadowRtMode,
                shadowMaxShadowedLocalLights,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame,
                false
        );
    }

    public static ShadowRenderConfig mapShadows(
            List<LightDesc> lights,
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            int shadowMaxShadowedLocalLights,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame,
            boolean shadowRtTraversalSupported
    ) {
        return mapShadows(
                lights,
                qualityTier,
                shadowFilterPath,
                shadowContactShadows,
                shadowRtMode,
                shadowMaxShadowedLocalLights,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame,
                shadowRtTraversalSupported,
                false,
                false,
                1,
                2,
                4,
                0L,
                Map.of()
        );
    }

    public static ShadowRenderConfig mapShadows(
            List<LightDesc> lights,
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            int shadowMaxShadowedLocalLights,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame,
            boolean shadowRtTraversalSupported,
            boolean shadowRtBvhSupported,
            boolean shadowSchedulerEnabled,
            int shadowSchedulerHeroPeriod,
            int shadowSchedulerMidPeriod,
            int shadowSchedulerDistantPeriod,
            long shadowSchedulerFrameTick,
            Map<String, Long> shadowSchedulerLastRenderedTicks
    ) {
        String filterPath = shadowFilterPath == null || shadowFilterPath.isBlank() ? "pcf" : shadowFilterPath.trim().toLowerCase(java.util.Locale.ROOT);
        boolean momentFilterEstimateOnly = false;
        boolean momentPipelineRequested = "vsm".equals(filterPath) || "evsm".equals(filterPath);
        boolean momentPipelineActive = false;
        String runtimeFilterPath = switch (filterPath) {
            case "pcss", "vsm", "evsm" -> filterPath;
            default -> "pcf";
        };
        String rtMode = shadowRtMode == null || shadowRtMode.isBlank() ? "off" : shadowRtMode.trim().toLowerCase(java.util.Locale.ROOT);
        if (lights == null || lights.isEmpty()) {
            return new ShadowRenderConfig(false, 0.45f, 0.0015f, 1.0f, 1.0f, 1, 1, 1024, 0, 0, "none", "none", 0, 0, 0.0f, 0, 0L, 0L, 0L, 0L, 0, 0, 0, "", 0, "", 0, filterPath, runtimeFilterPath, momentFilterEstimateOnly, momentPipelineRequested, momentPipelineActive, shadowContactShadows, rtMode, false, false);
        }
        int tierMaxShadowedLocalLights = switch (qualityTier) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case ULTRA -> 4;
        };
        int maxShadowedLocalLights = shadowMaxShadowedLocalLights > 0
                ? Math.min(VulkanContext.MAX_LOCAL_LIGHTS, shadowMaxShadowedLocalLights)
                : tierMaxShadowedLocalLights;
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
        localShadowCandidates.sort(Comparator.comparingDouble((LightDesc light) ->
                localShadowScore(light, shadowSchedulerLastRenderedTicks, shadowSchedulerFrameTick)).reversed());
        int selectedLocalShadowLights = Math.min(maxShadowedLocalLights, localShadowCandidates.size());
        selectedLocalShadowLights = Math.min(maxShadowedLocalLights, selectedLocalShadowLights);
        if (selectedLocalShadowLights >= 2 && localShadowCandidates.size() > selectedLocalShadowLights) {
            int selectedPointCount = countTypeInPrefix(localShadowCandidates, selectedLocalShadowLights, LightType.POINT);
            int selectedSpotCount = countTypeInPrefix(localShadowCandidates, selectedLocalShadowLights, LightType.SPOT);
            if (selectedPointCount == 0 && hasTypeBeyondPrefix(localShadowCandidates, selectedLocalShadowLights, LightType.POINT)) {
                int outsidePoint = firstTypeIndex(localShadowCandidates, selectedLocalShadowLights, localShadowCandidates.size(), LightType.POINT);
                int insideSpot = lastTypeIndex(localShadowCandidates, 0, selectedLocalShadowLights, LightType.SPOT);
                if (outsidePoint >= 0 && insideSpot >= 0) {
                    java.util.Collections.swap(localShadowCandidates, outsidePoint, insideSpot);
                }
            }
            selectedPointCount = countTypeInPrefix(localShadowCandidates, selectedLocalShadowLights, LightType.POINT);
            selectedSpotCount = countTypeInPrefix(localShadowCandidates, selectedLocalShadowLights, LightType.SPOT);
            if (selectedSpotCount == 0 && hasTypeBeyondPrefix(localShadowCandidates, selectedLocalShadowLights, LightType.SPOT)) {
                int outsideSpot = firstTypeIndex(localShadowCandidates, selectedLocalShadowLights, localShadowCandidates.size(), LightType.SPOT);
                int insidePoint = lastTypeIndex(localShadowCandidates, 0, selectedLocalShadowLights, LightType.POINT);
                if (outsideSpot >= 0 && insidePoint >= 0) {
                    java.util.Collections.swap(localShadowCandidates, outsideSpot, insidePoint);
                }
            }
        }
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
            return new ShadowRenderConfig(false, 0.45f, 0.0015f, 1.0f, 1.0f, 1, 1, 1024, maxShadowedLocalLights, 0, "none", "none", 0, 0, 0.0f, 0, 0L, 0L, 0L, 0L, 0, 0, 0, "", 0, "", 0, filterPath, runtimeFilterPath, momentFilterEstimateOnly, momentPipelineRequested, momentPipelineActive, shadowContactShadows, rtMode, false, false);
        }
        LightType type = primary.type() == null ? LightType.DIRECTIONAL : primary.type();
        ShadowDesc shadow = primary.shadow();
        int kernel = shadow == null ? 3 : Math.max(1, shadow.pcfKernelSize());
        int cascades = shadow == null ? 1 : Math.max(1, shadow.cascadeCount());
        int faceBudget = shadowMaxFacesPerFrame > 0
                ? Math.min(VULKAN_MAX_SHADOW_MATRICES, shadowMaxFacesPerFrame)
                : 0;
        LocalShadowSchedule schedule = scheduleLocalShadows(
                localShadowCandidates,
                selectedLocalShadowLights,
                maxShadowedLocalLights,
                maxShadowLayers,
                shadowSchedulerEnabled,
                shadowSchedulerHeroPeriod,
                shadowSchedulerMidPeriod,
                shadowSchedulerDistantPeriod,
                shadowSchedulerFrameTick,
                faceBudget,
                shadowSchedulerLastRenderedTicks
        );
        if (type == LightType.SPOT) {
            cascades = Math.max(1, Math.min(4, schedule.renderedSpotShadowLights()));
            if (faceBudget > 0) {
                cascades = Math.max(1, Math.min(cascades, faceBudget));
            }
        } else if (type == LightType.POINT) {
            int maxPointCubemaps = Math.max(1, maxShadowLayers / 6);
            cascades = 6 * Math.max(1, Math.min(maxPointCubemaps, schedule.renderedPointShadowCubemaps()));
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
        long shadowMomentAtlasBytesEstimate = switch (filterPath) {
            case "vsm" -> (long) resolution * (long) resolution * 8L;
            case "evsm" -> (long) resolution * (long) resolution * 16L;
            default -> 0L;
        };
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
        int localShadowPasses = schedule.renderedSpotShadowLights() + (schedule.renderedPointShadowCubemaps() * 6);
        int cascadesClamped = Math.min(cascades, maxCascades);
        if (localShadowPasses > 0) {
            cascadesClamped = Math.max(1, Math.min(Math.min(localShadowPasses, maxShadowLayers), VULKAN_MAX_SHADOW_MATRICES));
        }
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
        int renderedSpotShadowLights = schedule.renderedSpotShadowLights();
        int renderedPointShadowCubemaps = schedule.renderedPointShadowCubemaps();
        int renderedLocalShadowLights = renderedSpotShadowLights + renderedPointShadowCubemaps;
        boolean rtShadowActive;
        if ("bvh_dedicated".equals(rtMode) || "bvh_production".equals(rtMode)) {
            rtShadowActive = shadowRtBvhSupported;
        } else if ("rt_native".equals(rtMode) || "rt_native_denoised".equals(rtMode)) {
            rtShadowActive = shadowRtTraversalSupported;
        } else if ("bvh".equals(rtMode)) {
            rtShadowActive = shadowRtBvhSupported;
        } else {
            rtShadowActive = !"off".equals(rtMode) && shadowRtTraversalSupported;
        }
        boolean degraded = kernelClamped != kernel || cascadesClamped != cascades || resolution != requestedResolution
                || qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM;
        return new ShadowRenderConfig(
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
                shadowMomentAtlasBytesEstimate,
                renderedLocalShadowLights,
                renderedSpotShadowLights,
                renderedPointShadowCubemaps,
                schedule.renderedShadowLightIdsCsv(),
                schedule.deferredShadowLightCount(),
                schedule.deferredShadowLightIdsCsv(),
                schedule.staleBypassShadowLightCount(),
                filterPath,
                runtimeFilterPath,
                momentFilterEstimateOnly,
                momentPipelineRequested,
                momentPipelineActive,
                shadowContactShadows,
                rtMode,
                rtShadowActive,
                degraded
        );
    }

    private static LocalShadowSchedule scheduleLocalShadows(
            List<LightDesc> localShadowCandidates,
            int selectedLocalShadowLights,
            int maxShadowedLocalLights,
            int maxShadowLayers,
            boolean schedulerEnabled,
            int heroPeriod,
            int midPeriod,
            int distantPeriod,
            long frameTick,
            int faceBudget,
            Map<String, Long> lastRenderedTicks
    ) {
        int renderedSpot = 0;
        int renderedPoint = 0;
        int staleBypassCount = 0;
        int assignedLayers = 0;
        int assignedLights = 0;
        List<String> renderedIds = new ArrayList<>();
        List<String> deferredIds = new ArrayList<>();
        boolean hasSpotCandidates = false;
        boolean hasPointCandidates = false;
        for (int i = 0; i < selectedLocalShadowLights && i < localShadowCandidates.size(); i++) {
            LightDesc candidate = localShadowCandidates.get(i);
            if (candidate == null || !candidate.castsShadows()) {
                continue;
            }
            LightType type = candidate.type() == null ? LightType.DIRECTIONAL : candidate.type();
            hasSpotCandidates = hasSpotCandidates || type == LightType.SPOT;
            hasPointCandidates = hasPointCandidates || type == LightType.POINT;
        }
        boolean reserveMixedTypeParity = hasSpotCandidates
                && hasPointCandidates
                && maxShadowedLocalLights >= 2
                && maxShadowLayers >= 7;
        for (int rank = 0; rank < selectedLocalShadowLights && rank < localShadowCandidates.size(); rank++) {
            LightDesc candidate = localShadowCandidates.get(rank);
            if (candidate == null || !candidate.castsShadows()) {
                continue;
            }
            LightType localType = candidate.type() == null ? LightType.DIRECTIONAL : candidate.type();
            if (localType != LightType.SPOT && localType != LightType.POINT) {
                continue;
            }
            if (assignedLights >= maxShadowedLocalLights) {
                deferredIds.add(shadowLightId(candidate));
                break;
            }
            if (reserveMixedTypeParity && localType == LightType.SPOT
                    && renderedPoint == 0
                    && renderedSpot >= 1
                    && hasRemainingPointCandidate(localShadowCandidates, selectedLocalShadowLights, rank + 1)) {
                int spotLayerCost = 1;
                int reservedPointLayerCost = 6;
                int remainingLayerBudget = maxShadowLayers - (assignedLayers + spotLayerCost);
                int remainingFaceBudget = faceBudget > 0 ? faceBudget - (assignedLayers + spotLayerCost) : Integer.MAX_VALUE;
                int remainingLightBudget = maxShadowedLocalLights - (assignedLights + 1);
                boolean pointWouldBeBlockedByThisSpot = remainingLayerBudget < reservedPointLayerCost
                        || remainingFaceBudget < reservedPointLayerCost
                        || remainingLightBudget < 1;
                if (pointWouldBeBlockedByThisSpot) {
                    deferredIds.add(shadowLightId(candidate));
                    continue;
                }
            }
            int cadencePeriod = cadencePeriodForRank(rank, heroPeriod, midPeriod, distantPeriod);
            String candidateId = shadowLightId(candidate);
            boolean cadenceDue = isCadenceDue(frameTick, rank, cadencePeriod);
            boolean stalenessBypass = schedulerEnabled && !cadenceDue && isStalenessBypassDue(
                    frameTick,
                    candidateId,
                    cadencePeriod,
                    lastRenderedTicks
            );
            if (schedulerEnabled && !cadenceDue && !stalenessBypass) {
                deferredIds.add(shadowLightId(candidate));
                continue;
            }
            int layerCost = localType == LightType.SPOT ? 1 : 6;
            if (faceBudget > 0 && assignedLayers + layerCost > faceBudget) {
                deferredIds.add(shadowLightId(candidate));
                continue;
            }
            if (assignedLayers + layerCost > maxShadowLayers) {
                deferredIds.add(shadowLightId(candidate));
                continue;
            }
            assignedLayers += layerCost;
            assignedLights++;
            if (stalenessBypass) {
                staleBypassCount++;
            }
            renderedIds.add(candidateId);
            if (localType == LightType.SPOT) {
                renderedSpot++;
            } else {
                renderedPoint++;
            }
        }
        return new LocalShadowSchedule(
                renderedSpot,
                renderedPoint,
                String.join(",", renderedIds),
                deferredIds.size(),
                String.join(",", deferredIds),
                staleBypassCount
        );
    }

    private static boolean hasRemainingPointCandidate(
            List<LightDesc> candidates,
            int selectedLocalShadowLights,
            int start
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (int i = Math.max(0, start); i < selectedLocalShadowLights && i < candidates.size(); i++) {
            LightDesc candidate = candidates.get(i);
            if (candidate == null || !candidate.castsShadows()) {
                continue;
            }
            LightType type = candidate.type() == null ? LightType.DIRECTIONAL : candidate.type();
            if (type == LightType.POINT) {
                return true;
            }
        }
        return false;
    }

    private static int countTypeInPrefix(List<LightDesc> lights, int prefix, LightType expectedType) {
        if (lights == null || lights.isEmpty() || expectedType == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < prefix && i < lights.size(); i++) {
            LightDesc light = lights.get(i);
            if (light == null || !light.castsShadows()) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            if (type == expectedType) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasTypeBeyondPrefix(List<LightDesc> lights, int prefix, LightType expectedType) {
        return firstTypeIndex(lights, Math.max(0, prefix), lights == null ? 0 : lights.size(), expectedType) >= 0;
    }

    private static int firstTypeIndex(List<LightDesc> lights, int startInclusive, int endExclusive, LightType expectedType) {
        if (lights == null || lights.isEmpty() || expectedType == null) {
            return -1;
        }
        for (int i = Math.max(0, startInclusive); i < endExclusive && i < lights.size(); i++) {
            LightDesc light = lights.get(i);
            if (light == null || !light.castsShadows()) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            if (type == expectedType) {
                return i;
            }
        }
        return -1;
    }

    private static int lastTypeIndex(List<LightDesc> lights, int startInclusive, int endExclusive, LightType expectedType) {
        if (lights == null || lights.isEmpty() || expectedType == null) {
            return -1;
        }
        for (int i = Math.min(endExclusive, lights.size()) - 1; i >= Math.max(0, startInclusive); i--) {
            LightDesc light = lights.get(i);
            if (light == null || !light.castsShadows()) {
                continue;
            }
            LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
            if (type == expectedType) {
                return i;
            }
        }
        return -1;
    }

    private static int cadencePeriodForRank(int rank, int heroPeriod, int midPeriod, int distantPeriod) {
        if (rank <= 0) {
            return Math.max(1, heroPeriod);
        }
        if (rank <= 2) {
            return Math.max(1, midPeriod);
        }
        return Math.max(1, distantPeriod);
    }

    private static boolean isCadenceDue(long frameTick, int rank, int cadencePeriod) {
        if (cadencePeriod <= 1) {
            return true;
        }
        return Math.floorMod(frameTick + rank, cadencePeriod) == 0;
    }

    private static boolean isStalenessBypassDue(
            long frameTick,
            String lightId,
            int cadencePeriod,
            Map<String, Long> lastRenderedTicks
    ) {
        if (lastRenderedTicks == null || lightId == null || lightId.isBlank()) {
            return false;
        }
        long lastTick = lastRenderedTicks.getOrDefault(lightId, Long.MIN_VALUE);
        if (lastTick == Long.MIN_VALUE) {
            return false;
        }
        long age = Math.max(0L, frameTick - lastTick);
        long staleThreshold = Math.max(2L, (long) Math.max(1, cadencePeriod) * 2L);
        return age >= staleThreshold;
    }

    private static boolean layerRangeFits(int layerBase, int layerCost, int maxLayers, boolean[] usedLayers) {
        if (layerBase <= 0 || layerCost <= 0 || layerBase + layerCost - 1 > maxLayers) {
            return false;
        }
        for (int i = layerBase; i < layerBase + layerCost; i++) {
            if (usedLayers[i]) {
                return false;
            }
        }
        return true;
    }

    private static int firstFitLayerBase(int layerCost, int maxLayers, boolean[] usedLayers) {
        for (int base = 1; base + layerCost - 1 <= maxLayers; base++) {
            if (layerRangeFits(base, layerCost, maxLayers, usedLayers)) {
                return base;
            }
        }
        return 0;
    }

    private static void markLayerRange(int layerBase, int layerCost, boolean[] usedLayers, boolean value) {
        for (int i = layerBase; i < layerBase + layerCost && i < usedLayers.length; i++) {
            usedLayers[i] = value;
        }
    }

    private record LocalShadowSchedule(
            int renderedSpotShadowLights,
            int renderedPointShadowCubemaps,
            String renderedShadowLightIdsCsv,
            int deferredShadowLightCount,
            String deferredShadowLightIdsCsv,
            int staleBypassShadowLightCount
    ) {
    }

    private static String shadowLightId(LightDesc light) {
        if (light == null) {
            return "shadow-light";
        }
        if (light.id() != null && !light.id().isBlank()) {
            return light.id();
        }
        LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
        float px = light.position() == null ? 0f : light.position().x();
        float py = light.position() == null ? 0f : light.position().y();
        float pz = light.position() == null ? 0f : light.position().z();
        return type.name().toLowerCase(java.util.Locale.ROOT) + ":" + Math.round(px * 10f) + ":" + Math.round(py * 10f) + ":" + Math.round(pz * 10f);
    }

    private static double localShadowScore(
            LightDesc light,
            Map<String, Long> lastRenderedTicks,
            long frameTick
    ) {
        float base = localLightPriority(light);
        String id = shadowLightId(light);
        long lastTick = lastRenderedTicks == null ? Long.MIN_VALUE : lastRenderedTicks.getOrDefault(id, Long.MIN_VALUE);
        long age = lastTick == Long.MIN_VALUE ? 64 : Math.max(0L, frameTick - lastTick);
        double ageBoost = 1.0 + (Math.min(age, 64L) * 0.02);
        return base * ageBoost;
    }
}
