package org.dynamislight.impl.vulkan.runtime.mapper;

import org.dynamislight.impl.vulkan.runtime.math.VulkanEngineRuntimeCameraMath;

import org.dynamislight.impl.vulkan.runtime.model.*;

import org.dynamislight.impl.vulkan.runtime.config.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.ReflectionProbeDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.impl.vulkan.asset.VulkanMeshAssetLoader;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;

public final class VulkanEngineRuntimeSceneMapper {
    private VulkanEngineRuntimeSceneMapper() {
    }

    public static List<VulkanSceneMeshData> buildSceneMeshes(SceneDescriptor scene, VulkanMeshAssetLoader meshLoader, Path assetRoot) {
        return VulkanEngineRuntimeSceneAssembly.buildSceneMeshes(scene, meshLoader, assetRoot);
    }

    public static PostProcessRenderConfig mapPostProcess(
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
        return VulkanEngineRuntimeLightingMapper.mapPostProcess(
                desc,
                qualityTier,
                taaLumaClipEnabledDefault,
                aaPreset,
                aaMode,
                upscalerMode,
                upscalerQuality,
                tsrControls,
                reflectionProfile
        );
    }

    public static IblRenderConfig mapIbl(EnvironmentDesc environment, QualityTier qualityTier, Path assetRoot) {
        return VulkanEngineRuntimeIblMapper.mapIbl(environment, qualityTier, assetRoot);
    }

    public static CameraMatrices cameraMatricesFor(CameraDesc camera, float aspectRatio) {
        return VulkanEngineRuntimeCameraMath.cameraMatricesFor(camera, aspectRatio);
    }

    public static CameraDesc selectActiveCamera(SceneDescriptor scene) {
        return VulkanEngineRuntimeCameraMath.selectActiveCamera(scene);
    }

    public static LightingConfig mapLighting(
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
        return VulkanEngineRuntimeLightingMapper.mapLighting(
                lights,
                qualityTier,
                shadowMaxShadowedLocalLights,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame,
                shadowSchedulerEnabled,
                shadowSchedulerHeroPeriod,
                shadowSchedulerMidPeriod,
                shadowSchedulerDistantPeriod,
                shadowSchedulerFrameTick,
                shadowSchedulerLastRenderedTicks,
                shadowLayerAssignments
        );
    }

    public static boolean hasNonDirectionalShadowRequest(List<LightDesc> lights) {
        return VulkanEngineRuntimeLightingMapper.hasNonDirectionalShadowRequest(lights);
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
        return VulkanEngineRuntimeLightingMapper.mapShadows(
                lights,
                qualityTier,
                shadowFilterPath,
                shadowContactShadows,
                shadowRtMode,
                shadowMaxShadowedLocalLights,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame,
                shadowRtTraversalSupported,
                shadowRtBvhSupported,
                shadowSchedulerEnabled,
                shadowSchedulerHeroPeriod,
                shadowSchedulerMidPeriod,
                shadowSchedulerDistantPeriod,
                shadowSchedulerFrameTick,
                shadowSchedulerLastRenderedTicks
        );
    }

    public static FogRenderConfig mapFog(FogDesc fogDesc, QualityTier qualityTier) {
        return VulkanEngineRuntimeLightingMapper.mapFog(fogDesc, qualityTier);
    }

    public static SmokeRenderConfig mapSmoke(List<SmokeEmitterDesc> emitters, QualityTier qualityTier) {
        return VulkanEngineRuntimeLightingMapper.mapSmoke(emitters, qualityTier);
    }

    public static float safeAspect(int width, int height) {
        return VulkanEngineRuntimeCameraMath.safeAspect(width, height);
    }

    public static List<ReflectionProbeDesc> mapReflectionProbes(SceneDescriptor scene) {
        if (scene == null || scene.postProcess() == null || scene.postProcess().reflectionAdvanced() == null) {
            return List.of();
        }
        return scene.postProcess().reflectionAdvanced().probes();
    }
}
