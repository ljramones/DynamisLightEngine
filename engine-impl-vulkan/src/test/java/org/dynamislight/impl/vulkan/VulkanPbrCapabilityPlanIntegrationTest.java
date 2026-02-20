package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

class VulkanPbrCapabilityPlanIntegrationTest {
    @Test
    void emitsPbrCapabilityWarningAndTypedDiagnostics() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.pbr.specularGlossinessEnabled", "true"),
                    Map.entry("vulkan.pbr.detailMapsEnabled", "true"),
                    Map.entry("vulkan.pbr.materialLayeringEnabled", "true"),
                    Map.entry("vulkan.pbr.promotionReadyMinFrames", "1")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CAPABILITY_MODE_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_PROMOTION_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_PROMOTION_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CINEMATIC_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CINEMATIC_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CINEMATIC_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_SURFACE_OPTICS_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_SURFACE_OPTICS_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_SURFACE_OPTICS_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_SURFACE_GEOMETRY_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_SURFACE_GEOMETRY_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_SURFACE_GEOMETRY_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CHARACTER_SURFACES_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CHARACTER_SURFACES_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CHARACTER_SURFACES_PROMOTION_READY".equals(w.code())));
            var diagnostics = runtime.pbrCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.specularGlossinessEnabled());
            assertTrue(diagnostics.detailMapsEnabled());
            assertTrue(diagnostics.materialLayeringEnabled());
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.pbr.specular_glossiness"));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.pbr.detail_maps"));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.pbr.material_layering"));
            var promotion = runtime.pbrPromotionDiagnostics();
            assertTrue(promotion.available());
            assertFalse(promotion.envelopeBreachedLastFrame());
            assertTrue(promotion.promotionReadyLastFrame());
            assertFalse(promotion.cinematicEnvelopeBreachedLastFrame());
            assertTrue(promotion.cinematicPromotionReadyLastFrame());
            assertFalse(promotion.surfaceOpticsEnvelopeBreachedLastFrame());
            assertTrue(promotion.surfaceOpticsPromotionReadyLastFrame());
            assertFalse(promotion.surfaceGeometryEnvelopeBreachedLastFrame());
            assertTrue(promotion.surfaceGeometryPromotionReadyLastFrame());
            assertFalse(promotion.characterSurfaceEnvelopeBreachedLastFrame());
            assertTrue(promotion.characterSurfacePromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsPbrPromotionBreachWhenAdvancedAndEnergyValidationThresholdsMiss() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.pbr.specularGlossinessEnabled", "false"),
                    Map.entry("vulkan.pbr.detailMapsEnabled", "false"),
                    Map.entry("vulkan.pbr.materialLayeringEnabled", "false"),
                    Map.entry("vulkan.pbr.clearCoatEnabled", "false"),
                    Map.entry("vulkan.pbr.anisotropicEnabled", "false"),
                    Map.entry("vulkan.pbr.transmissionEnabled", "false"),
                    Map.entry("vulkan.pbr.refractionEnabled", "false"),
                    Map.entry("vulkan.pbr.vertexColorBlendEnabled", "false"),
                    Map.entry("vulkan.pbr.emissiveBloomControlEnabled", "false"),
                    Map.entry("vulkan.pbr.energyConservationValidationEnabled", "false"),
                    Map.entry("vulkan.pbr.advancedWarnMinFeatureCount", "1"),
                    Map.entry("vulkan.pbr.warnMinFrames", "1"),
                    Map.entry("vulkan.pbr.warnCooldownFrames", "0"),
                    Map.entry("vulkan.pbr.promotionReadyMinFrames", "1")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_PROMOTION_ENVELOPE_BREACH".equals(w.code())));
            var promotion = runtime.pbrPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.envelopeBreachedLastFrame());
            assertFalse(promotion.promotionReadyLastFrame());
            assertFalse(promotion.cinematicEnvelopeBreachedLastFrame());
            assertTrue(promotion.cinematicPromotionReadyLastFrame());
            assertFalse(promotion.surfaceOpticsEnvelopeBreachedLastFrame());
            assertTrue(promotion.surfaceOpticsPromotionReadyLastFrame());
            assertFalse(promotion.surfaceGeometryEnvelopeBreachedLastFrame());
            assertTrue(promotion.surfaceGeometryPromotionReadyLastFrame());
            assertFalse(promotion.characterSurfaceEnvelopeBreachedLastFrame());
            assertTrue(promotion.characterSurfacePromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsPbrCinematicEnvelopeBreachWhenRequestedCinematicFeaturesArePruned() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.pbr.subsurfaceScatteringEnabled", "true"),
                    Map.entry("vulkan.pbr.thinFilmIridescenceEnabled", "true"),
                    Map.entry("vulkan.pbr.sheenEnabled", "true"),
                    Map.entry("vulkan.pbr.parallaxOcclusionEnabled", "true"),
                    Map.entry("vulkan.pbr.tessellationEnabled", "true"),
                    Map.entry("vulkan.pbr.decalsEnabled", "true"),
                    Map.entry("vulkan.pbr.eyeShaderEnabled", "true"),
                    Map.entry("vulkan.pbr.hairShaderEnabled", "true"),
                    Map.entry("vulkan.pbr.clothShaderEnabled", "true"),
                    Map.entry("vulkan.pbr.warnMinFrames", "1"),
                    Map.entry("vulkan.pbr.warnCooldownFrames", "0"),
                    Map.entry("vulkan.pbr.promotionReadyMinFrames", "1")
            ), QualityTier.LOW), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CINEMATIC_ENVELOPE_BREACH".equals(w.code())));
            var promotion = runtime.pbrPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.expectedCinematicFeatureCount() > 0);
            assertTrue(promotion.activeCinematicFeatureCount() < promotion.expectedCinematicFeatureCount());
            assertTrue(promotion.cinematicEnvelopeBreachedLastFrame());
            assertFalse(promotion.cinematicPromotionReadyLastFrame());
            assertTrue(promotion.expectedSurfaceOpticsFeatureCount() > 0);
            assertTrue(promotion.activeSurfaceOpticsFeatureCount() < promotion.expectedSurfaceOpticsFeatureCount());
            assertTrue(promotion.surfaceOpticsEnvelopeBreachedLastFrame());
            assertFalse(promotion.surfaceOpticsPromotionReadyLastFrame());
            assertTrue(promotion.expectedSurfaceGeometryFeatureCount() > 0);
            assertTrue(promotion.activeSurfaceGeometryFeatureCount() < promotion.expectedSurfaceGeometryFeatureCount());
            assertTrue(promotion.surfaceGeometryEnvelopeBreachedLastFrame());
            assertFalse(promotion.surfaceGeometryPromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsPbrSurfaceOpticsEnvelopeBreachWhenSurfaceOpticsRequestedOnLowTier() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.pbr.subsurfaceScatteringEnabled", "true"),
                    Map.entry("vulkan.pbr.thinFilmIridescenceEnabled", "true"),
                    Map.entry("vulkan.pbr.sheenEnabled", "true"),
                    Map.entry("vulkan.pbr.warnMinFrames", "1"),
                    Map.entry("vulkan.pbr.warnCooldownFrames", "0"),
                    Map.entry("vulkan.pbr.surfaceOpticsWarnMinFeatureCount", "2"),
                    Map.entry("vulkan.pbr.promotionReadyMinFrames", "1")
            ), QualityTier.LOW), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_SURFACE_OPTICS_ENVELOPE_BREACH".equals(w.code())));
            var promotion = runtime.pbrPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.expectedSurfaceOpticsFeatureCount() > 0);
            assertTrue(promotion.activeSurfaceOpticsFeatureCount() < 2);
            assertTrue(promotion.surfaceOpticsEnvelopeBreachedLastFrame());
            assertFalse(promotion.surfaceOpticsPromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void activatesPbrSurfaceOpticsOnHighTierWithCinematicModeAndReadyGates() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.pbr.subsurfaceScatteringEnabled", "true"),
                    Map.entry("vulkan.pbr.thinFilmIridescenceEnabled", "true"),
                    Map.entry("vulkan.pbr.sheenEnabled", "true"),
                    Map.entry("vulkan.pbr.warnMinFrames", "1"),
                    Map.entry("vulkan.pbr.warnCooldownFrames", "0"),
                    Map.entry("vulkan.pbr.surfaceOpticsWarnMinFeatureCount", "2"),
                    Map.entry("vulkan.pbr.promotionReadyMinFrames", "1")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_SURFACE_OPTICS_PROMOTION_READY".equals(w.code())));
            var diagnostics = runtime.pbrCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue("cinematic_surface_stack".equals(diagnostics.mode()));
            assertTrue(diagnostics.subsurfaceScatteringEnabled());
            assertTrue(diagnostics.thinFilmIridescenceEnabled());
            assertTrue(diagnostics.sheenEnabled());
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.pbr.subsurface_scattering"));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.pbr.thin_film_iridescence"));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.pbr.sheen"));
            var promotion = runtime.pbrPromotionDiagnostics();
            assertTrue(promotion.available());
            assertFalse(promotion.surfaceOpticsEnvelopeBreachedLastFrame());
            assertTrue(promotion.surfaceOpticsPromotionReadyLastFrame());
            assertTrue(promotion.activeSurfaceOpticsFeatureCount() >= 3);
            assertFalse(promotion.surfaceGeometryEnvelopeBreachedLastFrame());
            assertTrue(promotion.surfaceGeometryPromotionReadyLastFrame());
            assertFalse(promotion.characterSurfaceEnvelopeBreachedLastFrame());
            assertTrue(promotion.characterSurfacePromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsPbrSurfaceGeometryEnvelopeBreachWhenGeometryFeaturesRequestedOnLowTier() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.pbr.parallaxOcclusionEnabled", "true"),
                    Map.entry("vulkan.pbr.tessellationEnabled", "true"),
                    Map.entry("vulkan.pbr.decalsEnabled", "true"),
                    Map.entry("vulkan.pbr.warnMinFrames", "1"),
                    Map.entry("vulkan.pbr.warnCooldownFrames", "0"),
                    Map.entry("vulkan.pbr.promotionReadyMinFrames", "1")
            ), QualityTier.LOW), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_SURFACE_GEOMETRY_ENVELOPE_BREACH".equals(w.code())));
            var promotion = runtime.pbrPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.expectedSurfaceGeometryFeatureCount() > 0);
            assertTrue(promotion.activeSurfaceGeometryFeatureCount() < promotion.expectedSurfaceGeometryFeatureCount());
            assertTrue(promotion.surfaceGeometryEnvelopeBreachedLastFrame());
            assertFalse(promotion.surfaceGeometryPromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsPbrCharacterSurfaceEnvelopeBreachWhenCharacterFeaturesRequestedOnLowTier() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.pbr.eyeShaderEnabled", "true"),
                    Map.entry("vulkan.pbr.hairShaderEnabled", "true"),
                    Map.entry("vulkan.pbr.clothShaderEnabled", "true"),
                    Map.entry("vulkan.pbr.warnMinFrames", "1"),
                    Map.entry("vulkan.pbr.warnCooldownFrames", "0"),
                    Map.entry("vulkan.pbr.promotionReadyMinFrames", "1")
            ), QualityTier.LOW), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CHARACTER_SURFACES_ENVELOPE_BREACH".equals(w.code())));
            var promotion = runtime.pbrPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.expectedCharacterSurfaceFeatureCount() > 0);
            assertTrue(promotion.activeCharacterSurfaceFeatureCount() < promotion.expectedCharacterSurfaceFeatureCount());
            assertTrue(promotion.characterSurfaceEnvelopeBreachedLastFrame());
            assertFalse(promotion.characterSurfacePromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void activatesCharacterSurfacesOnHighTierWithReadyGate() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.pbr.eyeShaderEnabled", "true"),
                    Map.entry("vulkan.pbr.hairShaderEnabled", "true"),
                    Map.entry("vulkan.pbr.clothShaderEnabled", "true"),
                    Map.entry("vulkan.pbr.warnMinFrames", "1"),
                    Map.entry("vulkan.pbr.warnCooldownFrames", "0"),
                    Map.entry("vulkan.pbr.promotionReadyMinFrames", "1")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "PBR_CHARACTER_SURFACES_PROMOTION_READY".equals(w.code())));
            var diagnostics = runtime.pbrCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.eyeShaderEnabled());
            assertTrue(diagnostics.hairShaderEnabled());
            assertTrue(diagnostics.clothShaderEnabled());
            var promotion = runtime.pbrPromotionDiagnostics();
            assertTrue(promotion.available());
            assertFalse(promotion.characterSurfaceEnvelopeBreachedLastFrame());
            assertTrue(promotion.characterSurfacePromotionReadyLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier tier) {
        return new EngineConfig(
                "vulkan",
                "vulkan-pbr-capability-plan-test",
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
                "pbr-plan-scene",
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
        public void onLog(LogMessage message) {
        }

        @Override
        public void onEvent(EngineEvent event) {
        }

        @Override
        public void onError(EngineErrorReport error) {
        }
    }
}
