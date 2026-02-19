package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.runtime.EngineFrameResult;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class VulkanAaPostCapabilityPlanIntegrationTest {
    @Test
    void emitsAaPostCapabilityPlanWarningAndTypedDiagnostics() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.aaMode", "taa")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene(true));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_POST_CAPABILITY_PLAN_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_TEMPORAL_POLICY_ACTIVE".equals(w.code())));
            var diagnostics = runtime.aaPostCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.aaEnabled());
            assertTrue("taa".equals(diagnostics.aaMode()));
            assertFalse(diagnostics.activeCapabilities().isEmpty());
            var temporalDiagnostics = runtime.aaTemporalPromotionDiagnostics();
            assertTrue(temporalDiagnostics.available());
            assertTrue(temporalDiagnostics.temporalPathRequested());
            assertTrue(temporalDiagnostics.temporalPathActive());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void lowTierFxaaPlanPrunesSsaoAndTemporalHistory() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.aaMode", "fxaa_low"),
                    Map.entry("vulkan.post.ssao", "true")
            ), QualityTier.LOW), new NoopCallbacks());
            runtime.loadScene(validScene(false));
            runtime.render();
            var diagnostics = runtime.aaPostCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertFalse(diagnostics.temporalHistoryActive());
            assertTrue(diagnostics.prunedCapabilities().stream().anyMatch(s -> s.contains("vulkan.post.ssao")));
            assertTrue(diagnostics.activeCapabilities().stream().anyMatch(s -> s.contains("vulkan.aa.fxaa_low")));
            assertFalse(diagnostics.activeCapabilities().stream().anyMatch(s -> s.contains("vulkan.post.taa_resolve")));
            var temporalDiagnostics = runtime.aaTemporalPromotionDiagnostics();
            assertTrue(temporalDiagnostics.available());
            assertFalse(temporalDiagnostics.temporalPathRequested());
            assertFalse(temporalDiagnostics.temporalPathActive());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void temporalEnvelopeCanBeForcedForCiGateValidation() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.aaMode", "taa"),
                    Map.entry("vulkan.aa.temporalRejectWarnMax", "0.0"),
                    Map.entry("vulkan.aa.temporalConfidenceWarnMin", "1.0"),
                    Map.entry("vulkan.aa.temporalDropWarnMin", "0"),
                    Map.entry("vulkan.aa.temporalWarnMinFrames", "1"),
                    Map.entry("vulkan.aa.temporalWarnCooldownFrames", "0"),
                    Map.entry("vulkan.aa.temporalPromotionReadyMinFrames", "1")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene(true));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_TEMPORAL_ENVELOPE_BREACH".equals(w.code())));
            var temporalDiagnostics = runtime.aaTemporalPromotionDiagnostics();
            assertTrue(temporalDiagnostics.envelopeBreachedLastFrame());
            assertFalse(temporalDiagnostics.promotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier tier) {
        return new EngineConfig(
                "vulkan",
                "vulkan-aa-post-capability-plan-test",
                1280,
                720,
                1.0f,
                true,
                60,
                tier,
                Path.of("."),
                backendOptions
        );
    }

    private static SceneDescriptor validScene(boolean taaEnabled) {
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

        PostProcessDesc post = new PostProcessDesc(
                true,
                true,
                1.0f,
                2.2f,
                true,
                1.0f,
                0.8f,
                true,
                0.3f,
                1.0f,
                0.02f,
                1.0f,
                true,
                0.4f,
                taaEnabled,
                0.75f,
                false
        );

        return new SceneDescriptor(
                "aa-post-plan-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(light),
                environment,
                fog,
                List.of(),
                post
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
