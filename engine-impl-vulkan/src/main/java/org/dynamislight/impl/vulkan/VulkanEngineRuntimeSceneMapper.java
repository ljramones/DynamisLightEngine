package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.util.List;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.impl.vulkan.asset.VulkanMeshAssetLoader;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;

final class VulkanEngineRuntimeSceneMapper {
    private VulkanEngineRuntimeSceneMapper() {
    }

    static List<VulkanSceneMeshData> buildSceneMeshes(SceneDescriptor scene, VulkanMeshAssetLoader meshLoader, Path assetRoot) {
        return VulkanEngineRuntimeSceneAssembly.buildSceneMeshes(scene, meshLoader, assetRoot);
    }

    static VulkanEngineRuntime.PostProcessRenderConfig mapPostProcess(
            PostProcessDesc desc,
            QualityTier qualityTier,
            boolean taaLumaClipEnabledDefault,
            VulkanEngineRuntime.AaPreset aaPreset,
            VulkanEngineRuntime.AaMode aaMode,
            VulkanEngineRuntime.TsrControls tsrControls
    ) {
        return VulkanEngineRuntimeLightingMapper.mapPostProcess(desc, qualityTier, taaLumaClipEnabledDefault, aaPreset, aaMode, tsrControls);
    }

    static VulkanEngineRuntime.IblRenderConfig mapIbl(EnvironmentDesc environment, QualityTier qualityTier, Path assetRoot) {
        return VulkanEngineRuntimeIblMapper.mapIbl(environment, qualityTier, assetRoot);
    }

    static VulkanEngineRuntime.CameraMatrices cameraMatricesFor(CameraDesc camera, float aspectRatio) {
        return VulkanEngineRuntimeCameraMath.cameraMatricesFor(camera, aspectRatio);
    }

    static CameraDesc selectActiveCamera(SceneDescriptor scene) {
        return VulkanEngineRuntimeCameraMath.selectActiveCamera(scene);
    }

    static VulkanEngineRuntime.LightingConfig mapLighting(List<LightDesc> lights) {
        return VulkanEngineRuntimeLightingMapper.mapLighting(lights);
    }

    static boolean hasNonDirectionalShadowRequest(List<LightDesc> lights) {
        return VulkanEngineRuntimeLightingMapper.hasNonDirectionalShadowRequest(lights);
    }

    static VulkanEngineRuntime.ShadowRenderConfig mapShadows(List<LightDesc> lights, QualityTier qualityTier) {
        return VulkanEngineRuntimeLightingMapper.mapShadows(lights, qualityTier);
    }

    static VulkanEngineRuntime.FogRenderConfig mapFog(FogDesc fogDesc, QualityTier qualityTier) {
        return VulkanEngineRuntimeLightingMapper.mapFog(fogDesc, qualityTier);
    }

    static VulkanEngineRuntime.SmokeRenderConfig mapSmoke(List<SmokeEmitterDesc> emitters, QualityTier qualityTier) {
        return VulkanEngineRuntimeLightingMapper.mapSmoke(emitters, qualityTier);
    }

    static float safeAspect(int width, int height) {
        return VulkanEngineRuntimeCameraMath.safeAspect(width, height);
    }
}
