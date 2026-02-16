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
import org.dynamislight.api.CameraDesc;
import org.dynamislight.api.EngineApiVersion;
import org.dynamislight.api.EngineConfig;
import org.dynamislight.api.EngineErrorCode;
import org.dynamislight.api.EngineErrorReport;
import org.dynamislight.api.EngineEvent;
import org.dynamislight.api.EngineException;
import org.dynamislight.api.EngineFrameResult;
import org.dynamislight.api.EngineHostCallbacks;
import org.dynamislight.api.EngineInput;
import org.dynamislight.api.EnvironmentDesc;
import org.dynamislight.api.FogDesc;
import org.dynamislight.api.FogMode;
import org.dynamislight.api.KeyCode;
import org.dynamislight.api.LightDesc;
import org.dynamislight.api.LogMessage;
import org.dynamislight.api.MaterialDesc;
import org.dynamislight.api.MeshDesc;
import org.dynamislight.api.QualityTier;
import org.dynamislight.api.SceneLoadedEvent;
import org.dynamislight.api.SceneDescriptor;
import org.dynamislight.api.SmokeEmitterDesc;
import org.dynamislight.api.TransformDesc;
import org.dynamislight.api.Vec3;
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

    private static EngineConfig validConfig(String backendId) {
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
                QualityTier.MEDIUM,
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
}
