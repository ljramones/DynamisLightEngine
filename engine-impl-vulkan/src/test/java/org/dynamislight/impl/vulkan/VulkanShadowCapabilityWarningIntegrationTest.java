package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.api.logging.LogMessage;
import org.junit.jupiter.api.Test;

class VulkanShadowCapabilityWarningIntegrationTest {
    @Test
    void emitsShadowCapabilityModeWarningIncludingFilterSignal() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.filterPath", "vsm"),
                    Map.entry("vulkan.shadow.rtMode", "off"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "0"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "0"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "0")
            )), new NoopCallbacks());
            runtime.loadScene(validScene());
            var frame = runtime.render();
            var modeWarning = frame.warnings().stream()
                    .filter(w -> "SHADOW_CAPABILITY_MODE_ACTIVE".equals(w.code()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(modeWarning);
            assertTrue(modeWarning.message().contains("mode=vsm"), modeWarning.message());
            assertTrue(modeWarning.message().contains("filterPath=vsm"), modeWarning.message());
            var diagnostics = runtime.shadowCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertEquals("vulkan.shadow", diagnostics.featureId());
            assertEquals("vsm", diagnostics.mode());
            assertTrue(diagnostics.signals().stream().anyMatch(s -> s.equals("filterPath=vsm")));
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsHybridModeWhenRtDenoisedAndContactEnabled() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.filterPath", "evsm"),
                    Map.entry("vulkan.shadow.rtMode", "bvh_dedicated"),
                    Map.entry("vulkan.shadow.contactShadows", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "0"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "0"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "0")
            )), new NoopCallbacks());
            runtime.loadScene(validScene());
            var frame = runtime.render();
            var modeWarning = frame.warnings().stream()
                    .filter(w -> "SHADOW_CAPABILITY_MODE_ACTIVE".equals(w.code()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(modeWarning);
            assertTrue(modeWarning.message().contains("mode=hybrid_cascade_contact_rt"), modeWarning.message());
            var diagnostics = runtime.shadowCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertEquals("vulkan.shadow", diagnostics.featureId());
            assertEquals("hybrid_cascade_contact_rt", diagnostics.mode());
            assertTrue(diagnostics.signals().stream().anyMatch(s -> s.equals("rtMode=bvh_dedicated")));
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowCapabilityDiagnosticsUnavailableWhenNoShadowCasterIsActive() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new NoopCallbacks());
            runtime.loadScene(validSceneWithoutShadowCaster());
            runtime.render();
            var diagnostics = runtime.shadowCapabilityDiagnostics();
            assertFalse(diagnostics.available());
            assertEquals("unavailable", diagnostics.featureId());
            assertEquals("unavailable", diagnostics.mode());
            assertTrue(diagnostics.signals().isEmpty());
        } finally {
            runtime.shutdown();
        }
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions) {
        return new EngineConfig(
                "vulkan",
                "vulkan-shadow-capability-warning-test",
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
        MaterialDesc material = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc(
                "light",
                new Vec3(0, 2, 0),
                new Vec3(1, 1, 1),
                1.0f,
                10f,
                true,
                new ShadowDesc(1024, 0.0015f, 1, 4)
        );
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "shadow-capability-warning-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(light),
                environment,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }

    private static SceneDescriptor validSceneWithoutShadowCaster() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc material = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc(
                "light",
                new Vec3(0, 2, 0),
                new Vec3(1, 1, 1),
                1.0f,
                10f,
                false,
                null
        );
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "shadow-capability-warning-scene-no-shadows",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(light),
                environment,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }

    private static final class NoopCallbacks implements EngineHostCallbacks {
        @Override
        public void onEvent(EngineEvent event) {
        }

        @Override
        public void onLog(LogMessage message) {
        }

        @Override
        public void onError(EngineErrorReport error) {
        }
    }
}
