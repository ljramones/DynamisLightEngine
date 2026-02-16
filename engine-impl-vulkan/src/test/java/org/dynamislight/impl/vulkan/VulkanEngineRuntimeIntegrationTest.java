package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.event.DeviceLostEvent;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.input.KeyCode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.lwjgl.glfw.GLFW;
import org.junit.jupiter.api.Test;

class VulkanEngineRuntimeIntegrationTest {
    @Test
    void guardedRealVulkanInitPath() {
        assumeRealVulkanReady("real Vulkan init integration test");

        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();

        try {
            runtime.initialize(validConfig(false), callbacks);
            runtime.loadScene(validScene());
            var frame = runtime.render();
            assertNotNull(frame);
            assertTrue(frame.frameIndex() > 0);
        } catch (EngineException e) {
            assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, e.code());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void mockVulkanPathAlwaysWorksInCi() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validScene());
        runtime.update(1.0 / 60.0, emptyInput());
        var frame = runtime.render();
        assertTrue(frame.frameIndex() > 0);
        runtime.shutdown();
    }

    @Test
    void forcedInitFailureMapsToBackendInitFailed() {
        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();

        EngineException ex = org.junit.jupiter.api.Assertions.assertThrows(
                EngineException.class,
                () -> runtime.initialize(validConfig(Map.of("vulkan.forceInitFailure", "true")), callbacks)
        );

        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, ex.code());
        assertFalse(callbacks.errors.isEmpty());
        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, callbacks.errors.getFirst().code());
    }

    @Test
    void mockVulkanStatsReflectSceneMeshWorkload() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validMultiMeshScene());
        runtime.render();

        assertEquals(2, runtime.getStats().drawCalls());
        assertEquals(3, runtime.getStats().triangles());
        assertEquals(2, runtime.getStats().visibleObjects());
        runtime.shutdown();
    }

    @Test
    void forcedDeviceLostOnRenderPropagatesErrorAndEvent() throws Exception {
        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.forceDeviceLostOnRender", "true"
        )), callbacks);
        runtime.loadScene(validScene());

        EngineException ex = org.junit.jupiter.api.Assertions.assertThrows(EngineException.class, runtime::render);

        assertEquals(EngineErrorCode.DEVICE_LOST, ex.code());
        assertFalse(callbacks.errors.isEmpty());
        assertTrue(callbacks.errors.stream().anyMatch(err -> err.code() == EngineErrorCode.DEVICE_LOST));
        assertTrue(callbacks.events.stream().anyMatch(DeviceLostEvent.class::isInstance));
        runtime.shutdown();
    }

    @Test
    void lowTierShadowAndSmokeEmitQualityWarnings() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true"), QualityTier.LOW), new RecordingCallbacks());
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0015f, 7, 3)));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_QUALITY_DEGRADED".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "SMOKE_QUALITY_DEGRADED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void ultraTierShadowAndSmokeAvoidQualityWarningsWhenWithinLimits() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true"), QualityTier.ULTRA), new RecordingCallbacks());
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0015f, 7, 3)));

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_QUALITY_DEGRADED".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "SMOKE_QUALITY_DEGRADED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void bloomRequestedDoesNotEmitNotImplementedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validPostProcessScene(true));

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "BLOOM_NOT_IMPLEMENTED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void iblEnvironmentEmitsBaselineActiveWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_PREFILTER_APPROX_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_MULTI_TAP_SPEC_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_CONTAINER_FALLBACK".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void iblLowTierEmitsQualityDegradedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true"), QualityTier.LOW), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_QUALITY_DEGRADED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void iblUltraTierDoesNotEmitQualityDegradedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true"), QualityTier.ULTRA), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_QUALITY_DEGRADED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void pointShadowRequestDoesNotEmitShadowTypeUnsupportedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validPointShadowScene());

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_TYPE_UNSUPPORTED".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "POINT_SHADOW_APPROX_ACTIVE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void spotShadowRequestDoesNotEmitShadowTypeUnsupportedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_TYPE_UNSUPPORTED".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "POINT_SHADOW_APPROX_ACTIVE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void postOffscreenRequestEmitsFallbackPipelineWarningInMockMode() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.postOffscreen", "true"
        )), new RecordingCallbacks());
        runtime.loadScene(validPostProcessScene(true));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "VULKAN_POST_PROCESS_PIPELINE".equals(w.code())
                        && w.message().contains("offscreenRequested=true")
                        && w.message().contains("offscreenActive=false")
                        && w.message().contains("mode=shader-fallback")));
        runtime.shutdown();
    }

    @Test
    void postOffscreenDisabledOmitsPipelineWarningInMockMode() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.postOffscreen", "false"
        )), new RecordingCallbacks());
        runtime.loadScene(validPostProcessScene(true));

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "VULKAN_POST_PROCESS_PIPELINE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void realVulkanDynamicSceneUpdateReusesBuffersWithoutRebuild() throws Exception {
        assumeRealVulkanReady("real Vulkan reuse integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableScene(true, false));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanMeshReorderStillHitsReusePath() throws Exception {
        assumeRealVulkanReady("real Vulkan reorder reuse integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableScene(false, true));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertTrue(after.reuseHits() > before.reuseHits());
        assertTrue(after.reorderReuseHits() > before.reorderReuseHits());
        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanLightingOnlySceneChangeReusesBuffersWithoutDescriptorRebuild() throws Exception {
        assumeRealVulkanReady("real Vulkan lighting-only reuse integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableSceneWithLightingVariant(1.7f));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        runtime.shutdown();
    }

    @Test
    void mockVulkanLightingOnlySceneChangeReusesBuffersWithoutDescriptorRebuild() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableSceneWithLightingVariant(1.7f));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanPostFogOnlySceneChangeReusesBuffersWithoutDescriptorRebuild() throws Exception {
        assumeRealVulkanReady("real Vulkan post/fog-only reuse integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableSceneWithPostFogVariant(0.12f, 1.0f));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableSceneWithPostFogVariant(0.22f, 1.18f));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        runtime.shutdown();
    }

    @Test
    void mockVulkanPostFogOnlySceneChangeReusesBuffersWithoutDescriptorRebuild() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validReusableSceneWithPostFogVariant(0.12f, 1.0f));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableSceneWithPostFogVariant(0.22f, 1.18f));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanResizeAndSceneSwitchEmitsResourceProfiles() throws Exception {
        assumeRealVulkanReady("real Vulkan resize/scene-switch integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0012f, 5, 4)));
        var frameA = runtime.render();
        assertTrue(frameA.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorSetsInRing=3")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("lastUniformUploadRanges=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("lastUniformUploadStartObject=")));
        assertTrue(frameA.warnings().stream().anyMatch(w -> "SHADOW_CASCADE_PROFILE".equals(w.code())));

        runtime.resize(1600, 900, 1.0f);
        runtime.loadScene(validReusableScene(true, false));
        var frameB = runtime.render();
        assertTrue(frameB.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())));
        assertTrue(frameB.warnings().stream().anyMatch(w -> "SCENE_REUSE_PROFILE".equals(w.code())));

        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(1024, 0.0015f, 3, 2)));
        var frameC = runtime.render();
        assertTrue(frameC.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void realVulkanEnduranceResizeAndSceneSwitchMaintainsHealthyProfiles() throws Exception {
        assumeRealVulkanReady("real Vulkan endurance integration test");

        var callbacks = new RecordingCallbacks();
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), callbacks);
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0012f, 5, 4)));

        for (int i = 0; i < 24; i++) {
            int width = 1280 + (i * 37);
            int height = 720 + (i * 23);
            runtime.resize(width, height, 1.0f);
            runtime.update(1.0 / 60.0, emptyInput());

            if ((i % 2) == 0) {
                runtime.loadScene(validReusableScene((i % 4) == 0, (i % 3) == 0));
            } else {
                runtime.loadScene(validShadowSmokeScene(new ShadowDesc(1024, 0.0015f, 3, 2)));
            }

            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w ->
                    "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("persistentStagingMapped=true")));
        }

        assertTrue(callbacks.errors.isEmpty(), "expected no host callback errors during endurance loop");
        runtime.shutdown();
    }

    @Test
    void realVulkanExtendedEnduranceMaintainsProfilesAndAvoidsCallbackErrors() throws Exception {
        assumeRealVulkanReady("real Vulkan extended endurance integration test");

        var callbacks = new RecordingCallbacks();
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), callbacks);
        runtime.loadScene(validReusableSceneWithPostFogVariant(0.12f, 1.0f));

        int reuseProfileFrames = 0;
        int resourceProfileFrames = 0;
        for (int i = 0; i < 48; i++) {
            int width = 1220 + (i * 19);
            int height = 700 + (i * 13);
            runtime.resize(width, height, 1.0f);
            runtime.update(1.0 / 60.0, emptyInput());

            if ((i % 3) == 0) {
                runtime.loadScene(validReusableScene((i % 2) == 0, (i % 4) == 0));
            } else if ((i % 3) == 1) {
                runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0012f, 5, 4)));
            } else {
                runtime.loadScene(validReusableSceneWithPostFogVariant(0.10f + (0.01f * (i % 7)), 1.0f + (0.04f * (i % 5))));
            }

            var frame = runtime.render();
            if (frame.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()))) {
                resourceProfileFrames++;
            }
            if (frame.warnings().stream().anyMatch(w -> "SCENE_REUSE_PROFILE".equals(w.code()))) {
                reuseProfileFrames++;
            }
        }

        assertTrue(resourceProfileFrames > 0, "expected frame-resource profiling warnings in extended endurance loop");
        assertTrue(reuseProfileFrames > 0, "expected scene-reuse profiling warnings in extended endurance loop");
        assertTrue(callbacks.errors.isEmpty(), "expected no host callback errors during extended endurance loop");
        runtime.shutdown();
    }

    @Test
    void realVulkanForcedDeviceLostPathPropagatesErrorsAndEvent() throws Exception {
        assumeRealVulkanReady("real Vulkan forced-device-lost integration test");

        var callbacks = new RecordingCallbacks();
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "false",
                "vulkan.forceDeviceLostOnRender", "true"
        )), callbacks);
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(1024, 0.0015f, 3, 2)));

        EngineException ex = org.junit.jupiter.api.Assertions.assertThrows(EngineException.class, runtime::render);
        assertEquals(EngineErrorCode.DEVICE_LOST, ex.code());
        assertTrue(callbacks.events.stream().anyMatch(DeviceLostEvent.class::isInstance));
        assertTrue(callbacks.errors.stream().anyMatch(err -> err.code() == EngineErrorCode.DEVICE_LOST));
        runtime.shutdown();
    }

    @Test
    void realVulkanForcedInitFailurePropagatesBackendInitFailed() {
        assumeRealVulkanReady("real Vulkan forced-init-failure integration test");

        var callbacks = new RecordingCallbacks();
        var runtime = new VulkanEngineRuntime();

        EngineException ex = org.junit.jupiter.api.Assertions.assertThrows(
                EngineException.class,
                () -> runtime.initialize(validConfig(Map.of(
                        "vulkan.mockContext", "false",
                        "vulkan.forceInitFailure", "true"
                )), callbacks)
        );

        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, ex.code());
        assertTrue(callbacks.errors.stream().anyMatch(err -> err.code() == EngineErrorCode.BACKEND_INIT_FAILED));
        runtime.shutdown();
    }

    private static EngineConfig validConfig(boolean mock) {
        return validConfig(Map.of("vulkan.mockContext", Boolean.toString(mock)));
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions) {
        return validConfig(backendOptions, QualityTier.MEDIUM);
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier qualityTier) {
        return new EngineConfig(
                "vulkan",
                "vulkan-test",
                1280,
                720,
                1.0f,
                true,
                60,
                qualityTier,
                Path.of("."),
                backendOptions
        );
    }

    private static SceneDescriptor validScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "vulkan-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }

    private static SceneDescriptor validPostProcessScene(boolean bloomEnabled) {
        SceneDescriptor base = validScene();
        PostProcessDesc post = new PostProcessDesc(
                true,
                true,
                1.05f,
                2.2f,
                bloomEnabled,
                1.0f,
                0.8f
        );
        return new SceneDescriptor(
                base.sceneName(),
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                post
        );
    }

    private static SceneDescriptor validMultiMeshScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc meshTriangle = new MeshDesc("mesh-triangle", "xform", "mat", "meshes/triangle.glb");
        MeshDesc meshQuad = new MeshDesc("mesh-quad", "xform", "mat", "meshes/quad.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "vulkan-multi-mesh-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(meshTriangle, meshQuad),
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }

    private static SceneDescriptor validShadowSmokeScene(ShadowDesc shadowDesc) {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(1, 3, 2), new Vec3(1, 1, 1), 1.0f, 15f, true, shadowDesc);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        SmokeEmitterDesc smoke = new SmokeEmitterDesc(
                "smoke-1",
                new Vec3(0, 0, 0),
                new Vec3(2, 1, 2),
                12f,
                0.8f,
                new Vec3(0.65f, 0.65f, 0.68f),
                0.4f,
                new Vec3(0f, 0.2f, 0f),
                0.25f,
                6f,
                true
        );

        return new SceneDescriptor(
                "vulkan-shadow-smoke-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.of(smoke)
        );
    }

    private static SceneDescriptor validReusableScene(boolean transformVariant, boolean reorderMeshes) {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transformA = new TransformDesc(
                "xform-a",
                transformVariant ? new Vec3(-0.35f, 0.08f, 0) : new Vec3(-0.35f, 0, 0),
                new Vec3(0, 0, 0),
                new Vec3(1, 1, 1)
        );
        TransformDesc transformB = new TransformDesc(
                "xform-b",
                transformVariant ? new Vec3(0.35f, -0.08f, 0) : new Vec3(0.35f, 0, 0),
                new Vec3(0, 0, 0),
                new Vec3(1, 1, 1)
        );
        MeshDesc meshA = new MeshDesc("mesh-a", "xform-a", "mat-a", "meshes/triangle.glb");
        MeshDesc meshB = new MeshDesc("mesh-b", "xform-b", "mat-b", "meshes/quad.glb");
        List<MeshDesc> meshes = reorderMeshes ? List.of(meshB, meshA) : List.of(meshA, meshB);

        MaterialDesc matA = new MaterialDesc("mat-a", new Vec3(0.9f, 0.35f, 0.3f), 0.2f, 0.55f, null, null);
        MaterialDesc matB = new MaterialDesc("mat-b", new Vec3(0.3f, 0.7f, 0.9f), 0.5f, 0.35f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(1, 3, 2), new Vec3(1, 1, 1), 1.0f, 15f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "vulkan-reuse-scene",
                List.of(camera),
                "cam",
                List.of(transformA, transformB),
                meshes,
                List.of(matA, matB),
                List.of(light),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor validReusableSceneWithLightingVariant(float lightIntensity) {
        SceneDescriptor base = validReusableScene(false, false);
        LightDesc light = new LightDesc(
                "light",
                new Vec3(1, 3, 2),
                new Vec3(0.95f, 0.92f, 1.0f),
                lightIntensity,
                15f,
                false,
                null
        );
        return new SceneDescriptor(
                "vulkan-reuse-lighting-variant",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(light),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validReusableSceneWithPostFogVariant(float fogDensity, float exposure) {
        SceneDescriptor base = validReusableScene(false, false);
        FogDesc fog = new FogDesc(
                true,
                FogMode.EXPONENTIAL,
                new Vec3(0.52f, 0.57f, 0.64f),
                fogDensity,
                0f,
                0.7f,
                0f,
                0f,
                0f
        );
        PostProcessDesc post = new PostProcessDesc(
                true,
                true,
                exposure,
                2.2f,
                true,
                1.0f,
                0.75f
        );
        return new SceneDescriptor(
                "vulkan-reuse-post-fog-variant",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                base.environment(),
                fog,
                base.smokeEmitters(),
                post
        );
    }

    private static SceneDescriptor validIblScene() {
        SceneDescriptor base = validScene();
        EnvironmentDesc env = new EnvironmentDesc(
                base.environment().ambientColor(),
                base.environment().ambientIntensity(),
                base.environment().skyboxAssetPath(),
                "textures/ibl_irradiance.ktx2",
                "textures/ibl_radiance.ktx2",
                "textures/ibl_brdf_lut.png"
        );
        return new SceneDescriptor(
                "vulkan-ibl-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                env,
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validPointShadowScene() {
        SceneDescriptor base = validScene();
        LightDesc pointShadow = new LightDesc(
                "point-shadow",
                new Vec3(0.5f, 1.5f, 1.8f),
                new Vec3(1f, 0.9f, 0.8f),
                1.2f,
                14f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.POINT
        );
        return new SceneDescriptor(
                "vulkan-point-shadow-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(pointShadow),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validSpotShadowScene() {
        SceneDescriptor base = validScene();
        LightDesc spotShadow = new LightDesc(
                "spot-shadow",
                new Vec3(0.4f, 1.6f, 1.5f),
                new Vec3(0.9f, 0.9f, 1f),
                1.1f,
                12f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.SPOT,
                new Vec3(0f, -1f, 0f),
                18f,
                32f
        );
        return new SceneDescriptor(
                "vulkan-spot-shadow-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(spotShadow),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static EngineInput emptyInput() {
        return new EngineInput(0, 0, 0, 0, false, false, Set.<KeyCode>of(), 0.0);
    }

    private static void assumeRealVulkanReady(String testLabel) {
        assumeTrue(Boolean.getBoolean("dle.test.vulkan.real"),
                "Set -Ddle.test.vulkan.real=true to run " + testLabel);
        try {
            boolean init = GLFW.glfwInit();
            assumeTrue(init, "Skipping " + testLabel + ": GLFW init failed on this machine");
            GLFW.glfwTerminate();
        } catch (Throwable t) {
            assumeTrue(false, "Skipping " + testLabel + ": LWJGL native runtime unavailable (" + t.getClass().getSimpleName() + ")");
        }
    }

    private static final class RecordingCallbacks implements EngineHostCallbacks {
        private final List<EngineEvent> events = new ArrayList<>();
        private final List<LogMessage> logs = new ArrayList<>();
        private final List<EngineErrorReport> errors = new ArrayList<>();

        @Override
        public void onEvent(EngineEvent event) {
            events.add(event);
        }

        @Override
        public void onLog(LogMessage message) {
            logs.add(message);
        }

        @Override
        public void onError(EngineErrorReport error) {
            errors.add(error);
        }
    }
}
