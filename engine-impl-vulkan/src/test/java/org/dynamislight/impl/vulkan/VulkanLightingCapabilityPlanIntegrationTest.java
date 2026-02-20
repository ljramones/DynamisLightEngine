package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class VulkanLightingCapabilityPlanIntegrationTest {
    @Test
    void emitsLightingCapabilityWarningAndTypedDiagnostics() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.lighting.physicallyBasedUnitsEnabled", "true"),
                    Map.entry("vulkan.lighting.prioritizationEnabled", "true"),
                    Map.entry("vulkan.lighting.emissiveMeshEnabled", "true"),
                    Map.entry("vulkan.lighting.areaApproxEnabled", "true"),
                    Map.entry("vulkan.lighting.iesProfilesEnabled", "true"),
                    Map.entry("vulkan.lighting.cookiesEnabled", "true"),
                    Map.entry("vulkan.lighting.volumetricShaftsEnabled", "true"),
                    Map.entry("vulkan.lighting.clusteringEnabled", "true"),
                    Map.entry("vulkan.lighting.lightLayersEnabled", "true"),
                    Map.entry("vulkan.lighting.localLightBudget", "2"),
                    Map.entry("vulkan.lighting.advancedFeaturePromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.lighting.budgetWarnMinFrames", "1"),
                    Map.entry("vulkan.lighting.budgetWarnCooldownFrames", "0")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validScene());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_CAPABILITY_MODE_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_TELEMETRY_PROFILE_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_BUDGET_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_BUDGET_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_BUDGET_ENVELOPE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_AREA_APPROX_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_IES_PROFILES_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_COOKIES_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_VOLUMETRIC_SHAFTS_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_CLUSTERING_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_LIGHT_LAYERS_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_AREA_APPROX_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_IES_PROFILES_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_COOKIES_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_VOLUMETRIC_SHAFTS_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_CLUSTERING_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_LIGHT_LAYERS_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_PHYS_UNITS_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_EMISSIVE_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_EMISSIVE_ENVELOPE_BREACH".equals(w.code())));
            var diagnostics = runtime.lightingCapabilityDiagnostics();
            assertTrue(diagnostics.available());
            assertTrue(diagnostics.activeCapabilities().stream().anyMatch(s -> s.contains("vulkan.lighting")));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.lighting.area_approx"));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.lighting.ies_profiles"));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.lighting.cookies"));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.lighting.volumetric_shafts"));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.lighting.clustering"));
            assertTrue(diagnostics.activeCapabilities().contains("vulkan.lighting.light_layers"));
            assertTrue(diagnostics.areaApproxEnabled());
            assertTrue(diagnostics.iesProfilesEnabled());
            assertTrue(diagnostics.cookiesEnabled());
            assertTrue(diagnostics.volumetricShaftsEnabled());
            assertTrue(diagnostics.clusteringEnabled());
            assertTrue(diagnostics.lightLayersEnabled());
            assertTrue(diagnostics.signals().stream().anyMatch(s -> s.startsWith("resolvedMode=")));
            var budget = runtime.lightingBudgetDiagnostics();
            assertTrue(budget.available());
            assertTrue(budget.envelopeBreached());
            assertTrue(budget.loadRatio() > 1.0);
            var promotion = runtime.lightingPromotionDiagnostics();
            assertTrue(promotion.available());
            assertTrue(promotion.highStreak() >= 1);
            var emissive = runtime.lightingEmissiveDiagnostics();
            assertTrue(emissive.available());
            assertTrue(emissive.emissiveEnabled());
            assertTrue(emissive.envelopeBreached());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsPromotionReadyWhenBudgetIsStable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.lighting.localLightBudget", "8"),
                    Map.entry("vulkan.lighting.baselinePromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.lighting.budgetPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.lighting.physUnitsPromotionReadyMinFrames", "1")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validSceneStableBudget());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_BASELINE_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_BUDGET_PROMOTION_READY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_PHYS_UNITS_PROMOTION_READY".equals(w.code())));
            assertTrue(runtime.lightingPromotionDiagnostics().baselinePromotionReady());
            assertTrue(runtime.lightingPromotionDiagnostics().promotionReady());
            assertTrue(runtime.lightingPromotionDiagnostics().physUnitsPromotionReady());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsEmissivePromotionReadyWhenEmissivePolicyIsStable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.lighting.emissiveMeshEnabled", "true"),
                    Map.entry("vulkan.lighting.emissiveWarnMinCandidateRatio", "0.20"),
                    Map.entry("vulkan.lighting.emissivePromotionReadyMinFrames", "1")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validSceneStableEmissivePolicy());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_EMISSIVE_PROMOTION_READY".equals(w.code())));
            assertTrue(runtime.lightingPromotionDiagnostics().emissivePromotionReady());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsPhase2PromotionReadyWhenBudgetPhysAndEmissiveAreStable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.lighting.localLightBudget", "8"),
                    Map.entry("vulkan.lighting.budgetPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.lighting.physUnitsPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.lighting.emissiveMeshEnabled", "true"),
                    Map.entry("vulkan.lighting.emissiveWarnMinCandidateRatio", "0.20"),
                    Map.entry("vulkan.lighting.emissivePromotionReadyMinFrames", "1")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validSceneStableEmissivePolicy());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_PHASE2_PROMOTION_READY".equals(w.code())));
            assertTrue(runtime.lightingPromotionDiagnostics().phase2PromotionReady());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsAdvancedPromotionReadyWhenAdvancedPolicyIsStable() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.lighting.areaApproxEnabled", "true"),
                    Map.entry("vulkan.lighting.iesProfilesEnabled", "true"),
                    Map.entry("vulkan.lighting.cookiesEnabled", "true"),
                    Map.entry("vulkan.lighting.volumetricShaftsEnabled", "true"),
                    Map.entry("vulkan.lighting.clusteringEnabled", "true"),
                    Map.entry("vulkan.lighting.lightLayersEnabled", "true"),
                    Map.entry("vulkan.lighting.advancedFeaturePromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.lighting.advancedPromotionReadyMinFrames", "1")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validSceneStableBudget());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_PROMOTION_READY".equals(w.code())));
            assertTrue(runtime.lightingPromotionDiagnostics().advancedPromotionReady());
            var advanced = runtime.lightingAdvancedDiagnostics();
            assertTrue(advanced.available());
            assertTrue(advanced.expectedAdvancedCapabilityCount() >= 1);
            assertEquals(advanced.expectedAdvancedCapabilityCount(), advanced.activeAdvancedCapabilityCount());
            assertFalse(advanced.advancedEnvelopeBreached());
            assertTrue(advanced.expectedFeatures().contains("area_approx"));
            assertTrue(advanced.expectedFeatures().contains("ies_profiles"));
            assertTrue(advanced.expectedFeatures().contains("cookies"));
            assertTrue(advanced.expectedFeatures().contains("volumetric_shafts"));
            assertTrue(advanced.expectedFeatures().contains("clustering"));
            assertTrue(advanced.expectedFeatures().contains("light_layers"));
            assertTrue(advanced.activeFeatures().contains("area_approx"));
            assertTrue(advanced.promotionReadyFeatures().contains("area_approx"));
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsAdvancedRequiredUnavailableBreachWhenStrictRequiredPathIsMissing() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.lighting.areaApproxEnabled", "true"),
                    Map.entry("vulkan.lighting.advancedRequireActive", "true"),
                    Map.entry("vulkan.lighting.advancedRequireMinFrames", "1"),
                    Map.entry("vulkan.lighting.advancedRequireCooldownFrames", "0"),
                    Map.entry("vulkan.lighting.advancedEnvelopeWarnMinFrames", "1"),
                    Map.entry("vulkan.lighting.advancedEnvelopeCooldownFrames", "0")
            ), QualityTier.MEDIUM), new NoopCallbacks());
            runtime.loadScene(validSceneStableBudget());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_REQUIRED_PATH_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_ENVELOPE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_REQUIRED_UNAVAILABLE_BREACH".equals(w.code())));
            var advanced = runtime.lightingAdvancedDiagnostics();
            assertTrue(advanced.available());
            assertTrue(advanced.advancedRequireActive());
            assertEquals(1, advanced.requiredAdvancedCapabilityCount());
            assertEquals(0, advanced.activeAdvancedCapabilityCount());
            assertTrue(advanced.advancedRequiredUnavailableBreached());
            assertTrue(advanced.advancedEnvelopeBreached());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void doesNotEmitAdvancedRequiredUnavailableBreachWhenStrictRequiredPathDisabled() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.lighting.areaApproxEnabled", "true"),
                    Map.entry("vulkan.lighting.advancedRequireActive", "false"),
                    Map.entry("vulkan.lighting.advancedRequireMinFrames", "1"),
                    Map.entry("vulkan.lighting.advancedRequireCooldownFrames", "0"),
                    Map.entry("vulkan.lighting.advancedEnvelopeWarnMinFrames", "1"),
                    Map.entry("vulkan.lighting.advancedEnvelopeCooldownFrames", "0")
            ), QualityTier.MEDIUM), new NoopCallbacks());
            runtime.loadScene(validSceneStableBudget());
            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_REQUIRED_PATH_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_ENVELOPE_BREACH".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_REQUIRED_UNAVAILABLE_BREACH".equals(w.code())));
            var advanced = runtime.lightingAdvancedDiagnostics();
            assertTrue(advanced.available());
            assertFalse(advanced.advancedRequireActive());
            assertFalse(advanced.advancedRequiredUnavailableBreached());
            assertTrue(advanced.advancedEnvelopeBreached());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void advancedEnvelopeCooldownSuppressesRepeatedBreachWarnings() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.lighting.areaApproxEnabled", "true"),
                    Map.entry("vulkan.lighting.advancedEnvelopeWarnMinFrames", "1"),
                    Map.entry("vulkan.lighting.advancedEnvelopeCooldownFrames", "3")
            ), QualityTier.MEDIUM), new NoopCallbacks());
            runtime.loadScene(validSceneStableBudget());
            EngineFrameResult frame1 = runtime.render();
            EngineFrameResult frame2 = runtime.render();
            assertTrue(frame1.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_ENVELOPE_BREACH".equals(w.code())));
            assertFalse(frame2.warnings().stream().anyMatch(w -> "LIGHTING_ADVANCED_ENVELOPE_BREACH".equals(w.code())));
            var advanced = runtime.lightingAdvancedDiagnostics();
            assertTrue(advanced.available());
            assertTrue(advanced.advancedEnvelopeCooldownRemaining() >= 1);
            assertTrue(advanced.advancedEnvelopeMismatchStreak() >= 1);
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void appliesTierProfileDefaultsWhenOverridesAbsent() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.of(
                    "vulkan.mockContext", "true"
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validSceneStableBudget());
            runtime.render();
            var budget = runtime.lightingBudgetDiagnostics();
            assertTrue(budget.available());
            assertEquals(1.10, budget.warnRatioThreshold(), 1e-9);
            var promotion = runtime.lightingPromotionDiagnostics();
            assertTrue(promotion.available());
            assertEquals(2, promotion.warnMinFrames());
            assertEquals(90, promotion.warnCooldownFrames());
            assertEquals(3, promotion.baselinePromotionReadyMinFrames());
            assertEquals(5, promotion.promotionReadyMinFrames());
            assertEquals(5, promotion.physUnitsPromotionReadyMinFrames());
            assertEquals(6, promotion.emissivePromotionReadyMinFrames());
            assertEquals(4, promotion.advancedPromotionReadyMinFrames());
            var emissive = runtime.lightingEmissiveDiagnostics();
            assertTrue(emissive.available());
            assertEquals(0.08, emissive.warnMinCandidateRatio(), 1e-9);
            var advanced = runtime.lightingAdvancedDiagnostics();
            assertTrue(advanced.available());
            assertEquals(2, advanced.advancedEnvelopeWarnMinFrames());
            assertEquals(90, advanced.advancedEnvelopeCooldownFrames());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void backendOverridesTakePrecedenceOverTierDefaults() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.lighting.budgetWarnRatioThreshold", "2.25"),
                    Map.entry("vulkan.lighting.budgetWarnMinFrames", "7"),
                    Map.entry("vulkan.lighting.budgetWarnCooldownFrames", "33"),
                    Map.entry("vulkan.lighting.baselinePromotionReadyMinFrames", "6"),
                    Map.entry("vulkan.lighting.budgetPromotionReadyMinFrames", "11"),
                    Map.entry("vulkan.lighting.physUnitsPromotionReadyMinFrames", "9"),
                    Map.entry("vulkan.lighting.emissivePromotionReadyMinFrames", "13"),
                    Map.entry("vulkan.lighting.advancedPromotionReadyMinFrames", "17"),
                    Map.entry("vulkan.lighting.advancedEnvelopeWarnMinFrames", "9"),
                    Map.entry("vulkan.lighting.advancedEnvelopeCooldownFrames", "44"),
                    Map.entry("vulkan.lighting.emissiveWarnMinCandidateRatio", "0.20")
            ), QualityTier.HIGH), new NoopCallbacks());
            runtime.loadScene(validSceneStableBudget());
            runtime.render();
            var budget = runtime.lightingBudgetDiagnostics();
            assertTrue(budget.available());
            assertEquals(2.25, budget.warnRatioThreshold(), 1e-9);
            var promotion = runtime.lightingPromotionDiagnostics();
            assertTrue(promotion.available());
            assertEquals(7, promotion.warnMinFrames());
            assertEquals(33, promotion.warnCooldownFrames());
            assertEquals(6, promotion.baselinePromotionReadyMinFrames());
            assertEquals(11, promotion.promotionReadyMinFrames());
            assertEquals(9, promotion.physUnitsPromotionReadyMinFrames());
            assertEquals(13, promotion.emissivePromotionReadyMinFrames());
            assertEquals(17, promotion.advancedPromotionReadyMinFrames());
            var emissive = runtime.lightingEmissiveDiagnostics();
            assertTrue(emissive.available());
            assertEquals(0.20, emissive.warnMinCandidateRatio(), 1e-9);
            var advanced = runtime.lightingAdvancedDiagnostics();
            assertTrue(advanced.available());
            assertEquals(17, advanced.advancedPromotionReadyMinFrames());
            assertEquals(9, advanced.advancedEnvelopeWarnMinFrames());
            assertEquals(44, advanced.advancedEnvelopeCooldownFrames());
        } finally {
            runtime.shutdown();
        }
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier tier) {
        return new EngineConfig(
                "vulkan",
                "vulkan-lighting-capability-plan-test",
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
        LightDesc pointA = new LightDesc(
                "point-a",
                new Vec3(2, 2, 0),
                new Vec3(1, 0.8f, 0.7f),
                2.0f,
                10f,
                false,
                null,
                LightType.POINT,
                new Vec3(0, -1, 0),
                15f,
                30f
        );
        LightDesc pointB = new LightDesc(
                "point-b",
                new Vec3(-2, 2, 0),
                new Vec3(0.7f, 0.8f, 1f),
                2.0f,
                10f,
                false,
                null,
                LightType.POINT,
                new Vec3(0, -1, 0),
                15f,
                30f
        );
        LightDesc spot = new LightDesc(
                "spot",
                new Vec3(0, 3, 2),
                new Vec3(1, 1, 0.8f),
                1.5f,
                12f,
                false,
                null,
                LightType.SPOT,
                new Vec3(0, -1, -1),
                20f,
                35f
        );
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "lighting-plan-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(directional, pointA, pointB, spot),
                environment,
                fog,
                List.of(),
                null
        );
    }

    private static SceneDescriptor validSceneStableBudget() {
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
                "lighting-plan-scene-stable",
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

    private static SceneDescriptor validSceneStableEmissivePolicy() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc material = new MaterialDesc(
                "mat",
                new Vec3(1, 1, 1),
                0.0f,
                0.5f,
                null,
                null,
                null,
                null,
                0.0f,
                false,
                false,
                1.0f,
                1.0f,
                2.0f,
                org.dynamislight.api.scene.ReactivePreset.AUTO
        );
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
                "lighting-plan-scene-stable-emissive",
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
