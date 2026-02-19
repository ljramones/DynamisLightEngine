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
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.runtime.EngineFrameResult;
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
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.api.logging.LogMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_HYBRID_COMPOSITION".equals(w.code())));
            var hybrid = runtime.shadowHybridDiagnostics();
            assertTrue(hybrid.available());
            assertTrue(hybrid.hybridModeActive());
            assertTrue(hybrid.cascadeShare() > 0.0);
            assertTrue(hybrid.contactShare() > 0.0);
            assertTrue(hybrid.rtShare() > 0.0);
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
            var cadence = runtime.shadowCadenceDiagnostics();
            assertFalse(cadence.available());
            var spot = runtime.shadowSpotProjectedDiagnostics();
            assertFalse(spot.available());
            assertFalse(runtime.shadowCacheDiagnostics().available());
            assertFalse(runtime.shadowExtendedModeDiagnostics().available());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsCadenceEnvelopeWarningAndTypedDiagnostics() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "true")
            )), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_CADENCE_ENVELOPE".equals(w.code())));
            var cadence = runtime.shadowCadenceDiagnostics();
            assertTrue(cadence.available());
            assertTrue(cadence.selectedLocalShadowLights() >= 0);
            assertTrue(cadence.deferredShadowLightCount() >= 0);
            assertTrue(cadence.deferredRatio() >= 0.0);
            assertFalse(runtime.shadowPointBudgetDiagnostics().available() && runtime.shadowPointBudgetDiagnostics().configuredMaxShadowFacesPerFrame() > 0);
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_SPOT_PROJECTED_CONTRACT".equals(w.code())));
            var spot = runtime.shadowSpotProjectedDiagnostics();
            assertTrue(spot.available());
            assertTrue(spot.active());
            assertTrue(spot.renderedSpotShadowLights() > 0);
            assertFalse(spot.contractBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void cadenceBreachGateTriggersWithLowThresholdAndNoCooldown() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "true"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "2"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "8"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "8"),
                    Map.entry("vulkan.shadow.cadenceWarnDeferredRatioMax", "0.10"),
                    Map.entry("vulkan.shadow.cadenceWarnMinFrames", "1"),
                    Map.entry("vulkan.shadow.cadenceWarnCooldownFrames", "0")
            )), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_CADENCE_ENVELOPE_BREACH".equals(w.code())));
            var cadence = runtime.shadowCadenceDiagnostics();
            assertTrue(cadence.envelopeBreachedLastFrame());
            assertTrue(cadence.highStreak() >= 1);
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void pointFaceBudgetBreachGateTriggersOnSaturatedDeferredPointWork() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "6"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "24"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "6"),
                    Map.entry("vulkan.shadow.pointFaceBudgetWarnSaturationMin", "0.90"),
                    Map.entry("vulkan.shadow.pointFaceBudgetWarnMinFrames", "1"),
                    Map.entry("vulkan.shadow.pointFaceBudgetWarnCooldownFrames", "0")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validThreePointShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_POINT_FACE_BUDGET_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_POINT_FACE_BUDGET_ENVELOPE_BREACH".equals(w.code())));
            var pointBudget = runtime.shadowPointBudgetDiagnostics();
            assertTrue(pointBudget.available());
            assertEquals(6, pointBudget.configuredMaxShadowFacesPerFrame());
            assertTrue(pointBudget.renderedPointFaces() >= 6);
            assertTrue(pointBudget.saturationRatio() >= 0.9);
            assertTrue(pointBudget.envelopeBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void emitsShadowTelemetryProfileWarningWithUltraDefaults() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.of(
                    "vulkan.mockContext", "true"
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            String profileWarning = warningMessageByCode(frame, "SHADOW_TELEMETRY_PROFILE_ACTIVE");
            assertTrue(profileWarning.contains("tier=ultra"), profileWarning);
            assertTrue(profileWarning.contains("cadenceDeferredRatioWarnMax=0.35"), profileWarning);
            assertTrue(profileWarning.contains("cadenceWarnMinFrames=2"), profileWarning);
            assertTrue(profileWarning.contains("cadenceWarnCooldownFrames=60"), profileWarning);
            assertTrue(profileWarning.contains("cadencePromotionReadyMinFrames=4"), profileWarning);
            assertTrue(profileWarning.contains("pointFaceBudgetSaturationWarnMin=0.9"), profileWarning);
            assertTrue(profileWarning.contains("pointFaceBudgetWarnMinFrames=2"), profileWarning);
            assertTrue(profileWarning.contains("pointFaceBudgetWarnCooldownFrames=60"), profileWarning);
            assertTrue(profileWarning.contains("pointFaceBudgetPromotionReadyMinFrames=4"), profileWarning);
            assertTrue(profileWarning.contains("cacheChurnWarnMax=0.22"), profileWarning);
            assertTrue(profileWarning.contains("cacheMissWarnMax=1"), profileWarning);
            assertTrue(profileWarning.contains("cacheWarnMinFrames=2"), profileWarning);
            assertTrue(profileWarning.contains("cacheWarnCooldownFrames=60"), profileWarning);
            assertTrue(profileWarning.contains("rtDenoiseWarnMin=0.34"), profileWarning);
            assertTrue(profileWarning.contains("rtSampleWarnMin=2"), profileWarning);
            assertTrue(profileWarning.contains("rtPerfMaxGpuMsUltra=4.8"), profileWarning);
            assertTrue(profileWarning.contains("rtWarnMinFrames=2"), profileWarning);
            assertTrue(profileWarning.contains("rtWarnCooldownFrames=60"), profileWarning);
            assertTrue(profileWarning.contains("hybridRtShareWarnMin=0.12"), profileWarning);
            assertTrue(profileWarning.contains("hybridContactShareWarnMin=0.06"), profileWarning);
            assertTrue(profileWarning.contains("hybridWarnMinFrames=2"), profileWarning);
            assertTrue(profileWarning.contains("hybridWarnCooldownFrames=60"), profileWarning);
            assertTrue(profileWarning.contains("transparentReceiverCandidateRatioWarnMax=0.35"), profileWarning);
            assertTrue(profileWarning.contains("transparentReceiverWarnMinFrames=2"), profileWarning);
            assertTrue(profileWarning.contains("transparentReceiverWarnCooldownFrames=60"), profileWarning);
            assertTrue(profileWarning.contains("spotProjectedPromotionReadyMinFrames=4"), profileWarning);
            assertTrue(profileWarning.contains("topologyLocalCoverageWarnMin=0.45"), profileWarning);
            assertTrue(profileWarning.contains("topologySpotCoverageWarnMin=0.42"), profileWarning);
            assertTrue(profileWarning.contains("topologyPointCoverageWarnMin=0.35"), profileWarning);
            assertTrue(profileWarning.contains("topologyWarnMinFrames=2"), profileWarning);
            assertTrue(profileWarning.contains("topologyWarnCooldownFrames=60"), profileWarning);
            assertTrue(profileWarning.contains("topologyPromotionReadyMinFrames=4"), profileWarning);
            assertTrue(profileWarning.contains("phaseAPromotionReadyMinFrames=2"), profileWarning);
            var cadence = runtime.shadowCadenceDiagnostics();
            assertEquals(0.35, cadence.deferredRatioWarnMax(), 1e-6);
            assertEquals(2, cadence.warnMinFrames());
            assertEquals(60, cadence.warnCooldownFrames());
            assertEquals(4, cadence.promotionReadyMinFrames());
            var pointBudget = runtime.shadowPointBudgetDiagnostics();
            assertEquals(0.90, pointBudget.saturationWarnMin(), 1e-6);
            assertEquals(2, pointBudget.warnMinFrames());
            assertEquals(60, pointBudget.warnCooldownFrames());
            assertEquals(4, pointBudget.promotionReadyMinFrames());
            var cache = runtime.shadowCacheDiagnostics();
            assertEquals(0.22, cache.churnWarnMax(), 1e-6);
            assertEquals(1, cache.missWarnMax());
            assertEquals(2, cache.warnMinFrames());
            assertEquals(60, cache.warnCooldownFrames());
            var hybrid = runtime.shadowHybridDiagnostics();
            assertEquals(0.12, hybrid.rtShareWarnMin(), 1e-6);
            assertEquals(0.06, hybrid.contactShareWarnMin(), 1e-6);
            assertEquals(2, hybrid.warnMinFrames());
            assertEquals(60, hybrid.warnCooldownFrames());
            var transparent = runtime.shadowTransparentReceiverDiagnostics();
            assertEquals(0.35, transparent.candidateRatioWarnMax(), 1e-6);
            assertEquals(2, transparent.warnMinFrames());
            assertEquals(60, transparent.warnCooldownFrames());
            var topology = runtime.shadowTopologyDiagnostics();
            assertEquals(0.45, topology.localCoverageWarnMin(), 1e-6);
            assertEquals(0.42, topology.spotCoverageWarnMin(), 1e-6);
            assertEquals(0.35, topology.pointCoverageWarnMin(), 1e-6);
            assertEquals(2, topology.warnMinFrames());
            assertEquals(60, topology.warnCooldownFrames());
            assertEquals(4, topology.promotionReadyMinFrames());
            var spot = runtime.shadowSpotProjectedDiagnostics();
            assertEquals(4, spot.promotionReadyMinFrames());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void explicitShadowTelemetryOptionsOverrideTierDefaults() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.cadenceWarnDeferredRatioMax", "0.22"),
                    Map.entry("vulkan.shadow.cadenceWarnMinFrames", "7"),
                    Map.entry("vulkan.shadow.cadenceWarnCooldownFrames", "77"),
                    Map.entry("vulkan.shadow.cadencePromotionReadyMinFrames", "15"),
                    Map.entry("vulkan.shadow.pointFaceBudgetWarnSaturationMin", "0.66"),
                    Map.entry("vulkan.shadow.pointFaceBudgetWarnMinFrames", "6"),
                    Map.entry("vulkan.shadow.pointFaceBudgetWarnCooldownFrames", "88"),
                    Map.entry("vulkan.shadow.pointFaceBudgetPromotionReadyMinFrames", "16"),
                    Map.entry("vulkan.shadow.cacheChurnWarnMax", "0.31"),
                    Map.entry("vulkan.shadow.cacheMissWarnMax", "9"),
                    Map.entry("vulkan.shadow.cacheWarnMinFrames", "4"),
                    Map.entry("vulkan.shadow.cacheWarnCooldownFrames", "55"),
                    Map.entry("vulkan.shadow.rtDenoiseWarnMin", "0.61"),
                    Map.entry("vulkan.shadow.rtSampleWarnMin", "7"),
                    Map.entry("vulkan.shadow.rtPerfMaxGpuMsLow", "0.8"),
                    Map.entry("vulkan.shadow.rtPerfMaxGpuMsMedium", "0.9"),
                    Map.entry("vulkan.shadow.rtPerfMaxGpuMsHigh", "1.0"),
                    Map.entry("vulkan.shadow.rtPerfMaxGpuMsUltra", "1.1"),
                    Map.entry("vulkan.shadow.rtWarnMinFrames", "5"),
                    Map.entry("vulkan.shadow.rtWarnCooldownFrames", "44"),
                    Map.entry("vulkan.shadow.hybridRtShareWarnMin", "0.45"),
                    Map.entry("vulkan.shadow.hybridContactShareWarnMin", "0.30"),
                    Map.entry("vulkan.shadow.hybridWarnMinFrames", "6"),
                    Map.entry("vulkan.shadow.hybridWarnCooldownFrames", "70"),
                    Map.entry("vulkan.shadow.transparentReceiverCandidateRatioWarnMax", "0.22"),
                    Map.entry("vulkan.shadow.transparentReceiverWarnMinFrames", "5"),
                    Map.entry("vulkan.shadow.transparentReceiverWarnCooldownFrames", "88"),
                    Map.entry("vulkan.shadow.spotProjectedPromotionReadyMinFrames", "17"),
                    Map.entry("vulkan.shadow.topologyLocalCoverageWarnMin", "0.81"),
                    Map.entry("vulkan.shadow.topologySpotCoverageWarnMin", "0.72"),
                    Map.entry("vulkan.shadow.topologyPointCoverageWarnMin", "0.63"),
                    Map.entry("vulkan.shadow.topologyWarnMinFrames", "4"),
                    Map.entry("vulkan.shadow.topologyWarnCooldownFrames", "99"),
                    Map.entry("vulkan.shadow.topologyPromotionReadyMinFrames", "11"),
                    Map.entry("vulkan.shadow.phaseAPromotionReadyMinFrames", "5")
            ), QualityTier.ULTRA), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            String profileWarning = warningMessageByCode(frame, "SHADOW_TELEMETRY_PROFILE_ACTIVE");
            assertTrue(profileWarning.contains("cadenceDeferredRatioWarnMax=0.22"), profileWarning);
            assertTrue(profileWarning.contains("cadenceWarnMinFrames=7"), profileWarning);
            assertTrue(profileWarning.contains("cadenceWarnCooldownFrames=77"), profileWarning);
            assertTrue(profileWarning.contains("cadencePromotionReadyMinFrames=15"), profileWarning);
            assertTrue(profileWarning.contains("pointFaceBudgetSaturationWarnMin=0.66"), profileWarning);
            assertTrue(profileWarning.contains("pointFaceBudgetWarnMinFrames=6"), profileWarning);
            assertTrue(profileWarning.contains("pointFaceBudgetWarnCooldownFrames=88"), profileWarning);
            assertTrue(profileWarning.contains("pointFaceBudgetPromotionReadyMinFrames=16"), profileWarning);
            assertTrue(profileWarning.contains("cacheChurnWarnMax=0.31"), profileWarning);
            assertTrue(profileWarning.contains("cacheMissWarnMax=9"), profileWarning);
            assertTrue(profileWarning.contains("cacheWarnMinFrames=4"), profileWarning);
            assertTrue(profileWarning.contains("cacheWarnCooldownFrames=55"), profileWarning);
            assertTrue(profileWarning.contains("rtDenoiseWarnMin=0.61"), profileWarning);
            assertTrue(profileWarning.contains("rtSampleWarnMin=7"), profileWarning);
            assertTrue(profileWarning.contains("rtPerfMaxGpuMsLow=0.8"), profileWarning);
            assertTrue(profileWarning.contains("rtPerfMaxGpuMsMedium=0.9"), profileWarning);
            assertTrue(profileWarning.contains("rtPerfMaxGpuMsHigh=1.0"), profileWarning);
            assertTrue(profileWarning.contains("rtPerfMaxGpuMsUltra=1.1"), profileWarning);
            assertTrue(profileWarning.contains("rtWarnMinFrames=5"), profileWarning);
            assertTrue(profileWarning.contains("rtWarnCooldownFrames=44"), profileWarning);
            assertTrue(profileWarning.contains("hybridRtShareWarnMin=0.45"), profileWarning);
            assertTrue(profileWarning.contains("hybridContactShareWarnMin=0.3"), profileWarning);
            assertTrue(profileWarning.contains("hybridWarnMinFrames=6"), profileWarning);
            assertTrue(profileWarning.contains("hybridWarnCooldownFrames=70"), profileWarning);
            assertTrue(profileWarning.contains("transparentReceiverCandidateRatioWarnMax=0.22"), profileWarning);
            assertTrue(profileWarning.contains("transparentReceiverWarnMinFrames=5"), profileWarning);
            assertTrue(profileWarning.contains("transparentReceiverWarnCooldownFrames=88"), profileWarning);
            assertTrue(profileWarning.contains("spotProjectedPromotionReadyMinFrames=17"), profileWarning);
            assertTrue(profileWarning.contains("topologyLocalCoverageWarnMin=0.81"), profileWarning);
            assertTrue(profileWarning.contains("topologySpotCoverageWarnMin=0.72"), profileWarning);
            assertTrue(profileWarning.contains("topologyPointCoverageWarnMin=0.63"), profileWarning);
            assertTrue(profileWarning.contains("topologyWarnMinFrames=4"), profileWarning);
            assertTrue(profileWarning.contains("topologyWarnCooldownFrames=99"), profileWarning);
            assertTrue(profileWarning.contains("topologyPromotionReadyMinFrames=11"), profileWarning);
            assertTrue(profileWarning.contains("phaseAPromotionReadyMinFrames=5"), profileWarning);
            var cadence = runtime.shadowCadenceDiagnostics();
            assertEquals(0.22, cadence.deferredRatioWarnMax(), 1e-6);
            assertEquals(7, cadence.warnMinFrames());
            assertEquals(77, cadence.warnCooldownFrames());
            assertEquals(15, cadence.promotionReadyMinFrames());
            var pointBudget = runtime.shadowPointBudgetDiagnostics();
            assertEquals(0.66, pointBudget.saturationWarnMin(), 1e-6);
            assertEquals(6, pointBudget.warnMinFrames());
            assertEquals(88, pointBudget.warnCooldownFrames());
            assertEquals(16, pointBudget.promotionReadyMinFrames());
            var cache = runtime.shadowCacheDiagnostics();
            assertEquals(0.31, cache.churnWarnMax(), 1e-6);
            assertEquals(9, cache.missWarnMax());
            assertEquals(4, cache.warnMinFrames());
            assertEquals(55, cache.warnCooldownFrames());
            var rt = runtime.shadowRtDiagnostics();
            assertEquals(0.61, rt.denoiseWarnMin(), 1e-6);
            assertEquals(7, rt.sampleWarnMin());
            assertEquals(0.8, runtimeWarningRtCapByTier(profileWarning, "rtPerfMaxGpuMsLow"), 1e-6);
            assertEquals(0.9, runtimeWarningRtCapByTier(profileWarning, "rtPerfMaxGpuMsMedium"), 1e-6);
            assertEquals(1.0, runtimeWarningRtCapByTier(profileWarning, "rtPerfMaxGpuMsHigh"), 1e-6);
            assertEquals(1.1, runtimeWarningRtCapByTier(profileWarning, "rtPerfMaxGpuMsUltra"), 1e-6);
            assertEquals(5, rt.warnMinFrames());
            assertEquals(44, rt.warnCooldownFrames());
            var hybrid = runtime.shadowHybridDiagnostics();
            assertEquals(0.45, hybrid.rtShareWarnMin(), 1e-6);
            assertEquals(0.30, hybrid.contactShareWarnMin(), 1e-6);
            assertEquals(6, hybrid.warnMinFrames());
            assertEquals(70, hybrid.warnCooldownFrames());
            var transparent = runtime.shadowTransparentReceiverDiagnostics();
            assertEquals(0.22, transparent.candidateRatioWarnMax(), 1e-6);
            assertEquals(5, transparent.warnMinFrames());
            assertEquals(88, transparent.warnCooldownFrames());
            var topology = runtime.shadowTopologyDiagnostics();
            assertEquals(0.81, topology.localCoverageWarnMin(), 1e-6);
            assertEquals(0.72, topology.spotCoverageWarnMin(), 1e-6);
            assertEquals(0.63, topology.pointCoverageWarnMin(), 1e-6);
            assertEquals(4, topology.warnMinFrames());
            assertEquals(99, topology.warnCooldownFrames());
            assertEquals(11, topology.promotionReadyMinFrames());
            var spot = runtime.shadowSpotProjectedDiagnostics();
            assertEquals(17, spot.promotionReadyMinFrames());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowTopologyPromotionReadyWarningEmitsAfterStableCoverageWindow() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "8"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "24"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "24"),
                    Map.entry("vulkan.shadow.topologyWarnMinFrames", "1"),
                    Map.entry("vulkan.shadow.topologyWarnCooldownFrames", "0"),
                    Map.entry("vulkan.shadow.topologyPromotionReadyMinFrames", "1")
            )), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_TOPOLOGY_CONTRACT".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_TOPOLOGY_PROMOTION_READY".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_TOPOLOGY_CONTRACT_BREACH".equals(w.code())));
            var topology = runtime.shadowTopologyDiagnostics();
            assertTrue(topology.available());
            assertTrue(topology.promotionReadyLastFrame());
            assertTrue(topology.stableStreak() >= topology.promotionReadyMinFrames());
            assertFalse(topology.envelopeBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowCadencePromotionReadyWarningEmitsAfterStableWindow() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "8"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "24"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "24"),
                    Map.entry("vulkan.shadow.cadenceWarnDeferredRatioMax", "0.95"),
                    Map.entry("vulkan.shadow.cadencePromotionReadyMinFrames", "1")
            )), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_CADENCE_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_CADENCE_PROMOTION_READY".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_CADENCE_ENVELOPE_BREACH".equals(w.code())));
            var cadence = runtime.shadowCadenceDiagnostics();
            assertTrue(cadence.available());
            assertTrue(cadence.promotionReadyLastFrame());
            assertTrue(cadence.stableStreak() >= cadence.promotionReadyMinFrames());
            assertFalse(cadence.envelopeBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowPointBudgetPromotionReadyWarningEmitsAfterStableWindow() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "8"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "24"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "24"),
                    Map.entry("vulkan.shadow.pointFaceBudgetWarnSaturationMin", "1.0"),
                    Map.entry("vulkan.shadow.pointFaceBudgetPromotionReadyMinFrames", "1")
            )), new NoopCallbacks());
            runtime.loadScene(validThreePointShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_POINT_FACE_BUDGET_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_POINT_FACE_BUDGET_PROMOTION_READY".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_POINT_FACE_BUDGET_ENVELOPE_BREACH".equals(w.code())));
            var pointBudget = runtime.shadowPointBudgetDiagnostics();
            assertTrue(pointBudget.available());
            assertTrue(pointBudget.promotionReadyLastFrame());
            assertTrue(pointBudget.stableStreak() >= pointBudget.promotionReadyMinFrames());
            assertFalse(pointBudget.envelopeBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowSpotProjectedPromotionReadyWarningEmitsAfterStableWindow() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "8"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "24"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "24"),
                    Map.entry("vulkan.shadow.spotProjectedPromotionReadyMinFrames", "1")
            )), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_SPOT_PROJECTED_CONTRACT".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_SPOT_PROJECTED_PROMOTION_READY".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_SPOT_PROJECTED_CONTRACT_BREACH".equals(w.code())));
            var spot = runtime.shadowSpotProjectedDiagnostics();
            assertTrue(spot.available());
            assertTrue(spot.promotionReadyLastFrame());
            assertTrue(spot.stableStreak() >= spot.promotionReadyMinFrames());
            assertFalse(spot.contractBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowPhaseAPromotionReadyWarningEmitsWhenCadencePointAndSpotAreReady() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "8"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "24"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "24"),
                    Map.entry("vulkan.shadow.cadenceWarnDeferredRatioMax", "0.95"),
                    Map.entry("vulkan.shadow.cadencePromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.shadow.pointFaceBudgetPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.shadow.spotProjectedPromotionReadyMinFrames", "1"),
                    Map.entry("vulkan.shadow.phaseAPromotionReadyMinFrames", "1")
            )), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_PHASEA_PROMOTION_READY".equals(w.code())));
            var phaseA = runtime.shadowPhaseAPromotionDiagnostics();
            assertTrue(phaseA.available());
            assertTrue(phaseA.cadencePromotionReady());
            assertTrue(phaseA.pointFaceBudgetPromotionReady());
            assertTrue(phaseA.spotProjectedPromotionReady());
            assertTrue(phaseA.promotionReadyLastFrame());
            assertTrue(phaseA.stableStreak() >= phaseA.promotionReadyMinFrames());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowCacheBreachGateTriggersWithAggressiveThresholds() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "true"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "2"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "2"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "2"),
                    Map.entry("vulkan.shadow.cacheChurnWarnMax", "0.01"),
                    Map.entry("vulkan.shadow.cacheMissWarnMax", "0"),
                    Map.entry("vulkan.shadow.cacheWarnMinFrames", "1"),
                    Map.entry("vulkan.shadow.cacheWarnCooldownFrames", "0")
            )), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_CACHE_POLICY_ACTIVE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_CACHE_CHURN_HIGH".equals(w.code())));
            var cache = runtime.shadowCacheDiagnostics();
            assertTrue(cache.available());
            assertTrue(cache.envelopeBreachedLastFrame());
            assertTrue(cache.cacheMissCount() > 0 || cache.cacheEvictionCount() > 0);
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowRtDenoiseEnvelopeBreachGateTriggersWithAggressiveThresholds() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.rtMode", "optional"),
                    Map.entry("vulkan.shadow.rtDenoiseStrength", "0.10"),
                    Map.entry("vulkan.shadow.rtSampleCount", "1"),
                    Map.entry("vulkan.shadow.rtDenoiseWarnMin", "0.90"),
                    Map.entry("vulkan.shadow.rtSampleWarnMin", "2"),
                    Map.entry("vulkan.shadow.rtPerfMaxGpuMsLow", "0.01"),
                    Map.entry("vulkan.shadow.rtPerfMaxGpuMsMedium", "0.01"),
                    Map.entry("vulkan.shadow.rtPerfMaxGpuMsHigh", "0.01"),
                    Map.entry("vulkan.shadow.rtPerfMaxGpuMsUltra", "0.01"),
                    Map.entry("vulkan.shadow.rtWarnMinFrames", "1"),
                    Map.entry("vulkan.shadow.rtWarnCooldownFrames", "0")
            )), new NoopCallbacks());
            runtime.loadScene(validScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_RT_DENOISE_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_RT_DENOISE_ENVELOPE_BREACH".equals(w.code())));
            var rt = runtime.shadowRtDiagnostics();
            assertTrue(rt.available());
            assertEquals("optional", rt.mode());
            assertTrue(rt.envelopeBreachedLastFrame());
            assertTrue(rt.denoiseStrength() < rt.denoiseWarnMin() || rt.sampleCount() < rt.sampleWarnMin() || rt.perfGpuMsEstimate() > rt.perfGpuMsWarnMax());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowHybridCompositionBreachGateTriggersWithAggressiveThresholds() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.filterPath", "evsm"),
                    Map.entry("vulkan.shadow.rtMode", "bvh_dedicated"),
                    Map.entry("vulkan.shadow.contactShadows", "true"),
                    Map.entry("vulkan.shadow.hybridRtShareWarnMin", "0.50"),
                    Map.entry("vulkan.shadow.hybridContactShareWarnMin", "0.40"),
                    Map.entry("vulkan.shadow.hybridWarnMinFrames", "1"),
                    Map.entry("vulkan.shadow.hybridWarnCooldownFrames", "0")
            )), new NoopCallbacks());
            runtime.loadScene(validScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_HYBRID_COMPOSITION".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_HYBRID_COMPOSITION_BREACH".equals(w.code())));
            var hybrid = runtime.shadowHybridDiagnostics();
            assertTrue(hybrid.available());
            assertTrue(hybrid.hybridModeActive());
            assertTrue(hybrid.envelopeBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowTransparentReceiverEnvelopeBreachGateTriggersWithAggressiveThresholds() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.transparentReceiversEnabled", "true"),
                    Map.entry("vulkan.shadow.transparentReceiverCandidateRatioWarnMax", "0.01"),
                    Map.entry("vulkan.shadow.transparentReceiverWarnMinFrames", "1"),
                    Map.entry("vulkan.shadow.transparentReceiverWarnCooldownFrames", "0")
            )), new NoopCallbacks());
            runtime.loadScene(validAlphaTestedShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_TRANSPARENT_RECEIVER_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_TRANSPARENT_RECEIVER_ENVELOPE_BREACH".equals(w.code())));
            var transparent = runtime.shadowTransparentReceiverDiagnostics();
            assertTrue(transparent.available());
            assertTrue(transparent.requested());
            assertFalse(transparent.supported());
            assertTrue(transparent.envelopeBreachedLastFrame());
            assertEquals("fallback_opaque_only", transparent.activePolicy());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowTopologyContractBreachGateTriggersWithAggressiveThresholds() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "true"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "1"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "1"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "1"),
                    Map.entry("vulkan.shadow.topologyLocalCoverageWarnMin", "1.0"),
                    Map.entry("vulkan.shadow.topologySpotCoverageWarnMin", "1.0"),
                    Map.entry("vulkan.shadow.topologyPointCoverageWarnMin", "1.0"),
                    Map.entry("vulkan.shadow.topologyWarnMinFrames", "1"),
                    Map.entry("vulkan.shadow.topologyWarnCooldownFrames", "0")
            )), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_TOPOLOGY_CONTRACT".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_TOPOLOGY_CONTRACT_BREACH".equals(w.code())));
            var topology = runtime.shadowTopologyDiagnostics();
            assertTrue(topology.available());
            assertTrue(topology.envelopeBreachedLastFrame());
            assertTrue(topology.localCoverageRatio() < topology.localCoverageWarnMin()
                    || topology.spotCoverageRatio() < topology.spotCoverageWarnMin()
                    || topology.pointCoverageRatio() < topology.pointCoverageWarnMin());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void localRenderDeferralUsesPolicyWarningInsteadOfBaselineWarning() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "6"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "24"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "6")
            )), new NoopCallbacks());
            runtime.loadScene(validThreePointShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_LOCAL_RENDER_DEFERRED_POLICY".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_LOCAL_RENDER_BASELINE".equals(w.code())));
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void shadowExtendedModeRequiredBreachesTriggerForUnavailableAreaAndDistanceField() throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.areaApproxEnabled", "true"),
                    Map.entry("vulkan.shadow.areaApproxRequireActive", "true"),
                    Map.entry("vulkan.shadow.distanceFieldSoftEnabled", "true"),
                    Map.entry("vulkan.shadow.distanceFieldRequireActive", "true")
            )), new NoopCallbacks());
            runtime.loadScene(validScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_AREA_APPROX_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_DISTANCE_FIELD_SOFT_POLICY".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_AREA_APPROX_REQUIRED_UNAVAILABLE_BREACH".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_DISTANCE_FIELD_REQUIRED_UNAVAILABLE_BREACH".equals(w.code())));
            var extended = runtime.shadowExtendedModeDiagnostics();
            assertTrue(extended.available());
            assertTrue(extended.areaApproxRequested());
            assertFalse(extended.areaApproxSupported());
            assertTrue(extended.areaApproxBreachedLastFrame());
            assertTrue(extended.distanceFieldRequested());
            assertFalse(extended.distanceFieldSupported());
            assertTrue(extended.distanceFieldBreachedLastFrame());
        } finally {
            runtime.shutdown();
        }
    }

    @ParameterizedTest
    @EnumSource(value = QualityTier.class, names = {"LOW", "MEDIUM", "HIGH", "ULTRA"})
    void cadenceEnvelopeStaysStableAcrossBlessedTiers(QualityTier tier) throws Exception {
        VulkanEngineRuntime runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                    Map.entry("vulkan.shadow.maxShadowedLocalLights", "8"),
                    Map.entry("vulkan.shadow.maxLocalShadowLayers", "24"),
                    Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "24")
            ), tier), new NoopCallbacks());
            runtime.loadScene(validThreeSpotShadowScene());
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_CADENCE_ENVELOPE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_CADENCE_ENVELOPE_BREACH".equals(w.code())));
            var cadence = runtime.shadowCadenceDiagnostics();
            assertTrue(cadence.available());
            assertFalse(cadence.envelopeBreachedLastFrame());
            assertTrue(cadence.deferredRatio() <= cadence.deferredRatioWarnMax());
        } finally {
            runtime.shutdown();
        }
    }

    private static String warningMessageByCode(EngineFrameResult frame, String code) {
        return frame.warnings().stream()
                .filter(w -> code.equals(w.code()))
                .map(EngineWarning::message)
                .findFirst()
                .orElse("");
    }

    private static double runtimeWarningRtCapByTier(String warning, String key) {
        if (warning == null || warning.isBlank() || key == null || key.isBlank()) {
            return Double.NaN;
        }
        String token = key + "=";
        int index = warning.indexOf(token);
        if (index < 0) {
            return Double.NaN;
        }
        int start = index + token.length();
        int end = warning.indexOf(',', start);
        if (end < 0) {
            end = warning.indexOf(')', start);
        }
        if (end < 0) {
            end = warning.length();
        }
        try {
            return Double.parseDouble(warning.substring(start, end).trim());
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions) {
        return validConfig(backendOptions, QualityTier.MEDIUM);
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier qualityTier) {
        return new EngineConfig(
                "vulkan",
                "vulkan-shadow-capability-warning-test",
                1280,
                720,
                1.0f,
                true,
                60,
                qualityTier,
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

    private static SceneDescriptor validThreeSpotShadowScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc material = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        ShadowDesc shadow = new ShadowDesc(1024, 0.0015f, 1, 4);
        LightDesc lightA = new LightDesc("spotA", new Vec3(1, 3, 2), new Vec3(1, 0.9f, 0.8f), 1.0f, 20f, true, shadow, LightType.SPOT);
        LightDesc lightB = new LightDesc("spotB", new Vec3(-2, 3, 2), new Vec3(0.8f, 1, 0.9f), 1.0f, 20f, true, shadow, LightType.SPOT);
        LightDesc lightC = new LightDesc("spotC", new Vec3(0, 3, -2), new Vec3(0.9f, 0.9f, 1), 1.0f, 20f, true, shadow, LightType.SPOT);
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "shadow-cadence-three-spot-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(lightA, lightB, lightC),
                environment,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }

    private static SceneDescriptor validThreePointShadowScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc material = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        ShadowDesc shadow = new ShadowDesc(1024, 0.0015f, 1, 4);
        LightDesc lightA = new LightDesc("pointA", new Vec3(1, 3, 2), new Vec3(1, 0.9f, 0.8f), 1.0f, 20f, true, shadow, LightType.POINT);
        LightDesc lightB = new LightDesc("pointB", new Vec3(-2, 3, 2), new Vec3(0.8f, 1, 0.9f), 1.0f, 20f, true, shadow, LightType.POINT);
        LightDesc lightC = new LightDesc("pointC", new Vec3(0, 3, -2), new Vec3(0.9f, 0.9f, 1), 1.0f, 20f, true, shadow, LightType.POINT);
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "shadow-point-budget-three-point-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(lightA, lightB, lightC),
                environment,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }

    private static SceneDescriptor validAlphaTestedShadowScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "matAlpha", "mesh.glb");
        MaterialDesc material = new MaterialDesc(
                "matAlpha",
                new Vec3(1, 1, 1),
                0.0f,
                0.5f,
                null,
                null,
                null,
                null,
                0f,
                true,
                false
        );
        ShadowDesc shadow = new ShadowDesc(1024, 0.0015f, 1, 4);
        LightDesc light = new LightDesc("spotAlpha", new Vec3(1, 3, 2), new Vec3(1, 1, 1), 1.0f, 20f, true, shadow, LightType.SPOT);
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        return new SceneDescriptor(
                "shadow-transparent-receiver-scene",
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
