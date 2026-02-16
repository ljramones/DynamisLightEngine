package org.dynamislight.impl.opengl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.runtime.EngineFrameResult;
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
import org.dynamislight.api.event.ResourceHotReloadedEvent;
import org.dynamislight.api.resource.ResourceState;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.event.SceneLoadFailedEvent;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.spi.EngineBackendProvider;
import org.junit.jupiter.api.Test;

class OpenGlEngineRuntimeLifecycleTest {
    @Test
    void serviceLoaderFindsOpenGlProvider() {
        var providers = ServiceLoader.load(EngineBackendProvider.class);
        boolean found = providers.stream().anyMatch(p -> p.get().backendId().equals("opengl"));
        assertTrue(found, "Expected opengl provider to be discoverable");
    }

    @Test
    void renderBeforeInitializeThrowsInvalidState() {
        var runtime = new OpenGlEngineRuntime();

        EngineException ex = assertThrows(EngineException.class, runtime::render);

        assertEquals(EngineErrorCode.INVALID_STATE, ex.code());
    }

    @Test
    void loadSceneBeforeInitializeThrowsInvalidState() {
        var runtime = new OpenGlEngineRuntime();

        EngineException ex = assertThrows(EngineException.class, () -> runtime.loadScene(validScene()));

        assertEquals(EngineErrorCode.INVALID_STATE, ex.code());
    }

    @Test
    void initializeThenRenderProducesFrameAndStats() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();

        runtime.initialize(validConfig(), callbacks);
        runtime.loadScene(validScene());
        EngineFrameResult update = runtime.update(1.0 / 60.0, emptyInput());
        EngineFrameResult render = runtime.render();

        assertNotNull(update);
        assertNotNull(render.frameHandle());
        assertEquals(1L, render.frameIndex());
        assertFalse(render.warnings().isEmpty());
        assertTrue(runtime.getStats().fps() > 0.0);
        assertFalse(callbacks.logs.isEmpty());

