package org.dynamisengine.light.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.config.EngineConfig;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.error.EngineErrorReport;
import org.dynamisengine.light.api.event.EngineEvent;
import org.dynamisengine.light.api.logging.LogMessage;
import org.dynamisengine.light.api.runtime.EngineFrameResult;
import org.dynamisengine.light.api.runtime.EngineHostCallbacks;
import org.dynamisengine.light.api.scene.CameraDesc;
import org.dynamisengine.light.api.scene.EnvironmentDesc;
import org.dynamisengine.light.api.scene.FogDesc;
import org.dynamisengine.light.api.scene.FogMode;
import org.dynamisengine.light.api.scene.LightDesc;
import org.dynamisengine.light.api.scene.LightType;
import org.dynamisengine.light.api.scene.MaterialDesc;
import org.dynamisengine.light.api.scene.MeshDesc;
import org.dynamisengine.light.api.scene.SceneDescriptor;
import org.dynamisengine.light.api.scene.ShadowDesc;
import org.dynamisengine.light.api.scene.TransformDesc;
import org.dynamisengine.light.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class VulkanRtCapabilityPromotionIntegrationTest {
    @Test
    void emitsRtCapabilityPolicyAndPromotionReadyWhenStable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.rt.capabilityPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.rt.qualityTiersEnabled", "true")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_CAPABILITY_MODE_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_CAPABILITY_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_AO_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_TRANSLUCENCY_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_QUALITY_TIERS_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_CAPABILITY_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_CAPABILITY_PROMOTION_READY".equals(w.code())));
            var diagnostics = runtime.rtCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertFalse(diagnostics.modeId().isBlank());
            assertFalse(diagnostics.prunedFeatures().contains("vulkan.rt.quality_tiers"));
            var promotion = runtime.rtCapabilityPromotionDiagnostics();
            assertTrue(promotion.available());
            assertFalse(promotion.envelopeBreachedLastFrame());
            assertTrue(promotion.promotionReadyLastFrame());
            assertFalse(promotion.aoEnvelopeBreachedLastFrame());
            assertTrue(promotion.aoPromotionReadyLastFrame());
            assertFalse(promotion.translucencyEnvelopeBreachedLastFrame());
            assertTrue(promotion.translucencyPromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsRtCapabilityEnvelopeBreachWhenRequestedFeaturesAreUnavailable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.rt.aoEnabled", "true"),
                    Map.entry("vulkan.rt.translucencyCausticsEnabled", "true"),
                    Map.entry("vulkan.rt.capabilityWarnMinFrames", "1"),
                    Map.entry("vulkan.rt.capabilityWarnCooldownFrames", "0"),
                    Map.entry("vulkan.rt.capabilityPromotionReadyMinFrames", "1")
            ), QualityTier.LOW), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_CAPABILITY_ENVELOPE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_AO_ENVELOPE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_TRANSLUCENCY_ENVELOPE_BREACH".equals(w.code())));
            var diagnostics = runtime.rtCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertFalse(diagnostics.modeId().isBlank());
            assertTrue(diagnostics.prunedFeatures().stream().anyMatch(v -> v.startsWith("vulkan.rt.ao")));
            assertTrue(diagnostics.prunedFeatures().stream().anyMatch(v -> v.startsWith("vulkan.rt.translucency_caustics")));
            var promotion = runtime.rtCapabilityPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.envelopeBreachedLastFrame());
            assertFalse(promotion.promotionReadyLastFrame());
            assertTrue(promotion.aoEnvelopeBreachedLastFrame());
            assertFalse(promotion.aoPromotionReadyLastFrame());
            assertTrue(promotion.translucencyEnvelopeBreachedLastFrame());
            assertFalse(promotion.translucencyPromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void resolvesRtCapabilityFullStackModeWhenMockRtSupportAndAllFeaturesEnabled() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.rt.mockTraversalSupported", "true"),
                    Map.entry("vulkan.rt.mockBvhSupported", "true"),
                    Map.entry("vulkan.rt.aoEnabled", "true"),
                    Map.entry("vulkan.rt.translucencyCausticsEnabled", "true"),
                    Map.entry("vulkan.rt.bvhCompactionEnabled", "true"),
                    Map.entry("vulkan.rt.denoiserFrameworkEnabled", "true"),
                    Map.entry("vulkan.rt.hybridCompositionEnabled", "true"),
                    Map.entry("vulkan.rt.qualityTiersEnabled", "true"),
                    Map.entry("vulkan.rt.inlineRayQueryEnabled", "true"),
                    Map.entry("vulkan.rt.dedicatedRaygenEnabled", "true"),
                    Map.entry("vulkan.rt.capabilityPromotionReadyMinFrames", "1")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "RT_CAPABILITY_PROMOTION_READY".equals(w.code())));
            var diagnostics = runtime.rtCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.prunedFeatures().isEmpty());
            assertTrue(diagnostics.activeFeatures().contains("vulkan.rt.ao"));
            assertTrue(diagnostics.activeFeatures().contains("vulkan.rt.translucency_caustics"));
            assertTrue(diagnostics.activeFeatures().contains("vulkan.rt.bvh_compaction"));
            assertTrue(diagnostics.activeFeatures().contains("vulkan.rt.denoiser_framework"));
            assertTrue(diagnostics.activeFeatures().contains("vulkan.rt.hybrid_composition"));
            assertTrue(diagnostics.activeFeatures().contains("vulkan.rt.quality_tiers"));
            assertTrue(diagnostics.activeFeatures().contains("vulkan.rt.inline_ray_query"));
            assertTrue(diagnostics.activeFeatures().contains("vulkan.rt.dedicated_raygen"));
            assertTrue("rt_full_stack".equals(diagnostics.modeId())
                    || "rt_translucency_caustics".equals(diagnostics.modeId()));
        } finally {
            runtime.shutdown();
        }
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier tier) {
        return new EngineConfig(
                "vulkan",
                "vulkan-rt-capability-promotion-test",
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
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc material = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc directional = new LightDesc(
                "sun",
                new Vec3(0, 6, 0),
                new Vec3(1, 1, 1),
                3.0f,
                50f,
                true,
                new ShadowDesc(2048, 0.0015f, 1, 4),
                LightType.DIRECTIONAL,
                new Vec3(0, -1, 0),
                15f,
                30f
        );
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        return new SceneDescriptor(
                "rt-capability-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(directional),
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
