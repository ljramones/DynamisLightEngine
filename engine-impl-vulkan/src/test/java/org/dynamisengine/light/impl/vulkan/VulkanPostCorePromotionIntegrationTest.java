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
import org.dynamisengine.light.api.scene.PostProcessDesc;
import org.dynamisengine.light.api.scene.SceneDescriptor;
import org.dynamisengine.light.api.scene.ShadowDesc;
import org.dynamisengine.light.api.scene.TransformDesc;
import org.dynamisengine.light.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class VulkanPostCorePromotionIntegrationTest {
    @Test
    void emitsPostCorePolicyAndPromotionWhenStable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.aaMode", "taa"),
                    Map.entry("vulkan.post.sharpening", "true"),
                    Map.entry("vulkan.post.volumetricFog", "true"),
                    Map.entry("vulkan.post.corePromotionReadyMinFrames", "1")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene(false));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "POST_CORE_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "POST_CORE_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "POST_CORE_PROMOTION_READY".equals(w.code())));
            var diagnostics = runtime.postCorePromotionDiagnostics();
            assertTrue(diagnostics.available());
            assertFalse(diagnostics.envelopeBreachedLastFrame());
            assertTrue(diagnostics.promotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsPostCoreEnvelopeBreachWhenThresholdsForced() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.aaMode", "taa"),
                    Map.entry("vulkan.post.sharpening", "true"),
                    Map.entry("vulkan.post.volumetricFog", "true"),
                    Map.entry("vulkan.post.coreWarnMinFrames", "1"),
                    Map.entry("vulkan.post.coreWarnCooldownFrames", "0"),
                    Map.entry("vulkan.post.tonemapWarnExposureMax", "0.1"),
                    Map.entry("vulkan.post.bloomWarnStrengthMax", "0.01"),
                    Map.entry("vulkan.post.ssaoWarnRadiusMax", "0.1"),
                    Map.entry("vulkan.post.sharpenWarnMax", "0.01"),
                    Map.entry("vulkan.post.volumetricFogWarnDensityMax", "0.0001")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validScene(true));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "POST_CORE_ENVELOPE_BREACH".equals(w.code())));
            var diagnostics = runtime.postCorePromotionDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.envelopeBreachedLastFrame());
            assertFalse(diagnostics.promotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier tier) {
        return new EngineConfig(
                "vulkan",
                "vulkan-post-core-promotion-test",
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

    private static SceneDescriptor validScene(boolean aggressivePost) {
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
        FogDesc fog = aggressivePost
                ? new FogDesc(true, FogMode.EXPONENTIAL, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0.25f, 16f, 0.0f, 0.0f)
                : new FogDesc(true, FogMode.EXPONENTIAL, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0.02f, 16f, 0.0f, 0.0f);
        PostProcessDesc post = aggressivePost
                ? new PostProcessDesc(true, true, 3.0f, 3.1f, true, 2.0f, 1.6f, true, 1.0f, 3.0f, 0.2f, 4.0f)
                : new PostProcessDesc(true, true, 1.0f, 2.2f, true, 1.0f, 0.4f, true, 0.8f, 1.0f, 0.02f, 1.0f);
        return new SceneDescriptor(
                "post-core-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(directional),
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
