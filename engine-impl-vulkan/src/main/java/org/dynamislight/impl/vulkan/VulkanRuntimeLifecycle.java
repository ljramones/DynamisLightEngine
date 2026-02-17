package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.util.List;

import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.impl.vulkan.asset.VulkanMeshAssetLoader;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.profile.VulkanFrameMetrics;

final class VulkanRuntimeLifecycle {
    private VulkanRuntimeLifecycle() {
    }

    static void initialize(
            VulkanContext context,
            EngineConfig config,
            VulkanRuntimeOptions.Parsed options,
            long plannedDrawCalls,
            long plannedTriangles,
            long plannedVisibleObjects
    ) throws EngineException {
        context.configureFrameResources(
                options.framesInFlight(),
                options.maxDynamicSceneObjects(),
                options.maxPendingUploadRanges()
        );
        context.configureDynamicUploadMergeGap(options.dynamicUploadMergeGapObjects());
        context.configureDynamicObjectSoftLimit(options.dynamicObjectSoftLimit());
        context.configureDescriptorRing(options.descriptorRingMaxSetCapacity());
        if (options.forceInitFailure()) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Forced Vulkan init failure", false);
        }
        if (!options.mockContext()) {
            context.configurePostProcessMode(options.postOffscreenRequested());
            context.initialize(config.appName(), config.initialWidthPx(), config.initialHeightPx(), options.windowVisible());
            context.setPlannedWorkload(plannedDrawCalls, plannedTriangles, plannedVisibleObjects);
        }
    }

    static SceneLoadState prepareScene(
            SceneDescriptor scene,
            QualityTier qualityTier,
            int viewportWidth,
            int viewportHeight,
            Path assetRoot,
            VulkanMeshAssetLoader meshLoader,
            boolean taaLumaClipEnabledDefault,
            VulkanEngineRuntime.AaPreset aaPreset
    ) {
        var camera = VulkanEngineRuntimeSceneMapper.selectActiveCamera(scene);
        var cameraMatrices = VulkanEngineRuntimeSceneMapper.cameraMatricesFor(
                camera,
                VulkanEngineRuntimeSceneMapper.safeAspect(viewportWidth, viewportHeight)
        );
        var lighting = VulkanEngineRuntimeSceneMapper.mapLighting(scene == null ? null : scene.lights());
        var shadows = VulkanEngineRuntimeSceneMapper.mapShadows(scene == null ? null : scene.lights(), qualityTier);
        var fog = VulkanEngineRuntimeSceneMapper.mapFog(scene == null ? null : scene.fog(), qualityTier);
        var smoke = VulkanEngineRuntimeSceneMapper.mapSmoke(scene == null ? null : scene.smokeEmitters(), qualityTier);
        var post = VulkanEngineRuntimeSceneMapper.mapPostProcess(
                scene == null ? null : scene.postProcess(),
                qualityTier,
                taaLumaClipEnabledDefault,
                aaPreset
        );
        var ibl = VulkanEngineRuntimeSceneMapper.mapIbl(scene == null ? null : scene.environment(), qualityTier, assetRoot);
        boolean nonDirectionalShadowRequested = VulkanEngineRuntimeSceneMapper.hasNonDirectionalShadowRequest(scene == null ? null : scene.lights());
        List<VulkanSceneMeshData> sceneMeshes = VulkanEngineRuntimeSceneMapper.buildSceneMeshes(scene, meshLoader, assetRoot);
        VulkanMeshAssetLoader.CacheProfile cache = meshLoader.cacheProfile();
        var cacheProfile = new VulkanEngineRuntime.MeshGeometryCacheProfile(
                cache.hits(), cache.misses(), cache.evictions(), cache.entries(), cache.maxEntries()
        );
        long plannedDrawCalls = sceneMeshes.size();
        long plannedTriangles = sceneMeshes.stream().mapToLong(m -> m.indices().length / 3).sum();
        long plannedVisibleObjects = plannedDrawCalls;
        return new SceneLoadState(
                cameraMatrices, lighting, fog, smoke, shadows, post, ibl,
                nonDirectionalShadowRequested,
                sceneMeshes,
                cacheProfile,
                plannedDrawCalls,
                plannedTriangles,
                plannedVisibleObjects
        );
    }

    static void applySceneToContext(VulkanContext context, SceneLoadState state) throws EngineException {
        context.setCameraMatrices(state.cameraMatrices().view(), state.cameraMatrices().proj());
        context.setLightingParameters(
                state.lighting().directionalDirection(),
                state.lighting().directionalColor(),
                state.lighting().directionalIntensity(),
                state.lighting().pointPosition(),
                state.lighting().pointColor(),
                state.lighting().pointIntensity(),
                state.lighting().pointDirection(),
                state.lighting().pointInnerCos(),
                state.lighting().pointOuterCos(),
                state.lighting().pointIsSpot(),
                state.lighting().pointRange(),
                state.lighting().pointCastsShadows()
        );
        context.setShadowParameters(
                state.shadows().enabled(),
                state.shadows().strength(),
                state.shadows().bias(),
                state.shadows().pcfRadius(),
                state.shadows().cascadeCount(),
                state.shadows().mapResolution()
        );
        context.setFogParameters(state.fog().enabled(), state.fog().r(), state.fog().g(), state.fog().b(), state.fog().density(), state.fog().steps());
        context.setSmokeParameters(state.smoke().enabled(), state.smoke().r(), state.smoke().g(), state.smoke().b(), state.smoke().intensity());
        context.setIblParameters(state.ibl().enabled(), state.ibl().diffuseStrength(), state.ibl().specularStrength(), state.ibl().prefilterStrength());
        context.setIblTexturePaths(state.ibl().irradiancePath(), state.ibl().radiancePath(), state.ibl().brdfLutPath());
        context.setPostProcessParameters(
                state.post().tonemapEnabled(),
                state.post().exposure(),
                state.post().gamma(),
                state.post().bloomEnabled(),
                state.post().bloomThreshold(),
                state.post().bloomStrength(),
                state.post().ssaoEnabled(),
                state.post().ssaoStrength(),
                state.post().ssaoRadius(),
                state.post().ssaoBias(),
                state.post().ssaoPower(),
                state.post().smaaEnabled(),
                state.post().smaaStrength(),
                state.post().taaEnabled(),
                state.post().taaBlend(),
                state.post().taaClipScale(),
                state.post().taaLumaClipEnabled(),
                state.post().taaSharpenStrength()
        );
        context.setSceneMeshes(state.sceneMeshes());
        context.setPlannedWorkload(state.plannedDrawCalls(), state.plannedTriangles(), state.plannedVisibleObjects());
    }

    static RenderState render(
            VulkanContext context,
            boolean mockContext,
            boolean forceDeviceLostOnRender,
            boolean deviceLostRaised,
            long plannedDrawCalls,
            long plannedTriangles,
            long plannedVisibleObjects
    ) throws EngineException {
        if (forceDeviceLostOnRender && !deviceLostRaised) {
            throw new EngineException(EngineErrorCode.DEVICE_LOST, "Forced Vulkan device loss on render", false);
        }
        if (mockContext) {
            return new RenderState(
                    true,
                    0.2,
                    0.1,
                    plannedDrawCalls,
                    plannedTriangles,
                    plannedVisibleObjects,
                    0
            );
        }
        VulkanFrameMetrics frame = context.renderFrame();
        return new RenderState(
                false,
                frame.cpuFrameMs(),
                frame.gpuFrameMs(),
                frame.drawCalls(),
                frame.triangles(),
                frame.visibleObjects(),
                frame.gpuMemoryBytes()
        );
    }

    static void resize(VulkanContext context, boolean mockContext, int widthPx, int heightPx) throws EngineException {
        if (!mockContext) {
            context.resize(widthPx, heightPx);
        }
    }

    static void shutdown(VulkanContext context, boolean mockContext) {
        if (!mockContext) {
            context.shutdown();
        }
    }

    record SceneLoadState(
            VulkanEngineRuntime.CameraMatrices cameraMatrices,
            VulkanEngineRuntime.LightingConfig lighting,
            VulkanEngineRuntime.FogRenderConfig fog,
            VulkanEngineRuntime.SmokeRenderConfig smoke,
            VulkanEngineRuntime.ShadowRenderConfig shadows,
            VulkanEngineRuntime.PostProcessRenderConfig post,
            VulkanEngineRuntime.IblRenderConfig ibl,
            boolean nonDirectionalShadowRequested,
            List<VulkanSceneMeshData> sceneMeshes,
            VulkanEngineRuntime.MeshGeometryCacheProfile meshGeometryCacheProfile,
            long plannedDrawCalls,
            long plannedTriangles,
            long plannedVisibleObjects
    ) {
    }

    record RenderState(
            boolean deviceLostRaised,
            double cpuMs,
            double gpuMs,
            long drawCalls,
            long triangles,
            long visibleObjects,
            long gpuBytes
    ) {
    }
}
