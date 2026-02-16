package org.dynamislight.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.runtime.EngineFrameResult;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.input.KeyCode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.SceneLoadedEvent;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.spi.EngineBackendProvider;
import org.dynamislight.spi.registry.BackendRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class BackendParityIntegrationTest {
    private static final EngineApiVersion HOST_REQUIRED_API = new EngineApiVersion(1, 0, 0);

    @Test
    void bothBackendsAreDiscoverable() {
        BackendRegistry registry = BackendRegistry.discover();
        Set<String> backendIds = registry.providers().stream().map(EngineBackendProvider::backendId).collect(java.util.stream.Collectors.toSet());

        assertTrue(backendIds.contains("opengl"));
        assertTrue(backendIds.contains("vulkan"));
    }

    @Test
    void lifecycleContractParityForOpenGlAndVulkan() throws Exception {
        runParityLifecycle("opengl");
        runParityLifecycle("vulkan");
    }

    @Test
    void invalidStateAndArgumentErrorsAreConsistentAcrossBackends() throws Exception {
        assertContractErrors("opengl");
        assertContractErrors("vulkan");
    }

    @Test
    void materialAndLightingSceneProducesParitySignalsAcrossBackends() throws Exception {
        SceneDescriptor scene = materialLightingScene();
        BackendRunResult openGl = runSceneAndCollect("opengl", scene);
        BackendRunResult vulkan = runSceneAndCollect("vulkan", scene);

        assertEquals(openGl.drawCalls(), vulkan.drawCalls());
        assertEquals(openGl.visibleObjects(), vulkan.visibleObjects());
        assertTrue(openGl.triangles() > 0);
        assertTrue(vulkan.triangles() > 0);
        assertTrue(openGl.warningCodes().contains("FEATURE_BASELINE"));
        assertTrue(vulkan.warningCodes().contains("FEATURE_BASELINE"));
    }

    @Test
    void repeatedResizeIsStableAcrossBackends() throws Exception {
        assertResizeStability("opengl");
        assertResizeStability("vulkan");
    }

    @Test
    void qualityTierWarningsAreConsistentAcrossBackends() throws Exception {
        SceneDescriptor scene = fogSmokeScene();

        Set<String> openGlLow = renderWarningCodes("opengl", scene, QualityTier.LOW);
        Set<String> vulkanLow = renderWarningCodes("vulkan", scene, QualityTier.LOW);
        assertTrue(openGlLow.contains("FOG_QUALITY_DEGRADED"));
        assertTrue(openGlLow.contains("SMOKE_QUALITY_DEGRADED"));
        assertTrue(vulkanLow.contains("FOG_QUALITY_DEGRADED"));
        assertTrue(vulkanLow.contains("SMOKE_QUALITY_DEGRADED"));

        Set<String> openGlHigh = renderWarningCodes("opengl", scene, QualityTier.HIGH);
        Set<String> vulkanHigh = renderWarningCodes("vulkan", scene, QualityTier.HIGH);
        assertFalse(openGlHigh.contains("FOG_QUALITY_DEGRADED"));
        assertFalse(openGlHigh.contains("SMOKE_QUALITY_DEGRADED"));
        assertFalse(vulkanHigh.contains("FOG_QUALITY_DEGRADED"));
        assertFalse(vulkanHigh.contains("SMOKE_QUALITY_DEGRADED"));
    }

    @Test
    void shadowQualityWarningsAreConsistentAcrossBackends() throws Exception {
        SceneDescriptor scene = shadowScene();

        Set<String> openGlLow = renderWarningCodes("opengl", scene, QualityTier.LOW);
        Set<String> vulkanLow = renderWarningCodes("vulkan", scene, QualityTier.LOW);
        assertTrue(openGlLow.contains("SHADOW_QUALITY_DEGRADED"));
        assertTrue(vulkanLow.contains("SHADOW_QUALITY_DEGRADED"));

        Set<String> openGlHigh = renderWarningCodes("opengl", scene, QualityTier.HIGH);
        Set<String> vulkanHigh = renderWarningCodes("vulkan", scene, QualityTier.HIGH);
        assertFalse(openGlHigh.contains("SHADOW_QUALITY_DEGRADED"));
        assertFalse(vulkanHigh.contains("SHADOW_QUALITY_DEGRADED"));
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessProducesImagesWithBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("material-lighting");
        var report = BackendCompareHarness.run(outDir, materialLightingScene(), QualityTier.MEDIUM, "material-lighting-medium");

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.30, "diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessShadowSceneHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("shadow-single");
        var report = BackendCompareHarness.run(outDir, shadowScene(), QualityTier.HIGH, "shadow-high");

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.33, "shadow diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessShadowCascadeStressHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("shadow-cascade-stress");
        var report = BackendCompareHarness.run(outDir, shadowCascadeStressScene(), QualityTier.ULTRA, "shadow-cascade-stress-ultra");

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.25, "shadow cascade stress diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessFogShadowStressHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("fog-shadow-cascade-stress");
        var report = BackendCompareHarness.run(outDir, fogShadowCascadeStressScene(), QualityTier.ULTRA, "fog-shadow-cascade-stress-ultra");

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.25, "fog+shadow cascade stress diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessSmokeShadowStressHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("smoke-shadow-cascade-stress");
        var report = BackendCompareHarness.run(
                outDir,
                smokeShadowCascadeStressScene(),
                QualityTier.ULTRA,
                "smoke-shadow-cascade-stress-ultra"
        );

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.25, "smoke+shadow cascade stress diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessTextureHeavySceneHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("texture-heavy");
        var report = BackendCompareHarness.run(
                outDir,
                textureHeavyScene(),
                QualityTier.ULTRA,
                "texture-heavy-ultra"
        );

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.32, "texture-heavy diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessBrdfTierExtremeSceneHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("brdf-tier-extremes");
        var report = BackendCompareHarness.run(
                outDir,
                brdfTierExtremesScene(),
                QualityTier.ULTRA,
                "brdf-tier-extremes-ultra"
        );

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.29, "brdf-tier-extremes diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessPostProcessSceneHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("post-process");
        var report = BackendCompareHarness.run(
                outDir,
                postProcessScene(false),
                QualityTier.HIGH,
                "post-process-high"
        );

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.vulkanSnapshot().warningCodes().contains("VULKAN_POST_PROCESS_PIPELINE"));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.33, "post-process diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessPostProcessBloomSceneHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("post-process-bloom");
        var report = BackendCompareHarness.run(
                outDir,
                postProcessScene(true),
                QualityTier.HIGH,
                "post-process-bloom-high"
        );

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.vulkanSnapshot().warningCodes().contains("VULKAN_POST_PROCESS_PIPELINE"));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.06, "post-process bloom diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessFogSmokeShadowPostStressHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("fog-smoke-shadow-post-stress");
        var report = BackendCompareHarness.run(
                outDir,
                fogSmokeShadowPostStressScene(),
                QualityTier.ULTRA,
                "fog-smoke-shadow-post-stress-ultra"
        );

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.vulkanSnapshot().warningCodes().contains("VULKAN_POST_PROCESS_PIPELINE"));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.05, "fog+smoke+shadow+post stress diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessMaterialFogSmokeShadowStressHasBoundedDiff() throws Exception {
        Path outDir = compareOutputDir("material-fog-smoke-shadow-cascade-stress");
        var report = BackendCompareHarness.run(
                outDir,
                materialFogSmokeShadowCascadeStressScene(),
                QualityTier.ULTRA,
                "material-fog-smoke-shadow-cascade-stress-ultra"
        );

        assertTrue(Files.exists(report.openGlImage()));
        assertTrue(Files.exists(report.vulkanImage()));
        assertTrue(report.diffMetric() >= 0.0);
        assertTrue(report.diffMetric() <= 0.30, "material+fog+smoke+shadow stress diff was " + report.diffMetric());
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessTieredGoldenProfilesStayBounded() throws Exception {
        Map<QualityTier, Double> fogSmokeMaxDiff = Map.of(
                QualityTier.LOW, 0.45,
                QualityTier.MEDIUM, 0.35,
                QualityTier.HIGH, 0.29,
                QualityTier.ULTRA, 0.29
        );
        Map<QualityTier, Double> shadowMaxDiff = Map.of(
                QualityTier.LOW, 0.50,
                QualityTier.MEDIUM, 0.40,
                QualityTier.HIGH, 0.33,
                QualityTier.ULTRA, 0.33
        );
        Map<QualityTier, Double> textureHeavyMaxDiff = Map.of(
                QualityTier.LOW, 0.52,
                QualityTier.MEDIUM, 0.42,
                QualityTier.HIGH, 0.34,
                QualityTier.ULTRA, 0.32
        );
        Map<QualityTier, Double> brdfTierExtremeMaxDiff = Map.of(
                QualityTier.LOW, 0.55,
                QualityTier.MEDIUM, 0.45,
                QualityTier.HIGH, 0.35,
                QualityTier.ULTRA, 0.29
        );
        Map<QualityTier, Double> postProcessMaxDiff = Map.of(
                QualityTier.LOW, 0.42,
                QualityTier.MEDIUM, 0.37,
                QualityTier.HIGH, 0.32,
                QualityTier.ULTRA, 0.32
        );
        Map<QualityTier, Double> postProcessBloomMaxDiff = Map.of(
                QualityTier.LOW, 0.45,
                QualityTier.MEDIUM, 0.40,
                QualityTier.HIGH, 0.36,
                QualityTier.ULTRA, 0.37
        );
        Map<QualityTier, Double> materialFogSmokeShadowMaxDiff = Map.of(
                QualityTier.LOW, 0.54,
                QualityTier.MEDIUM, 0.44,
                QualityTier.HIGH, 0.34,
                QualityTier.ULTRA, 0.30
        );

        for (QualityTier tier : QualityTier.values()) {
            Path fogSmokeDir = compareOutputDir("fog-smoke-" + tier.name().toLowerCase());
            var fogSmokeReport = BackendCompareHarness.run(
                    fogSmokeDir,
                    fogSmokeScene(),
                    tier,
                    "fog-smoke-" + tier.name().toLowerCase()
            );
            assertTrue(
                    fogSmokeReport.diffMetric() <= fogSmokeMaxDiff.get(tier),
                    "fog/smoke diff " + fogSmokeReport.diffMetric() + " exceeded " + fogSmokeMaxDiff.get(tier) + " at " + tier
            );

            Path shadowDir = compareOutputDir("shadow-" + tier.name().toLowerCase());
            var shadowReport = BackendCompareHarness.run(
                    shadowDir,
                    shadowScene(),
                    tier,
                    "shadow-" + tier.name().toLowerCase()
            );
            assertTrue(
                    shadowReport.diffMetric() <= shadowMaxDiff.get(tier),
                    "shadow diff " + shadowReport.diffMetric() + " exceeded " + shadowMaxDiff.get(tier) + " at " + tier
            );

            Path textureHeavyDir = compareOutputDir("texture-heavy-" + tier.name().toLowerCase());
            var textureHeavyReport = BackendCompareHarness.run(
                    textureHeavyDir,
                    textureHeavyScene(),
                    tier,
                    "texture-heavy-" + tier.name().toLowerCase()
            );
            assertTrue(
                    textureHeavyReport.diffMetric() <= textureHeavyMaxDiff.get(tier),
                    "texture-heavy diff " + textureHeavyReport.diffMetric()
                            + " exceeded " + textureHeavyMaxDiff.get(tier) + " at " + tier
            );

            Path brdfTierExtremeDir = compareOutputDir("brdf-tier-extremes-" + tier.name().toLowerCase());
            var brdfTierExtremeReport = BackendCompareHarness.run(
                    brdfTierExtremeDir,
                    brdfTierExtremesScene(),
                    tier,
                    "brdf-tier-extremes-" + tier.name().toLowerCase()
            );
            assertTrue(
                    brdfTierExtremeReport.diffMetric() <= brdfTierExtremeMaxDiff.get(tier),
                    "brdf-tier-extremes diff " + brdfTierExtremeReport.diffMetric()
                            + " exceeded " + brdfTierExtremeMaxDiff.get(tier) + " at " + tier
            );

            Path postDir = compareOutputDir("post-process-" + tier.name().toLowerCase());
            var postReport = BackendCompareHarness.run(
                    postDir,
                    postProcessScene(false),
                    tier,
                    "post-process-" + tier.name().toLowerCase()
            );
            assertTrue(
                    postReport.diffMetric() <= postProcessMaxDiff.get(tier),
                    "post-process diff " + postReport.diffMetric()
                            + " exceeded " + postProcessMaxDiff.get(tier) + " at " + tier
            );

            Path postBloomDir = compareOutputDir("post-process-bloom-" + tier.name().toLowerCase());
            var postBloomReport = BackendCompareHarness.run(
                    postBloomDir,
                    postProcessScene(true),
                    tier,
                    "post-process-bloom-" + tier.name().toLowerCase()
            );
            assertTrue(
                    postBloomReport.diffMetric() <= postProcessBloomMaxDiff.get(tier),
                    "post-process bloom diff " + postBloomReport.diffMetric()
                            + " exceeded " + postProcessBloomMaxDiff.get(tier) + " at " + tier
            );

            Path materialFogSmokeShadowDir = compareOutputDir("material-fog-smoke-shadow-" + tier.name().toLowerCase());
            var materialFogSmokeShadowReport = BackendCompareHarness.run(
                    materialFogSmokeShadowDir,
                    materialFogSmokeShadowCascadeStressScene(),
                    tier,
                    "material-fog-smoke-shadow-" + tier.name().toLowerCase()
            );
            assertTrue(
                    materialFogSmokeShadowReport.diffMetric() <= materialFogSmokeShadowMaxDiff.get(tier),
                    "material-fog-smoke-shadow diff " + materialFogSmokeShadowReport.diffMetric()
                            + " exceeded " + materialFogSmokeShadowMaxDiff.get(tier) + " at " + tier
            );
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "dle.compare.tests", matches = "true")
    void compareHarnessStressGoldenProfilesStayBounded() throws Exception {
        Map<String, Double> stressMaxDiff = Map.of(
                "shadow-cascade-stress", 0.25,
                "fog-shadow-cascade-stress", 0.25,
                "smoke-shadow-cascade-stress", 0.25,
                "texture-heavy", 0.32,
                "brdf-tier-extremes", 0.29,
                "fog-smoke-shadow-post-stress", 0.05,
                "material-fog-smoke-shadow-cascade-stress", 0.30
        );

        var reports = Map.of(
                "shadow-cascade-stress", BackendCompareHarness.run(
                        compareOutputDir("shadow-cascade-stress-golden"),
                        shadowCascadeStressScene(),
                        QualityTier.ULTRA,
                        "shadow-cascade-stress-golden-ultra"
                ),
                "fog-shadow-cascade-stress", BackendCompareHarness.run(
                        compareOutputDir("fog-shadow-cascade-stress-golden"),
                        fogShadowCascadeStressScene(),
                        QualityTier.ULTRA,
                        "fog-shadow-cascade-stress-golden-ultra"
                ),
                "smoke-shadow-cascade-stress", BackendCompareHarness.run(
                        compareOutputDir("smoke-shadow-cascade-stress-golden"),
                        smokeShadowCascadeStressScene(),
                        QualityTier.ULTRA,
                        "smoke-shadow-cascade-stress-golden-ultra"
                ),
                "texture-heavy", BackendCompareHarness.run(
                        compareOutputDir("texture-heavy-golden"),
                        textureHeavyScene(),
                        QualityTier.ULTRA,
                        "texture-heavy-golden-ultra"
                ),
                "brdf-tier-extremes", BackendCompareHarness.run(
                        compareOutputDir("brdf-tier-extremes-golden"),
                        brdfTierExtremesScene(),
                        QualityTier.ULTRA,
                        "brdf-tier-extremes-golden-ultra"
                ),
                "fog-smoke-shadow-post-stress", BackendCompareHarness.run(
                        compareOutputDir("fog-smoke-shadow-post-stress-golden"),
                        fogSmokeShadowPostStressScene(),
                        QualityTier.ULTRA,
                        "fog-smoke-shadow-post-stress-golden-ultra"
                ),
                "material-fog-smoke-shadow-cascade-stress", BackendCompareHarness.run(
                        compareOutputDir("material-fog-smoke-shadow-cascade-stress-golden"),
                        materialFogSmokeShadowCascadeStressScene(),
                        QualityTier.ULTRA,
                        "material-fog-smoke-shadow-cascade-stress-golden-ultra"
                )
        );

        reports.forEach((profile, report) -> assertTrue(
                report.diffMetric() <= stressMaxDiff.get(profile),
                profile + " diff " + report.diffMetric() + " exceeded " + stressMaxDiff.get(profile)
        ));
    }

    private static Path compareOutputDir(String label) throws Exception {
        String base = System.getProperty("dle.compare.outputDir", "").trim();
        if (base.isEmpty()) {
            return Files.createTempDirectory("dle-compare-" + label);
        }
        Path dir = Path.of(base, label);
        Files.createDirectories(dir);
        return dir;
    }

    private static void runParityLifecycle(String backendId) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        RecordingCallbacks callbacks = new RecordingCallbacks();

        try (var runtime = provider.createRuntime()) {
            runtime.initialize(validConfig(backendId), callbacks);
            runtime.loadScene(validScene());

            EngineFrameResult update = runtime.update(1.0 / 60.0, emptyInput());
            EngineFrameResult render1 = runtime.render();
            EngineFrameResult render2 = runtime.render();

            assertNotNull(update);
            assertEquals(1L, render1.frameIndex());
            assertEquals(2L, render2.frameIndex());
            assertTrue(render1.cpuFrameMs() >= 0.0);
            assertTrue(render1.gpuFrameMs() >= 0.0);
            assertFalse(render1.warnings().isEmpty());

            runtime.resize(1024, 768, 1.0f);
            runtime.shutdown();
            runtime.shutdown();
        }

        assertFalse(callbacks.logs.isEmpty(), "expected runtime logs for " + backendId);
        assertTrue(callbacks.events.stream().anyMatch(SceneLoadedEvent.class::isInstance),
                "expected SceneLoadedEvent for " + backendId);
        Set<String> categories = callbacks.logs.stream().map(LogMessage::category).collect(Collectors.toSet());
        assertTrue(categories.contains("LIFECYCLE"), "missing LIFECYCLE logs for " + backendId);
        assertTrue(categories.contains("SCENE"), "missing SCENE logs for " + backendId);
        assertTrue(categories.contains("SHADER"), "missing SHADER logs for " + backendId);
        assertTrue(categories.contains("RENDER"), "missing RENDER logs for " + backendId);
        assertTrue(categories.contains("PERF"), "missing PERF logs for " + backendId);
    }

    private static void assertContractErrors(String backendId) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);

        try (var runtime = provider.createRuntime()) {
            EngineException renderBeforeInit = assertThrows(EngineException.class, runtime::render);
            assertEquals(EngineErrorCode.INVALID_STATE, renderBeforeInit.code());

            runtime.initialize(validConfig(backendId), new RecordingCallbacks());

            EngineException badResize = assertThrows(EngineException.class, () -> runtime.resize(0, 720, 1.0f));
            assertEquals(EngineErrorCode.INVALID_ARGUMENT, badResize.code());

            EngineException badUpdate = assertThrows(EngineException.class, () -> runtime.update(-0.1, emptyInput()));
            assertEquals(EngineErrorCode.INVALID_ARGUMENT, badUpdate.code());
        }
    }

    private static BackendRunResult runSceneAndCollect(String backendId, SceneDescriptor scene) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        try (var runtime = provider.createRuntime()) {
            runtime.initialize(validConfig(backendId), callbacks);
            runtime.loadScene(scene);
            EngineFrameResult frame = runtime.render();
            return new BackendRunResult(
                    runtime.getStats().drawCalls(),
                    runtime.getStats().triangles(),
                    runtime.getStats().visibleObjects(),
                    frame.warnings().stream().map(w -> w.code()).collect(Collectors.toSet())
            );
        }
    }

    private static void assertResizeStability(String backendId) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        try (var runtime = provider.createRuntime()) {
            runtime.initialize(validConfig(backendId), new RecordingCallbacks());
            runtime.loadScene(validScene());
            for (int i = 0; i < 12; i++) {
                int width = 800 + (i * 37);
                int height = 600 + (i * 23);
                runtime.resize(width, height, 1.0f);
                runtime.render();
            }
            assertTrue(runtime.getStats().fps() >= 0.0);
        }
    }

    private static Set<String> renderWarningCodes(String backendId, SceneDescriptor scene, QualityTier qualityTier) throws Exception {
        EngineBackendProvider provider = BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
        try (var runtime = provider.createRuntime()) {
            runtime.initialize(validConfig(backendId, qualityTier), new RecordingCallbacks());
            runtime.loadScene(scene);
            return runtime.render().warnings().stream().map(w -> w.code()).collect(Collectors.toSet());
        }
    }

    private static EngineConfig validConfig(String backendId) {
        return validConfig(backendId, QualityTier.MEDIUM);
    }

    private static EngineConfig validConfig(String backendId, QualityTier qualityTier) {
        Map<String, String> options = "opengl".equalsIgnoreCase(backendId)
                ? Map.of("opengl.mockContext", "true")
                : Map.of();

        return new EngineConfig(
                backendId,
                "backend-parity-test",
                1280,
                720,
                1.0f,
                true,
                60,
                qualityTier,
                Path.of(".."),
                options
        );
    }

    private static SceneDescriptor validScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "parity-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }

    private static SceneDescriptor materialLightingScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 6), new Vec3(0, 0, 0), 65f, 0.1f, 150f);
        TransformDesc a = new TransformDesc("xform-a", new Vec3(-0.8f, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        TransformDesc b = new TransformDesc("xform-b", new Vec3(0.8f, 0.1f, 0), new Vec3(0, 20, 0), new Vec3(1, 1, 1));
        MeshDesc triangle = new MeshDesc("mesh-a", "xform-a", "mat-a", "meshes/triangle.gltf");
        MeshDesc quad = new MeshDesc("mesh-b", "xform-b", "mat-b", "meshes/quad.gltf");
        MaterialDesc matA = new MaterialDesc("mat-a", new Vec3(0.95f, 0.3f, 0.25f), 0.15f, 0.55f, "textures/a.png", "textures/a_n.png");
        MaterialDesc matB = new MaterialDesc("mat-b", new Vec3(0.25f, 0.65f, 0.95f), 0.65f, 0.35f, "textures/b.png", "textures/b_n.png");
        LightDesc key = new LightDesc("key", new Vec3(1.2f, 2.0f, 1.8f), new Vec3(1f, 0.96f, 0.9f), 1.0f, 15f, false, null);
        LightDesc fill = new LightDesc("fill", new Vec3(-1.4f, 1.2f, 1.0f), new Vec3(0.35f, 0.55f, 1f), 0.7f, 12f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.08f, 0.1f, 0.12f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "parity-material-lighting-scene",
                List.of(camera),
                "cam",
                List.of(a, b),
                List.of(triangle, quad),
                List.of(matA, matB),
                List.of(key, fill),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor fogSmokeScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "meshes/quad.gltf");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.1f, 0.6f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 1), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(true, FogMode.EXPONENTIAL, new Vec3(0.5f, 0.55f, 0.6f), 0.35f, 0f, 0f, 0f, 0f, 0f);
        SmokeEmitterDesc smoke = new SmokeEmitterDesc(
                "smoke",
                new Vec3(0f, 0f, 0f),
                new Vec3(1f, 1f, 1f),
                10f,
                0.8f,
                new Vec3(0.65f, 0.65f, 0.68f),
                0.1f,
                new Vec3(0f, 0.1f, 0f),
                0.2f,
                2.0f,
                true
        );

        return new SceneDescriptor(
                "parity-fog-smoke-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.of(smoke)
        );
    }

    private static SceneDescriptor shadowScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "meshes/quad.gltf");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.1f, 0.6f, null, null);
        LightDesc light = new LightDesc("shadow-light", new Vec3(0, 2, 1), new Vec3(1, 1, 1), 1.0f, 10f, true,
                new ShadowDesc(1024, 0.0008f, 3, 2));
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.55f, 0.6f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "parity-shadow-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor shadowCascadeStressScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0.2f, 1.2f, 8.5f), new Vec3(-6f, 12f, 0f), 72f, 0.1f, 180f);
        TransformDesc near = new TransformDesc("xform-near", new Vec3(-1.2f, -0.1f, 1.0f), new Vec3(0, 20, 0), new Vec3(1, 1, 1));
        TransformDesc mid = new TransformDesc("xform-mid", new Vec3(0.3f, -0.2f, -9.0f), new Vec3(0, -15, 0), new Vec3(1.2f, 1.2f, 1.2f));
        TransformDesc far = new TransformDesc("xform-far", new Vec3(1.8f, -0.35f, -36.0f), new Vec3(0, 5, 0), new Vec3(2.2f, 2.2f, 2.2f));
        TransformDesc ground = new TransformDesc("xform-ground", new Vec3(0f, -1.1f, -15f), new Vec3(0, 0, 0), new Vec3(6.5f, 1f, 6.5f));

        MeshDesc meshNear = new MeshDesc("mesh-near", "xform-near", "mat-near", "meshes/quad.gltf");
        MeshDesc meshMid = new MeshDesc("mesh-mid", "xform-mid", "mat-mid", "meshes/quad.gltf");
        MeshDesc meshFar = new MeshDesc("mesh-far", "xform-far", "mat-far", "meshes/quad.gltf");
        MeshDesc meshGround = new MeshDesc("mesh-ground", "xform-ground", "mat-ground", "meshes/quad.gltf");

        MaterialDesc matNear = new MaterialDesc("mat-near", new Vec3(0.95f, 0.55f, 0.45f), 0.2f, 0.45f, null, null);
        MaterialDesc matMid = new MaterialDesc("mat-mid", new Vec3(0.55f, 0.85f, 0.95f), 0.35f, 0.5f, null, null);
        MaterialDesc matFar = new MaterialDesc("mat-far", new Vec3(0.75f, 0.75f, 0.78f), 0.1f, 0.75f, null, null);
        MaterialDesc matGround = new MaterialDesc("mat-ground", new Vec3(0.62f, 0.64f, 0.58f), 0.0f, 0.9f, null, null);

        LightDesc shadowLight = new LightDesc(
                "shadow-light",
                new Vec3(7f, 18f, 6f),
                new Vec3(1f, 0.98f, 0.95f),
                1.1f,
                220f,
                true,
                new ShadowDesc(2048, 0.0006f, 5, 4)
        );
        LightDesc fill = new LightDesc("fill", new Vec3(-4f, 6f, -3f), new Vec3(0.35f, 0.45f, 0.65f), 0.45f, 80f, false, null);

        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.09f, 0.10f, 0.12f), 0.18f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.55f, 0.6f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "parity-shadow-cascade-stress-scene",
                List.of(camera),
                "cam",
                List.of(near, mid, far, ground),
                List.of(meshNear, meshMid, meshFar, meshGround),
                List.of(matNear, matMid, matFar, matGround),
                List.of(shadowLight, fill),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor textureHeavyScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0.3f, 0.7f, 7.2f), new Vec3(0f, 0f, -6f), 68f, 0.1f, 180f);
        TransformDesc near = new TransformDesc("xform-near", new Vec3(-1.4f, -0.1f, 1.2f), new Vec3(0, 18, 0), new Vec3(1, 1, 1));
        TransformDesc mid = new TransformDesc("xform-mid", new Vec3(0.1f, -0.2f, -8.5f), new Vec3(0, -20, 0), new Vec3(1.4f, 1.4f, 1.4f));
        TransformDesc far = new TransformDesc("xform-far", new Vec3(1.8f, -0.4f, -33.0f), new Vec3(0, 6, 0), new Vec3(2.2f, 2.2f, 2.2f));
        TransformDesc ground = new TransformDesc("xform-ground", new Vec3(0f, -1.2f, -14f), new Vec3(0, 0, 0), new Vec3(7.6f, 1f, 7.6f));

        MeshDesc meshNear = new MeshDesc("mesh-near", "xform-near", "mat-near", "meshes/quad.gltf");
        MeshDesc meshMid = new MeshDesc("mesh-mid", "xform-mid", "mat-mid", "meshes/quad.gltf");
        MeshDesc meshFar = new MeshDesc("mesh-far", "xform-far", "mat-far", "meshes/quad.gltf");
        MeshDesc meshGround = new MeshDesc("mesh-ground", "xform-ground", "mat-ground", "meshes/quad.gltf");

        MaterialDesc matNear = new MaterialDesc(
                "mat-near",
                new Vec3(0.9f, 0.48f, 0.42f),
                0.28f,
                0.42f,
                "textures/a.png",
                "textures/a_n.png",
                "textures/a_mr.png",
                "textures/a_ao.png"
        );
        MaterialDesc matMid = new MaterialDesc(
                "mat-mid",
                new Vec3(0.45f, 0.8f, 0.95f),
                0.64f,
                0.34f,
                "textures/b.png",
                "textures/b_n.png",
                "textures/b_mr.png",
                "textures/b_ao.png"
        );
        MaterialDesc matFar = new MaterialDesc(
                "mat-far",
                new Vec3(0.76f, 0.78f, 0.82f),
                0.18f,
                0.74f,
                "textures/c.png",
                "textures/c_n.png",
                "textures/c_mr.png",
                "textures/c_ao.png"
        );
        MaterialDesc matGround = new MaterialDesc(
                "mat-ground",
                new Vec3(0.58f, 0.6f, 0.55f),
                0.08f,
                0.9f,
                "textures/d.png",
                "textures/d_n.png",
                "textures/d_mr.png",
                "textures/d_ao.png"
        );

        LightDesc shadowLight = new LightDesc(
                "shadow-light",
                new Vec3(7f, 18f, 8f),
                new Vec3(1f, 0.98f, 0.95f),
                1.12f,
                260f,
                true,
                new ShadowDesc(2048, 0.0006f, 5, 4)
        );
        LightDesc fill = new LightDesc("fill", new Vec3(-4f, 7f, -3f), new Vec3(0.32f, 0.45f, 0.68f), 0.5f, 95f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.08f, 0.09f, 0.11f), 0.18f, null);
        FogDesc fog = new FogDesc(true, FogMode.HEIGHT_EXPONENTIAL, new Vec3(0.50f, 0.54f, 0.6f), 0.3f, 0.35f, 0.68f, 0.08f, 0.95f, 0.18f);

        return new SceneDescriptor(
                "parity-texture-heavy-scene",
                List.of(camera),
                "cam",
                List.of(near, mid, far, ground),
                List.of(meshNear, meshMid, meshFar, meshGround),
                List.of(matNear, matMid, matFar, matGround),
                List.of(shadowLight, fill),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor brdfTierExtremesScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0f, 0f, 7f), new Vec3(0f, 0f, 0f), 58f, 0.1f, 150f);
        TransformDesc t1 = new TransformDesc("x1", new Vec3(-1.25f, 0f, 0f), new Vec3(0f, 0f, 0f), new Vec3(1f, 1f, 1f));
        TransformDesc t2 = new TransformDesc("x2", new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 0f), new Vec3(1f, 1f, 1f));
        TransformDesc t3 = new TransformDesc("x3", new Vec3(1.25f, 0f, 0f), new Vec3(0f, 0f, 0f), new Vec3(1f, 1f, 1f));
        MeshDesc m1 = new MeshDesc("m1", "x1", "mat-gloss", "meshes/quad.glb");
        MeshDesc m2 = new MeshDesc("m2", "x2", "mat-mid", "meshes/quad.glb");
        MeshDesc m3 = new MeshDesc("m3", "x3", "mat-rough", "meshes/quad.glb");
        MaterialDesc matGloss = new MaterialDesc(
                "mat-gloss",
                new Vec3(0.95f, 0.93f, 0.90f),
                1.0f,
                0.04f,
                "assets/textures/albedo.png",
                "assets/textures/normal.png"
        );
        MaterialDesc matMid = new MaterialDesc(
                "mat-mid",
                new Vec3(0.65f, 0.72f, 0.92f),
                0.55f,
                0.35f,
                "assets/textures/albedo.png",
                "assets/textures/normal.png"
        );
        MaterialDesc matRough = new MaterialDesc(
                "mat-rough",
                new Vec3(0.38f, 0.42f, 0.48f),
                0.05f,
                1.0f,
                "assets/textures/albedo.png",
                null
        );
        LightDesc light = new LightDesc(
                "sun",
                new Vec3(2.0f, 4.0f, 2.5f),
                new Vec3(1.0f, 0.98f, 0.95f),
                1.2f,
                22f,
                true,
                new ShadowDesc(2048, 0.0012f, 5, 3)
        );
        EnvironmentDesc env = new EnvironmentDesc(
                new Vec3(0.18f, 0.20f, 0.24f),
                0.35f,
                null,
                "assets/textures/albedo.png",
                "assets/textures/albedo.png",
                "assets/textures/albedo.png"
        );
        FogDesc fog = new FogDesc(true, FogMode.HEIGHT_EXPONENTIAL, new Vec3(0.55f, 0.58f, 0.62f), 0.08f, 0.0f, 0.65f, 0f, 0f, 0f);
        SmokeEmitterDesc smoke = new SmokeEmitterDesc(
                "smoke",
                new Vec3(0f, 0.3f, 0f),
                new Vec3(1.5f, 0.6f, 1.0f),
                12f,
                0.6f,
                new Vec3(0.55f, 0.57f, 0.60f),
                0.3f,
                new Vec3(0.0f, 0.3f, 0.0f),
                0.2f,
                2.5f,
                true
        );

        return new SceneDescriptor(
                "brdf-tier-extremes-scene",
                List.of(camera),
                "cam",
                List.of(t1, t2, t3),
                List.of(m1, m2, m3),
                List.of(matGloss, matMid, matRough),
                List.of(light),
                env,
                fog,
                List.of(smoke)
        );
    }

    private static SceneDescriptor postProcessScene(boolean bloomEnabled) {
        SceneDescriptor base = materialLightingScene();
        PostProcessDesc post = new PostProcessDesc(
                true,
                true,
                1.08f,
                2.2f,
                bloomEnabled,
                1.0f,
                0.75f
        );
        return new SceneDescriptor(
                "parity-post-process-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                post
        );
    }

    private static SceneDescriptor fogShadowCascadeStressScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0.1f, 1.4f, 8.8f), new Vec3(-8f, 10f, 0f), 74f, 0.1f, 220f);
        TransformDesc near = new TransformDesc("xform-near", new Vec3(-1.0f, -0.15f, 0.8f), new Vec3(0, 18, 0), new Vec3(1, 1, 1));
        TransformDesc mid = new TransformDesc("xform-mid", new Vec3(0.4f, -0.25f, -10.5f), new Vec3(0, -12, 0), new Vec3(1.3f, 1.3f, 1.3f));
        TransformDesc far = new TransformDesc("xform-far", new Vec3(2.0f, -0.45f, -42.0f), new Vec3(0, 8, 0), new Vec3(2.4f, 2.4f, 2.4f));
        TransformDesc ground = new TransformDesc("xform-ground", new Vec3(0f, -1.2f, -18f), new Vec3(0, 0, 0), new Vec3(7.0f, 1f, 7.0f));

        MeshDesc meshNear = new MeshDesc("mesh-near", "xform-near", "mat-near", "meshes/quad.gltf");
        MeshDesc meshMid = new MeshDesc("mesh-mid", "xform-mid", "mat-mid", "meshes/quad.gltf");
        MeshDesc meshFar = new MeshDesc("mesh-far", "xform-far", "mat-far", "meshes/quad.gltf");
        MeshDesc meshGround = new MeshDesc("mesh-ground", "xform-ground", "mat-ground", "meshes/quad.gltf");

        MaterialDesc matNear = new MaterialDesc("mat-near", new Vec3(0.92f, 0.52f, 0.42f), 0.18f, 0.46f, null, null);
        MaterialDesc matMid = new MaterialDesc("mat-mid", new Vec3(0.52f, 0.82f, 0.92f), 0.32f, 0.52f, null, null);
        MaterialDesc matFar = new MaterialDesc("mat-far", new Vec3(0.72f, 0.74f, 0.78f), 0.08f, 0.78f, null, null);
        MaterialDesc matGround = new MaterialDesc("mat-ground", new Vec3(0.60f, 0.62f, 0.56f), 0.0f, 0.92f, null, null);

        LightDesc shadowLight = new LightDesc(
                "shadow-light",
                new Vec3(8f, 20f, 7f),
                new Vec3(1f, 0.98f, 0.95f),
                1.1f,
                250f,
                true,
                new ShadowDesc(2048, 0.0006f, 5, 4)
        );
        LightDesc fill = new LightDesc("fill", new Vec3(-5f, 7f, -4f), new Vec3(0.32f, 0.45f, 0.66f), 0.5f, 90f, false, null);

        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.08f, 0.10f, 0.12f), 0.16f, null);
        FogDesc fog = new FogDesc(true, FogMode.HEIGHT_EXPONENTIAL, new Vec3(0.52f, 0.56f, 0.62f), 0.42f, 0.38f, 0.75f, 0.12f, 1.1f, 0.25f);

        return new SceneDescriptor(
                "parity-fog-shadow-cascade-stress-scene",
                List.of(camera),
                "cam",
                List.of(near, mid, far, ground),
                List.of(meshNear, meshMid, meshFar, meshGround),
                List.of(matNear, matMid, matFar, matGround),
                List.of(shadowLight, fill),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor smokeShadowCascadeStressScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0.0f, 1.6f, 8.9f), new Vec3(-7f, 11f, 0f), 73f, 0.1f, 220f);
        TransformDesc near = new TransformDesc("xform-near", new Vec3(-1.1f, -0.1f, 1.1f), new Vec3(0, 16, 0), new Vec3(1, 1, 1));
        TransformDesc mid = new TransformDesc("xform-mid", new Vec3(0.5f, -0.2f, -11.0f), new Vec3(0, -10, 0), new Vec3(1.35f, 1.35f, 1.35f));
        TransformDesc far = new TransformDesc("xform-far", new Vec3(2.1f, -0.4f, -41.0f), new Vec3(0, 10, 0), new Vec3(2.35f, 2.35f, 2.35f));
        TransformDesc ground = new TransformDesc("xform-ground", new Vec3(0f, -1.2f, -18f), new Vec3(0, 0, 0), new Vec3(7.2f, 1f, 7.2f));

        MeshDesc meshNear = new MeshDesc("mesh-near", "xform-near", "mat-near", "meshes/quad.gltf");
        MeshDesc meshMid = new MeshDesc("mesh-mid", "xform-mid", "mat-mid", "meshes/quad.gltf");
        MeshDesc meshFar = new MeshDesc("mesh-far", "xform-far", "mat-far", "meshes/quad.gltf");
        MeshDesc meshGround = new MeshDesc("mesh-ground", "xform-ground", "mat-ground", "meshes/quad.gltf");

        MaterialDesc matNear = new MaterialDesc("mat-near", new Vec3(0.92f, 0.54f, 0.44f), 0.18f, 0.44f, null, null);
        MaterialDesc matMid = new MaterialDesc("mat-mid", new Vec3(0.54f, 0.84f, 0.94f), 0.34f, 0.5f, null, null);
        MaterialDesc matFar = new MaterialDesc("mat-far", new Vec3(0.72f, 0.75f, 0.79f), 0.08f, 0.77f, null, null);
        MaterialDesc matGround = new MaterialDesc("mat-ground", new Vec3(0.61f, 0.63f, 0.57f), 0.0f, 0.92f, null, null);

        LightDesc shadowLight = new LightDesc(
                "shadow-light",
                new Vec3(8f, 20f, 8f),
                new Vec3(1f, 0.98f, 0.95f),
                1.1f,
                250f,
                true,
                new ShadowDesc(2048, 0.0006f, 5, 4)
        );
        LightDesc fill = new LightDesc("fill", new Vec3(-5f, 7f, -4f), new Vec3(0.34f, 0.46f, 0.68f), 0.48f, 90f, false, null);

        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.08f, 0.10f, 0.12f), 0.16f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.55f, 0.6f), 0f, 0f, 0f, 0f, 0f, 0f);
        SmokeEmitterDesc smokeA = new SmokeEmitterDesc(
                "smoke-a",
                new Vec3(-0.7f, -0.2f, -4.5f),
                new Vec3(2.0f, 1.2f, 2.0f),
                22f,
                0.9f,
                new Vec3(0.68f, 0.68f, 0.72f),
                0.18f,
                new Vec3(0.04f, 0.16f, -0.03f),
                0.3f,
                3.6f,
                true
        );
        SmokeEmitterDesc smokeB = new SmokeEmitterDesc(
                "smoke-b",
                new Vec3(1.1f, -0.25f, -17.0f),
                new Vec3(2.8f, 1.4f, 2.8f),
                19f,
                0.78f,
                new Vec3(0.62f, 0.64f, 0.66f),
                0.16f,
                new Vec3(-0.02f, 0.14f, 0.01f),
                0.26f,
                3.2f,
                true
        );

        return new SceneDescriptor(
                "parity-smoke-shadow-cascade-stress-scene",
                List.of(camera),
                "cam",
                List.of(near, mid, far, ground),
                List.of(meshNear, meshMid, meshFar, meshGround),
                List.of(matNear, matMid, matFar, matGround),
                List.of(shadowLight, fill),
                env,
                fog,
                List.of(smokeA, smokeB)
        );
    }

    private static SceneDescriptor fogSmokeShadowPostStressScene() {
        SceneDescriptor base = smokeShadowCascadeStressScene();
        FogDesc fog = new FogDesc(
                true,
                FogMode.HEIGHT_EXPONENTIAL,
                new Vec3(0.52f, 0.57f, 0.64f),
                0.44f,
                0.42f,
                0.78f,
                0.16f,
                1.2f,
                0.28f
        );
        PostProcessDesc post = new PostProcessDesc(
                true,
                true,
                1.1f,
                2.2f,
                true,
                0.95f,
                0.82f
        );
        return new SceneDescriptor(
                "parity-fog-smoke-shadow-post-stress-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                base.environment(),
                fog,
                base.smokeEmitters(),
                post
        );
    }

    private static SceneDescriptor materialFogSmokeShadowCascadeStressScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0.35f, 1.5f, 9.4f), new Vec3(-10f, 11f, 0f), 74f, 0.1f, 260f);
        TransformDesc near = new TransformDesc("xform-near", new Vec3(-1.2f, -0.15f, 1.2f), new Vec3(0, 20, 0), new Vec3(1f, 1f, 1f));
        TransformDesc mid = new TransformDesc("xform-mid", new Vec3(0.35f, -0.25f, -11.8f), new Vec3(0, -14, 0), new Vec3(1.5f, 1.5f, 1.5f));
        TransformDesc far = new TransformDesc("xform-far", new Vec3(2.25f, -0.45f, -46.0f), new Vec3(0, 10, 0), new Vec3(2.55f, 2.55f, 2.55f));
        TransformDesc ground = new TransformDesc("xform-ground", new Vec3(0f, -1.25f, -20f), new Vec3(0, 0, 0), new Vec3(8.0f, 1f, 8.0f));

        MeshDesc meshNear = new MeshDesc("mesh-near", "xform-near", "mat-near", "meshes/quad.gltf");
        MeshDesc meshMid = new MeshDesc("mesh-mid", "xform-mid", "mat-mid", "meshes/quad.gltf");
        MeshDesc meshFar = new MeshDesc("mesh-far", "xform-far", "mat-far", "meshes/quad.gltf");
        MeshDesc meshGround = new MeshDesc("mesh-ground", "xform-ground", "mat-ground", "meshes/quad.gltf");

        MaterialDesc matNear = new MaterialDesc(
                "mat-near",
                new Vec3(0.88f, 0.50f, 0.40f),
                0.26f,
                0.44f,
                "textures/a.png",
                "textures/a_n.png",
                "textures/a_mr.png",
                "textures/a_ao.png"
        );
        MaterialDesc matMid = new MaterialDesc(
                "mat-mid",
                new Vec3(0.48f, 0.80f, 0.94f),
                0.60f,
                0.36f,
                "textures/b.png",
                "textures/b_n.png",
                "textures/b_mr.png",
                "textures/b_ao.png"
        );
        MaterialDesc matFar = new MaterialDesc(
                "mat-far",
                new Vec3(0.74f, 0.76f, 0.80f),
                0.14f,
                0.76f,
                "textures/c.png",
                "textures/c_n.png",
                "textures/c_mr.png",
                "textures/c_ao.png"
        );
        MaterialDesc matGround = new MaterialDesc(
                "mat-ground",
                new Vec3(0.60f, 0.63f, 0.57f),
                0.06f,
                0.90f,
                "textures/d.png",
                "textures/d_n.png",
                "textures/d_mr.png",
                "textures/d_ao.png"
        );

        LightDesc shadowLight = new LightDesc(
                "shadow-light",
                new Vec3(8f, 21f, 9f),
                new Vec3(1f, 0.98f, 0.95f),
                1.14f,
                280f,
                true,
                new ShadowDesc(2048, 0.0006f, 5, 4)
        );
        LightDesc fill = new LightDesc("fill", new Vec3(-5f, 7f, -4f), new Vec3(0.34f, 0.47f, 0.70f), 0.52f, 105f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.08f, 0.10f, 0.12f), 0.18f, null);
        FogDesc fog = new FogDesc(true, FogMode.HEIGHT_EXPONENTIAL, new Vec3(0.53f, 0.58f, 0.64f), 0.42f, 0.38f, 0.77f, 0.14f, 1.15f, 0.26f);
        SmokeEmitterDesc smokeA = new SmokeEmitterDesc(
                "smoke-a",
                new Vec3(-0.8f, -0.2f, -5.2f),
                new Vec3(2.1f, 1.3f, 2.1f),
                22f,
                0.86f,
                new Vec3(0.67f, 0.68f, 0.72f),
                0.18f,
                new Vec3(0.04f, 0.16f, -0.03f),
                0.3f,
                3.6f,
                true
        );
        SmokeEmitterDesc smokeB = new SmokeEmitterDesc(
                "smoke-b",
                new Vec3(1.2f, -0.24f, -18.8f),
                new Vec3(3.0f, 1.6f, 3.0f),
                18f,
                0.72f,
                new Vec3(0.62f, 0.64f, 0.67f),
                0.15f,
                new Vec3(-0.03f, 0.13f, 0.02f),
                0.25f,
                3.3f,
                true
        );

        return new SceneDescriptor(
                "parity-material-fog-smoke-shadow-cascade-stress-scene",
                List.of(camera),
                "cam",
                List.of(near, mid, far, ground),
                List.of(meshNear, meshMid, meshFar, meshGround),
                List.of(matNear, matMid, matFar, matGround),
                List.of(shadowLight, fill),
                env,
                fog,
                List.of(smokeA, smokeB)
        );
    }

    private static EngineInput emptyInput() {
        return new EngineInput(0, 0, 0, 0, false, false, Set.<KeyCode>of(), 0.0);
    }

    private static final class RecordingCallbacks implements EngineHostCallbacks {
        private final List<EngineEvent> events = new ArrayList<>();
        private final List<LogMessage> logs = new ArrayList<>();
        private final List<EngineErrorReport> errors = new ArrayList<>();

        @Override
        public void onEvent(EngineEvent event) {
            events.add(event);
        }

        @Override
        public void onLog(LogMessage message) {
            logs.add(message);
        }

        @Override
        public void onError(EngineErrorReport error) {
            errors.add(error);
        }
    }

    private record BackendRunResult(long drawCalls, long triangles, long visibleObjects, Set<String> warningCodes) {
    }
}
