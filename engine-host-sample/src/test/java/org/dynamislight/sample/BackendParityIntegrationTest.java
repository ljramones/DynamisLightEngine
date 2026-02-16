package org.dynamislight.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.runtime.EngineApiVersion;
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
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.SceneLoadedEvent;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.spi.EngineBackendProvider;
import org.dynamislight.spi.registry.BackendRegistry;
import org.junit.jupiter.api.Test;

class BackendParityIntegrationTest {
    private static final EngineApiVersion HOST_REQUIRED_API = new EngineApiVersion(1, 0, 0);

    @Test
    void bothBackendsAreDiscoverable() {
        BackendRegistry registry = BackendRegistry.discover();
        Set<String> backendIds = registry.providers().stream().map(EngineBackendProvider::backendId).collect(java.util.stream.Collectors.toSet());

        assertTrue(backendIds.contains("opengl"));
        assertTrue(backendIds.contains("vulkan"));
    }

    @Test
    void lifecycleContractParityForOpenGlAndVulkan() throws Exception {
        runParityLifecycle("opengl");
        runParityLifecycle("vulkan");
    }

    @Test
    void invalidStateAndArgumentErrorsAreConsistentAcrossBackends() throws Exception {
        assertContractErrors("opengl");
        assertContractErrors("vulkan");
    }

    @Test
    void materialAndLightingSceneProducesParitySignalsAcrossBackends() throws Exception {
        SceneDescriptor scene = materialLightingScene();
        BackendRunResult openGl = runSceneAndCollect("opengl", scene);
        BackendRunResult vulkan = runSceneAndCollect("vulkan", scene);

        assertEquals(openGl.drawCalls(), vulkan.drawCalls());
        assertEquals(openGl.visibleObjects(), vulkan.visibleObjects());
        assertTrue(openGl.triangles() > 0);
        assertTrue(vulkan.triangles() > 0);
        assertTrue(openGl.warningCodes().contains("FEATURE_BASELINE"));
        assertTrue(vulkan.warningCodes().contains("FEATURE_BASELINE"));
    }

    @Test
    void repeatedResizeIsStableAcrossBackends() throws Exception {
        assertResizeStability("opengl");
        assertResizeStability("vulkan");
    }

    @Test
    void qualityTierWarningsAreConsistentAcrossBackends() throws Exception {
        SceneDescriptor scene = fogSmokeScene();

        Set<String> openGlLow = renderWarningCodes("opengl", scene, QualityTier.LOW);
        Set<String> vulkanLow = renderWarningCodes("vulkan", scene, QualityTier.LOW);
        assertTrue(openGlLow.contains("FOG_QUALITY_DEGRADED"));
        assertTrue(openGlLow.contains("SMOKE_QUALITY_DEGRADED"));
        assertTrue(vulkanLow.contains("FOG_QUALITY_DEGRADED"));
        assertTrue(vulkanLow.contains("SMOKE_QUALITY_DEGRADED"));

        Set<String> openGlHigh = renderWarningCodes("opengl", scene, QualityTier.HIGH);
        Set<String> vulkanHigh = renderWarningCodes("vulkan", scene, QualityTier.HIGH);
        assertFalse(openGlHigh.contains("FOG_QUALITY_DEGRADED"));
        assertFalse(openGlHigh.contains("SMOKE_QUALITY_DEGRADED"));
        assertFalse(vulkanHigh.contains("FOG_QUALITY_DEGRADED"));
        assertFalse(vulkanHigh.contains("SMOKE_QUALITY_DEGRADED"));
    }

    private static void runParityLifecycle(String backendId) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        RecordingCallbacks callbacks = new RecordingCallbacks();

        try (var runtime = provider.createRuntime()) {
            runtime.initialize(validConfig(backendId), callbacks);
            runtime.loadScene(validScene());

            EngineFrameResult update = runtime.update(1.0 / 60.0, emptyInput());
            EngineFrameResult render1 = runtime.render();
            EngineFrameResult render2 = runtime.render();

            assertNotNull(update);
            assertEquals(1L, render1.frameIndex());
            assertEquals(2L, render2.frameIndex());
            assertTrue(render1.cpuFrameMs() >= 0.0);
            assertTrue(render1.gpuFrameMs() >= 0.0);
            assertFalse(render1.warnings().isEmpty());

            runtime.resize(1024, 768, 1.0f);
            runtime.shutdown();
            runtime.shutdown();
        }

