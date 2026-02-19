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
            runtime.loadScene(validScene(true, false, false));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_POST_CAPABILITY_PLAN_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_TEMPORAL_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_REACTIVE_MASK_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_HISTORY_CLAMP_POLICY".equals(w.code())));
            var diagnostics = runtime.aaPostCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.aaEnabled());
            assertTrue("taa".equals(diagnostics.aaMode()));
            assertFalse(diagnostics.activeCapabilities().isEmpty());
            var temporalDiagnostics = runtime.aaTemporalPromotionDiagnostics();
            assertTrue(temporalDiagnostics.available());
            assertTrue(temporalDiagnostics.temporalPathRequested());
            assertTrue(temporalDiagnostics.temporalPathActive());
            assertTrue(temporalDiagnostics.materialCount() > 0);
            var upscaleDiagnostics = runtime.aaUpscalePromotionDiagnostics();
            assertTrue(upscaleDiagnostics.available());
            assertFalse(upscaleDiagnostics.upscaleModeActive());
            var msaaDiagnostics = runtime.aaMsaaPromotionDiagnostics();
            assertTrue(msaaDiagnostics.available());
            assertFalse(msaaDiagnostics.msaaModeActive());
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
            runtime.loadScene(validScene(false, false, false));
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
            assertFalse(temporalDiagnostics.reactiveMaskBreachedLastFrame());
            assertFalse(temporalDiagnostics.historyClampBreachedLastFrame());
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
                    Map.entry("vulkan.aa.temporalPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.aa.reactiveMaskWarnMinCoverage", "0.95"),
                    Map.entry("vulkan.aa.reactiveMaskWarnMinFrames", "1"),
                    Map.entry("vulkan.aa.reactiveMaskWarnCooldownFrames", "0"),
                    Map.entry("vulkan.aa.historyClampWarnMinCustomizedRatio", "0.95"),
                    Map.entry("vulkan.aa.historyClampWarnMinFrames", "1"),
                    Map.entry("vulkan.aa.historyClampWarnCooldownFrames", "0")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene(true, false, false));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_TEMPORAL_ENVELOPE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_REACTIVE_MASK_ENVELOPE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_HISTORY_CLAMP_ENVELOPE_BREACH".equals(w.code())));
            var temporalDiagnostics = runtime.aaTemporalPromotionDiagnostics();
            assertTrue(temporalDiagnostics.envelopeBreachedLastFrame());
            assertFalse(temporalDiagnostics.promotionReadyLastFrame());
            assertTrue(temporalDiagnostics.reactiveMaskBreachedLastFrame());
            assertTrue(temporalDiagnostics.historyClampBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void upscaleEnvelopeCanBeForcedForCiGateValidation() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.aaMode", "tsr"),
                    Map.entry("vulkan.upscalerMode", "dlss"),
                    Map.entry("vulkan.aa.upscaleWarnMaxRenderScaleTsr", "0.1"),
                    Map.entry("vulkan.aa.upscaleWarnMinFrames", "1"),
                    Map.entry("vulkan.aa.upscaleWarnCooldownFrames", "0"),
                    Map.entry("vulkan.aa.upscalePromotionReadyMinFrames", "1")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene(true, false, false));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_UPSCALE_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_UPSCALE_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_UPSCALE_ENVELOPE_BREACH".equals(w.code())));
            var upscaleDiagnostics = runtime.aaUpscalePromotionDiagnostics();
            assertTrue(upscaleDiagnostics.available());
            assertTrue(upscaleDiagnostics.upscaleModeActive());
            assertTrue("tsr".equals(upscaleDiagnostics.aaMode()));
            assertTrue(upscaleDiagnostics.envelopeBreachedLastFrame());
            assertFalse(upscaleDiagnostics.promotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void msaaEnvelopeCanBeForcedForCiGateValidation() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.aaMode", "msaa_selective"),
                    Map.entry("vulkan.aa.msaaCandidateWarnMinRatio", "0.95"),
                    Map.entry("vulkan.aa.msaaWarnMinFrames", "1"),
                    Map.entry("vulkan.aa.msaaWarnCooldownFrames", "0"),
                    Map.entry("vulkan.aa.msaaPromotionReadyMinFrames", "1")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene(false, false, false));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_MSAA_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_MSAA_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_MSAA_ENVELOPE_BREACH".equals(w.code())));
            var msaaDiagnostics = runtime.aaMsaaPromotionDiagnostics();
            assertTrue(msaaDiagnostics.available());
            assertTrue(msaaDiagnostics.msaaModeActive());
            assertTrue("msaa_selective".equals(msaaDiagnostics.aaMode()));
            assertTrue(msaaDiagnostics.envelopeBreachedLastFrame());
            assertFalse(msaaDiagnostics.promotionReadyLastFrame());
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

    @Test
    void qualityEnvelopeCanBeForcedForCiGateValidation() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.aaMode", "dlaa"),
                    Map.entry("vulkan.aa.dlaaWarnMinBlend", "0.99"),
                    Map.entry("vulkan.aa.dlaaWarnMinFrames", "1"),
                    Map.entry("vulkan.aa.dlaaWarnCooldownFrames", "0"),
                    Map.entry("vulkan.aa.dlaaPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.aa.specularWarnMaxClipScale", "0.1"),
                    Map.entry("vulkan.aa.specularWarnMinFrames", "1"),
                    Map.entry("vulkan.aa.specularWarnCooldownFrames", "0"),
                    Map.entry("vulkan.aa.specularPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.aa.geometricWarnMinThinFeatureRatio", "0.95"),
                    Map.entry("vulkan.aa.geometricWarnMinFrames", "1"),
                    Map.entry("vulkan.aa.geometricWarnCooldownFrames", "0"),
                    Map.entry("vulkan.aa.geometricPromotionReadyMinFrames", "1")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene(true, true, false));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_DLAA_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_DLAA_ENVELOPE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_SPECULAR_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_SPECULAR_ENVELOPE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_GEOMETRIC_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_GEOMETRIC_ENVELOPE".equals(w.code())));
            var qualityDiagnostics = runtime.aaQualityPromotionDiagnostics();
            assertTrue(qualityDiagnostics.available());
            assertTrue(qualityDiagnostics.dlaaModeActive());
            assertTrue(qualityDiagnostics.dlaaEnvelopeBreachedLastFrame());
            assertTrue(qualityDiagnostics.specularEnvelopeBreachedLastFrame());
            assertTrue(qualityDiagnostics.geometricPolicyActive());
            assertTrue(qualityDiagnostics.geometricEnvelopeBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void alphaToCoverageEnvelopeCanBeForcedForCiGateValidation() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.aaMode", "msaa_selective"),
                    Map.entry("vulkan.aa.a2cWarnMinThinFeatureRatio", "0.99"),
                    Map.entry("vulkan.aa.a2cWarnMinFrames", "1"),
                    Map.entry("vulkan.aa.a2cWarnCooldownFrames", "0"),
                    Map.entry("vulkan.aa.a2cPromotionReadyMinFrames", "1")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene(false, false, false));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_A2C_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "AA_A2C_ENVELOPE_BREACH".equals(w.code())));
            var qualityDiagnostics = runtime.aaQualityPromotionDiagnostics();
            assertTrue(qualityDiagnostics.available());
            assertTrue(qualityDiagnostics.alphaToCoveragePolicyActive());
            assertTrue(qualityDiagnostics.alphaToCoverageEnvelopeBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    private static SceneDescriptor validScene(boolean taaEnabled, boolean normalMapped, boolean thinFeature) {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc material = new MaterialDesc(
                "mat",
                new Vec3(1, 1, 1),
                0.0f,
                0.5f,
                null,
                normalMapped ? "normal.png" : null,
                null,
                null,
                0f,
                thinFeature,
                thinFeature,
                1.0f,
                1.0f,
                1.0f,
                org.dynamislight.api.scene.ReactivePreset.AUTO,
                org.dynamislight.api.scene.ReflectionOverrideMode.AUTO
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
