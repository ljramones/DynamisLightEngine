package org.dynamislight.impl.opengl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.dynamislight.api.CameraDesc;
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
import org.dynamislight.api.SceneDescriptor;
import org.dynamislight.api.SmokeEmitterDesc;
import org.dynamislight.api.TransformDesc;
import org.dynamislight.api.Vec3;
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
        assertTrue(callbacks.logs.stream().anyMatch(log -> "RENDER".equals(log.category())));
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

        EngineException ex = assertThrows(EngineException.class,
                () -> runtime.initialize(validConfig(Map.of("opengl.forceInitFailure", "true")), new RecordingCallbacks()));

        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, ex.code());
    }

    private static EngineConfig validConfig() {
        return validConfig(Map.of("opengl.mockContext", "true"));
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions) {
        return new EngineConfig(
                "opengl",
                "test-host",
                1280,
                720,
                1.0f,
                true,
                60,
                QualityTier.MEDIUM,
                Path.of("."),
                backendOptions
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