        assertFalse(callbacks.logs.isEmpty(), "expected runtime logs for " + backendId);
        assertTrue(callbacks.events.stream().anyMatch(SceneLoadedEvent.class::isInstance),
                "expected SceneLoadedEvent for " + backendId);
        Set<String> categories = callbacks.logs.stream().map(LogMessage::category).collect(Collectors.toSet());
        assertTrue(categories.contains("LIFECYCLE"), "missing LIFECYCLE logs for " + backendId);
        assertTrue(categories.contains("SCENE"), "missing SCENE logs for " + backendId);
        assertTrue(categories.contains("SHADER"), "missing SHADER logs for " + backendId);
        assertTrue(categories.contains("RENDER"), "missing RENDER logs for " + backendId);
        assertTrue(categories.contains("PERF"), "missing PERF logs for " + backendId);
    }

    private static void assertContractErrors(String backendId) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);

        try (var runtime = provider.createRuntime()) {
            EngineException renderBeforeInit = assertThrows(EngineException.class, runtime::render);
            assertEquals(EngineErrorCode.INVALID_STATE, renderBeforeInit.code());

            runtime.initialize(validConfig(backendId), new RecordingCallbacks());

            EngineException badResize = assertThrows(EngineException.class, () -> runtime.resize(0, 720, 1.0f));
            assertEquals(EngineErrorCode.INVALID_ARGUMENT, badResize.code());

            EngineException badUpdate = assertThrows(EngineException.class, () -> runtime.update(-0.1, emptyInput()));
            assertEquals(EngineErrorCode.INVALID_ARGUMENT, badUpdate.code());
        }
    }

    private static BackendRunResult runSceneAndCollect(String backendId, SceneDescriptor scene) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        try (var runtime = provider.createRuntime()) {
            runtime.initialize(validConfig(backendId), callbacks);
            runtime.loadScene(scene);
            EngineFrameResult frame = runtime.render();
            return new BackendRunResult(
                    runtime.getStats().drawCalls(),
                    runtime.getStats().triangles(),
                    runtime.getStats().visibleObjects(),
                    frame.warnings().stream().map(w -> w.code()).collect(Collectors.toSet())
            );
        }
    }

    private static void assertResizeStability(String backendId) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        try (var runtime = provider.createRuntime()) {
            runtime.initialize(validConfig(backendId), new RecordingCallbacks());
            runtime.loadScene(validScene());
            for (int i = 0; i < 12; i++) {
                int width = 800 + (i * 37);
                int height = 600 + (i * 23);
                runtime.resize(width, height, 1.0f);
                runtime.render();
            }
            assertTrue(runtime.getStats().fps() >= 0.0);
        }
    }

    private static Set<String> renderWarningCodes(String backendId, SceneDescriptor scene, QualityTier qualityTier) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        try (var runtime = provider.createRuntime()) {
            runtime.initialize(validConfig(backendId, qualityTier), new RecordingCallbacks());
            runtime.loadScene(scene);
            return runtime.render().warnings().stream().map(w -> w.code()).collect(Collectors.toSet());
        }
    }

    private static EngineConfig validConfig(String backendId) {
        return validConfig(backendId, QualityTier.MEDIUM);
    }

    private static EngineConfig validConfig(String backendId, QualityTier qualityTier) {
        Map<String, String> options = "opengl".equalsIgnoreCase(backendId)
                ? Map.of("opengl.mockContext", "true")
                : Map.of();

        return new EngineConfig(
                backendId,
                "backend-parity-test",
                1280,
                720,
                1.0f,
                true,
                60,
                qualityTier,
                Path.of(".."),
                options
        );
    }

    private static SceneDescriptor validScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "parity-scene",
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

    private static SceneDescriptor materialLightingScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 6), new Vec3(0, 0, 0), 65f, 0.1f, 150f);
        TransformDesc a = new TransformDesc("xform-a", new Vec3(-0.8f, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        TransformDesc b = new TransformDesc("xform-b", new Vec3(0.8f, 0.1f, 0), new Vec3(0, 20, 0), new Vec3(1, 1, 1));
        MeshDesc triangle = new MeshDesc("mesh-a", "xform-a", "mat-a", "meshes/triangle.gltf");
        MeshDesc quad = new MeshDesc("mesh-b", "xform-b", "mat-b", "meshes/quad.gltf");
        MaterialDesc matA = new MaterialDesc("mat-a", new Vec3(0.95f, 0.3f, 0.25f), 0.15f, 0.55f, "textures/a.png", "textures/a_n.png");
        MaterialDesc matB = new MaterialDesc("mat-b", new Vec3(0.25f, 0.65f, 0.95f), 0.65f, 0.35f, "textures/b.png", "textures/b_n.png");
        LightDesc key = new LightDesc("key", new Vec3(1.2f, 2.0f, 1.8f), new Vec3(1f, 0.96f, 0.9f), 1.0f, 15f, false);
        LightDesc fill = new LightDesc("fill", new Vec3(-1.4f, 1.2f, 1.0f), new Vec3(0.35f, 0.55f, 1f), 0.7f, 12f, false);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.08f, 0.1f, 0.12f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "parity-material-lighting-scene",
                List.of(camera),
                "cam",
                List.of(a, b),
                List.of(triangle, quad),
                List.of(matA, matB),
                List.of(key, fill),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor fogSmokeScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "meshes/quad.gltf");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.1f, 0.6f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 1), new Vec3(1, 1, 1), 1.0f, 10f, false);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(true, FogMode.EXPONENTIAL, new Vec3(0.5f, 0.55f, 0.6f), 0.35f, 0f, 0f, 0f, 0f, 0f);
        SmokeEmitterDesc smoke = new SmokeEmitterDesc(
                "smoke",
                new Vec3(0f, 0f, 0f),
                new Vec3(1f, 1f, 1f),
                10f,
                0.8f,
                new Vec3(0.65f, 0.65f, 0.68f),
                0.1f,
                new Vec3(0f, 0.1f, 0f),
                0.2f,
                2.0f,
                true
        );

        return new SceneDescriptor(
                "parity-fog-smoke-scene",
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

    private static EngineInput emptyInput() {
        return new EngineInput(0, 0, 0, 0, false, false, Set.<KeyCode>of(), 0.0);
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

    private record BackendRunResult(long drawCalls, long triangles, long visibleObjects, Set<String> warningCodes) {
    }
}
