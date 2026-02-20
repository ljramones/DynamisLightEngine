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
import org.dynamislight.api.runtime.EngineFrameResult;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class VulkanSkyCapabilityPromotionIntegrationTest {
    @Test
    void emitsSkyPolicyAndPromotionReadyForHdriSkyboxPath() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.sky.promotionReadyMinFrames", "1")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validScene(true));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SKY_CAPABILITY_PLAN_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SKY_CAPABILITY_MODE_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SKY_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SKY_PROMOTION_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SKY_PROMOTION_READY".equals(w.code())));
            var diagnostics = runtime.skyCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.hdriSkyboxActive());
            assertTrue(diagnostics.activeFeatures().contains("vulkan.sky.hdri_skybox"));
            var promotion = runtime.skyPromotionDiagnostics();
            assertTrue(promotion.available());
            assertFalse(promotion.envelopeBreachedLastFrame());
            assertTrue(promotion.promotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsSkyEnvelopeBreachWhenProceduralIsRequestedButUnavailable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.sky.proceduralEnabled", "true"),
                    Map.entry("vulkan.sky.warnMinFrames", "1"),
                    Map.entry("vulkan.sky.warnCooldownFrames", "0"),
                    Map.entry("vulkan.sky.promotionReadyMinFrames", "1")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validScene(false));
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SKY_PROMOTION_ENVELOPE_BREACH".equals(w.code())));
            var diagnostics = runtime.skyCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.prunedFeatures().contains("vulkan.sky.procedural_sky"));
            var promotion = runtime.skyPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.envelopeBreachedLastFrame());
            assertFalse(promotion.promotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier tier) {
        return new EngineConfig(
                "vulkan",
                "vulkan-sky-capability-promotion-test",
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

    private static SceneDescriptor validScene(boolean withSkybox) {
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
        EnvironmentDesc environment = withSkybox
                ? new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, "skybox.hdr")
                : new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        return new SceneDescriptor(
                "sky-capability-scene",
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