        runtime.shutdown();
        runtime.shutdown();
    }

    @Test
    void fullLifecycleFlowSupportsResizeAndShutdown() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();

        runtime.initialize(validConfig(), callbacks);
        runtime.loadScene(validScene());
        runtime.update(1.0 / 60.0, emptyInput());
        runtime.resize(1920, 1080, 1.0f);
        EngineFrameResult frame = runtime.render();
        runtime.shutdown();

        assertEquals(1L, frame.frameIndex());
        assertTrue(callbacks.logs.stream().anyMatch(log -> "LIFECYCLE".equals(log.category())));
        assertTrue(callbacks.logs.stream().anyMatch(log -> "SCENE".equals(log.category())));
        assertTrue(callbacks.logs.stream().anyMatch(log -> "SHADER".equals(log.category())));
        assertTrue(callbacks.logs.stream().anyMatch(log -> "RENDER".equals(log.category())));
        assertTrue(callbacks.logs.stream().anyMatch(log -> "PERF".equals(log.category())));
        assertFalse(callbacks.events.isEmpty());
    }

    @Test
    void initializeTwiceThrowsInvalidState() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());

        EngineException ex = assertThrows(EngineException.class,
                () -> runtime.initialize(validConfig(), new RecordingCallbacks()));

        assertEquals(EngineErrorCode.INVALID_STATE, ex.code());
    }

    @Test
    void invalidResizeThrowsInvalidArgument() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());

        EngineException ex = assertThrows(EngineException.class, () -> runtime.resize(0, 720, 1.0f));

        assertEquals(EngineErrorCode.INVALID_ARGUMENT, ex.code());
    }

    @Test
    void negativeDeltaTimeThrowsInvalidArgument() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());

        EngineException ex = assertThrows(EngineException.class, () -> runtime.update(-0.1, emptyInput()));

        assertEquals(EngineErrorCode.INVALID_ARGUMENT, ex.code());
    }

    @Test
    void renderAfterShutdownThrowsInvalidState() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.shutdown();

        EngineException ex = assertThrows(EngineException.class, runtime::render);

        assertEquals(EngineErrorCode.INVALID_STATE, ex.code());
    }

    @Test
    void forcedInitFailureMapsToBackendInitFailed() {
        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();

        EngineException ex = assertThrows(EngineException.class,
                () -> runtime.initialize(validConfig(Map.of("opengl.forceInitFailure", "true")), callbacks));

        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, ex.code());
        assertTrue(callbacks.logs.stream().anyMatch(log -> "ERROR".equals(log.category())));
        assertFalse(callbacks.errors.isEmpty());
        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, callbacks.errors.getFirst().code());
    }

    @Test
    void fogQualityTierImpactsFogSamplingAndDensityScale() {
        assertEquals(4, OpenGlEngineRuntime.fogSteps(QualityTier.LOW));
        assertEquals(8, OpenGlEngineRuntime.fogSteps(QualityTier.MEDIUM));
        assertEquals(16, OpenGlEngineRuntime.fogSteps(QualityTier.HIGH));
        assertEquals(0, OpenGlEngineRuntime.fogSteps(QualityTier.ULTRA));

        assertTrue(OpenGlEngineRuntime.fogDensityScale(QualityTier.LOW) < OpenGlEngineRuntime.fogDensityScale(QualityTier.HIGH));
        assertTrue(OpenGlEngineRuntime.fogDensityScale(QualityTier.ULTRA) > OpenGlEngineRuntime.fogDensityScale(QualityTier.HIGH));
    }

    @Test
    void loadSceneWithFogEnabledRendersSuccessfully() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validFogScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.frameIndex() > 0);
    }

    @Test
    void lowTierSmokeEmitsQualityDegradedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validLowQualityConfig(), new RecordingCallbacks());
        runtime.loadScene(validSmokeScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "SMOKE_QUALITY_DEGRADED".equals(w.code())));
    }

    @Test
    void ultraTierSmokeDoesNotEmitQualityDegradedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validUltraQualityConfig(), new RecordingCallbacks());
        runtime.loadScene(validSmokeScene());

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SMOKE_QUALITY_DEGRADED".equals(w.code())));
    }

    @Test
    void bloomRequestedDoesNotEmitNotImplementedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validPostProcessScene(true));

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "BLOOM_NOT_IMPLEMENTED".equals(w.code())));
    }

    @Test
    void iblEnvironmentEmitsBaselineActiveWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_PREFILTER_APPROX_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_MULTI_TAP_SPEC_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BRDF_ENERGY_COMP_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_CONTAINER_FALLBACK".equals(w.code())));
    }

    @Test
    void iblMissingAssetsEmitFallbackWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validMissingIblScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
    }

    @Test
    void iblSkyboxOnlySceneEnablesBaselineWithFallbackWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validSkyboxOnlyIblScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
    }

    @Test
    void iblLowTierEmitsQualityDegradedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validLowQualityConfig(), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_QUALITY_DEGRADED".equals(w.code())));
    }

    @Test
    void iblUltraTierDoesNotEmitQualityDegradedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validUltraQualityConfig(), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_QUALITY_DEGRADED".equals(w.code())));
    }

    @Test
    void pointShadowRequestDoesNotEmitShadowTypeUnsupportedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validPointShadowScene());

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_TYPE_UNSUPPORTED".equals(w.code())));
    }

    @Test
    void spotShadowRequestDoesNotEmitShadowTypeUnsupportedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_TYPE_UNSUPPORTED".equals(w.code())));
    }

    @Test
    void sceneMeshesDriveDrawCallAndTriangleStatsInMockMode() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validMultiMeshScene());
        runtime.render();

        assertEquals(2, runtime.getStats().drawCalls());
        assertEquals(3, runtime.getStats().triangles());
        assertEquals(2, runtime.getStats().visibleObjects());
    }

    @Test
    void resourceServiceTracksSceneAssetsAndReleasesOnShutdown() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(), callbacks);
        runtime.loadScene(validSceneWithResources());

        assertFalse(runtime.resources().loadedResources().isEmpty());
        assertTrue(runtime.resources().loadedResources().stream().allMatch(r -> r.state() == ResourceState.FAILED));
        assertTrue(callbacks.events.stream().anyMatch(SceneLoadFailedEvent.class::isInstance));
        runtime.shutdown();
        assertTrue(runtime.resources().loadedResources().isEmpty());
    }

    @Test
    void resourceHotReloadEmitsResourceReloadedEvent() throws Exception {
        Path root = Files.createTempDirectory("dle-resources");
        Files.createDirectories(root.resolve("meshes"));
        Files.createDirectories(root.resolve("textures"));
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v1");
        Files.writeString(root.resolve("textures/albedo.png"), "albedo-v1");
        Files.writeString(root.resolve("textures/normal.png"), "normal-v1");
        Files.writeString(root.resolve("textures/skybox.hdr"), "sky-v1");

        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.of("opengl.mockContext", "true"), QualityTier.MEDIUM, root), callbacks);
        runtime.loadScene(validSceneWithResources());

        var resource = runtime.resources().loadedResources().stream()
                .filter(r -> r.descriptor().sourcePath().equals("meshes/box.glb"))
                .findFirst()
                .orElseThrow();
        assertEquals(ResourceState.LOADED, resource.state());
        assertTrue(resource.resolvedPath().endsWith("meshes/box.glb"));
        assertNotNull(resource.lastChecksum());
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v2");
        var reloaded = runtime.resources().reload(resource.descriptor().id());

        assertTrue(callbacks.events.stream().anyMatch(ResourceHotReloadedEvent.class::isInstance));
        assertNotNull(reloaded.lastChecksum());
        assertFalse(resource.lastChecksum().equals(reloaded.lastChecksum()));
    }

    @Test
    void resourceHotReloadWithoutChecksumChangeStillEmitsEvent() throws Exception {
        Path root = Files.createTempDirectory("dle-resources-nochange");
        Files.createDirectories(root.resolve("meshes"));
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v1");

        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.of("opengl.mockContext", "true"), QualityTier.MEDIUM, root), callbacks);
        runtime.loadScene(new SceneDescriptor(
                "mesh-only-scene",
                List.of(new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f)),
                "cam",
                List.of(new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "xform", "mat", "meshes/box.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 0.5f, null, null)),
                List.of(),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.of()
        ));

        callbacks.events.clear();
        var resource = runtime.resources().loadedResources().getFirst();
        var reloaded = runtime.resources().reload(resource.descriptor().id());

        assertTrue(callbacks.events.stream().anyMatch(ResourceHotReloadedEvent.class::isInstance));
        assertEquals(resource.lastChecksum(), reloaded.lastChecksum());
    }

    @Test
    void resourceStatsTrackHitsMissesAndReloads() throws Exception {
        Path root = Files.createTempDirectory("dle-resource-stats");
        Files.createDirectories(root.resolve("meshes"));
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v1");

        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(Map.of("opengl.mockContext", "true"), QualityTier.MEDIUM, root), new RecordingCallbacks());
        runtime.loadScene(new SceneDescriptor(
                "mesh-only-scene-stats",
                List.of(new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f)),
                "cam",
                List.of(new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "xform", "mat", "meshes/box.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 0.5f, null, null)),
                List.of(),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.of()
        ));
        var id = runtime.resources().loadedResources().getFirst().descriptor().id();
        runtime.resources().reload(id);

        var stats = runtime.resources().stats();
        assertTrue(stats.cacheMisses() >= 1);
        assertTrue(stats.cacheHits() >= 0);
        assertTrue(stats.reloadRequests() >= 1);
    }

    @Test
    void resourceWatcherAutoReloadsOnFileChange() throws Exception {
        assumeTrue(Boolean.getBoolean("dle.test.resource.watch"),
                "Set -Ddle.test.resource.watch=true to run filesystem watcher integration test");
        Path root = Files.createTempDirectory("dle-resources-watch");
        Files.createDirectories(root.resolve("meshes"));
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v1");

        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.of(
                "opengl.mockContext", "true",
                "resource.watch.enabled", "true"
        ), QualityTier.MEDIUM, root), callbacks);
        runtime.loadScene(new SceneDescriptor(
                "mesh-only-scene-watch",
                List.of(new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f)),
                "cam",
                List.of(new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "xform", "mat", "meshes/box.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 0.5f, null, null)),
                List.of(),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.of()
        ));

        callbacks.events.clear();
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v2");

        boolean reloaded = false;
        for (int i = 0; i < 30; i++) {
            if (callbacks.events.stream().anyMatch(ResourceHotReloadedEvent.class::isInstance)) {
                reloaded = true;
                break;
            }
            Thread.sleep(50);
        }

        runtime.shutdown();
        assertTrue(reloaded);
    }

    private static EngineConfig validConfig() {
        return validConfig(Map.of("opengl.mockContext", "true"));
    }

    private static EngineConfig validLowQualityConfig() {
        return validConfig(Map.of("opengl.mockContext", "true"), QualityTier.LOW);
    }

    private static EngineConfig validUltraQualityConfig() {
        return validConfig(Map.of("opengl.mockContext", "true"), QualityTier.ULTRA);
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions) {
        return validConfig(backendOptions, QualityTier.MEDIUM);
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier qualityTier) {
        return validConfig(backendOptions, qualityTier, Path.of("."));
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier qualityTier, Path assetRoot) {
        return new EngineConfig(
                "opengl",
                "test-host",
                1280,
                720,
                1.0f,
                true,
                60,
                qualityTier,
                assetRoot,
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
                "test-scene",
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

    private static SceneDescriptor validFogScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(true, FogMode.EXPONENTIAL, new Vec3(0.65f, 0.7f, 0.8f), 0.4f, 0.0f, 1.0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "fog-scene",
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

    private static SceneDescriptor validSmokeScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        SmokeEmitterDesc emitter = new SmokeEmitterDesc(
                "smoke-1",
                new Vec3(0, 0, 0),
                new Vec3(1, 1, 1),
                10f,
                0.7f,
                new Vec3(0.65f, 0.66f, 0.7f),
                1f,
                new Vec3(0, 1, 0),
                0.3f,
                10f,
                true
        );

        return new SceneDescriptor(
                "smoke-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.of(emitter)
        );
    }

    private static SceneDescriptor validSceneWithResources() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "meshes/box.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, "textures/albedo.png", "textures/normal.png");
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, "textures/skybox.hdr");
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "resource-scene",
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
                "multi-mesh-scene",
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
                "ibl-scene",
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

    private static SceneDescriptor validMissingIblScene() {
        SceneDescriptor base = validScene();
        EnvironmentDesc env = new EnvironmentDesc(
                base.environment().ambientColor(),
                base.environment().ambientIntensity(),
                base.environment().skyboxAssetPath(),
                "textures/missing_ibl_irradiance.ktx2",
                "textures/missing_ibl_radiance.ktx2",
                "textures/missing_ibl_brdf_lut.png"
        );
        return new SceneDescriptor(
                "ibl-missing-scene",
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

    private static SceneDescriptor validSkyboxOnlyIblScene() {
        SceneDescriptor base = validScene();
        EnvironmentDesc env = new EnvironmentDesc(
                base.environment().ambientColor(),
                base.environment().ambientIntensity(),
                "textures/skybox.hdr"
        );
        return new SceneDescriptor(
                "ibl-skybox-only-scene",
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
                "point-shadow-scene",
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
                "spot-shadow-scene",
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
        return new EngineInput(0, 0, 0, 0, false, false, java.util.Set.<KeyCode>of(), 0.0);
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
