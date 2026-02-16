package org.dynamislight.sample;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.CameraDesc;
import org.dynamislight.api.DeviceLostEvent;
import org.dynamislight.api.EngineApiVersion;
import org.dynamislight.api.EngineConfig;
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
import org.dynamislight.api.ResourceHotReloadedEvent;
import org.dynamislight.api.SceneDescriptor;
import org.dynamislight.api.SceneLoadFailedEvent;
import org.dynamislight.api.SceneLoadedEvent;
import org.dynamislight.api.SmokeEmitterDesc;
import org.dynamislight.api.TransformDesc;
import org.dynamislight.api.Vec3;
import org.dynamislight.spi.EngineBackendProvider;
import org.dynamislight.api.validation.EngineConfigValidator;
import org.dynamislight.api.validation.SceneValidator;
import org.dynamislight.spi.registry.BackendRegistry;

public final class SampleHostApp {
    private static final EngineApiVersion HOST_REQUIRED_API = new EngineApiVersion(1, 0, 0);

    private SampleHostApp() {
    }

    public static void main(String[] args) throws Exception {
        String backendId = args.length > 0 ? args[0] : "opengl";
        boolean resourceProbe = java.util.Arrays.asList(args).contains("--resources");
        EngineBackendProvider provider = resolveProvider(backendId);
        EngineConfig config = defaultConfig(backendId);
        SceneDescriptor scene = defaultScene();
        EngineConfigValidator.validate(config);
        SceneValidator.validate(scene);

        try (var runtime = provider.createRuntime()) {
            runtime.initialize(config, new ConsoleCallbacks());
            runtime.loadScene(scene);
            if (resourceProbe) {
                printResources(runtime.resources().loadedResources(), "loaded");
                if (!runtime.resources().loadedResources().isEmpty()) {
                    var first = runtime.resources().loadedResources().getFirst();
                    var reloaded = runtime.resources().reload(first.descriptor().id());
                    System.out.println("reloaded=" + reloaded.descriptor().id().value() + " checksum=" + reloaded.lastChecksum());
                }
            }

            for (int i = 0; i < 3; i++) {
                EngineFrameResult updateResult = runtime.update(1.0 / 60.0, emptyInput());
                EngineFrameResult renderResult = runtime.render();
                System.out.printf(
                        "frame=%d updateCpuMs=%.2f renderCpuMs=%.2f warnings=%d%n",
                        renderResult.frameIndex(),
                        updateResult.cpuFrameMs(),
                        renderResult.cpuFrameMs(),
                        renderResult.warnings().size()
                );
            }

            runtime.shutdown();
            System.out.println("Shutdown complete.");
        }
    }

    private static void printResources(List<org.dynamislight.api.ResourceInfo> resources, String label) {
        System.out.println("resources(" + label + ") count=" + resources.size());
        for (var info : resources) {
            System.out.printf("  id=%s state=%s ref=%d path=%s checksum=%s%n",
                    info.descriptor().id().value(),
                    info.state(),
                    info.refCount(),
                    info.resolvedPath(),
                    info.lastChecksum());
        }
    }

    private static EngineBackendProvider resolveProvider(String backendId) throws EngineException {
        return BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
    }

    private static EngineConfig defaultConfig(String backendId) {
        Map<String, String> backendOptions = "opengl".equalsIgnoreCase(backendId)
                ? Map.of("opengl.mockContext", System.getProperty("dle.opengl.mockContext", "true"))
                : Map.of();
        return new EngineConfig(
                backendId,
                "DynamicLightEngine Sample Host",
                1280,
                720,
                1.0f,
                true,
                60,
                QualityTier.MEDIUM,
                Path.of("assets"),
                backendOptions
        );
    }

    private static SceneDescriptor defaultScene() {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0, 2, 5), new Vec3(0, 0, 0), 60f, 0.1f, 1000f);
        TransformDesc transform = new TransformDesc("root", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh-1", "root", "mat-1", "meshes/triangle.glb");
        MaterialDesc material = new MaterialDesc("mat-1", new Vec3(1, 1, 1), 0.1f, 0.7f, null, null);
        LightDesc light = new LightDesc("sun", new Vec3(0, 10, 0), new Vec3(1, 1, 1), 1.0f, 100f, false);
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.12f), 0.25f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "sample-scene",
                List.of(camera),
                camera.id(),
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(light),
                environment,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }

    private static EngineInput emptyInput() {
        return new EngineInput(0, 0, 0, 0, false, false, Set.<KeyCode>of(), 0);
    }

    private static final class ConsoleCallbacks implements EngineHostCallbacks {
        @Override
        public void onEvent(EngineEvent event) {
            if (event instanceof SceneLoadedEvent || event instanceof SceneLoadFailedEvent || event instanceof ResourceHotReloadedEvent
                    || event instanceof DeviceLostEvent) {
                System.out.println("event=" + event);
            }
        }

        @Override
        public void onLog(LogMessage message) {
            System.out.printf("[%s] %s %s%n", Instant.ofEpochMilli(message.epochMillis()), message.category(), message.message());
        }

        @Override
        public void onError(EngineErrorReport error) {
            System.err.printf("error=%s recoverable=%s message=%s%n", error.code(), error.recoverable(), error.message());
        }
    }
}
