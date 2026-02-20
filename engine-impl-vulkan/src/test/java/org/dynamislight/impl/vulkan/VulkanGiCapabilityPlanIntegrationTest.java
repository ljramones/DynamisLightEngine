package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.runtime.EngineFrameResult;
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
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class VulkanGiCapabilityPlanIntegrationTest {
    @Test
    void emitsGiCapabilityPlanWarningAndTypedDiagnostics() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "true"),
                    Map.entry("vulkan.gi.mode", "hybrid_probe_ssgi_rt"),
                    Map.entry("vulkan.gi.promotionReadyMinFrames", "1"),
                    Map.entry("vulkan.gi.ssgiPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.gi.probePromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.gi.hybridWarnMinFrames", "1"),
                    Map.entry("vulkan.gi.hybridWarnCooldownFrames", "0")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_CAPABILITY_PLAN_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PROMOTION_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_SSGI_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_SSGI_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_SSGI_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PROBE_GRID_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PROBE_GRID_STREAMING_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PROBE_GRID_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PROBE_GRID_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PROBE_GRID_STREAMING_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_RT_DETAIL_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_RT_DETAIL_FALLBACK_CHAIN".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_RT_DETAIL_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_RT_DETAIL_ENVELOPE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_HYBRID_COMPOSITION".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_HYBRID_COMPOSITION_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PROMOTION_READY".equals(w.code())));
            var diagnostics = runtime.giCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.giEnabled());
            assertTrue(diagnostics.activeCapabilities().stream().anyMatch(s -> s.contains("vulkan.gi")));
            var promotion = runtime.giPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.promotionReady());
            assertTrue(promotion.rtFallbackActive());
            assertTrue(promotion.ssgiActive());
            assertTrue(promotion.ssgiExpected());
            assertFalse(promotion.ssgiEnvelopeBreachedLastFrame());
            assertTrue(promotion.ssgiPromotionReady());
            assertTrue(promotion.probeGridActive());
            assertTrue(promotion.probeGridExpected());
            assertFalse(promotion.probeGridEnvelopeBreachedLastFrame());
            assertTrue(promotion.probeGridPromotionReady());
            assertTrue(promotion.probeGridConfiguredCount() > 0);
            assertTrue(promotion.probeGridUpdateBudgetPerFrame() >= 0);
            assertTrue(promotion.probeGridUpdateCoverageRatio() >= 0.0);
            assertTrue(promotion.rtDetailExpected());
            assertFalse(promotion.rtDetailActive());
            assertTrue(promotion.rtDetailEnvelopeBreachedLastFrame());
            assertFalse(promotion.rtDetailPromotionReady());
            assertFalse(promotion.phase2PromotionReady());
            assertFalse(promotion.rtDetailActive());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsPhase2PromotionReadyWhenExpectedGiLanesAreStable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "true"),
                    Map.entry("vulkan.gi.mode", "ssgi"),
                    Map.entry("vulkan.gi.promotionReadyMinFrames", "1"),
                    Map.entry("vulkan.gi.ssgiPromotionReadyMinFrames", "1")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PHASE2_PROMOTION_READY".equals(w.code())));
            var promotion = runtime.giPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.promotionReady());
            assertTrue(promotion.ssgiPromotionReady());
            assertFalse(promotion.probeGridExpected());
            assertFalse(promotion.rtDetailExpected());
            assertTrue(promotion.phase2PromotionReady());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void giDisabledProducesPrunedCapabilitiesOnly() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "false"),
                    Map.entry("vulkan.gi.mode", "ssgi")
            ), QualityTier.MEDIUM), new NoopCallbacks());
            runtime.loadScene(validScene());
            runtime.render();
            var diagnostics = runtime.giCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertFalse(diagnostics.giEnabled());
            assertTrue(diagnostics.activeCapabilities().isEmpty());
            assertTrue(diagnostics.prunedCapabilities().stream().anyMatch(s -> s.contains("gi disabled")));
            var promotion = runtime.giPromotionDiagnostics();
            assertTrue(promotion.available());
            assertFalse(promotion.promotionReady());
            assertFalse(promotion.ssgiPromotionReady());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsRtFallbackInPromotionDiagnosticsWhenRtUnavailable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "true"),
                    Map.entry("vulkan.gi.mode", "rtgi_single"),
                    Map.entry("vulkan.gi.promotionReadyMinFrames", "1")
            ), QualityTier.MEDIUM), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_RT_DETAIL_FALLBACK_CHAIN".equals(w.code())));
            var promotion = runtime.giPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.rtFallbackActive());
            assertTrue(promotion.ssgiActive());
            assertFalse(promotion.rtDetailActive());
            assertTrue(runtime.giCapabilityDiagnostics().activeCapabilities().contains("vulkan.gi.ssgi"));
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void appliesTierProfileDefaultsForSsgiEnvelopeThresholdsWhenOverridesAbsent() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "true"),
                    Map.entry("vulkan.gi.mode", "ssgi")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene());
            runtime.render();
            var promotion = runtime.giPromotionDiagnostics();
            assertTrue(promotion.available());
            assertEquals(1.0, promotion.ssgiWarnMinActiveRatio(), 1e-9);
            assertEquals(1, promotion.ssgiWarnMinFrames());
            assertEquals(90, promotion.ssgiWarnCooldownFrames());
            assertEquals(4, promotion.ssgiPromotionReadyMinFrames());
            assertEquals(1.0, promotion.probeGridWarnMinActiveRatio(), 1e-9);
            assertEquals(1, promotion.probeGridWarnMinFrames());
            assertEquals(90, promotion.probeGridWarnCooldownFrames());
            assertEquals(4, promotion.probeGridPromotionReadyMinFrames());
            assertEquals(1.0, promotion.rtDetailWarnMinActiveRatio(), 1e-9);
            assertEquals(1, promotion.rtDetailWarnMinFrames());
            assertEquals(90, promotion.rtDetailWarnCooldownFrames());
            assertEquals(4, promotion.rtDetailPromotionReadyMinFrames());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void backendOverridesTakePrecedenceForSsgiEnvelopeThresholds() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "true"),
                    Map.entry("vulkan.gi.mode", "ssgi"),
                    Map.entry("vulkan.gi.ssgiWarnMinActiveRatio", "0.75"),
                    Map.entry("vulkan.gi.ssgiWarnMinFrames", "7"),
                    Map.entry("vulkan.gi.ssgiWarnCooldownFrames", "33"),
                    Map.entry("vulkan.gi.ssgiPromotionReadyMinFrames", "6"),
                    Map.entry("vulkan.gi.probeWarnMinActiveRatio", "0.80"),
                    Map.entry("vulkan.gi.probeWarnMinFrames", "8"),
                    Map.entry("vulkan.gi.probeWarnCooldownFrames", "44"),
                    Map.entry("vulkan.gi.probePromotionReadyMinFrames", "7"),
                    Map.entry("vulkan.gi.rtWarnMinActiveRatio", "0.82"),
                    Map.entry("vulkan.gi.rtWarnMinFrames", "9"),
                    Map.entry("vulkan.gi.rtWarnCooldownFrames", "45"),
                    Map.entry("vulkan.gi.rtPromotionReadyMinFrames", "8"),
                    Map.entry("vulkan.gi.probeStreamingWarnMinCoverageRatio", "0.66"),
                    Map.entry("vulkan.gi.probeStreamingWarnMinFrames", "5"),
                    Map.entry("vulkan.gi.probeStreamingWarnCooldownFrames", "55"),
                    Map.entry("vulkan.gi.probeUpdateBudgetPerFrame", "7"),
                    Map.entry("vulkan.gi.probeConfiguredCount", "13")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene());
            runtime.render();
            var promotion = runtime.giPromotionDiagnostics();
            assertTrue(promotion.available());
            assertEquals(0.75, promotion.ssgiWarnMinActiveRatio(), 1e-9);
            assertEquals(7, promotion.ssgiWarnMinFrames());
            assertEquals(33, promotion.ssgiWarnCooldownFrames());
            assertEquals(6, promotion.ssgiPromotionReadyMinFrames());
            assertEquals(0.80, promotion.probeGridWarnMinActiveRatio(), 1e-9);
            assertEquals(8, promotion.probeGridWarnMinFrames());
            assertEquals(44, promotion.probeGridWarnCooldownFrames());
            assertEquals(7, promotion.probeGridPromotionReadyMinFrames());
            assertEquals(0.82, promotion.rtDetailWarnMinActiveRatio(), 1e-9);
            assertEquals(9, promotion.rtDetailWarnMinFrames());
            assertEquals(45, promotion.rtDetailWarnCooldownFrames());
            assertEquals(8, promotion.rtDetailPromotionReadyMinFrames());
            assertEquals(0.66, promotion.probeGridStreamingWarnMinCoverageRatio(), 1e-9);
            assertEquals(5, promotion.probeGridStreamingWarnMinFrames());
            assertEquals(55, promotion.probeGridStreamingWarnCooldownFrames());
            assertEquals(7, promotion.probeGridUpdateBudgetPerFrame());
            assertEquals(0, promotion.probeGridConfiguredCount());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void ssgiDisocclusionSceneMaintainsEnvelopeUnderRelaxedThresholds() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "true"),
                    Map.entry("vulkan.gi.mode", "ssgi"),
                    Map.entry("vulkan.gi.ssgiWarnMinFrames", "1"),
                    Map.entry("vulkan.gi.ssgiWarnCooldownFrames", "0"),
                    Map.entry("vulkan.gi.ssgiWarnMinActiveRatio", "1.0")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(disocclusionScene());
            EngineFrameResult frameA = runtime.render();
            EngineFrameResult frameB = runtime.render();
            assertTrue(frameA.warnings().stream().anyMatch(w -> "GI_SSGI_ENVELOPE".equals(w.code())));
            assertFalse(frameB.warnings().stream().anyMatch(w -> "GI_SSGI_ENVELOPE_BREACH".equals(w.code())));
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void ssgiThinGeometrySceneMaintainsEnvelopeUnderRelaxedThresholds() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "true"),
                    Map.entry("vulkan.gi.mode", "ssgi"),
                    Map.entry("vulkan.gi.ssgiWarnMinFrames", "1"),
                    Map.entry("vulkan.gi.ssgiWarnCooldownFrames", "0"),
                    Map.entry("vulkan.gi.ssgiWarnMinActiveRatio", "1.0")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(thinGeometryScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_SSGI_ENVELOPE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "GI_SSGI_ENVELOPE_BREACH".equals(w.code())));
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void ssgiCameraMotionSceneMaintainsEnvelopeAcrossSceneReloads() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "true"),
                    Map.entry("vulkan.gi.mode", "ssgi"),
                    Map.entry("vulkan.gi.ssgiWarnMinFrames", "1"),
                    Map.entry("vulkan.gi.ssgiWarnCooldownFrames", "0"),
                    Map.entry("vulkan.gi.ssgiWarnMinActiveRatio", "1.0")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(cameraMotionScene(0.0f));
            EngineFrameResult frameA = runtime.render();
            runtime.loadScene(cameraMotionScene(1.5f));
            EngineFrameResult frameB = runtime.render();
            runtime.loadScene(cameraMotionScene(3.0f));
            EngineFrameResult frameC = runtime.render();
            assertTrue(frameA.warnings().stream().anyMatch(w -> "GI_SSGI_ENVELOPE".equals(w.code())));
            assertTrue(frameB.warnings().stream().anyMatch(w -> "GI_SSGI_ENVELOPE".equals(w.code())));
            assertTrue(frameC.warnings().stream().anyMatch(w -> "GI_SSGI_ENVELOPE".equals(w.code())));
            assertFalse(frameC.warnings().stream().anyMatch(w -> "GI_SSGI_ENVELOPE_BREACH".equals(w.code())));
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void probeGridStreamingEnvelopeBreachesWhenUpdateCoverageTooLow() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.gi.enabled", "true"),
                    Map.entry("vulkan.gi.mode", "probe_grid"),
                    Map.entry("vulkan.gi.probeConfiguredCount", "24"),
                    Map.entry("vulkan.gi.probeUpdateBudgetPerFrame", "1"),
                    Map.entry("vulkan.gi.probeStreamingWarnMinCoverageRatio", "0.40"),
                    Map.entry("vulkan.gi.probeStreamingWarnMinFrames", "1"),
                    Map.entry("vulkan.gi.probeStreamingWarnCooldownFrames", "0")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PROBE_GRID_STREAMING_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "GI_PROBE_GRID_STREAMING_ENVELOPE_BREACH".equals(w.code())));
            var promotion = runtime.giPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.probeGridExpected());
            assertTrue(promotion.probeGridStreamingEnvelopeBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier tier) {
        return new EngineConfig(
                "vulkan",
                "vulkan-gi-capability-plan-test",
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

    private static SceneDescriptor validScene() {
        return sceneWithCameraXOffset("gi-plan-scene", 0.0f, List.of(
                new MeshDesc("mesh", "xform", "mat", "mesh.glb")
        ));
    }

    private static SceneDescriptor disocclusionScene() {
        return sceneWithCameraXOffset("gi-disocclusion-scene", -0.8f, List.of(
                new MeshDesc("mesh_fg", "xform_fg", "mat_fg", "mesh_fg.glb"),
                new MeshDesc("mesh_bg", "xform_bg", "mat_bg", "mesh_bg.glb")
        ));
    }

    private static SceneDescriptor thinGeometryScene() {
        return sceneWithCameraXOffset("gi-thin-geometry-scene", 0.2f, List.of(
                new MeshDesc("mesh_thin_a", "xform_thin_a", "mat_thin", "thin_a.glb"),
                new MeshDesc("mesh_thin_b", "xform_thin_b", "mat_thin", "thin_b.glb"),
                new MeshDesc("mesh_fill", "xform_fill", "mat_fill", "fill.glb")
        ));
    }

    private static SceneDescriptor cameraMotionScene(float cameraX) {
        return sceneWithCameraXOffset("gi-camera-motion-scene-" + cameraX, cameraX, List.of(
                new MeshDesc("mesh_motion", "xform_motion", "mat_motion", "motion.glb")
        ));
    }

    private static SceneDescriptor sceneWithCameraXOffset(String sceneId, float cameraX, List<MeshDesc> meshes) {
        CameraDesc movedCamera = new CameraDesc("cam", new Vec3(cameraX, 0, 5), new Vec3(cameraX, 0, 0), 60f, 0.1f, 100f);
        List<TransformDesc> transforms = List.of(
                new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1)),
                new TransformDesc("xform_fg", new Vec3(-0.7f, 0, -0.4f), new Vec3(0, 0, 0), new Vec3(1, 1, 1)),
                new TransformDesc("xform_bg", new Vec3(0.7f, 0, -1.8f), new Vec3(0, 0, 0), new Vec3(1, 1, 1)),
                new TransformDesc("xform_thin_a", new Vec3(-0.3f, 0, -0.9f), new Vec3(0, 0, 0), new Vec3(0.08f, 1.0f, 1.0f)),
                new TransformDesc("xform_thin_b", new Vec3(0.35f, 0, -1.1f), new Vec3(0, 0, 0), new Vec3(0.08f, 1.0f, 1.0f)),
                new TransformDesc("xform_fill", new Vec3(0.0f, -0.2f, -2.0f), new Vec3(0, 0, 0), new Vec3(2.0f, 0.4f, 2.0f)),
                new TransformDesc("xform_motion", new Vec3(0.0f, 0, -1.3f), new Vec3(0, 0, 0), new Vec3(1, 1, 1))
        );
        List<MaterialDesc> materials = List.of(
                new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null),
                new MaterialDesc("mat_fg", new Vec3(0.8f, 0.9f, 1.0f), 0.0f, 0.45f, null, null),
                new MaterialDesc("mat_bg", new Vec3(0.9f, 0.85f, 0.8f), 0.0f, 0.55f, null, null),
                new MaterialDesc("mat_thin", new Vec3(0.75f, 0.95f, 0.75f), 0.0f, 0.35f, null, null),
                new MaterialDesc("mat_fill", new Vec3(0.7f, 0.7f, 0.75f), 0.0f, 0.6f, null, null),
                new MaterialDesc("mat_motion", new Vec3(0.95f, 0.8f, 0.75f), 0.0f, 0.5f, null, null)
        );
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
                sceneId,
                List.of(movedCamera),
                "cam",
                transforms,
                meshes,
                materials,
                List.of(light),
                environment,
                fog,
                List.of(),
                null
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
