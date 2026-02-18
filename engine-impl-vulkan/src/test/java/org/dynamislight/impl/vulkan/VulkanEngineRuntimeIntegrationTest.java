package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.event.DeviceLostEvent;
import org.dynamislight.api.event.AaTelemetryEvent;
import org.dynamislight.api.event.PerformanceWarningEvent;
import org.dynamislight.api.event.ReflectionAdaptiveTelemetryEvent;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.input.KeyCode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.ReflectionAdvancedDesc;
import org.dynamislight.api.scene.ReflectionDesc;
import org.dynamislight.api.scene.ReflectionOverrideMode;
import org.dynamislight.api.scene.ReflectionProbeDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.lwjgl.glfw.GLFW;
import org.junit.jupiter.api.Test;

class VulkanEngineRuntimeIntegrationTest {
    @Test
    void guardedRealVulkanInitPath() {
        assumeRealVulkanReady("real Vulkan init integration test");

        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();

        try {
            runtime.initialize(validConfig(false), callbacks);
            runtime.loadScene(validScene());
            var frame = runtime.render();
            assertNotNull(frame);
            assertTrue(frame.frameIndex() > 0);
        } catch (EngineException e) {
            assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, e.code());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void guardedRealVulkanPlanarPerfTimingSourceFollowsTimestampAvailability() {
        assumeRealVulkanReady("real Vulkan planar perf timing-source integration test");

        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();
        try {
            runtime.initialize(validConfig(false), callbacks);
            runtime.loadScene(validReflectionsScene("planar"));
            runtime.render();
            runtime.render();

            var diagnostics = runtime.debugReflectionPlanarPerfDiagnostics();
            assertNotNull(diagnostics);
            assertNotNull(diagnostics.timingSource());
            if (diagnostics.timestampAvailable()) {
                assertEquals("gpu_timestamp", diagnostics.timingSource());
                assertEquals(false, diagnostics.timestampRequirementUnmet());
            } else {
                assertEquals("frame_estimate", diagnostics.timingSource());
            }
        } catch (EngineException e) {
            assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, e.code());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void guardedRealVulkanRtReflectionContractEmitsPathDiagnostics() {
        assumeRealVulkanReady("real Vulkan RT reflection contract integration test");

        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();
        try {
            runtime.initialize(validConfig(false), callbacks);
            runtime.loadScene(validReflectionsScene("rt_hybrid"));
            var frame = runtime.render();
            runtime.render();

            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_REQUESTED".equals(w.code())));
            var diagnostics = runtime.debugReflectionRtPathDiagnostics();
            assertEquals(true, diagnostics.laneRequested());
            assertTrue(diagnostics.fallbackChain() != null && !diagnostics.fallbackChain().isBlank());
        } catch (EngineException e) {
            assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, e.code());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void guardedRealVulkanRtRequireActiveBehaviorMatchesLaneAvailability() {
        assumeRealVulkanReady("real Vulkan RT require-active integration test");

        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.reflections.rtRequireActive", "true")
            ), QualityTier.HIGH), callbacks);
            runtime.loadScene(validReflectionsScene("rt_hybrid"));
            var frame = runtime.render();

            var diagnostics = runtime.debugReflectionRtPathDiagnostics();
            boolean requiredBreach = frame.warnings().stream()
                    .anyMatch(w -> "REFLECTION_RT_PATH_REQUIRED_UNAVAILABLE_BREACH".equals(w.code()));
            if (diagnostics.laneActive()) {
                assertFalse(requiredBreach);
            } else {
                assertTrue(requiredBreach);
            }
        } catch (EngineException e) {
            assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, e.code());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void guardedRealVulkanRtRequireDedicatedPipelineFollowsCapabilityAndEnableState() {
        assumeRealVulkanReady("real Vulkan RT require-dedicated-pipeline integration test");

        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.reflections.rtDedicatedPipelineEnabled", "true"),
                    Map.entry("vulkan.reflections.rtRequireDedicatedPipeline", "true")
            ), QualityTier.HIGH), callbacks);
            runtime.loadScene(validReflectionsScene("rt_hybrid"));
            var frame = runtime.render();

            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_REQUESTED".equals(w.code())));
            boolean active = frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_DEDICATED_PIPELINE_ACTIVE".equals(w.code()));
            boolean pending = frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_DEDICATED_PIPELINE_PENDING".equals(w.code()));
            boolean breach = frame.warnings().stream().anyMatch(
                    w -> "REFLECTION_RT_DEDICATED_PIPELINE_REQUIRED_UNAVAILABLE_BREACH".equals(w.code()));
            var diagnostics = runtime.debugReflectionRtPathDiagnostics();
            assertTrue(diagnostics.requireDedicatedPipeline());
            assertTrue(diagnostics.dedicatedPipelineEnabled());
            if (diagnostics.dedicatedCapabilitySupported() && diagnostics.laneActive()) {
                assertTrue(active);
                assertFalse(pending);
                assertFalse(breach);
                assertFalse(diagnostics.requireDedicatedPipelineUnmetLastFrame());
                assertTrue(diagnostics.dedicatedHardwarePipelineActive());
            } else {
                assertFalse(active);
                assertTrue(pending);
                assertTrue(breach);
                assertTrue(diagnostics.requireDedicatedPipelineUnmetLastFrame());
                assertFalse(diagnostics.dedicatedHardwarePipelineActive());
            }
        } catch (EngineException e) {
            assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, e.code());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void mockVulkanPathAlwaysWorksInCi() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validScene());
        runtime.update(1.0 / 60.0, emptyInput());
        var frame = runtime.render();
        assertTrue(frame.frameIndex() > 0);
        runtime.shutdown();
    }

    @Test
    void strictBvhModeFailsFastWhenBvhCapabilityIsUnavailable() {
        var runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.rtMode", "bvh"),
                    Map.entry("vulkan.shadow.rtBvhStrict", "true")
            )), new RecordingCallbacks());
        } catch (EngineException e) {
            assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, e.code());
            assertTrue(e.getMessage().contains("Strict BVH RT shadow mode requested"));
            return;
        } finally {
            runtime.shutdown();
        }
        throw new AssertionError("Expected strict BVH mode to fail when BVH capability is unavailable");
    }

    @Test
    void strictDedicatedBvhModeFailsFast() {
        var runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.rtMode", "bvh_dedicated"),
                    Map.entry("vulkan.shadow.rtBvhStrict", "true")
            )), new RecordingCallbacks());
        } catch (EngineException e) {
            assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, e.code());
            assertTrue(e.getMessage().contains("Strict dedicated BVH RT shadow mode requested"));
            return;
        } finally {
            runtime.shutdown();
        }
        throw new AssertionError("Expected strict dedicated BVH mode to fail while dedicated pipeline is unavailable");
    }

    @Test
    void strictNativeRtModeFailsFastWhenTraversalCapabilityIsUnavailable() {
        var runtime = new VulkanEngineRuntime();
        try {
            runtime.initialize(validConfig(Map.ofEntries(
                    Map.entry("vulkan.mockContext", "true"),
                    Map.entry("vulkan.shadow.rtMode", "rt_native"),
                    Map.entry("vulkan.shadow.rtBvhStrict", "true")
            )), new RecordingCallbacks());
        } catch (EngineException e) {
            assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, e.code());
            assertTrue(e.getMessage().contains("Strict native RT shadow mode requested"));
            return;
        } finally {
            runtime.shutdown();
        }
        throw new AssertionError("Expected strict native RT mode to fail when traversal capability is unavailable");
    }

    @Test
    void mockVulkanBackendOptionsConfigureFrameAndCacheLimits() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.framesInFlight", "4"),
                Map.entry("vulkan.maxDynamicSceneObjects", "4096"),
                Map.entry("vulkan.maxPendingUploadRanges", "128"),
                Map.entry("vulkan.dynamicUploadMergeGapObjects", "7"),
                Map.entry("vulkan.dynamicObjectSoftLimit", "1024"),
                Map.entry("vulkan.uniformUploadSoftLimitBytes", "123456"),
                Map.entry("vulkan.uniformUploadWarnCooldownFrames", "9"),
                Map.entry("vulkan.descriptorRingActiveSoftLimit", "512"),
                Map.entry("vulkan.descriptorRingActiveWarnCooldownFrames", "11"),
                Map.entry("vulkan.maxTextureDescriptorSets", "8192"),
                Map.entry("vulkan.meshGeometryCacheEntries", "512")
        )), new RecordingCallbacks());

        VulkanEngineRuntime.FrameResourceConfig config = runtime.debugFrameResourceConfig();
        var profile = runtime.debugFrameResourceProfile();
        assertEquals(4, config.framesInFlight());
        assertEquals(4096, config.maxDynamicSceneObjects());
        assertEquals(128, config.maxPendingUploadRanges());
        assertEquals(8192, config.maxTextureDescriptorSets());
        assertEquals(512, config.meshGeometryCacheEntries());
        assertEquals(7, profile.dynamicUploadMergeGapObjects());
        assertEquals(1024, profile.dynamicObjectSoftLimit());
        runtime.shutdown();
    }

    @Test
    void mockVulkanBackendOptionsClampAndFallbackWhenInvalid() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.framesInFlight", "99"),
                Map.entry("vulkan.maxDynamicSceneObjects", "10"),
                Map.entry("vulkan.maxPendingUploadRanges", "-1"),
                Map.entry("vulkan.dynamicUploadMergeGapObjects", "99"),
                Map.entry("vulkan.dynamicObjectSoftLimit", "10"),
                Map.entry("vulkan.uniformUploadSoftLimitBytes", "12"),
                Map.entry("vulkan.uniformUploadWarnCooldownFrames", "-1"),
                Map.entry("vulkan.descriptorRingActiveSoftLimit", "2"),
                Map.entry("vulkan.descriptorRingActiveWarnCooldownFrames", "-1"),
                Map.entry("vulkan.maxTextureDescriptorSets", "1"),
                Map.entry("vulkan.meshGeometryCacheEntries", "nope")
        )), new RecordingCallbacks());

        VulkanEngineRuntime.FrameResourceConfig config = runtime.debugFrameResourceConfig();
        var profile = runtime.debugFrameResourceProfile();
        assertEquals(6, config.framesInFlight());
        assertEquals(256, config.maxDynamicSceneObjects());
        assertEquals(8, config.maxPendingUploadRanges());
        assertEquals(256, config.maxTextureDescriptorSets());
        assertEquals(256, config.meshGeometryCacheEntries());
        assertEquals(32, profile.dynamicUploadMergeGapObjects());
        assertEquals(128, profile.dynamicObjectSoftLimit());
        runtime.shutdown();
    }

    @Test
    void forcedInitFailureMapsToBackendInitFailed() {
        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();

        EngineException ex = org.junit.jupiter.api.Assertions.assertThrows(
                EngineException.class,
                () -> runtime.initialize(validConfig(Map.of("vulkan.forceInitFailure", "true")), callbacks)
        );

        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, ex.code());
        assertFalse(callbacks.errors.isEmpty());
        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, callbacks.errors.getFirst().code());
    }

    @Test
    void mockVulkanStatsReflectSceneMeshWorkload() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validMultiMeshScene());
        runtime.render();

        assertEquals(2, runtime.getStats().drawCalls());
        assertEquals(3, runtime.getStats().triangles());
        assertEquals(2, runtime.getStats().visibleObjects());
        runtime.shutdown();
    }

    @Test
    void mockVulkanRenderPublishesAaTelemetryEventAndStatsWithinRange() throws Exception {
        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(true), callbacks);
        runtime.loadScene(validScene());

        runtime.render();

        assertTrue(runtime.getStats().taaHistoryRejectRate() >= 0.0 && runtime.getStats().taaHistoryRejectRate() <= 1.0);
        assertTrue(runtime.getStats().taaConfidenceMean() >= 0.0 && runtime.getStats().taaConfidenceMean() <= 1.0);
        assertTrue(runtime.getStats().taaConfidenceDropEvents() >= 0L);
        assertTrue(callbacks.events.stream().anyMatch(AaTelemetryEvent.class::isInstance));
        runtime.shutdown();
    }

    @Test
    void reflectionsRenderPublishesAdaptiveTelemetryEventWithTrendMetrics() throws Exception {
        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "true"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityRejectMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityConfidenceMax", "1.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityDropEventsMin", "0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityWarnMinFrames", "1")
        )), callbacks);
        runtime.loadScene(validReflectionsScene("hybrid"));

        runtime.render();
        runtime.render();

        var adaptiveEvents = callbacks.events.stream()
                .filter(ReflectionAdaptiveTelemetryEvent.class::isInstance)
                .map(ReflectionAdaptiveTelemetryEvent.class::cast)
                .toList();
        assertFalse(adaptiveEvents.isEmpty());
        ReflectionAdaptiveTelemetryEvent latest = adaptiveEvents.getLast();
        assertTrue(latest.enabled());
        assertTrue(latest.samples() >= 1L);
        assertTrue(latest.instantSeverity() >= 0.0 && latest.instantSeverity() <= 1.0);
        assertTrue(latest.meanSeverity() >= 0.0 && latest.meanSeverity() <= 1.0);
        assertTrue(latest.peakSeverity() >= 0.0 && latest.peakSeverity() <= 1.0);
        assertTrue(latest.meanTemporalDelta() >= 0.0);
        assertTrue(latest.meanSsrStrengthDelta() <= 0.0);
        assertTrue(latest.meanSsrStepScaleDelta() >= 0.0);
        runtime.shutdown();
    }

    @Test
    void forcedDeviceLostOnRenderPropagatesErrorAndEvent() throws Exception {
        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.forceDeviceLostOnRender", "true"
        )), callbacks);
        runtime.loadScene(validScene());

        EngineException ex = org.junit.jupiter.api.Assertions.assertThrows(EngineException.class, runtime::render);

        assertEquals(EngineErrorCode.DEVICE_LOST, ex.code());
        assertFalse(callbacks.errors.isEmpty());
        assertTrue(callbacks.errors.stream().anyMatch(err -> err.code() == EngineErrorCode.DEVICE_LOST));
        assertTrue(callbacks.events.stream().anyMatch(DeviceLostEvent.class::isInstance));
        runtime.shutdown();
    }

    @Test
    void lowTierShadowAndSmokeEmitQualityWarnings() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true"), QualityTier.LOW), new RecordingCallbacks());
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0015f, 7, 3)));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_QUALITY_DEGRADED".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "SMOKE_QUALITY_DEGRADED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void ultraTierShadowAndSmokeAvoidQualityWarningsWhenWithinLimits() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true"), QualityTier.ULTRA), new RecordingCallbacks());
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0015f, 7, 3)));

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_QUALITY_DEGRADED".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "SMOKE_QUALITY_DEGRADED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void bloomRequestedDoesNotEmitNotImplementedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validPostProcessScene(true));

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "BLOOM_NOT_IMPLEMENTED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void reflectionsEnabledEmitsBaselineWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTIONS_BASELINE_ACTIVE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void reflectionOverrideModesAreMappedIntoRuntimeMeshState() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validMaterialOverrideReflectionScene(ReflectionOverrideMode.PROBE_ONLY));

        runtime.render();

        assertEquals(List.of(1), runtime.debugReflectionOverrideModes());
        runtime.shutdown();
    }

    @Test
    void reflectionOverrideModesUpdateThroughDynamicSceneReloadPath() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validMaterialOverrideReflectionScene(ReflectionOverrideMode.AUTO));
        runtime.render();
        assertEquals(List.of(0), runtime.debugReflectionOverrideModes());

        runtime.loadScene(validMaterialOverrideReflectionScene(ReflectionOverrideMode.SSR_ONLY));
        runtime.render();

        assertEquals(List.of(2), runtime.debugReflectionOverrideModes());
        runtime.shutdown();
    }

    @Test
    void hybridReflectionsWarningEnvelopePersistsWithMaterialOverrides() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());

        runtime.loadScene(validMaterialOverrideReflectionScene(ReflectionOverrideMode.PROBE_ONLY));
        var probeOnlyFrame = runtime.render();
        assertTrue(probeOnlyFrame.warnings().stream().anyMatch(w -> "REFLECTIONS_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(probeOnlyFrame.warnings().stream().anyMatch(w -> "REFLECTIONS_QUALITY_DEGRADED".equals(w.code())));
        String probeOnlyBaseline = warningMessageByCode(probeOnlyFrame, "REFLECTIONS_BASELINE_ACTIVE");
        assertTrue(probeOnlyBaseline.contains("overrideAuto=0"));
        assertTrue(probeOnlyBaseline.contains("overrideProbeOnly=1"));
        assertTrue(probeOnlyBaseline.contains("overrideSsrOnly=0"));
        assertTrue(probeOnlyBaseline.contains("overrideOther=0"));
        assertEquals(List.of(1), runtime.debugReflectionOverrideModes());

        runtime.loadScene(validMaterialOverrideReflectionScene(ReflectionOverrideMode.SSR_ONLY));
        var ssrOnlyFrame = runtime.render();
        assertTrue(ssrOnlyFrame.warnings().stream().anyMatch(w -> "REFLECTIONS_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(ssrOnlyFrame.warnings().stream().anyMatch(w -> "REFLECTIONS_QUALITY_DEGRADED".equals(w.code())));
        String ssrOnlyBaseline = warningMessageByCode(ssrOnlyFrame, "REFLECTIONS_BASELINE_ACTIVE");
        assertTrue(ssrOnlyBaseline.contains("overrideAuto=0"));
        assertTrue(ssrOnlyBaseline.contains("overrideProbeOnly=0"));
        assertTrue(ssrOnlyBaseline.contains("overrideSsrOnly=1"));
        assertTrue(ssrOnlyBaseline.contains("overrideOther=0"));
        assertEquals(List.of(2), runtime.debugReflectionOverrideModes());

        runtime.shutdown();
    }

    @Test
    void hybridReflectionsWarningIncludesMixedMaterialOverrideCounts() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validMixedMaterialOverrideReflectionScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTIONS_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_OVERRIDE_POLICY".equals(w.code())));
        String baseline = warningMessageByCode(frame, "REFLECTIONS_BASELINE_ACTIVE");
        assertTrue(baseline.contains("overrideAuto=1"));
        assertTrue(baseline.contains("overrideProbeOnly=1"));
        assertTrue(baseline.contains("overrideSsrOnly=1"));
        assertTrue(baseline.contains("overrideOther=0"));
        String policy = warningMessageByCode(frame, "REFLECTION_OVERRIDE_POLICY");
        assertTrue(policy.contains("auto=1"));
        assertTrue(policy.contains("probeOnly=1"));
        assertTrue(policy.contains("ssrOnly=1"));
        assertTrue(policy.contains("planarSelectiveExcludes=probe_only|ssr_only"));
        assertEquals(List.of(0, 1, 2), runtime.debugReflectionOverrideModes());
        var policyDiagnostics = runtime.debugReflectionOverridePolicyDiagnostics();
        assertEquals(1, policyDiagnostics.autoCount());
        assertEquals(1, policyDiagnostics.probeOnlyCount());
        assertEquals(1, policyDiagnostics.ssrOnlyCount());
        runtime.shutdown();
    }

    @Test
    void reflectionProbeBlendDiagnosticsWarningReportsProbeTelemetry() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validReflectionProbeDiagnosticsScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTIONS_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PROBE_BLEND_DIAGNOSTICS".equals(w.code())));
        String baseline = warningMessageByCode(frame, "REFLECTIONS_BASELINE_ACTIVE");
        assertTrue(baseline.contains("probeConfigured=3"));
        assertTrue(baseline.contains("probeSlots=2"));
        assertTrue(baseline.contains("probeCapacity="));
        assertTrue(baseline.contains("probeDelta="));
        assertTrue(baseline.contains("probeChurnEvents="));
        int baselineActive = parseIntMetricField(baseline, "probeActive");
        assertTrue(baselineActive >= 0);
        assertTrue(baselineActive <= 3);

        String diagnostics = warningMessageByCode(frame, "REFLECTION_PROBE_BLEND_DIAGNOSTICS");
        assertTrue(diagnostics.contains("configured=3"));
        assertTrue(diagnostics.contains("slots=2"));
        assertTrue(diagnostics.contains("delta="));
        assertTrue(diagnostics.contains("churnEvents="));
        assertTrue(diagnostics.contains("warnMinDelta="));
        assertTrue(diagnostics.contains("warnMinStreak="));
        assertTrue(diagnostics.contains("warnCooldownFrames="));
        int diagnosticActive = parseIntMetricField(diagnostics, "active");
        assertEquals(baselineActive, diagnosticActive);
        var runtimeDiagnostics = runtime.debugReflectionProbeDiagnostics();
        assertEquals(3, runtimeDiagnostics.configuredProbeCount());
        assertEquals(2, runtimeDiagnostics.slotCount());
        assertEquals(runtimeDiagnostics.activeProbeCount(), diagnosticActive);
        assertTrue(runtimeDiagnostics.metadataCapacity() >= 0);
        var churnDiagnostics = runtime.debugReflectionProbeChurnDiagnostics();
        assertTrue(churnDiagnostics.lastDelta() >= 0);
        assertTrue(churnDiagnostics.churnEvents() >= 0);
        runtime.shutdown();
    }

    @Test
    void reflectionProbeQualitySweepEmitsEnvelopeDiagnostics() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.probeQualityOverlapWarnMaxPairs", "0"),
                Map.entry("vulkan.reflections.probeQualityBleedRiskWarnMaxPairs", "0"),
                Map.entry("vulkan.reflections.probeQualityMinOverlapPairsWhenMultiple", "0")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionProbeDiagnosticsScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PROBE_QUALITY_SWEEP".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PROBE_QUALITY_ENVELOPE_BREACH".equals(w.code())));
        String sweep = warningMessageByCode(frame, "REFLECTION_PROBE_QUALITY_SWEEP");
        assertTrue(sweep.contains("configured=3"));
        assertTrue(sweep.contains("overlapPairs="));
        assertTrue(sweep.contains("bleedRiskPairs="));
        var diagnostics = runtime.debugReflectionProbeQualityDiagnostics();
        assertEquals(3, diagnostics.configuredProbeCount());
        assertTrue(diagnostics.envelopeBreached());
        runtime.shutdown();
    }

    @Test
    void reflectionProbeStreamingDiagnosticsEmitBudgetPressureWhenVisibleBudgetIsTight() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.probeMaxVisible", "1"),
                Map.entry("vulkan.reflections.probeUpdateCadenceFrames", "2")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionProbeDiagnosticsScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PROBE_STREAMING_DIAGNOSTICS".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PROBE_STREAMING_BUDGET_PRESSURE".equals(w.code())));
        String streaming = warningMessageByCode(frame, "REFLECTION_PROBE_STREAMING_DIAGNOSTICS");
        assertTrue(streaming.contains("configured=3"));
        assertTrue(streaming.contains("effectiveBudget=1"));
        assertTrue(streaming.contains("cadenceFrames=2"));
        assertTrue(streaming.contains("budgetPressure=true"));
        var diagnostics = runtime.debugReflectionProbeStreamingDiagnostics();
        assertEquals(3, diagnostics.configuredProbeCount());
        assertEquals(1, diagnostics.maxVisibleBudget());
        assertEquals(2, diagnostics.updateCadenceFrames());
        assertTrue(diagnostics.budgetPressure());
        runtime.shutdown();
    }

    @Test
    void reflectionProbeChurnDiagnosticsRemainAvailableAcrossSceneTransitions() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());

        runtime.loadScene(validReflectionsScene("hybrid"));
        runtime.render();

        runtime.loadScene(validReflectionProbeDiagnosticsScene());
        var frameA = runtime.render();

        runtime.loadScene(validReflectionsScene("hybrid"));
        var frameB = runtime.render();

        runtime.loadScene(validReflectionProbeDiagnosticsScene());
        var frameC = runtime.render();

        assertTrue(frameA.warnings().stream().anyMatch(w -> "REFLECTION_PROBE_BLEND_DIAGNOSTICS".equals(w.code())));
        assertTrue(frameB.warnings().stream().anyMatch(w -> "REFLECTION_PROBE_BLEND_DIAGNOSTICS".equals(w.code())));
        assertTrue(frameC.warnings().stream().anyMatch(w -> "REFLECTION_PROBE_BLEND_DIAGNOSTICS".equals(w.code())));
        var churnDiagnostics = runtime.debugReflectionProbeChurnDiagnostics();
        String diagnostics = warningMessageByCode(frameC, "REFLECTION_PROBE_BLEND_DIAGNOSTICS");
        assertTrue(diagnostics.contains("churnEvents="));
        assertTrue(churnDiagnostics.churnEvents() >= 0);
        assertTrue(churnDiagnostics.meanDelta() >= 0.0);
        runtime.shutdown();
    }

    @Test
    void reflectionSsrTaaDiagnosticsWarningIncludesTemporalSignals() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_SSR_TAA_DIAGNOSTICS".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_TELEMETRY_PROFILE_ACTIVE".equals(w.code())));
        String diagnostics = warningMessageByCode(frame, "REFLECTION_SSR_TAA_DIAGNOSTICS");
        assertTrue(diagnostics.contains("mode=hybrid"));
        assertTrue(diagnostics.contains("ssrStrength="));
        assertTrue(diagnostics.contains("reflectionTemporalWeight="));
        assertTrue(diagnostics.contains("taaBlend="));
        assertTrue(diagnostics.contains("historyRejectRate="));
        assertTrue(diagnostics.contains("confidenceMean="));
        assertTrue(diagnostics.contains("confidenceDropEvents="));
        assertTrue(diagnostics.contains("instabilityRejectMin="));
        assertTrue(diagnostics.contains("instabilityConfidenceMax="));
        assertTrue(diagnostics.contains("instabilityDropEventsMin="));
        assertTrue(diagnostics.contains("instabilityWarnMinFrames="));
        assertTrue(diagnostics.contains("instabilityWarnCooldownFrames="));
        assertTrue(diagnostics.contains("instabilityRiskEmaAlpha="));
        assertTrue(diagnostics.contains("instabilityRiskHighStreak="));
        assertTrue(diagnostics.contains("instabilityRiskEmaReject="));
        assertTrue(diagnostics.contains("instabilityRiskEmaConfidence="));
        var riskDiagnostics = runtime.debugReflectionSsrTaaRiskDiagnostics();
        assertTrue(riskDiagnostics.highStreak() >= 0);
        assertTrue(riskDiagnostics.emaReject() >= 0.0);
        assertTrue(riskDiagnostics.emaConfidence() >= 0.0);
        String profileWarning = warningMessageByCode(frame, "REFLECTION_TELEMETRY_PROFILE_ACTIVE");
        assertTrue(profileWarning.contains("profile=balanced"));
        runtime.shutdown();
    }

    @Test
    void planarReflectionPathEmitsScopeAndOrderingContracts() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("planar"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_CONTRACT".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
        String contract = warningMessageByCode(frame, "REFLECTION_PLANAR_SCOPE_CONTRACT");
        assertTrue(contract.contains("status=prepass_capture_then_main_sample"));
        assertTrue(contract.contains("requiredOrder=planar_capture_before_main_sample_before_post"));
        assertTrue(contract.contains("mirrorCameraActive=true"));
        assertTrue(contract.contains("dedicatedCaptureLaneActive=true"));
        var diagnostics = runtime.debugReflectionPlanarContractDiagnostics();
        assertEquals("prepass_capture_then_main_sample", diagnostics.status());
        assertTrue(diagnostics.mirrorCameraActive());
        assertTrue(diagnostics.dedicatedCaptureLaneActive());
        assertTrue(diagnostics.scopedMeshEligibleCount() >= 0);
        assertTrue(diagnostics.scopedMeshExcludedCount() >= 0);
        int runtimeMode = runtime.debugReflectionRuntimeMode();
        if (diagnostics.scopedMeshEligibleCount() > 0) {
            assertTrue((runtimeMode & (1 << 14)) != 0);
            assertTrue((runtimeMode & (1 << 18)) != 0);
            assertTrue((runtimeMode & (1 << 20)) != 0);
        }
        runtime.shutdown();
    }

    @Test
    void planarReflectionContractReportsConfiguredPlaneHeight() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validPlanarClipHeightScene(2.5f));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_CONTRACT".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
        String contract = warningMessageByCode(frame, "REFLECTION_PLANAR_SCOPE_CONTRACT");
        assertTrue(contract.contains("planeHeight=2.5"));
        assertTrue(contract.contains("mirrorCameraActive=true"));
        assertTrue(contract.contains("dedicatedCaptureLaneActive=true"));
        var diagnostics = runtime.debugReflectionPlanarContractDiagnostics();
        assertTrue(diagnostics.mirrorCameraActive());
        assertTrue(diagnostics.dedicatedCaptureLaneActive());
        runtime.shutdown();
    }

    @Test
    void planarClipHeightSceneMaintainsMirrorCameraContractAcrossFrames() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validPlanarClipHeightScene(1.75f));

        var frameA = runtime.render();
        var frameB = runtime.render();
        var frameC = runtime.render();

        assertTrue(frameA.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_CONTRACT".equals(w.code())));
        assertTrue(frameB.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_CONTRACT".equals(w.code())));
        assertTrue(frameC.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_CONTRACT".equals(w.code())));
        assertTrue(frameA.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
        assertTrue(frameB.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
        assertTrue(frameC.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
        String contractA = warningMessageByCode(frameA, "REFLECTION_PLANAR_SCOPE_CONTRACT");
        String contractB = warningMessageByCode(frameB, "REFLECTION_PLANAR_SCOPE_CONTRACT");
        String contractC = warningMessageByCode(frameC, "REFLECTION_PLANAR_SCOPE_CONTRACT");
        assertTrue(contractA.contains("planeHeight=1.75"));
        assertTrue(contractB.contains("planeHeight=1.75"));
        assertTrue(contractC.contains("planeHeight=1.75"));
        assertTrue(contractA.contains("mirrorCameraActive=true"));
        assertTrue(contractB.contains("mirrorCameraActive=true"));
        assertTrue(contractC.contains("mirrorCameraActive=true"));
        assertTrue(contractA.contains("dedicatedCaptureLaneActive=true"));
        assertTrue(contractB.contains("dedicatedCaptureLaneActive=true"));
        assertTrue(contractC.contains("dedicatedCaptureLaneActive=true"));
        var diagnostics = runtime.debugReflectionPlanarContractDiagnostics();
        assertTrue(diagnostics.mirrorCameraActive());
        assertTrue(diagnostics.dedicatedCaptureLaneActive());
        var stability = runtime.debugReflectionPlanarStabilityDiagnostics();
        assertTrue(stability.coverageRatio() >= 0.0);
        assertTrue(stability.planeDelta() >= 0.0);
        runtime.shutdown();
    }

    @Test
    void planarContractCoverageIncludesHybridAndRtHybridModes() throws Exception {
        for (String mode : List.of("planar", "hybrid", "rt_hybrid")) {
            var runtime = new VulkanEngineRuntime();
            runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
            runtime.loadScene(validReflectionsScene(mode));

            var frame = runtime.render();

            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_CONTRACT".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_RESOURCE_CONTRACT".equals(w.code())));
            String contract = warningMessageByCode(frame, "REFLECTION_PLANAR_SCOPE_CONTRACT");
            assertTrue(contract.contains("status=prepass_capture_then_main_sample"));
            assertTrue(contract.contains("mirrorCameraActive=true"));
            assertTrue(contract.contains("dedicatedCaptureLaneActive=true"));
            String resource = warningMessageByCode(frame, "REFLECTION_PLANAR_RESOURCE_CONTRACT");
            assertTrue(resource.contains("status=capture_available_before_post_sample"));
            runtime.shutdown();
        }
    }

    @Test
    void planarResourceContractFallsBackWhenPlanarPathInactive() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("ssr"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_RESOURCE_CONTRACT".equals(w.code())));
        String resource = warningMessageByCode(frame, "REFLECTION_PLANAR_RESOURCE_CONTRACT");
        assertTrue(resource.contains("status=fallback_scene_color"));
        runtime.shutdown();
    }

    @Test
    void planarStabilityEnvelopeBreachEmitsUnderStrictThresholdsWhenScopeIsEmpty() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.planarEnvelopeWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.planarEnvelopeWarnCooldownFrames", "8"),
                Map.entry("vulkan.reflections.planarEnvelopeCoverageRatioWarnMin", "0.95")
        )), new RecordingCallbacks());
        runtime.loadScene(validPlanarExcludedScopeScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE_BREACH".equals(w.code())));
        String envelope = warningMessageByCode(frame, "REFLECTION_PLANAR_STABILITY_ENVELOPE");
        assertTrue(envelope.contains("risk=true"));
        assertTrue(envelope.contains("emptyScopeRisk=true"));
        var diagnostics = runtime.debugReflectionPlanarStabilityDiagnostics();
        assertTrue(diagnostics.breachedLastFrame());
        assertTrue(diagnostics.highStreak() >= 1);
        runtime.shutdown();
    }

    @Test
    void planarPerfGateBreachEmitsUnderStrictCaps() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.planarPerfMaxGpuMsMedium", "0.0001"),
                Map.entry("vulkan.reflections.planarPerfDrawInflationWarnMax", "1.01"),
                Map.entry("vulkan.reflections.planarPerfMemoryBudgetMb", "0.0001"),
                Map.entry("vulkan.reflections.planarPerfWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.planarPerfWarnCooldownFrames", "8")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("planar"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_PERF_GATES".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_PERF_GATES_BREACH".equals(w.code())));
        String perf = warningMessageByCode(frame, "REFLECTION_PLANAR_PERF_GATES");
        assertTrue(perf.contains("risk=true"));
        var diagnostics = runtime.debugReflectionPlanarPerfDiagnostics();
        assertTrue(diagnostics.breachedLastFrame());
        runtime.shutdown();
    }

    @Test
    void planarPerfGateBreachEmitsWhenGpuTimestampIsRequiredButUnavailable() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.planarPerfRequireGpuTimestamp", "true"),
                Map.entry("vulkan.reflections.planarPerfWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.planarPerfWarnCooldownFrames", "8")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("planar"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_PERF_GATES".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_PERF_GATES_BREACH".equals(w.code())));
        String perf = warningMessageByCode(frame, "REFLECTION_PLANAR_PERF_GATES");
        assertTrue(perf.contains("timingSource=frame_estimate"));
        assertTrue(perf.contains("requireGpuTimestamp=true"));
        assertTrue(perf.contains("timestampRequirementUnmet=true"));
        var diagnostics = runtime.debugReflectionPlanarPerfDiagnostics();
        assertEquals("frame_estimate", diagnostics.timingSource());
        assertEquals(false, diagnostics.timestampAvailable());
        assertEquals(true, diagnostics.requireGpuTimestamp());
        assertEquals(true, diagnostics.timestampRequirementUnmet());
        assertTrue(diagnostics.breachedLastFrame());
        runtime.shutdown();
    }

    @Test
    void planarScopePolicyAllowsProbeOnlyWhenConfigured() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.planarScopeIncludeAuto", "false"),
                Map.entry("vulkan.reflections.planarScopeIncludeProbeOnly", "true"),
                Map.entry("vulkan.reflections.planarScopeIncludeSsrOnly", "false"),
                Map.entry("vulkan.reflections.planarScopeIncludeOther", "false")
        )), new RecordingCallbacks());
        runtime.loadScene(validMaterialOverrideReflectionScene(ReflectionOverrideMode.PROBE_ONLY));

        var frame = runtime.render();

        String contract = warningMessageByCode(frame, "REFLECTION_PLANAR_SCOPE_CONTRACT");
        assertTrue(contract.contains("scopeIncludes=auto=false|probe_only=true|ssr_only=false|other=false"));
        assertTrue(contract.contains("eligibleMeshes=1"));
        assertTrue(contract.contains("excludedMeshes=0"));
        runtime.shutdown();
    }

    @Test
    void planarRapidCameraMovementMaintainsContractAndCoverage() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        List<SceneDescriptor> cameraSweep = List.of(
                validPlanarCameraScene(new Vec3(0f, 1.2f, 6f), new Vec3(0f, 180f, 0f)),
                validPlanarCameraScene(new Vec3(2.5f, 1.0f, 4.0f), new Vec3(-10f, 150f, 0f)),
                validPlanarCameraScene(new Vec3(-2.2f, 1.8f, 3.5f), new Vec3(8f, 210f, 0f)),
                validPlanarCameraScene(new Vec3(0.5f, 2.2f, 8.5f), new Vec3(-14f, 175f, 0f))
        );
        for (SceneDescriptor scene : cameraSweep) {
            runtime.loadScene(scene);
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_CONTRACT".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_EMPTY".equals(w.code())));
            String contract = warningMessageByCode(frame, "REFLECTION_PLANAR_SCOPE_CONTRACT");
            assertTrue(contract.contains("mirrorCameraActive=true"));
            assertTrue(contract.contains("dedicatedCaptureLaneActive=true"));
        }
        runtime.shutdown();
    }

    @Test
    void planarFrequentPlaneHeightChangesMaintainMirrorContract() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.planarEnvelopeWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.planarEnvelopeWarnCooldownFrames", "8")
        )), new RecordingCallbacks());
        for (float planeHeight : List.of(0.0f, 0.6f, -0.4f, 1.1f, -0.9f)) {
            runtime.loadScene(validPlanarClipHeightScene(planeHeight));
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_CONTRACT".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
            String contract = warningMessageByCode(frame, "REFLECTION_PLANAR_SCOPE_CONTRACT");
            assertTrue(contract.contains("planeHeight=" + planeHeight));
            assertTrue(contract.contains("mirrorCameraActive=true"));
        }
        var stability = runtime.debugReflectionPlanarStabilityDiagnostics();
        assertTrue(stability.planeDelta() >= 0.0);
        runtime.shutdown();
    }

    @Test
    void planarSelectiveScopeStressMaintainsDeterministicEligibleCounts() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.planarScopeIncludeAuto", "false"),
                Map.entry("vulkan.reflections.planarScopeIncludeProbeOnly", "true"),
                Map.entry("vulkan.reflections.planarScopeIncludeSsrOnly", "false"),
                Map.entry("vulkan.reflections.planarScopeIncludeOther", "false")
        )), new RecordingCallbacks());
        runtime.loadScene(validPlanarScopeStressScene(18, 26));

        var frame = runtime.render();

        String contract = warningMessageByCode(frame, "REFLECTION_PLANAR_SCOPE_CONTRACT");
        assertTrue(contract.contains("scopeIncludes=auto=false|probe_only=true|ssr_only=false|other=false"));
        assertTrue(contract.contains("eligibleMeshes=18"));
        assertTrue(contract.contains("excludedMeshes=26"));
        var diagnostics = runtime.debugReflectionPlanarContractDiagnostics();
        assertEquals(18, diagnostics.scopedMeshEligibleCount());
        assertEquals(26, diagnostics.scopedMeshExcludedCount());
        runtime.shutdown();
    }

    @Test
    void planarSceneCoverageMatrixEmitsContractsForInteriorOutdoorMultiAndDynamic() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        List<SceneDescriptor> scenes = List.of(
                validPlanarInteriorMirrorScene(),
                validPlanarOutdoorScene(),
                validPlanarMultiPlaneScene(),
                validPlanarDynamicCrossingScene(0.8f),
                validPlanarDynamicCrossingScene(-0.8f)
        );
        for (SceneDescriptor scene : scenes) {
            runtime.loadScene(scene);
            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_CONTRACT".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_STABILITY_ENVELOPE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "REFLECTION_PLANAR_SCOPE_EMPTY".equals(w.code())));
        }
        runtime.shutdown();
    }

    @Test
    void rtReflectionRequestInMockContextActivatesExecutionLaneAndDenoisePath() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.rtSingleBounceEnabled", "true"),
                Map.entry("vulkan.reflections.rtMultiBounceEnabled", "true"),
                Map.entry("vulkan.reflections.rtDenoiseStrength", "0.71")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("rt_hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_REQUESTED".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PIPELINE_LIFECYCLE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_HYBRID_COMPOSITION".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_FALLBACK_ACTIVE".equals(w.code())));
        String rt = warningMessageByCode(frame, "REFLECTION_RT_PATH_REQUESTED");
        assertTrue(rt.contains("singleBounceEnabled=true"));
        assertTrue(rt.contains("multiBounceEnabled=true"));
        assertTrue(rt.contains("dedicatedDenoisePipelineEnabled=true"));
        assertTrue(rt.contains("denoiseStrength=0.71"));
        var diagnostics = runtime.debugReflectionRtPathDiagnostics();
        assertTrue(diagnostics.laneRequested());
        assertTrue(diagnostics.laneActive());
        assertTrue(diagnostics.traversalSupported());
        assertTrue(diagnostics.dedicatedCapabilitySupported());
        assertFalse(diagnostics.requireDedicatedPipeline());
        assertFalse(diagnostics.requireDedicatedPipelineUnmetLastFrame());
        assertFalse(diagnostics.dedicatedHardwarePipelineActive());
        assertTrue(diagnostics.dedicatedDenoisePipelineEnabled());
        assertEquals("rt->ssr->probe", diagnostics.fallbackChain());
        var pipeline = runtime.debugReflectionRtPipelineDiagnostics();
        assertEquals("pending", pipeline.blasLifecycleState());
        assertEquals("pending", pipeline.tlasLifecycleState());
        assertEquals("pending", pipeline.sbtLifecycleState());
        assertEquals(0, pipeline.blasObjectCount());
        var hybrid = runtime.debugReflectionRtHybridDiagnostics();
        assertTrue(hybrid.rtShare() >= 0.0);
        assertTrue(hybrid.ssrShare() >= 0.0);
        assertTrue(hybrid.probeShare() >= 0.0);
        assertEquals(1.0, hybrid.rtShare() + hybrid.ssrShare() + hybrid.probeShare(), 1e-6);
        int runtimeMode = runtime.debugReflectionRuntimeMode();
        assertTrue((runtimeMode & 0x7) > 0);
        assertTrue((runtimeMode & (1 << 15)) != 0);
        assertTrue((runtimeMode & (1 << 17)) != 0);
        assertTrue((runtimeMode & (1 << 19)) != 0);
        assertEquals(0.71, runtime.debugReflectionRuntimeRtDenoiseStrength(), 1e-6);
        runtime.shutdown();
    }

    @Test
    void rtReflectionRequireActiveEmitsBreachWhenLaneUnavailable() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.rtSingleBounceEnabled", "false"),
                Map.entry("vulkan.reflections.rtRequireActive", "true")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("rt_hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_REQUESTED".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_FALLBACK_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_REQUIRED_UNAVAILABLE_BREACH".equals(w.code())));
        String requested = warningMessageByCode(frame, "REFLECTION_RT_PATH_REQUESTED");
        assertTrue(requested.contains("requireActive=true"));
        var diagnostics = runtime.debugReflectionRtPathDiagnostics();
        assertEquals(true, diagnostics.laneRequested());
        assertEquals(false, diagnostics.laneActive());
        assertEquals(true, diagnostics.requireActive());
        assertEquals(true, diagnostics.requireActiveUnmetLastFrame());
        assertEquals("ssr->probe", diagnostics.fallbackChain());
        runtime.shutdown();
    }

    @Test
    void rtPerfGateBreachEmitsUnderStrictCaps() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.rtSingleBounceEnabled", "true"),
                Map.entry("vulkan.reflections.rtMultiBounceEnabled", "true"),
                Map.entry("vulkan.reflections.rtDedicatedDenoisePipelineEnabled", "true"),
                Map.entry("vulkan.reflections.rtPerfMaxGpuMsMedium", "0.0001"),
                Map.entry("vulkan.reflections.rtPerfWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.rtPerfWarnCooldownFrames", "8")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("rt_hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PERF_GATES".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PERF_GATES_BREACH".equals(w.code())));
        String perf = warningMessageByCode(frame, "REFLECTION_RT_PERF_GATES");
        assertTrue(perf.contains("risk=true"));
        var diagnostics = runtime.debugReflectionRtPerfDiagnostics();
        assertTrue(diagnostics.breachedLastFrame());
        assertTrue(diagnostics.gpuMsCap() <= 0.0001 + 1e-9);
        runtime.shutdown();
    }

    @Test
    void rtReflectionRequireMultiBounceEmitsBreachWhenUnavailable() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.rtSingleBounceEnabled", "true"),
                Map.entry("vulkan.reflections.rtMultiBounceEnabled", "false"),
                Map.entry("vulkan.reflections.rtRequireMultiBounce", "true")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("rt_hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_REQUESTED".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_MULTI_BOUNCE_REQUIRED_UNAVAILABLE_BREACH".equals(w.code())));
        String requested = warningMessageByCode(frame, "REFLECTION_RT_PATH_REQUESTED");
        assertTrue(requested.contains("requireMultiBounce=true"));
        var diagnostics = runtime.debugReflectionRtPathDiagnostics();
        assertEquals(true, diagnostics.requireMultiBounce());
        assertEquals(true, diagnostics.requireMultiBounceUnmetLastFrame());
        runtime.shutdown();
    }

    @Test
    void rtReflectionRequireDedicatedPipelineEmitsBreachWhenUnavailable() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.rtSingleBounceEnabled", "true"),
                Map.entry("vulkan.reflections.rtDedicatedPipelineEnabled", "false"),
                Map.entry("vulkan.reflections.rtRequireDedicatedPipeline", "true")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("rt_hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_REQUESTED".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_DEDICATED_PIPELINE_PENDING".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_DEDICATED_PIPELINE_REQUIRED_UNAVAILABLE_BREACH".equals(w.code())));
        String requested = warningMessageByCode(frame, "REFLECTION_RT_PATH_REQUESTED");
        assertTrue(requested.contains("requireDedicatedPipeline=true"));
        var diagnostics = runtime.debugReflectionRtPathDiagnostics();
        assertTrue(diagnostics.laneRequested());
        assertTrue(diagnostics.laneActive());
        assertFalse(diagnostics.dedicatedPipelineEnabled());
        assertTrue(diagnostics.traversalSupported());
        assertTrue(diagnostics.dedicatedCapabilitySupported());
        assertTrue(diagnostics.requireDedicatedPipeline());
        assertTrue(diagnostics.requireDedicatedPipelineUnmetLastFrame());
        assertFalse(diagnostics.dedicatedHardwarePipelineActive());
        var pipeline = runtime.debugReflectionRtPipelineDiagnostics();
        assertEquals("pending", pipeline.blasLifecycleState());
        assertEquals("pending", pipeline.tlasLifecycleState());
        assertEquals("pending", pipeline.sbtLifecycleState());
        assertEquals(0, pipeline.blasObjectCount());
        runtime.shutdown();
    }

    @Test
    void rtReflectionDedicatedPipelineEnabledActivatesPreviewLaneInMockContext() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.rtSingleBounceEnabled", "true"),
                Map.entry("vulkan.reflections.rtDedicatedPipelineEnabled", "true"),
                Map.entry("vulkan.reflections.rtRequireDedicatedPipeline", "true")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("rt_hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PATH_REQUESTED".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_DEDICATED_PIPELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_HYBRID_COMPOSITION".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_DEDICATED_PIPELINE_PENDING".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(
                w -> "REFLECTION_RT_DEDICATED_PIPELINE_REQUIRED_UNAVAILABLE_BREACH".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_RT_PIPELINE_LIFECYCLE".equals(w.code())));
        var diagnostics = runtime.debugReflectionRtPathDiagnostics();
        assertTrue(diagnostics.laneActive());
        assertTrue(diagnostics.dedicatedPipelineEnabled());
        assertTrue(diagnostics.traversalSupported());
        assertTrue(diagnostics.dedicatedCapabilitySupported());
        assertTrue(diagnostics.requireDedicatedPipeline());
        assertFalse(diagnostics.requireDedicatedPipelineUnmetLastFrame());
        assertTrue(diagnostics.dedicatedHardwarePipelineActive());
        var pipeline = runtime.debugReflectionRtPipelineDiagnostics();
        assertEquals("preview_bound", pipeline.blasLifecycleState());
        assertEquals("preview_bound", pipeline.tlasLifecycleState());
        assertEquals("preview_bound", pipeline.sbtLifecycleState());
        assertTrue(pipeline.blasObjectCount() > 0);
        assertTrue(pipeline.tlasInstanceCount() > 0);
        assertTrue(pipeline.sbtRecordCount() > 0);
        var hybrid = runtime.debugReflectionRtHybridDiagnostics();
        assertEquals(1.0, hybrid.rtShare() + hybrid.ssrShare() + hybrid.probeShare(), 1e-6);
        runtime.shutdown();
    }

    void transparentCandidatesEmitStageGateWarningUntilRtLaneIsActive() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validAlphaTestedReflectionsScene("hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_TRANSPARENCY_STAGE_GATE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_TRANSPARENCY_REFRACTION_PENDING".equals(w.code())));
        String gate = warningMessageByCode(frame, "REFLECTION_TRANSPARENCY_STAGE_GATE");
        assertTrue(gate.contains("status=blocked_until_rt_minimal_stable"));
        var diagnostics = runtime.debugReflectionTransparencyDiagnostics();
        assertTrue(diagnostics.transparentCandidateCount() > 0);
        assertEquals("blocked_until_rt_minimal_stable", diagnostics.stageGateStatus());
        assertEquals("probe_only", diagnostics.fallbackPath());
        runtime.shutdown();
    }

    @Test
    void transparentCandidatesWithRtPathEnablePreviewStageGateAndRuntimeIntegrationBit() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.rtSingleBounceEnabled", "true")
        )), new RecordingCallbacks());
        runtime.loadScene(validAlphaTestedReflectionsScene("rt_hybrid"));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "REFLECTION_TRANSPARENCY_STAGE_GATE".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "REFLECTION_TRANSPARENCY_REFRACTION_PENDING".equals(w.code())));
        String gate = warningMessageByCode(frame, "REFLECTION_TRANSPARENCY_STAGE_GATE");
        assertTrue(gate.contains("status=preview_enabled"));
        var diagnostics = runtime.debugReflectionTransparencyDiagnostics();
        assertTrue(diagnostics.transparentCandidateCount() > 0);
        assertEquals("preview_enabled", diagnostics.stageGateStatus());
        assertEquals("rt_or_probe", diagnostics.fallbackPath());
        int runtimeMode = runtime.debugReflectionRuntimeMode();
        assertTrue((runtimeMode & (1 << 16)) != 0);
        runtime.shutdown();
    }

    @Test
    void ssrReprojectionEnvelopeGateEmitsBreachWarningWhenThresholdsAreStrict() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrEnvelopeRejectWarnMax", "0.0"),
                Map.entry("vulkan.reflections.ssrEnvelopeConfidenceWarnMin", "1.0"),
                Map.entry("vulkan.reflections.ssrEnvelopeDropWarnMin", "0"),
                Map.entry("vulkan.reflections.ssrEnvelopeWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.ssrEnvelopeWarnCooldownFrames", "8")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        var frameA = runtime.render();
        var frameB = runtime.render();

        assertTrue(frameA.warnings().stream().anyMatch(w -> "REFLECTION_SSR_REPROJECTION_ENVELOPE".equals(w.code())));
        assertTrue(frameA.warnings().stream().anyMatch(w -> "REFLECTION_SSR_REPROJECTION_ENVELOPE_BREACH".equals(w.code())));
        assertFalse(frameB.warnings().stream().anyMatch(w -> "REFLECTION_SSR_REPROJECTION_ENVELOPE_BREACH".equals(w.code())));
        String envelope = warningMessageByCode(frameA, "REFLECTION_SSR_REPROJECTION_ENVELOPE");
        assertTrue(envelope.contains("breached=true"));
        runtime.shutdown();
    }

    @Test
    void reflectionSsrTaaAdaptivePolicyAdjustsActiveValuesWhenEnabled() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTemporalBoostMax", "0.24"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveSsrStrengthScaleMin", "0.55"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveStepScaleBoostMax", "0.30"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityRejectMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityConfidenceMax", "1.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityDropEventsMin", "0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityWarnMinFrames", "1")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        runtime.render();
        var frame = runtime.render();

        String adaptivePolicy = warningMessageByCode(frame, "REFLECTION_SSR_TAA_ADAPTIVE_POLICY_ACTIVE");
        assertTrue(adaptivePolicy.contains("enabled=true"));
        double baseTemporalWeight = parseDoubleMetricField(adaptivePolicy, "baseTemporalWeight");
        double activeTemporalWeight = parseDoubleMetricField(adaptivePolicy, "activeTemporalWeight");
        double baseSsrStrength = parseDoubleMetricField(adaptivePolicy, "baseSsrStrength");
        double activeSsrStrength = parseDoubleMetricField(adaptivePolicy, "activeSsrStrength");
        double baseSsrStepScale = parseDoubleMetricField(adaptivePolicy, "baseSsrStepScale");
        double activeSsrStepScale = parseDoubleMetricField(adaptivePolicy, "activeSsrStepScale");
        assertTrue(activeTemporalWeight >= baseTemporalWeight);
        assertTrue(activeSsrStrength <= baseSsrStrength);
        assertTrue(activeSsrStepScale >= baseSsrStepScale);

        String diagnostics = warningMessageByCode(frame, "REFLECTION_SSR_TAA_DIAGNOSTICS");
        assertTrue(diagnostics.contains("adaptiveTemporalWeightActive="));
        assertTrue(diagnostics.contains("adaptiveSsrStrengthActive="));
        assertTrue(diagnostics.contains("adaptiveSsrStepScaleActive="));
        String trendReport = warningMessageByCode(frame, "REFLECTION_SSR_TAA_ADAPTIVE_TREND_REPORT");
        assertTrue(trendReport.contains("windowFrames="));
        assertTrue(trendReport.contains("windowSamples="));
        assertTrue(trendReport.contains("meanSeverity="));
        assertTrue(trendReport.contains("severityHighRatio="));
        assertTrue(trendReport.contains("meanTemporalDelta="));
        assertTrue(trendReport.contains("meanSsrStrengthDelta="));
        assertTrue(trendReport.contains("meanSsrStepScaleDelta="));
        String sloAudit = warningMessageByCode(frame, "REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_AUDIT");
        assertTrue(sloAudit.contains("status="));
        assertTrue(sloAudit.contains("reason="));
        runtime.shutdown();
    }

    @Test
    void reflectionSsrTaaAdaptivePolicyLeavesValuesUnchangedWhenDisabled() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        var frame = runtime.render();

        String adaptivePolicy = warningMessageByCode(frame, "REFLECTION_SSR_TAA_ADAPTIVE_POLICY_ACTIVE");
        assertTrue(adaptivePolicy.contains("enabled=false"));
        double baseTemporalWeight = parseDoubleMetricField(adaptivePolicy, "baseTemporalWeight");
        double activeTemporalWeight = parseDoubleMetricField(adaptivePolicy, "activeTemporalWeight");
        double baseSsrStrength = parseDoubleMetricField(adaptivePolicy, "baseSsrStrength");
        double activeSsrStrength = parseDoubleMetricField(adaptivePolicy, "activeSsrStrength");
        double baseSsrStepScale = parseDoubleMetricField(adaptivePolicy, "baseSsrStepScale");
        double activeSsrStepScale = parseDoubleMetricField(adaptivePolicy, "activeSsrStepScale");
        assertEquals(baseTemporalWeight, activeTemporalWeight, 1e-6);
        assertEquals(baseSsrStrength, activeSsrStrength, 1e-6);
        assertEquals(baseSsrStepScale, activeSsrStepScale, 1e-6);
        runtime.shutdown();
    }

    @Test
    void reflectionAdaptivePolicyDiagnosticsExposeResolvedRuntimeState() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTemporalBoostMax", "0.19"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveSsrStrengthScaleMin", "0.62"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveStepScaleBoostMax", "0.23")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        runtime.render();
        runtime.render();
        var diagnostics = runtime.debugReflectionAdaptivePolicyDiagnostics();

        assertTrue(diagnostics.enabled());
        assertEquals(0.19, diagnostics.temporalBoostMax(), 1e-6);
        assertEquals(0.62, diagnostics.ssrStrengthScaleMin(), 1e-6);
        assertEquals(0.23, diagnostics.stepScaleBoostMax(), 1e-6);
        assertTrue(diagnostics.activeTemporalWeight() >= diagnostics.baseTemporalWeight());
        assertTrue(diagnostics.activeSsrStrength() <= diagnostics.baseSsrStrength());
        assertTrue(diagnostics.activeSsrStepScale() >= diagnostics.baseSsrStepScale());
        runtime.shutdown();
    }

    @Test
    void reflectionAdaptiveTrendHighRiskGateUsesThresholdAndCooldown() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "true"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityRejectMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityConfidenceMax", "1.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityDropEventsMin", "0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendHighRatioWarnMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWarnCooldownFrames", "8"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWarnMinSamples", "1")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        var frameA = runtime.render();
        var frameB = runtime.render();

        assertTrue(frameA.warnings().stream().anyMatch(w -> "REFLECTION_SSR_TAA_ADAPTIVE_TREND_HIGH_RISK".equals(w.code())));
        assertFalse(frameB.warnings().stream().anyMatch(w -> "REFLECTION_SSR_TAA_ADAPTIVE_TREND_HIGH_RISK".equals(w.code())));
        String trendReport = warningMessageByCode(frameA, "REFLECTION_SSR_TAA_ADAPTIVE_TREND_REPORT");
        assertTrue(trendReport.contains("highRatioWarnTriggered=true"));
        runtime.shutdown();
    }

    @Test
    void reflectionAdaptiveTrendSloAuditEmitsStructuredStatusAndOptionalFailureWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "true"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityRejectMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityConfidenceMax", "1.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityDropEventsMin", "0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendSloMeanSeverityMax", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendSloHighRatioMax", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendSloMinSamples", "1")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        boolean observedStructuredAudit = false;
        for (int i = 0; i < 8; i++) {
            var frame = runtime.render();
            String sloAudit = warningMessageByCode(frame, "REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_AUDIT");
            if (!sloAudit.isBlank()) {
                observedStructuredAudit = true;
                assertTrue(
                        sloAudit.contains("status=pass")
                                || sloAudit.contains("status=pending")
                                || sloAudit.contains("status=fail")
                );
                if (sloAudit.contains("status=fail")) {
                    assertTrue(frame.warnings().stream().anyMatch(
                            w -> "REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_FAILED".equals(w.code())
                    ));
                    break;
                }
            }
        }
        assertTrue(observedStructuredAudit);
        runtime.shutdown();
    }

    @Test
    void reflectionTrendFailuresCanEmitPerformanceWarningEvents() throws Exception {
        var runtime = new VulkanEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "true"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityRejectMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityConfidenceMax", "1.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityDropEventsMin", "0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendHighRatioWarnMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWarnCooldownFrames", "8"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWarnMinSamples", "1"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendSloMeanSeverityMax", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendSloHighRatioMax", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendSloMinSamples", "1")
        )), callbacks);
        runtime.loadScene(validReflectionsScene("hybrid"));

        boolean emitted = false;
        for (int i = 0; i < 8; i++) {
            runtime.render();
            emitted = callbacks.events.stream()
                    .filter(PerformanceWarningEvent.class::isInstance)
                    .map(PerformanceWarningEvent.class::cast)
                    .anyMatch(e ->
                            "REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_FAILED".equals(e.warningCode())
                                    || "REFLECTION_SSR_TAA_ADAPTIVE_TREND_HIGH_RISK".equals(e.warningCode())
                    );
            if (emitted) {
                break;
            }
        }

        assertTrue(emitted);
        runtime.shutdown();
    }

    @Test
    void reflectionAdaptiveTrendDiagnosticsExposeResolvedGateAndWindowState() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWindowFrames", "32"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendHighRatioWarnMin", "0.41"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWarnMinFrames", "3"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWarnCooldownFrames", "90"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWarnMinSamples", "7")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        runtime.render();
        runtime.render();
        var diagnostics = runtime.debugReflectionAdaptiveTrendDiagnostics();

        assertTrue(diagnostics.windowSamples() >= 1);
        assertTrue(diagnostics.lowRatio() >= 0.0 && diagnostics.lowRatio() <= 1.0);
        assertTrue(diagnostics.mediumRatio() >= 0.0 && diagnostics.mediumRatio() <= 1.0);
        assertTrue(diagnostics.highRatio() >= 0.0 && diagnostics.highRatio() <= 1.0);
        assertEquals(0.41, diagnostics.highRatioWarnMin(), 1e-6);
        assertEquals(3, diagnostics.highRatioWarnMinFrames());
        assertEquals(90, diagnostics.highRatioWarnCooldownFrames());
        assertEquals(7, diagnostics.highRatioWarnMinSamples());
        assertTrue(diagnostics.highRatioWarnMin() >= 0.0 && diagnostics.highRatioWarnMin() <= 1.0);
        assertTrue(diagnostics.highRatioWarnHighStreak() >= 0);
        assertTrue(diagnostics.highRatioWarnCooldownRemaining() >= 0);
        runtime.shutdown();
    }

    @Test
    void reflectionSsrTaaHistoryPolicyEscalatesToRejectWhenRiskStreakCrossesThreshold() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "true"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityRejectMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityConfidenceMax", "1.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityDropEventsMin", "0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityWarnMinFrames", "1"),
                Map.entry("vulkan.reflections.ssrTaaHistoryRejectSeverityMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaHistoryConfidenceDecaySeverityMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaHistoryRejectRiskStreakMin", "99")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        runtime.render();
        var frame = runtime.render();

        String historyPolicy = warningMessageByCode(frame, "REFLECTION_SSR_TAA_HISTORY_POLICY");
        assertTrue(historyPolicy.contains("policy=reflection_region_reject"));
        assertTrue(historyPolicy.contains("reprojectionPolicy=reflection_space_reject"));
        assertTrue(historyPolicy.contains("rejectSeverityMin=0.0"));
        var diagnostics = runtime.debugReflectionSsrTaaHistoryPolicyDiagnostics();
        assertEquals("reflection_region_reject", diagnostics.policy());
        assertEquals("reflection_space_reject", diagnostics.reprojectionPolicy());
        int runtimeMode = runtime.debugReflectionRuntimeMode();
        assertTrue((runtimeMode & (1 << 11)) != 0);
        assertTrue((runtimeMode & (1 << 12)) != 0);
        assertEquals(99, diagnostics.rejectRiskStreakMin());
        assertTrue(diagnostics.latestRejectRate() >= 0.0);
        assertTrue(diagnostics.latestConfidenceMean() >= 0.0);
        assertTrue(diagnostics.latestDropEvents() >= 0);
        assertTrue(diagnostics.rejectBias() >= 0.0 && diagnostics.rejectBias() <= 1.0);
        assertTrue(diagnostics.confidenceDecay() >= 0.0 && diagnostics.confidenceDecay() <= 1.0);
        runtime.shutdown();
    }

    @Test
    void reflectionAdaptiveTrendSloDiagnosticsExposeMachineReadableAuditState() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "true"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendSloMeanSeverityMax", "0.42"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendSloHighRatioMax", "0.31"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendSloMinSamples", "5")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        runtime.render();
        runtime.render();
        var slo = runtime.debugReflectionAdaptiveTrendSloDiagnostics();

        assertTrue("pass".equals(slo.status()) || "pending".equals(slo.status()) || "fail".equals(slo.status()));
        assertFalse(slo.reason().isBlank());
        assertEquals(0.42, slo.sloMeanSeverityMax(), 1e-6);
        assertEquals(0.31, slo.sloHighRatioMax(), 1e-6);
        assertEquals(5, slo.sloMinSamples());
        assertTrue(slo.windowSamples() >= 0);
        assertTrue(slo.meanSeverity() >= 0.0 && slo.meanSeverity() <= 1.0);
        assertTrue(slo.highRatio() >= 0.0 && slo.highRatio() <= 1.0);
        runtime.shutdown();
    }

    @Test
    void reflectionAdaptiveTrendSloDiagnosticsAreAvailableViaBackendAgnosticRuntimeApi() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        runtime.render();
        runtime.render();
        var apiDiagnostics = runtime.reflectionAdaptiveTrendSloDiagnostics();
        var backendDiagnostics = runtime.debugReflectionAdaptiveTrendSloDiagnostics();

        assertFalse("unavailable".equals(apiDiagnostics.status()));
        assertEquals(backendDiagnostics.status(), apiDiagnostics.status());
        assertEquals(backendDiagnostics.reason(), apiDiagnostics.reason());
        assertEquals(backendDiagnostics.failed(), apiDiagnostics.failed());
        assertEquals(backendDiagnostics.windowSamples(), apiDiagnostics.windowSamples());
        assertEquals(backendDiagnostics.meanSeverity(), apiDiagnostics.meanSeverity(), 1e-6);
        assertEquals(backendDiagnostics.highRatio(), apiDiagnostics.highRatio(), 1e-6);
        assertEquals(backendDiagnostics.sloMeanSeverityMax(), apiDiagnostics.sloMeanSeverityMax(), 1e-6);
        assertEquals(backendDiagnostics.sloHighRatioMax(), apiDiagnostics.sloHighRatioMax(), 1e-6);
        assertEquals(backendDiagnostics.sloMinSamples(), apiDiagnostics.sloMinSamples());
        runtime.shutdown();
    }

    @Test
    void reflectionBlessedProfilesProduceExpectedTrendEnvelopes() throws Exception {
        Map<String, String> forcedRisk = Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityRejectMin", "0.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityConfidenceMax", "1.0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityDropEventsMin", "0"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityWarnMinFrames", "1")
        );

        assertBlessedProfileTrendEnvelope(
                "performance",
                150,
                0.55,
                0.25,
                0.20,
                30,
                false,
                forcedRisk
        );
        assertBlessedProfileTrendEnvelope(
                "quality",
                120,
                0.45,
                0.40,
                0.30,
                24,
                false,
                forcedRisk
        );
        assertBlessedProfileTrendEnvelope(
                "stability",
                180,
                0.30,
                0.65,
                0.45,
                16,
                true,
                forcedRisk
        );
    }

    @Test
    void stabilityReflectionProfileAppliesTelemetryDefaultsWhenNotOverridden() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflectionsProfile", "stability")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        var frame = runtime.render();

        String diagnostics = warningMessageByCode(frame, "REFLECTION_SSR_TAA_DIAGNOSTICS");
        assertTrue(diagnostics.contains("instabilityRejectMin=0.28"));
        assertTrue(diagnostics.contains("instabilityConfidenceMax=0.78"));
        assertTrue(diagnostics.contains("instabilityWarnMinFrames=2"));
        assertTrue(diagnostics.contains("instabilityWarnCooldownFrames=60"));
        assertTrue(diagnostics.contains("instabilityRiskEmaAlpha=0.45"));
        String profileWarning = warningMessageByCode(frame, "REFLECTION_TELEMETRY_PROFILE_ACTIVE");
        assertTrue(profileWarning.contains("ssrTaaAdaptiveEnabled=true"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTemporalBoostMax=0.18"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveSsrStrengthScaleMin=0.6"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveStepScaleBoostMax=0.25"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWindowFrames=180"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendHighRatioWarnMin=0.3"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWarnMinFrames=2"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWarnCooldownFrames=90"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWarnMinSamples=16"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloMeanSeverityMax=0.65"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloHighRatioMax=0.45"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloMinSamples=16"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsLow=2.2"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsMedium=3.2"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsHigh=4.4"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsUltra=5.8"));
        assertTrue(profileWarning.contains("rtPerfWarnMinFrames=2"));
        assertTrue(profileWarning.contains("rtPerfWarnCooldownFrames=90"));
        String probeDiagnostics = warningMessageByCode(frame, "REFLECTION_PROBE_BLEND_DIAGNOSTICS");
        assertTrue(probeDiagnostics.contains("warnMinStreak=2"));
        assertTrue(probeDiagnostics.contains("warnCooldownFrames=60"));
        runtime.shutdown();
    }

    @Test
    void reflectionTelemetryExplicitOptionsOverrideProfileDefaults() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflectionsProfile", "stability"),
                Map.entry("vulkan.reflections.ssrTaaInstabilityWarnMinFrames", "9"),
                Map.entry("vulkan.reflections.ssrTaaRiskEmaAlpha", "0.33"),
                Map.entry("vulkan.reflections.probeChurnWarnCooldownFrames", "155"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveEnabled", "false"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTemporalBoostMax", "0.07"),
                Map.entry("vulkan.reflections.ssrTaaAdaptiveTrendWindowFrames", "64")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        var frame = runtime.render();

        String diagnostics = warningMessageByCode(frame, "REFLECTION_SSR_TAA_DIAGNOSTICS");
        assertTrue(diagnostics.contains("instabilityWarnMinFrames=9"));
        assertTrue(diagnostics.contains("instabilityRiskEmaAlpha=0.33"));
        String probeDiagnostics = warningMessageByCode(frame, "REFLECTION_PROBE_BLEND_DIAGNOSTICS");
        assertTrue(probeDiagnostics.contains("warnCooldownFrames=155"));
        String profileWarning = warningMessageByCode(frame, "REFLECTION_TELEMETRY_PROFILE_ACTIVE");
        assertTrue(profileWarning.contains("ssrTaaAdaptiveEnabled=false"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTemporalBoostMax=0.07"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWindowFrames=64"));
        runtime.shutdown();
    }

    @Test
    void performanceReflectionProfileAppliesTelemetryDefaultsWhenNotOverridden() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflectionsProfile", "performance")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        var frame = runtime.render();

        String profileWarning = warningMessageByCode(frame, "REFLECTION_TELEMETRY_PROFILE_ACTIVE");
        assertTrue(profileWarning.contains("profile=performance"));
        assertTrue(profileWarning.contains("probeWarnMinDelta=2"));
        assertTrue(profileWarning.contains("ssrTaaWarnMinFrames=4"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWindowFrames=150"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendHighRatioWarnMin=0.55"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWarnMinFrames=4"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWarnCooldownFrames=240"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWarnMinSamples=30"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloMeanSeverityMax=0.25"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloHighRatioMax=0.2"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloMinSamples=30"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsLow=1.4"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsMedium=2.0"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsHigh=2.8"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsUltra=3.6"));
        assertTrue(profileWarning.contains("rtPerfWarnMinFrames=4"));
        assertTrue(profileWarning.contains("rtPerfWarnCooldownFrames=180"));
        String diagnostics = warningMessageByCode(frame, "REFLECTION_SSR_TAA_DIAGNOSTICS");
        assertTrue(diagnostics.contains("instabilityRejectMin=0.45"));
        assertTrue(diagnostics.contains("instabilityConfidenceMax=0.6"));
        assertTrue(diagnostics.contains("instabilityWarnMinFrames=4"));
        assertTrue(diagnostics.contains("instabilityWarnCooldownFrames=240"));
        assertTrue(diagnostics.contains("instabilityRiskEmaAlpha=0.2"));
        String probeDiagnostics = warningMessageByCode(frame, "REFLECTION_PROBE_BLEND_DIAGNOSTICS");
        assertTrue(probeDiagnostics.contains("warnMinDelta=2"));
        assertTrue(probeDiagnostics.contains("warnMinStreak=4"));
        assertTrue(probeDiagnostics.contains("warnCooldownFrames=180"));
        runtime.shutdown();
    }

    @Test
    void qualityReflectionProfileAppliesTelemetryDefaultsWhenNotOverridden() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflectionsProfile", "quality")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        var frame = runtime.render();

        String diagnostics = warningMessageByCode(frame, "REFLECTION_SSR_TAA_DIAGNOSTICS");
        assertTrue(diagnostics.contains("instabilityRejectMin=0.32"));
        assertTrue(diagnostics.contains("instabilityConfidenceMax=0.74"));
        assertTrue(diagnostics.contains("instabilityWarnMinFrames=2"));
        assertTrue(diagnostics.contains("instabilityWarnCooldownFrames=90"));
        assertTrue(diagnostics.contains("instabilityRiskEmaAlpha=0.3"));
        String profileWarning = warningMessageByCode(frame, "REFLECTION_TELEMETRY_PROFILE_ACTIVE");
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWindowFrames=120"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendHighRatioWarnMin=0.45"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWarnMinFrames=3"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWarnCooldownFrames=120"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWarnMinSamples=24"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloMeanSeverityMax=0.4"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloHighRatioMax=0.3"));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloMinSamples=24"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsLow=1.8"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsMedium=2.8"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsHigh=3.9"));
        assertTrue(profileWarning.contains("rtPerfMaxGpuMsUltra=5.2"));
        assertTrue(profileWarning.contains("rtPerfWarnMinFrames=3"));
        assertTrue(profileWarning.contains("rtPerfWarnCooldownFrames=120"));
        String probeDiagnostics = warningMessageByCode(frame, "REFLECTION_PROBE_BLEND_DIAGNOSTICS");
        assertTrue(probeDiagnostics.contains("warnMinDelta=1"));
        assertTrue(probeDiagnostics.contains("warnMinStreak=2"));
        assertTrue(probeDiagnostics.contains("warnCooldownFrames=90"));
        runtime.shutdown();
    }

    @Test
    void reflectionProfilesApplyTypedPlanarEnvelopeAndPerfDefaults() throws Exception {
        assertPlanarProfileDefaults(
                "performance",
                0.45, 0.30, 4, 180,
                1.2, 1.9, 2.6, 3.2,
                1.7, 20.0, 4, 180
        );
        assertPlanarProfileDefaults(
                "quality",
                0.30, 0.20, 3, 120,
                1.5, 2.4, 3.3, 4.5,
                2.1, 36.0, 3, 120
        );
        assertPlanarProfileDefaults(
                "stability",
                0.22, 0.15, 2, 90,
                1.8, 2.8, 3.8, 5.0,
                2.4, 48.0, 2, 90
        );
    }

    @Test
    void reflectionPlanarExplicitOptionsOverrideProfileDefaults() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflectionsProfile", "performance"),
                Map.entry("vulkan.reflections.planarEnvelopePlaneDeltaWarnMax", "0.91"),
                Map.entry("vulkan.reflections.planarEnvelopeCoverageRatioWarnMin", "0.44"),
                Map.entry("vulkan.reflections.planarEnvelopeWarnMinFrames", "7"),
                Map.entry("vulkan.reflections.planarEnvelopeWarnCooldownFrames", "77"),
                Map.entry("vulkan.reflections.planarPerfMaxGpuMsLow", "9.1"),
                Map.entry("vulkan.reflections.planarPerfMaxGpuMsMedium", "9.2"),
                Map.entry("vulkan.reflections.planarPerfMaxGpuMsHigh", "9.3"),
                Map.entry("vulkan.reflections.planarPerfMaxGpuMsUltra", "9.4"),
                Map.entry("vulkan.reflections.planarPerfDrawInflationWarnMax", "3.3"),
                Map.entry("vulkan.reflections.planarPerfMemoryBudgetMb", "99.0"),
                Map.entry("vulkan.reflections.planarPerfWarnMinFrames", "6"),
                Map.entry("vulkan.reflections.planarPerfWarnCooldownFrames", "66")
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("planar"));

        var frame = runtime.render();

        String profileWarning = warningMessageByCode(frame, "REFLECTION_TELEMETRY_PROFILE_ACTIVE");
        assertTrue(profileWarning.contains("planarEnvelopePlaneDeltaWarnMax=0.91"));
        assertTrue(profileWarning.contains("planarEnvelopeCoverageRatioWarnMin=0.44"));
        assertTrue(profileWarning.contains("planarEnvelopeWarnMinFrames=7"));
        assertTrue(profileWarning.contains("planarEnvelopeWarnCooldownFrames=77"));
        assertTrue(profileWarning.contains("planarPerfMaxGpuMsLow=9.1"));
        assertTrue(profileWarning.contains("planarPerfMaxGpuMsMedium=9.2"));
        assertTrue(profileWarning.contains("planarPerfMaxGpuMsHigh=9.3"));
        assertTrue(profileWarning.contains("planarPerfMaxGpuMsUltra=9.4"));
        assertTrue(profileWarning.contains("planarPerfDrawInflationWarnMax=3.3"));
        assertTrue(profileWarning.contains("planarPerfMemoryBudgetMb=99.0"));
        assertTrue(profileWarning.contains("planarPerfWarnMinFrames=6"));
        assertTrue(profileWarning.contains("planarPerfWarnCooldownFrames=66"));
        runtime.shutdown();
    }

    @Test
    void iblEnvironmentEmitsBaselineActiveWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_PREFILTER_APPROX_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_MULTI_TAP_SPEC_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BRDF_ENERGY_COMP_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_CONTAINER_FALLBACK".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void iblMissingAssetsEmitFallbackWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validMissingIblScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void iblSkyboxOnlySceneEnablesBaselineWithFallbackWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validSkyboxOnlyIblScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_SKYBOX_DERIVED_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void iblKtxPathsCanFallbackToSkyboxInputs() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true"), QualityTier.MEDIUM, Path.of("..", "assets")), new RecordingCallbacks());
        runtime.loadScene(validKtxSkyboxFallbackScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_SKYBOX_FALLBACK_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_SKYBOX_DERIVED_ACTIVE".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void iblExistingKtxWithoutSidecarEmitsDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-vk-irr-", ".ktx2");
        Path rad = Files.createTempFile("dle-vk-rad-", ".ktx2");
        try {
            writeKtx2Stub(irr, 16, 16);
            writeKtx2Stub(rad, 16, 16);
            Path brdf = Path.of("..", "assets", "textures", "albedo.png").toAbsolutePath().normalize();

            SceneDescriptor base = validScene();
            EnvironmentDesc env = new EnvironmentDesc(
                    base.environment().ambientColor(),
                    base.environment().ambientIntensity(),
                    null,
                    irr.toString(),
                    rad.toString(),
                    brdf.toString()
            );
            var runtime = new VulkanEngineRuntime();
            runtime.initialize(validConfig(true), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "vulkan-ibl-ktx-decode-unavailable-scene",
                    base.cameras(),
                    base.activeCameraId(),
                    base.transforms(),
                    base.meshes(),
                    base.materials(),
                    base.lights(),
                    env,
                    base.fog(),
                    base.smokeEmitters(),
                    base.postProcess()
            ));

            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_CONTAINER_FALLBACK".equals(w.code())));
            runtime.shutdown();
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblDecodableKtx2DoesNotEmitDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-vk-irr-decode-", ".ktx2");
        Path rad = Files.createTempFile("dle-vk-rad-decode-", ".ktx2");
        try {
            writeKtx2Rgba8(irr, 2, 2, (byte) 210, (byte) 170, (byte) 120, (byte) 255);
            writeKtx2Rgba8(rad, 2, 2, (byte) 220, (byte) 200, (byte) 180, (byte) 255);
            Path brdf = Path.of("..", "assets", "textures", "albedo.png").toAbsolutePath().normalize();

            SceneDescriptor base = validScene();
            EnvironmentDesc env = new EnvironmentDesc(
                    base.environment().ambientColor(),
                    base.environment().ambientIntensity(),
                    null,
                    irr.toString(),
                    rad.toString(),
                    brdf.toString()
            );
            var runtime = new VulkanEngineRuntime();
            runtime.initialize(validConfig(true), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "vulkan-ibl-ktx-decode-available-scene",
                    base.cameras(),
                    base.activeCameraId(),
                    base.transforms(),
                    base.meshes(),
                    base.materials(),
                    base.lights(),
                    env,
                    base.fog(),
                    base.smokeEmitters(),
                    base.postProcess()
            ));

            var frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
            runtime.shutdown();
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblZlibSupercompressedKtx2DoesNotEmitDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-vk-irr-zlib-", ".ktx2");
        Path rad = Files.createTempFile("dle-vk-rad-zlib-", ".ktx2");
        try {
            writeKtx2ZlibRgba8(irr, 2, 2, (byte) 180, (byte) 160, (byte) 120, (byte) 255);
            writeKtx2ZlibRgba8(rad, 2, 2, (byte) 220, (byte) 200, (byte) 180, (byte) 255);
            Path brdf = Path.of("..", "assets", "textures", "albedo.png").toAbsolutePath().normalize();

            SceneDescriptor base = validScene();
            EnvironmentDesc env = new EnvironmentDesc(
                    base.environment().ambientColor(),
                    base.environment().ambientIntensity(),
                    null,
                    irr.toString(),
                    rad.toString(),
                    brdf.toString()
            );
            var runtime = new VulkanEngineRuntime();
            runtime.initialize(validConfig(true), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "vulkan-ibl-ktx-zlib-decode-available-scene",
                    base.cameras(),
                    base.activeCameraId(),
                    base.transforms(),
                    base.meshes(),
                    base.materials(),
                    base.lights(),
                    env,
                    base.fog(),
                    base.smokeEmitters(),
                    base.postProcess()
            ));

            var frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
            runtime.shutdown();
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblZstdSupercompressedKtx2DoesNotEmitDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-vk-irr-zstd-", ".ktx2");
        Path rad = Files.createTempFile("dle-vk-rad-zstd-", ".ktx2");
        try {
            writeKtx2ZstdRgba8(irr, 2, 2, (byte) 170, (byte) 150, (byte) 110, (byte) 255);
            writeKtx2ZstdRgba8(rad, 2, 2, (byte) 210, (byte) 190, (byte) 170, (byte) 255);
            Path brdf = Path.of("..", "assets", "textures", "albedo.png").toAbsolutePath().normalize();

            SceneDescriptor base = validScene();
            EnvironmentDesc env = new EnvironmentDesc(
                    base.environment().ambientColor(),
                    base.environment().ambientIntensity(),
                    null,
                    irr.toString(),
                    rad.toString(),
                    brdf.toString()
            );
            var runtime = new VulkanEngineRuntime();
            runtime.initialize(validConfig(true), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "vulkan-ibl-ktx-zstd-decode-available-scene",
                    base.cameras(),
                    base.activeCameraId(),
                    base.transforms(),
                    base.meshes(),
                    base.materials(),
                    base.lights(),
                    env,
                    base.fog(),
                    base.smokeEmitters(),
                    base.postProcess()
            ));

            var frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
            runtime.shutdown();
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblBasisLzKtx2EmitsTranscodeRequiredWarning() throws Exception {
        Path irr = Files.createTempFile("dle-vk-irr-super-", ".ktx2");
        Path rad = Files.createTempFile("dle-vk-rad-super-", ".ktx2");
        try {
            writeKtx2SupercompressedStub(irr, 8, 8);
            writeKtx2SupercompressedStub(rad, 8, 8);
            Path brdf = Path.of("..", "assets", "textures", "albedo.png").toAbsolutePath().normalize();

            SceneDescriptor base = validScene();
            EnvironmentDesc env = new EnvironmentDesc(
                    base.environment().ambientColor(),
                    base.environment().ambientIntensity(),
                    null,
                    irr.toString(),
                    rad.toString(),
                    brdf.toString()
            );
            var runtime = new VulkanEngineRuntime();
            runtime.initialize(validConfig(true), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "vulkan-ibl-ktx-supercompressed-scene",
                    base.cameras(),
                    base.activeCameraId(),
                    base.transforms(),
                    base.meshes(),
                    base.materials(),
                    base.lights(),
                    env,
                    base.fog(),
                    base.smokeEmitters(),
                    base.postProcess()
            ));

            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_TRANSCODE_REQUIRED".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            runtime.shutdown();
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblBasisLzKtx2TranscodedDoesNotEmitTranscodeRequiredWarning() throws Exception {
        Path irr = Files.createTempFile("dle-vk-irr-basis-real-", ".ktx2");
        Path rad = Files.createTempFile("dle-vk-rad-basis-real-", ".ktx2");
        try {
            assumeTrue(
                    writeBasisLzKtx2(irr, 2, 2, (byte) 170, (byte) 140, (byte) 110, (byte) 255)
                            && writeBasisLzKtx2(rad, 2, 2, (byte) 210, (byte) 180, (byte) 150, (byte) 255),
                    "libktx BasisLZ encode not available in this environment"
            );
            Path brdf = Path.of("..", "assets", "textures", "albedo.png").toAbsolutePath().normalize();

            SceneDescriptor base = validScene();
            EnvironmentDesc env = new EnvironmentDesc(
                    base.environment().ambientColor(),
                    base.environment().ambientIntensity(),
                    null,
                    irr.toString(),
                    rad.toString(),
                    brdf.toString()
            );
            var runtime = new VulkanEngineRuntime();
            runtime.initialize(validConfig(true), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "vulkan-ibl-ktx-basis-transcoded-scene",
                    base.cameras(),
                    base.activeCameraId(),
                    base.transforms(),
                    base.meshes(),
                    base.materials(),
                    base.lights(),
                    env,
                    base.fog(),
                    base.smokeEmitters(),
                    base.postProcess()
            ));

            var frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_TRANSCODE_REQUIRED".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
            runtime.shutdown();
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblKtxContainerDecodableViaStbDoesNotEmitDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-vk-irr-stb-", ".ktx2");
        Path rad = Files.createTempFile("dle-vk-rad-stb-", ".ktx2");
        try {
            Path png = Path.of("..", "assets", "textures", "albedo.png").toAbsolutePath().normalize();
            Files.copy(png, irr, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(png, rad, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            assumeTrue(canDecodeViaStb(irr) && canDecodeViaStb(rad), "STB cannot decode this container payload on this environment");
            Path brdf = png;

            SceneDescriptor base = validScene();
            EnvironmentDesc env = new EnvironmentDesc(
                    base.environment().ambientColor(),
                    base.environment().ambientIntensity(),
                    null,
                    irr.toString(),
                    rad.toString(),
                    brdf.toString()
            );
            var runtime = new VulkanEngineRuntime();
            runtime.initialize(validConfig(true), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "vulkan-ibl-ktx-stb-decode-available-scene",
                    base.cameras(),
                    base.activeCameraId(),
                    base.transforms(),
                    base.meshes(),
                    base.materials(),
                    base.lights(),
                    env,
                    base.fog(),
                    base.smokeEmitters(),
                    base.postProcess()
            ));

            var frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
            runtime.shutdown();
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblLowTierEmitsQualityDegradedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true"), QualityTier.LOW), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_QUALITY_DEGRADED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void iblUltraTierDoesNotEmitQualityDegradedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true"), QualityTier.ULTRA), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_QUALITY_DEGRADED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void pointShadowRequestDoesNotEmitShadowTypeUnsupportedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validPointShadowScene());

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_TYPE_UNSUPPORTED".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "POINT_SHADOW_APPROX_ACTIVE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void spotShadowRequestDoesNotEmitShadowTypeUnsupportedWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_TYPE_UNSUPPORTED".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "POINT_SHADOW_APPROX_ACTIVE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void shadowPolicyWarningIncludesCadenceAndAtlasTelemetry() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of("vulkan.mockContext", "true")), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("cadencePolicy=hero:1 mid:2 distant:4")
                        && w.message().contains("directionalTexelSnapEnabled=true")
                        && w.message().contains("directionalTexelSnapScale=1.0")
                        && w.message().contains("renderedShadowLightIds=")
                        && w.message().contains("deferredShadowLightCount=")
                        && w.message().contains("deferredShadowLightIds=")
                        && w.message().contains("staleBypassShadowLightCount=")
                        && w.message().contains("shadowMomentAtlasBytesEstimate=")
                        && w.message().contains("atlasMemoryD16Bytes=")
                        && w.message().contains("atlasMemoryD32Bytes=")
                        && w.message().contains("shadowUpdateBytesEstimate=")));
        runtime.shutdown();
    }

    @Test
    void multiSpotLocalShadowSceneUsesMultiLocalRenderPath() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.scheduler.enabled", "false"
        ), QualityTier.ULTRA), new RecordingCallbacks());
        runtime.loadScene(validMultiSpotShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("renderedLocalShadows=3")
                        && w.message().contains("renderedSpotShadows=3")));
        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_LOCAL_RENDER_BASELINE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void multiPointLocalShadowSceneUsesConcurrentPointCubemapBudgetAtUltra() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.scheduler.enabled", "false"
        ), QualityTier.ULTRA), new RecordingCallbacks());
        runtime.loadScene(validBalancedMultiPointShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("renderedPointShadowCubemaps=2")
                        && w.message().contains("renderedLocalShadows=2")));
        runtime.shutdown();
    }

    @Test
    void multiPointLocalShadowSceneHonorsOverrideForThreeConcurrentCubemaps() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.scheduler.enabled", "false",
                "vulkan.shadow.maxShadowedLocalLights", "6",
                "vulkan.shadow.maxLocalShadowLayers", "24",
                "vulkan.shadow.maxShadowFacesPerFrame", "24"
        ), QualityTier.ULTRA), new RecordingCallbacks());
        runtime.loadScene(validMultiPointShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("renderedPointShadowCubemaps=3")
                        && w.message().contains("renderedLocalShadows=3")));
        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_LOCAL_RENDER_BASELINE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void shadowAllocatorTelemetryShowsReuseAcrossFrames() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.scheduler.enabled", "false"
        ), QualityTier.ULTRA), new RecordingCallbacks());
        runtime.loadScene(validMultiSpotShadowScene());
        runtime.render(); // Prime allocation state.
        runtime.loadScene(validMultiSpotShadowSceneReordered());
        var second = runtime.render();

        String policy = second.warnings().stream()
                .filter(w -> "SHADOW_POLICY_ACTIVE".equals(w.code()))
                .map(w -> w.message())
                .findFirst()
                .orElse("");
        int reused = parseWarningIntField(policy, "shadowAllocatorReusedAssignments");
        int evictions = parseWarningIntField(policy, "shadowAllocatorEvictions");
        assertTrue(reused > 0);
        assertTrue(evictions >= 0);
        runtime.shutdown();
    }

    @Test
    void shadowSchedulerCadenceDefersPointWorkUnderFaceBudget() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.scheduler.enabled", "true",
                "vulkan.shadow.scheduler.heroPeriod", "1",
                "vulkan.shadow.scheduler.midPeriod", "2",
                "vulkan.shadow.scheduler.distantPeriod", "4",
                "vulkan.shadow.maxShadowedLocalLights", "3",
                "vulkan.shadow.maxLocalShadowLayers", "24",
                "vulkan.shadow.maxShadowFacesPerFrame", "8"
        ), QualityTier.ULTRA), new RecordingCallbacks());
        runtime.loadScene(validMultiPointShadowScene());

        boolean sawRendered = false;
        boolean sawDeferred = false;
        for (int i = 0; i < 8; i++) {
            var frame = runtime.render();
            String policy = frame.warnings().stream()
                    .filter(w -> "SHADOW_POLICY_ACTIVE".equals(w.code()))
                    .map(w -> w.message())
                    .findFirst()
                    .orElse("");
            String renderedIds = parseWarningStringField(policy, "renderedShadowLightIds");
            sawRendered = sawRendered || !renderedIds.isBlank();
            sawDeferred = sawDeferred || parseWarningIntField(policy, "deferredShadowLightCount") > 0;
        }

        assertTrue(sawRendered);
        assertTrue(sawDeferred);
        runtime.shutdown();
    }

    @Test
    void shadowQualityPathRequestsEmitTrackingWarnings() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.filterPath", "evsm",
                "vulkan.shadow.contactShadows", "true",
                "vulkan.shadow.rtMode", "optional",
                "vulkan.shadow.rtDenoiseStrength", "0.8",
                "vulkan.shadow.rtRayLength", "120",
                "vulkan.shadow.rtSampleCount", "4"
        )), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("filterPath=evsm")
                        && w.message().contains("runtimeFilterPath=evsm")
                        && w.message().contains("momentFilterEstimateOnly=false")
                        && w.message().contains("momentPipelineRequested=true")
                        && w.message().contains("momentPipelineActive=false")
                        && w.message().contains("momentResourceAllocated=false")
                        && w.message().contains("momentResourceFormat=none")
                        && w.message().contains("momentInitialized=false")
                        && w.message().contains("momentPhase=pending")
                        && w.message().contains("contactShadows=true")
                        && w.message().contains("rtMode=optional")
                        && w.message().contains("rtDenoiseStrength=0.8")
                        && w.message().contains("rtRayLength=120.0")
                        && w.message().contains("rtSampleCount=4")));
        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_FILTER_MOMENT_ESTIMATE_ONLY".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_MOMENT_PIPELINE_PENDING".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_MOMENT_PIPELINE_INITIALIZING".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_MOMENT_APPROX_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_PATH_REQUESTED".equals(w.code())
                        && w.message().contains("denoiseStrength=0.8")
                        && w.message().contains("rayLength=120.0")
                        && w.message().contains("sampleCount=4")));
        assertTrue(frame.warnings().stream().anyMatch(w -> "SHADOW_RT_PATH_FALLBACK_ACTIVE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void bvhShadowModeRequestEmitsExplicitFallbackContext() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.filterPath", "pcss",
                "vulkan.shadow.rtMode", "bvh",
                "vulkan.shadow.rtDenoiseStrength", "0.82",
                "vulkan.shadow.rtRayLength", "140",
                "vulkan.shadow.rtSampleCount", "6"
        )), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("rtMode=bvh")
                        && w.message().contains("rtSampleCount=6")));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_PATH_REQUESTED".equals(w.code())
                        && w.message().contains("RT shadow mode requested: bvh")
                        && w.message().contains("denoiseStrength=0.82")
                        && w.message().contains("rayLength=140.0")
                        && w.message().contains("sampleCount=6")));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_PATH_FALLBACK_ACTIVE".equals(w.code())
                        && w.message().contains("BVH mode requested")));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_BVH_PIPELINE_PENDING".equals(w.code())
                        && w.message().contains("dedicated BVH traversal/denoise pipeline remains pending")));
        runtime.shutdown();
    }

    @Test
    void dedicatedBvhShadowModeRequestEmitsExplicitFallbackContext() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.filterPath", "pcss",
                "vulkan.shadow.rtMode", "bvh_dedicated",
                "vulkan.shadow.rtDenoiseStrength", "0.82",
                "vulkan.shadow.rtRayLength", "140",
                "vulkan.shadow.rtSampleCount", "6",
                "vulkan.shadow.rtDedicatedDenoiseStrength", "0.94",
                "vulkan.shadow.rtDedicatedRayLength", "180",
                "vulkan.shadow.rtDedicatedSampleCount", "10"
        )), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("rtMode=bvh_dedicated")
                        && w.message().contains("rtSampleCount=6")
                        && w.message().contains("rtDedicatedSampleCount=10")
                        && w.message().contains("rtEffectiveSampleCount=10")));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_PATH_REQUESTED".equals(w.code())
                        && w.message().contains("RT shadow mode requested: bvh_dedicated")));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_PATH_FALLBACK_ACTIVE".equals(w.code())
                        && w.message().contains("Dedicated BVH mode requested")));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_BVH_PIPELINE_PENDING".equals(w.code())
                        && w.message().contains("dedicated BVH traversal/denoise pipeline remains pending")));
        runtime.shutdown();
    }

    @Test
    void productionBvhShadowModeRequestUsesProductionOverrides() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.filterPath", "pcss",
                "vulkan.shadow.rtMode", "bvh_production",
                "vulkan.shadow.rtDenoiseStrength", "0.82",
                "vulkan.shadow.rtRayLength", "140",
                "vulkan.shadow.rtSampleCount", "6",
                "vulkan.shadow.rtProductionDenoiseStrength", "0.97",
                "vulkan.shadow.rtProductionRayLength", "240",
                "vulkan.shadow.rtProductionSampleCount", "14"
        )), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("rtMode=bvh_production")
                        && w.message().contains("rtProductionSampleCount=14")
                        && w.message().contains("rtEffectiveSampleCount=14")));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_PATH_REQUESTED".equals(w.code())
                        && w.message().contains("RT shadow mode requested: bvh_production")));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_PATH_FALLBACK_ACTIVE".equals(w.code())
                        && w.message().contains("Production BVH mode requested")));
        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_RT_BVH_PIPELINE_PENDING".equals(w.code())
                        && w.message().contains("dedicated BVH traversal/denoise pipeline remains pending")));
        runtime.shutdown();
    }

    @Test
    void pcssShadowQualityRequestTracksActivePathWithoutMomentWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.filterPath", "pcss"
        )), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("filterPath=pcss")
                        && w.message().contains("runtimeFilterPath=pcss")
                        && w.message().contains("momentFilterEstimateOnly=false")
                        && w.message().contains("momentPipelineRequested=false")
                        && w.message().contains("momentPipelineActive=false")
                        && w.message().contains("momentResourceAllocated=false")
                        && w.message().contains("momentResourceFormat=none")
                        && w.message().contains("momentInitialized=false")
                        && w.message().contains("momentPhase=pending")));
        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_FILTER_MOMENT_ESTIMATE_ONLY".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_MOMENT_PIPELINE_PENDING".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void shadowQualityTuningOptionsAreReportedInPolicyWarning() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.shadow.filterPath", "evsm",
                "vulkan.shadow.pcssSoftness", "1.35",
                "vulkan.shadow.momentBlend", "0.85",
                "vulkan.shadow.momentBleedReduction", "1.10",
                "vulkan.shadow.contactStrength", "1.40",
                "vulkan.shadow.contactTemporalMotionScale", "1.7",
                "vulkan.shadow.contactTemporalMinStability", "0.6"
        )), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "SHADOW_POLICY_ACTIVE".equals(w.code())
                        && w.message().contains("shadowPcssSoftness=1.35")
                        && w.message().contains("shadowMomentBlend=0.85")
                        && w.message().contains("shadowMomentBleedReduction=1.1")
                        && w.message().contains("shadowContactStrength=1.4")
                        && w.message().contains("shadowContactTemporalMotionScale=1.7")
                        && w.message().contains("shadowContactTemporalMinStability=0.6")));
        runtime.shutdown();
    }

    @Test
    void postOffscreenRequestEmitsFallbackPipelineWarningInMockMode() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.postOffscreen", "true"
        )), new RecordingCallbacks());
        runtime.loadScene(validPostProcessScene(true));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "VULKAN_POST_PROCESS_PIPELINE".equals(w.code())
                        && w.message().contains("offscreenRequested=true")
                        && w.message().contains("offscreenActive=false")
                        && w.message().contains("mode=shader-fallback")));
        runtime.shutdown();
    }

    @Test
    void postOffscreenDisabledOmitsPipelineWarningInMockMode() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "true",
                "vulkan.postOffscreen", "false"
        )), new RecordingCallbacks());
        runtime.loadScene(validPostProcessScene(true));

        var frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "VULKAN_POST_PROCESS_PIPELINE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void realVulkanDynamicSceneUpdateReusesBuffersWithoutRebuild() throws Exception {
        assumeRealVulkanReady("real Vulkan reuse integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableScene(true, false));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanMeshReorderStillHitsReusePath() throws Exception {
        assumeRealVulkanReady("real Vulkan reorder reuse integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableScene(false, true));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertTrue(after.reuseHits() > before.reuseHits());
        assertTrue(after.reorderReuseHits() > before.reorderReuseHits());
        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanLightingOnlySceneChangeReusesBuffersWithoutDescriptorRebuild() throws Exception {
        assumeRealVulkanReady("real Vulkan lighting-only reuse integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));
        runtime.render();
        var before = runtime.debugSceneReuseStats();
        var frameBefore = runtime.debugFrameResourceProfile();

        runtime.loadScene(validReusableSceneWithLightingVariant(1.7f));
        runtime.render();
        var after = runtime.debugSceneReuseStats();
        var frameAfter = runtime.debugFrameResourceProfile();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        assertTrue(frameAfter.descriptorRingReuseHits() > frameBefore.descriptorRingReuseHits());
        assertEquals(frameBefore.descriptorRingGrowthRebuilds(), frameAfter.descriptorRingGrowthRebuilds());
        assertEquals(frameBefore.descriptorRingSteadyRebuilds(), frameAfter.descriptorRingSteadyRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanTextureOnlySceneChangeUsesTextureRebindPathWithoutMeshRebuild() throws Exception {
        assumeRealVulkanReady("real Vulkan texture-only rebind integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableSceneWithTextureVariant(false));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableSceneWithTextureVariant(true));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertTrue(after.textureRebindHits() > before.textureRebindHits());
        runtime.shutdown();
    }

    @Test
    void mockVulkanLightingOnlySceneChangeReusesBuffersWithoutDescriptorRebuild() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableSceneWithLightingVariant(1.7f));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanPostFogOnlySceneChangeReusesBuffersWithoutDescriptorRebuild() throws Exception {
        assumeRealVulkanReady("real Vulkan post/fog-only reuse integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableSceneWithPostFogVariant(0.12f, 1.0f));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableSceneWithPostFogVariant(0.22f, 1.18f));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanSparseDynamicUpdateEmitsMultiRangeUniformProfile() throws Exception {
        assumeRealVulkanReady("real Vulkan sparse dynamic-update integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validSparseReusableScene(false));
        runtime.render();

        runtime.loadScene(validSparseReusableScene(true));
        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())
                        && w.message().contains("lastUniformUploadRanges=2")));
        runtime.shutdown();
    }

    @Test
    void realVulkanPendingUploadRangeSoftLimitWarningEmitsOnSparseDynamicUpdates() throws Exception {
        assumeRealVulkanReady("real Vulkan pending-upload-range soft-limit warning integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "false"),
                Map.entry("vulkan.pendingUploadRangeSoftLimit", "1"),
                Map.entry("vulkan.pendingUploadRangeWarnCooldownFrames", "0")
        )), new RecordingCallbacks());
        runtime.loadScene(validSparseReusableScene(false));
        runtime.render();

        runtime.loadScene(validSparseReusableScene(true));
        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "PENDING_UPLOAD_RANGE_SOFT_LIMIT_EXCEEDED".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void realVulkanStableStateEventuallySkipsUniformUploadsOnReusedFrameSlot() throws Exception {
        assumeRealVulkanReady("real Vulkan stable-state uniform-skip integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));

        runtime.render(); // frame slot 0 first sync
        runtime.render(); // frame slot 1 first sync
        runtime.render(); // frame slot 2 first sync
        var frame = runtime.render(); // frame slot 0 should now be stable

        assertTrue(frame.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())
                        && w.message().contains("lastUniformUploadBytes=0")
                        && w.message().contains("lastUniformUploadRanges=0")));
        runtime.shutdown();
    }

    @Test
    void mockVulkanPostFogOnlySceneChangeReusesBuffersWithoutDescriptorRebuild() throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(true), new RecordingCallbacks());
        runtime.loadScene(validReusableSceneWithPostFogVariant(0.12f, 1.0f));
        runtime.render();
        var before = runtime.debugSceneReuseStats();

        runtime.loadScene(validReusableSceneWithPostFogVariant(0.22f, 1.18f));
        runtime.render();
        var after = runtime.debugSceneReuseStats();

        assertEquals(before.fullRebuilds(), after.fullRebuilds());
        assertEquals(before.meshBufferRebuilds(), after.meshBufferRebuilds());
        assertEquals(before.descriptorPoolBuilds(), after.descriptorPoolBuilds());
        assertEquals(before.descriptorPoolRebuilds(), after.descriptorPoolRebuilds());
        runtime.shutdown();
    }

    @Test
    void realVulkanResizeAndSceneSwitchEmitsResourceProfiles() throws Exception {
        assumeRealVulkanReady("real Vulkan resize/scene-switch integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0012f, 5, 4)));
        var frameA = runtime.render();
        assertTrue(frameA.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "MESH_GEOMETRY_CACHE_PROFILE".equals(w.code()) && w.message().contains("evictions=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "MESH_GEOMETRY_CACHE_PROFILE".equals(w.code()) && w.message().contains("maxEntries=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorSetsInRing=3")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("lastUniformUploadRanges=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("lastUniformUploadStartObject=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("pendingRangeOverflows=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingReuseHits=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingSetCapacity=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingGrowthRebuilds=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingSteadyRebuilds=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingPoolReuses=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingPoolResetFailures=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingActiveSetCount=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingWasteSetCount=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingMaxSetCapacity=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingCapBypasses=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("dynamicUploadMergeGapObjects=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("dynamicObjectSoftLimit=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("maxObservedDynamicObjects=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("uniformUploadSoftLimitBytes=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("uniformUploadWarnCooldownRemaining=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingActiveSoftLimit=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingActiveWarnCooldownRemaining=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingWasteWarnCooldownRemaining=")));
        assertTrue(frameA.warnings().stream().anyMatch(w ->
                "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("descriptorRingCapPressureWarnCooldownRemaining=")));
        assertTrue(frameA.warnings().stream().anyMatch(w -> "SHADOW_CASCADE_PROFILE".equals(w.code())));

        runtime.resize(1600, 900, 1.0f);
        runtime.loadScene(validReusableScene(true, false));
        var frameB = runtime.render();
        assertTrue(frameB.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())));
        assertTrue(frameB.warnings().stream().anyMatch(w -> "SCENE_REUSE_PROFILE".equals(w.code())));

        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(1024, 0.0015f, 3, 2)));
        var frameC = runtime.render();
        assertTrue(frameC.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void realVulkanDescriptorPoolResetsAreUsedWhenSceneShrinks() throws Exception {
        assumeRealVulkanReady("real Vulkan descriptor-pool reset reuse integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false)); // 2 meshes
        runtime.render();
        var before = runtime.debugFrameResourceProfile();

        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(1024, 0.0015f, 3, 2))); // 1 mesh
        runtime.render();
        var after = runtime.debugFrameResourceProfile();

        assertTrue(after.descriptorRingPoolReuses() > before.descriptorRingPoolReuses());
        assertEquals(before.descriptorRingPoolResetFailures(), after.descriptorRingPoolResetFailures());
        assertTrue(after.descriptorRingSetCapacity() >= after.descriptorRingActiveSetCount());
        assertEquals(
                after.descriptorRingSetCapacity() - after.descriptorRingActiveSetCount(),
                after.descriptorRingWasteSetCount()
        );
        runtime.shutdown();
    }

    @Test
    void realVulkanSustainedDescriptorWasteEmitsWarning() throws Exception {
        assumeRealVulkanReady("real Vulkan descriptor waste warning integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "false",
                "vulkan.descriptorRingWasteWarnRatio", "0.90",
                "vulkan.descriptorRingWasteWarnMinFrames", "3",
                "vulkan.descriptorRingWasteWarnMinCapacity", "64"
        )), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false)); // small active set against ring capacity

        runtime.render();
        runtime.render();
        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "DESCRIPTOR_RING_WASTE_HIGH".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void realVulkanDescriptorWasteWarningCooldownSuppressesEveryFrameEmission() throws Exception {
        assumeRealVulkanReady("real Vulkan descriptor waste warning cooldown integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "false",
                "vulkan.descriptorRingWasteWarnRatio", "0.90",
                "vulkan.descriptorRingWasteWarnMinFrames", "1",
                "vulkan.descriptorRingWasteWarnMinCapacity", "64",
                "vulkan.descriptorRingWasteWarnCooldownFrames", "5"
        )), new RecordingCallbacks());
        runtime.loadScene(validReusableScene(false, false));

        var frameA = runtime.render();
        var frameB = runtime.render();

        assertTrue(frameA.warnings().stream().anyMatch(w -> "DESCRIPTOR_RING_WASTE_HIGH".equals(w.code())));
        assertFalse(frameB.warnings().stream().anyMatch(w -> "DESCRIPTOR_RING_WASTE_HIGH".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void realVulkanDescriptorCapPressureEmitsWarning() throws Exception {
        assumeRealVulkanReady("real Vulkan descriptor cap pressure warning integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "false",
                "vulkan.maxTextureDescriptorSets", "256",
                "vulkan.descriptorRingCapPressureWarnMinBypasses", "1",
                "vulkan.descriptorRingCapPressureWarnMinFrames", "1"
        )), new RecordingCallbacks());
        runtime.loadScene(validLargeMeshScene(300));

        var frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "DESCRIPTOR_RING_CAP_PRESSURE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void realVulkanDescriptorCapPressureWarningCooldownSuppressesEveryFrameEmission() throws Exception {
        assumeRealVulkanReady("real Vulkan descriptor cap pressure cooldown integration test");

        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "false",
                "vulkan.maxTextureDescriptorSets", "256",
                "vulkan.descriptorRingCapPressureWarnMinBypasses", "1",
                "vulkan.descriptorRingCapPressureWarnMinFrames", "1",
                "vulkan.descriptorRingCapPressureWarnCooldownFrames", "5"
        )), new RecordingCallbacks());
        runtime.loadScene(validLargeMeshScene(300));

        var frameA = runtime.render();
        var frameB = runtime.render();

        assertTrue(frameA.warnings().stream().anyMatch(w -> "DESCRIPTOR_RING_CAP_PRESSURE".equals(w.code())));
        assertFalse(frameB.warnings().stream().anyMatch(w -> "DESCRIPTOR_RING_CAP_PRESSURE".equals(w.code())));
        runtime.shutdown();
    }

    @Test
    void realVulkanEnduranceResizeAndSceneSwitchMaintainsHealthyProfiles() throws Exception {
        assumeRealVulkanReady("real Vulkan endurance integration test");

        var callbacks = new RecordingCallbacks();
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), callbacks);
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0012f, 5, 4)));

        for (int i = 0; i < 24; i++) {
            int width = 1280 + (i * 37);
            int height = 720 + (i * 23);
            runtime.resize(width, height, 1.0f);
            runtime.update(1.0 / 60.0, emptyInput());

            if ((i % 2) == 0) {
                runtime.loadScene(validReusableScene((i % 4) == 0, (i % 3) == 0));
            } else {
                runtime.loadScene(validShadowSmokeScene(new ShadowDesc(1024, 0.0015f, 3, 2)));
            }

            var frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w ->
                    "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()) && w.message().contains("persistentStagingMapped=true")));
        }

        assertTrue(callbacks.errors.isEmpty(), "expected no host callback errors during endurance loop");
        runtime.shutdown();
    }

    @Test
    void realVulkanExtendedEnduranceMaintainsProfilesAndAvoidsCallbackErrors() throws Exception {
        assumeRealVulkanReady("real Vulkan extended endurance integration test");

        var callbacks = new RecordingCallbacks();
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), callbacks);
        runtime.loadScene(validReusableSceneWithPostFogVariant(0.12f, 1.0f));

        int reuseProfileFrames = 0;
        int resourceProfileFrames = 0;
        for (int i = 0; i < 48; i++) {
            int width = 1220 + (i * 19);
            int height = 700 + (i * 13);
            runtime.resize(width, height, 1.0f);
            runtime.update(1.0 / 60.0, emptyInput());

            if ((i % 3) == 0) {
                runtime.loadScene(validReusableScene((i % 2) == 0, (i % 4) == 0));
            } else if ((i % 3) == 1) {
                runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0012f, 5, 4)));
            } else {
                runtime.loadScene(validReusableSceneWithPostFogVariant(0.10f + (0.01f * (i % 7)), 1.0f + (0.04f * (i % 5))));
            }

            var frame = runtime.render();
            if (frame.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()))) {
                resourceProfileFrames++;
            }
            if (frame.warnings().stream().anyMatch(w -> "SCENE_REUSE_PROFILE".equals(w.code()))) {
                reuseProfileFrames++;
            }
        }

        assertTrue(resourceProfileFrames > 0, "expected frame-resource profiling warnings in extended endurance loop");
        assertTrue(reuseProfileFrames > 0, "expected scene-reuse profiling warnings in extended endurance loop");
        assertTrue(callbacks.errors.isEmpty(), "expected no host callback errors during extended endurance loop");
        runtime.shutdown();
    }

    @Test
    void realVulkanLongEnduranceMatrixMaintainsProfilesAndErrorPathStability() throws Exception {
        assumeRealVulkanLongReady("real Vulkan long endurance matrix integration test");

        var callbacks = new RecordingCallbacks();
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(false), callbacks);
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(2048, 0.0012f, 5, 4)));

        int resourceProfileFrames = 0;
        int reuseProfileFrames = 0;
        for (int i = 0; i < 96; i++) {
            int width = 1200 + ((i * 17) % 640);
            int height = 700 + ((i * 11) % 360);
            runtime.resize(width, height, 1.0f);
            runtime.update(1.0 / 60.0, emptyInput());

            if ((i % 4) == 0) {
                runtime.loadScene(validReusableScene((i % 2) == 0, (i % 3) == 0));
            } else if ((i % 4) == 1) {
                runtime.loadScene(validShadowSmokeScene(new ShadowDesc(1024, 0.0015f, 3, 2)));
            } else if ((i % 4) == 2) {
                runtime.loadScene(validReusableSceneWithPostFogVariant(0.08f + (0.01f * (i % 8)), 1.0f + (0.03f * (i % 6))));
            } else {
                runtime.loadScene(validReusableSceneWithTextureVariant((i % 2) == 0));
            }

            var frame = runtime.render();
            if (frame.warnings().stream().anyMatch(w -> "VULKAN_FRAME_RESOURCE_PROFILE".equals(w.code()))) {
                resourceProfileFrames++;
            }
            if (frame.warnings().stream().anyMatch(w -> "SCENE_REUSE_PROFILE".equals(w.code()))) {
                reuseProfileFrames++;
            }
        }

        assertTrue(resourceProfileFrames > 0, "expected frame-resource profile warnings during long endurance loop");
        assertTrue(reuseProfileFrames > 0, "expected scene-reuse profile warnings during long endurance loop");
        assertTrue(callbacks.errors.isEmpty(), "expected no host callback errors during long endurance loop");
        runtime.shutdown();

        var forcedCallbacks = new RecordingCallbacks();
        var forcedRuntime = new VulkanEngineRuntime();
        forcedRuntime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "false",
                "vulkan.forceDeviceLostOnRender", "true"
        )), forcedCallbacks);
        forcedRuntime.loadScene(validShadowSmokeScene(new ShadowDesc(1024, 0.0015f, 3, 2)));
        EngineException ex = org.junit.jupiter.api.Assertions.assertThrows(EngineException.class, forcedRuntime::render);
        assertEquals(EngineErrorCode.DEVICE_LOST, ex.code());
        assertTrue(forcedCallbacks.errors.stream().anyMatch(err -> err.code() == EngineErrorCode.DEVICE_LOST));
        forcedRuntime.shutdown();
    }

    @Test
    void realVulkanForcedDeviceLostPathPropagatesErrorsAndEvent() throws Exception {
        assumeRealVulkanReady("real Vulkan forced-device-lost integration test");

        var callbacks = new RecordingCallbacks();
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.of(
                "vulkan.mockContext", "false",
                "vulkan.forceDeviceLostOnRender", "true"
        )), callbacks);
        runtime.loadScene(validShadowSmokeScene(new ShadowDesc(1024, 0.0015f, 3, 2)));

        EngineException ex = org.junit.jupiter.api.Assertions.assertThrows(EngineException.class, runtime::render);
        assertEquals(EngineErrorCode.DEVICE_LOST, ex.code());
        assertTrue(callbacks.events.stream().anyMatch(DeviceLostEvent.class::isInstance));
        assertTrue(callbacks.errors.stream().anyMatch(err -> err.code() == EngineErrorCode.DEVICE_LOST));
        runtime.shutdown();
    }

    @Test
    void realVulkanForcedInitFailurePropagatesBackendInitFailed() {
        assumeRealVulkanReady("real Vulkan forced-init-failure integration test");

        var callbacks = new RecordingCallbacks();
        var runtime = new VulkanEngineRuntime();

        EngineException ex = org.junit.jupiter.api.Assertions.assertThrows(
                EngineException.class,
                () -> runtime.initialize(validConfig(Map.of(
                        "vulkan.mockContext", "false",
                        "vulkan.forceInitFailure", "true"
                )), callbacks)
        );

        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, ex.code());
        assertTrue(callbacks.errors.stream().anyMatch(err -> err.code() == EngineErrorCode.BACKEND_INIT_FAILED));
        runtime.shutdown();
    }

    private static EngineConfig validConfig(boolean mock) {
        return validConfig(Map.of("vulkan.mockContext", Boolean.toString(mock)));
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions) {
        return validConfig(backendOptions, QualityTier.MEDIUM);
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier qualityTier) {
        return validConfig(backendOptions, qualityTier, Path.of("."));
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier qualityTier, Path assetRoot) {
        return new EngineConfig(
                "vulkan",
                "vulkan-test",
                1280,
                720,
                1.0f,
                true,
                60,
                qualityTier,
                assetRoot,
                backendOptions
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
                "vulkan-scene",
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

    private static SceneDescriptor validPostProcessScene(boolean bloomEnabled) {
        SceneDescriptor base = validScene();
        PostProcessDesc post = new PostProcessDesc(
                true,
                true,
                1.05f,
                2.2f,
                bloomEnabled,
                1.0f,
                0.8f
        );
        return new SceneDescriptor(
                base.sceneName(),
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

    private static SceneDescriptor validReflectionsScene(String mode) {
        SceneDescriptor base = validScene();
        PostProcessDesc post = new PostProcessDesc(
                true,
                true,
                1.05f,
                2.2f,
                true,
                1.0f,
                0.8f,
                true,
                0.42f,
                1.0f,
                0.02f,
                1.0f,
                true,
                0.52f,
                true,
                0.56f,
                true,
                null,
                new ReflectionDesc(true, mode, 0.72f, 0.80f, 1.2f, 0.82f, 0.42f)
        );
        return new SceneDescriptor(
                base.sceneName(),
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

    private static SceneDescriptor validMaterialOverrideReflectionScene(ReflectionOverrideMode overrideMode) {
        SceneDescriptor base = validReflectionsScene("hybrid");
        MaterialDesc mat = new MaterialDesc(
                "mat",
                new Vec3(1, 1, 1),
                0.0f,
                0.5f,
                null,
                null,
                null,
                null,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                null,
                overrideMode
        );
        return new SceneDescriptor(
                base.sceneName(),
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                List.of(mat),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validAlphaTestedReflectionsScene(String mode) {
        SceneDescriptor base = validReflectionsScene(mode);
        MaterialDesc mat = new MaterialDesc(
                "mat",
                new Vec3(1, 1, 1),
                0.0f,
                0.5f,
                null,
                null,
                null,
                null,
                0f,
                true,
                false,
                1.0f,
                1.0f,
                1.0f,
                null,
                ReflectionOverrideMode.AUTO
        );
        return new SceneDescriptor(
                base.sceneName(),
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                List.of(mat),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validMixedMaterialOverrideReflectionScene() {
        SceneDescriptor base = validReflectionsScene("hybrid");
        MeshDesc meshAuto = new MeshDesc("mesh-auto", "xform", "mat-auto", "mesh.glb");
        MeshDesc meshProbe = new MeshDesc("mesh-probe", "xform", "mat-probe", "mesh.glb");
        MeshDesc meshSsr = new MeshDesc("mesh-ssr", "xform", "mat-ssr", "mesh.glb");
        MaterialDesc matAuto = new MaterialDesc(
                "mat-auto",
                new Vec3(1, 1, 1),
                0.0f,
                0.5f,
                null,
                null,
                null,
                null,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                null,
                ReflectionOverrideMode.AUTO
        );
        MaterialDesc matProbe = new MaterialDesc(
                "mat-probe",
                new Vec3(1, 1, 1),
                0.0f,
                0.5f,
                null,
                null,
                null,
                null,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                null,
                ReflectionOverrideMode.PROBE_ONLY
        );
        MaterialDesc matSsr = new MaterialDesc(
                "mat-ssr",
                new Vec3(1, 1, 1),
                0.0f,
                0.5f,
                null,
                null,
                null,
                null,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                null,
                ReflectionOverrideMode.SSR_ONLY
        );
        return new SceneDescriptor(
                base.sceneName(),
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                List.of(meshAuto, meshProbe, meshSsr),
                List.of(matAuto, matProbe, matSsr),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validPlanarClipHeightScene(float planeHeight) {
        SceneDescriptor base = validReflectionsScene("planar");
        ReflectionAdvancedDesc advanced = new ReflectionAdvancedDesc(
                true,
                5,
                2,
                true,
                planeHeight,
                0.2f,
                8.0f,
                false,
                false,
                2.0f,
                List.of(),
                false,
                0.75f,
                "planar"
        );
        PostProcessDesc post = new PostProcessDesc(
                base.postProcess().enabled(),
                base.postProcess().tonemapEnabled(),
                base.postProcess().exposure(),
                base.postProcess().gamma(),
                base.postProcess().bloomEnabled(),
                base.postProcess().bloomThreshold(),
                base.postProcess().bloomStrength(),
                base.postProcess().ssaoEnabled(),
                base.postProcess().ssaoStrength(),
                base.postProcess().ssaoRadius(),
                base.postProcess().ssaoBias(),
                base.postProcess().ssaoPower(),
                base.postProcess().smaaEnabled(),
                base.postProcess().smaaStrength(),
                base.postProcess().taaEnabled(),
                base.postProcess().taaBlend(),
                base.postProcess().taaLumaClipEnabled(),
                base.postProcess().antiAliasing(),
                base.postProcess().reflections(),
                advanced
        );
        return new SceneDescriptor(
                base.sceneName(),
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

    private static SceneDescriptor validPlanarExcludedScopeScene() {
        SceneDescriptor base = validReflectionsScene("planar");
        MaterialDesc mat = new MaterialDesc(
                "mat",
                new Vec3(1, 1, 1),
                0.0f,
                0.5f,
                null,
                null,
                null,
                null,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                null,
                ReflectionOverrideMode.PROBE_ONLY
        );
        return new SceneDescriptor(
                base.sceneName(),
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                List.of(mat),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validPlanarInteriorMirrorScene() {
        return validPlanarClipHeightScene(0.0f);
    }

    private static SceneDescriptor validPlanarOutdoorScene() {
        SceneDescriptor base = validPlanarClipHeightScene(-2.0f);
        CameraDesc outdoor = new CameraDesc("cam-outdoor", new Vec3(0f, 3f, 12f), new Vec3(-8f, 180f, 0f), 70f, 0.1f, 220f);
        return new SceneDescriptor(
                base.sceneName(),
                List.of(outdoor),
                "cam-outdoor",
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validPlanarMultiPlaneScene() {
        SceneDescriptor base = validPlanarClipHeightScene(0.4f);
        List<TransformDesc> transforms = new ArrayList<>(base.transforms());
        List<MeshDesc> meshes = new ArrayList<>(base.meshes());
        transforms.add(new TransformDesc(
                "xform-planar-secondary",
                new Vec3(0.0f, 1.2f, -1.5f),
                new Vec3(0.0f, 0.0f, 0.0f),
                new Vec3(1.2f, 1.0f, 1.2f)
        ));
        meshes.add(new MeshDesc(
                "mesh-planar-secondary",
                "xform-planar-secondary",
                meshes.isEmpty() ? "mat-planar" : meshes.get(0).materialId(),
                "models/plane.glb"
        ));
        return new SceneDescriptor(
                base.sceneName(),
                base.cameras(),
                base.activeCameraId(),
                transforms,
                meshes,
                base.materials(),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validPlanarDynamicCrossingScene(float yOffset) {
        SceneDescriptor base = validPlanarClipHeightScene(0.0f);
        TransformDesc xform = base.transforms().isEmpty()
                ? new TransformDesc("xform", new Vec3(0f, yOffset, 0f), new Vec3(0f, 0f, 0f), new Vec3(1f, 1f, 1f))
                : new TransformDesc(
                base.transforms().getFirst().id(),
                new Vec3(0f, yOffset, 0f),
                base.transforms().getFirst().rotationEulerDeg(),
                base.transforms().getFirst().scale()
        );
        return new SceneDescriptor(
                base.sceneName(),
                base.cameras(),
                base.activeCameraId(),
                List.of(xform),
                base.meshes(),
                base.materials(),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validReflectionProbeDiagnosticsScene() {
        SceneDescriptor base = validReflectionsScene("hybrid");
        ReflectionAdvancedDesc advanced = new ReflectionAdvancedDesc(
                true,
                5,
                2,
                false,
                0.0f,
                0.5f,
                6.0f,
                true,
                true,
                2.0f,
                List.of(
                        new ReflectionProbeDesc(
                                1,
                                new Vec3(0f, 0f, 0f),
                                new Vec3(-100f, -100f, -100f),
                                new Vec3(100f, 100f, 100f),
                                "textures/probes/atrium.ktx2",
                                2,
                                1.5f,
                                1.0f,
                                true
                        ),
                        new ReflectionProbeDesc(
                                2,
                                new Vec3(6f, 0f, 0f),
                                new Vec3(3f, -2f, -3f),
                                new Vec3(9f, 2f, 3f),
                                "textures/probes/atrium.ktx2",
                                1,
                                1.25f,
                                0.9f,
                                true
                        ),
                        new ReflectionProbeDesc(
                                3,
                                new Vec3(-6f, 0f, 0f),
                                new Vec3(-9f, -2f, -3f),
                                new Vec3(-3f, 2f, 3f),
                                "textures/probes/gallery.ktx2",
                                3,
                                1.0f,
                                1.1f,
                                false
                        )
                ),
                false,
                0.75f,
                "hybrid"
        );
        PostProcessDesc post = new PostProcessDesc(
                base.postProcess().enabled(),
                base.postProcess().tonemapEnabled(),
                base.postProcess().exposure(),
                base.postProcess().gamma(),
                base.postProcess().bloomEnabled(),
                base.postProcess().bloomThreshold(),
                base.postProcess().bloomStrength(),
                base.postProcess().ssaoEnabled(),
                base.postProcess().ssaoStrength(),
                base.postProcess().ssaoRadius(),
                base.postProcess().ssaoBias(),
                base.postProcess().ssaoPower(),
                base.postProcess().smaaEnabled(),
                base.postProcess().smaaStrength(),
                base.postProcess().taaEnabled(),
                base.postProcess().taaBlend(),
                base.postProcess().taaLumaClipEnabled(),
                base.postProcess().antiAliasing(),
                base.postProcess().reflections(),
                advanced
        );
        return new SceneDescriptor(
                base.sceneName(),
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

    private static SceneDescriptor validPlanarCameraScene(Vec3 position, Vec3 rotationEulerDeg) {
        SceneDescriptor base = validPlanarClipHeightScene(0.0f);
        CameraDesc camera = new CameraDesc("cam-planar-stress", position, rotationEulerDeg, 66f, 0.1f, 180f);
        return new SceneDescriptor(
                base.sceneName(),
                List.of(camera),
                "cam-planar-stress",
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validPlanarScopeStressScene(int probeOnlyCount, int ssrOnlyCount) {
        SceneDescriptor base = validPlanarClipHeightScene(0.0f);
        int total = Math.max(1, probeOnlyCount + ssrOnlyCount);
        List<TransformDesc> transforms = new ArrayList<>(total);
        List<MeshDesc> meshes = new ArrayList<>(total);
        MaterialDesc probeMat = new MaterialDesc(
                "mat-planar-scope-probe",
                new Vec3(1.0f, 1.0f, 1.0f),
                0.0f,
                0.25f,
                null,
                null,
                null,
                null,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                null,
                ReflectionOverrideMode.PROBE_ONLY
        );
        MaterialDesc ssrMat = new MaterialDesc(
                "mat-planar-scope-ssr",
                new Vec3(1.0f, 1.0f, 1.0f),
                0.0f,
                0.25f,
                null,
                null,
                null,
                null,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                null,
                ReflectionOverrideMode.SSR_ONLY
        );
        for (int i = 0; i < total; i++) {
            String id = "xform-planar-scope-" + i;
            float x = (float) ((i % 11) - 5) * 0.45f;
            float z = (float) (i / 11) * -0.65f;
            transforms.add(new TransformDesc(
                    id,
                    new Vec3(x, 0.0f, z),
                    new Vec3(0.0f, (i * 9.0f) % 360.0f, 0.0f),
                    new Vec3(1.0f, 1.0f, 1.0f)
            ));
            boolean probeOnly = i < probeOnlyCount;
            meshes.add(new MeshDesc(
                    "mesh-planar-scope-" + i,
                    id,
                    probeOnly ? probeMat.id() : ssrMat.id(),
                    "models/triangle.glb"
            ));
        }
        return new SceneDescriptor(
                base.sceneName(),
                base.cameras(),
                base.activeCameraId(),
                transforms,
                meshes,
                List.of(probeMat, ssrMat),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static String warningMessageByCode(org.dynamislight.api.runtime.EngineFrameResult frame, String code) {
        return frame.warnings().stream()
                .filter(w -> code.equals(w.code()))
                .findFirst()
                .map(org.dynamislight.api.event.EngineWarning::message)
                .orElse("");
    }

    private static void assertBlessedProfileTrendEnvelope(
            String profile,
            int expectedWindowFrames,
            double expectedHighRatioWarnMin,
            double expectedSloMeanSeverityMax,
            double expectedSloHighRatioMax,
            int expectedSloMinSamples,
            boolean adaptiveExpected,
            Map<String, String> forcedRisk
    ) throws Exception {
        Map<String, String> options = new java.util.LinkedHashMap<>(forcedRisk);
        options.put("vulkan.reflectionsProfile", profile);
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(options), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("hybrid"));

        runtime.render();
        var frame = runtime.render();

        String profileWarning = warningMessageByCode(frame, "REFLECTION_TELEMETRY_PROFILE_ACTIVE");
        String trendReport = warningMessageByCode(frame, "REFLECTION_SSR_TAA_ADAPTIVE_TREND_REPORT");
        assertTrue(profileWarning.contains("profile=" + profile));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveEnabled=" + adaptiveExpected));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendWindowFrames=" + expectedWindowFrames));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendHighRatioWarnMin=" + expectedHighRatioWarnMin));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloMeanSeverityMax=" + expectedSloMeanSeverityMax));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloHighRatioMax=" + expectedSloHighRatioMax));
        assertTrue(profileWarning.contains("ssrTaaAdaptiveTrendSloMinSamples=" + expectedSloMinSamples));
        double meanTemporalDelta = parseDoubleMetricField(trendReport, "meanTemporalDelta");
        double meanSsrStrengthDelta = parseDoubleMetricField(trendReport, "meanSsrStrengthDelta");
        double meanSsrStepScaleDelta = parseDoubleMetricField(trendReport, "meanSsrStepScaleDelta");
        if (adaptiveExpected) {
            assertTrue(meanTemporalDelta >= 0.0);
            assertTrue(meanSsrStrengthDelta <= 0.0);
            assertTrue(meanSsrStepScaleDelta >= 0.0);
        } else {
            assertEquals(0.0, meanTemporalDelta, 1e-6);
            assertEquals(0.0, meanSsrStrengthDelta, 1e-6);
            assertEquals(0.0, meanSsrStepScaleDelta, 1e-6);
        }
        runtime.shutdown();
    }

    private static void assertPlanarProfileDefaults(
            String profile,
            double expectedPlaneDeltaWarnMax,
            double expectedCoverageRatioWarnMin,
            int expectedEnvelopeWarnMinFrames,
            int expectedEnvelopeWarnCooldownFrames,
            double expectedPerfLow,
            double expectedPerfMedium,
            double expectedPerfHigh,
            double expectedPerfUltra,
            double expectedDrawInflationWarnMax,
            double expectedMemoryBudgetMb,
            int expectedPerfWarnMinFrames,
            int expectedPerfWarnCooldownFrames
    ) throws Exception {
        var runtime = new VulkanEngineRuntime();
        runtime.initialize(validConfig(Map.ofEntries(
                Map.entry("vulkan.mockContext", "true"),
                Map.entry("vulkan.reflectionsProfile", profile)
        )), new RecordingCallbacks());
        runtime.loadScene(validReflectionsScene("planar"));

        var frame = runtime.render();
        var perf = runtime.debugReflectionPlanarPerfDiagnostics();
        var stability = runtime.debugReflectionPlanarStabilityDiagnostics();
        String profileWarning = warningMessageByCode(frame, "REFLECTION_TELEMETRY_PROFILE_ACTIVE");

        assertEquals(expectedPlaneDeltaWarnMax, stability.planeDeltaWarnMax(), 1e-6);
        assertEquals(expectedCoverageRatioWarnMin, stability.coverageRatioWarnMin(), 1e-6);
        assertEquals(expectedEnvelopeWarnMinFrames, stability.warnMinFrames());
        assertEquals(expectedEnvelopeWarnCooldownFrames, stability.warnCooldownFrames());

        assertEquals(expectedPerfWarnMinFrames, perf.warnMinFrames());
        assertEquals(expectedPerfWarnCooldownFrames, perf.warnCooldownFrames());
        assertEquals(Math.round(expectedMemoryBudgetMb * 1024.0 * 1024.0), perf.memoryBudgetBytes());

        assertTrue(profileWarning.contains("planarEnvelopePlaneDeltaWarnMax=" + expectedPlaneDeltaWarnMax));
        assertTrue(profileWarning.contains("planarEnvelopeCoverageRatioWarnMin=" + expectedCoverageRatioWarnMin));
        assertTrue(profileWarning.contains("planarEnvelopeWarnMinFrames=" + expectedEnvelopeWarnMinFrames));
        assertTrue(profileWarning.contains("planarEnvelopeWarnCooldownFrames=" + expectedEnvelopeWarnCooldownFrames));
        assertTrue(profileWarning.contains("planarPerfMaxGpuMsLow=" + expectedPerfLow));
        assertTrue(profileWarning.contains("planarPerfMaxGpuMsMedium=" + expectedPerfMedium));
        assertTrue(profileWarning.contains("planarPerfMaxGpuMsHigh=" + expectedPerfHigh));
        assertTrue(profileWarning.contains("planarPerfMaxGpuMsUltra=" + expectedPerfUltra));
        assertTrue(profileWarning.contains("planarPerfDrawInflationWarnMax=" + expectedDrawInflationWarnMax));
        assertTrue(profileWarning.contains("planarPerfMemoryBudgetMb=" + expectedMemoryBudgetMb));
        assertTrue(profileWarning.contains("planarPerfWarnMinFrames=" + expectedPerfWarnMinFrames));
        assertTrue(profileWarning.contains("planarPerfWarnCooldownFrames=" + expectedPerfWarnCooldownFrames));
        runtime.shutdown();
    }

    private static int parseIntMetricField(String message, String key) {
        String token = key + "=";
        int start = message.indexOf(token);
        if (start < 0) {
            return -1;
        }
        int valueStart = start + token.length();
        int end = valueStart;
        while (end < message.length()) {
            char c = message.charAt(end);
            if ((c >= '0' && c <= '9') || c == '-') {
                end++;
            } else {
                break;
            }
        }
        if (end == valueStart) {
            return -1;
        }
        return Integer.parseInt(message.substring(valueStart, end));
    }

    private static double parseDoubleMetricField(String message, String key) {
        String token = key + "=";
        int start = message.indexOf(token);
        if (start < 0) {
            return Double.NaN;
        }
        int valueStart = start + token.length();
        int end = valueStart;
        while (end < message.length()) {
            char c = message.charAt(end);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                end++;
            } else {
                break;
            }
        }
        if (end == valueStart) {
            return Double.NaN;
        }
        return Double.parseDouble(message.substring(valueStart, end));
    }

    private static SceneDescriptor validMultiMeshScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc meshTriangle = new MeshDesc("mesh-triangle", "xform", "mat", "meshes/triangle.glb");
        MeshDesc meshQuad = new MeshDesc("mesh-quad", "xform", "mat", "meshes/quad.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "vulkan-multi-mesh-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(meshTriangle, meshQuad),
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }

    private static SceneDescriptor validShadowSmokeScene(ShadowDesc shadowDesc) {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(1, 3, 2), new Vec3(1, 1, 1), 1.0f, 15f, true, shadowDesc);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        SmokeEmitterDesc smoke = new SmokeEmitterDesc(
                "smoke-1",
                new Vec3(0, 0, 0),
                new Vec3(2, 1, 2),
                12f,
                0.8f,
                new Vec3(0.65f, 0.65f, 0.68f),
                0.4f,
                new Vec3(0f, 0.2f, 0f),
                0.25f,
                6f,
                true
        );

        return new SceneDescriptor(
                "vulkan-shadow-smoke-scene",
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

    private static SceneDescriptor validReusableScene(boolean transformVariant, boolean reorderMeshes) {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transformA = new TransformDesc(
                "xform-a",
                transformVariant ? new Vec3(-0.35f, 0.08f, 0) : new Vec3(-0.35f, 0, 0),
                new Vec3(0, 0, 0),
                new Vec3(1, 1, 1)
        );
        TransformDesc transformB = new TransformDesc(
                "xform-b",
                transformVariant ? new Vec3(0.35f, -0.08f, 0) : new Vec3(0.35f, 0, 0),
                new Vec3(0, 0, 0),
                new Vec3(1, 1, 1)
        );
        MeshDesc meshA = new MeshDesc("mesh-a", "xform-a", "mat-a", "meshes/triangle.glb");
        MeshDesc meshB = new MeshDesc("mesh-b", "xform-b", "mat-b", "meshes/quad.glb");
        List<MeshDesc> meshes = reorderMeshes ? List.of(meshB, meshA) : List.of(meshA, meshB);

        MaterialDesc matA = new MaterialDesc("mat-a", new Vec3(0.9f, 0.35f, 0.3f), 0.2f, 0.55f, null, null);
        MaterialDesc matB = new MaterialDesc("mat-b", new Vec3(0.3f, 0.7f, 0.9f), 0.5f, 0.35f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(1, 3, 2), new Vec3(1, 1, 1), 1.0f, 15f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "vulkan-reuse-scene",
                List.of(camera),
                "cam",
                List.of(transformA, transformB),
                meshes,
                List.of(matA, matB),
                List.of(light),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor validReusableSceneWithLightingVariant(float lightIntensity) {
        SceneDescriptor base = validReusableScene(false, false);
        LightDesc light = new LightDesc(
                "light",
                new Vec3(1, 3, 2),
                new Vec3(0.95f, 0.92f, 1.0f),
                lightIntensity,
                15f,
                false,
                null
        );
        return new SceneDescriptor(
                "vulkan-reuse-lighting-variant",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(light),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validReusableSceneWithTextureVariant(boolean textured) {
        SceneDescriptor base = validReusableScene(false, false);
        MaterialDesc matA = new MaterialDesc(
                "mat-a",
                new Vec3(0.9f, 0.35f, 0.3f),
                0.2f,
                0.55f,
                textured ? "assets/textures/albedo.png" : null,
                textured ? "assets/textures/normal.png" : null
        );
        MaterialDesc matB = new MaterialDesc("mat-b", new Vec3(0.3f, 0.7f, 0.9f), 0.5f, 0.35f, null, null);
        return new SceneDescriptor(
                textured ? "vulkan-reuse-texture-variant-on" : "vulkan-reuse-texture-variant-off",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                List.of(matA, matB),
                base.lights(),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validLargeMeshScene(int meshCount) {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 8), new Vec3(0, 0, 0), 60f, 0.1f, 200f);
        List<TransformDesc> transforms = new ArrayList<>(meshCount);
        List<MeshDesc> meshes = new ArrayList<>(meshCount);
        for (int i = 0; i < meshCount; i++) {
            String transformId = "xform-" + i;
            float x = ((i % 20) - 10) * 0.4f;
            float y = ((i / 20) - 7) * 0.35f;
            transforms.add(new TransformDesc(transformId, new Vec3(x, y, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
            String meshPath = (i % 2 == 0) ? "meshes/quad.glb" : "meshes/triangle.glb";
            meshes.add(new MeshDesc("mesh-" + i, transformId, "mat", meshPath));
        }

        MaterialDesc mat = new MaterialDesc("mat", new Vec3(0.85f, 0.85f, 0.9f), 0.2f, 0.55f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(1, 3, 2), new Vec3(1, 1, 1), 1.0f, 15f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "vulkan-large-mesh-scene",
                List.of(camera),
                "cam",
                transforms,
                meshes,
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor validReusableSceneWithPostFogVariant(float fogDensity, float exposure) {
        SceneDescriptor base = validReusableScene(false, false);
        FogDesc fog = new FogDesc(
                true,
                FogMode.EXPONENTIAL,
                new Vec3(0.52f, 0.57f, 0.64f),
                fogDensity,
                0f,
                0.7f,
                0f,
                0f,
                0f
        );
        PostProcessDesc post = new PostProcessDesc(
                true,
                true,
                exposure,
                2.2f,
                true,
                1.0f,
                0.75f
        );
        return new SceneDescriptor(
                "vulkan-reuse-post-fog-variant",
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

    private static SceneDescriptor validSparseReusableScene(boolean variant) {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 6), new Vec3(0, 0, 0), 62f, 0.1f, 100f);
        TransformDesc transformA = new TransformDesc("xform-a", new Vec3(-1.2f, 0f, 0f), new Vec3(0, 0, 0), new Vec3(1f, 1f, 1f));
        TransformDesc transformB = new TransformDesc("xform-b", new Vec3(-0.4f, 0f, 0f), new Vec3(0, 0, 0), new Vec3(1f, 1f, 1f));
        TransformDesc transformC = new TransformDesc("xform-c", new Vec3(0.4f, 0f, 0f), new Vec3(0, 0, 0), new Vec3(1f, 1f, 1f));
        TransformDesc transformD = new TransformDesc("xform-d", new Vec3(1.2f, 0f, 0f), new Vec3(0, 0, 0), new Vec3(1f, 1f, 1f));

        MeshDesc meshA = new MeshDesc("mesh-a", "xform-a", "mat-a", "meshes/triangle.glb");
        MeshDesc meshB = new MeshDesc("mesh-b", "xform-b", "mat-b", "meshes/quad.glb");
        MeshDesc meshC = new MeshDesc("mesh-c", "xform-c", "mat-c", "meshes/triangle.glb");
        MeshDesc meshD = new MeshDesc("mesh-d", "xform-d", "mat-d", "meshes/quad.glb");

        MaterialDesc matA = new MaterialDesc("mat-a", variant ? new Vec3(0.95f, 0.38f, 0.30f) : new Vec3(0.88f, 0.34f, 0.30f), 0.22f, 0.56f, null, null);
        MaterialDesc matB = new MaterialDesc("mat-b", new Vec3(0.30f, 0.70f, 0.90f), 0.48f, 0.38f, null, null);
        MaterialDesc matC = new MaterialDesc("mat-c", new Vec3(0.70f, 0.72f, 0.76f), 0.10f, 0.80f, null, null);
        MaterialDesc matD = new MaterialDesc("mat-d", variant ? new Vec3(0.52f, 0.82f, 0.58f) : new Vec3(0.48f, 0.76f, 0.54f), 0.30f, 0.52f, null, null);

        LightDesc light = new LightDesc("light", new Vec3(1, 3, 2), new Vec3(1, 1, 1), 1.0f, 15f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                variant ? "vulkan-sparse-reuse-variant" : "vulkan-sparse-reuse-base",
                List.of(camera),
                "cam",
                List.of(transformA, transformB, transformC, transformD),
                List.of(meshA, meshB, meshC, meshD),
                List.of(matA, matB, matC, matD),
                List.of(light),
                env,
                fog,
                List.of()
        );
    }

    private static SceneDescriptor validIblScene() {
        SceneDescriptor base = validScene();
        EnvironmentDesc env = new EnvironmentDesc(
                base.environment().ambientColor(),
                base.environment().ambientIntensity(),
                base.environment().skyboxAssetPath(),
                "textures/ibl_irradiance.ktx2",
                "textures/ibl_radiance.ktx2",
                "textures/ibl_brdf_lut.png"
        );
        return new SceneDescriptor(
                "vulkan-ibl-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                env,
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validMissingIblScene() {
        SceneDescriptor base = validScene();
        EnvironmentDesc env = new EnvironmentDesc(
                base.environment().ambientColor(),
                base.environment().ambientIntensity(),
                base.environment().skyboxAssetPath(),
                "textures/missing_ibl_irradiance.ktx2",
                "textures/missing_ibl_radiance.ktx2",
                "textures/missing_ibl_brdf_lut.png"
        );
        return new SceneDescriptor(
                "vulkan-ibl-missing-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                env,
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validSkyboxOnlyIblScene() {
        SceneDescriptor base = validScene();
        EnvironmentDesc env = new EnvironmentDesc(
                base.environment().ambientColor(),
                base.environment().ambientIntensity(),
                "textures/skybox.hdr"
        );
        return new SceneDescriptor(
                "vulkan-ibl-skybox-only-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                env,
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validKtxSkyboxFallbackScene() {
        SceneDescriptor base = validScene();
        EnvironmentDesc env = new EnvironmentDesc(
                base.environment().ambientColor(),
                base.environment().ambientIntensity(),
                "textures/skybox.hdr",
                "textures/ibl_irradiance.ktx2",
                "textures/ibl_radiance.ktx2",
                "textures/albedo.png"
        );
        return new SceneDescriptor(
                "vulkan-ibl-ktx-skybox-fallback-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                base.lights(),
                env,
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validPointShadowScene() {
        SceneDescriptor base = validScene();
        LightDesc pointShadow = new LightDesc(
                "point-shadow",
                new Vec3(0.5f, 1.5f, 1.8f),
                new Vec3(1f, 0.9f, 0.8f),
                1.2f,
                14f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.POINT
        );
        return new SceneDescriptor(
                "vulkan-point-shadow-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(pointShadow),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validSpotShadowScene() {
        SceneDescriptor base = validScene();
        LightDesc spotShadow = new LightDesc(
                "spot-shadow",
                new Vec3(0.4f, 1.6f, 1.5f),
                new Vec3(0.9f, 0.9f, 1f),
                1.1f,
                12f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.SPOT,
                new Vec3(0f, -1f, 0f),
                18f,
                32f
        );
        return new SceneDescriptor(
                "vulkan-spot-shadow-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(spotShadow),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validMultiSpotShadowScene() {
        SceneDescriptor base = validScene();
        LightDesc spotA = new LightDesc(
                "spot-shadow-a",
                new Vec3(0.4f, 1.6f, 1.5f),
                new Vec3(0.9f, 0.9f, 1f),
                1.1f,
                12f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.SPOT,
                new Vec3(0f, -1f, 0f),
                18f,
                32f
        );
        LightDesc spotB = new LightDesc(
                "spot-shadow-b",
                new Vec3(-0.8f, 1.4f, 1.7f),
                new Vec3(1f, 0.92f, 0.8f),
                1.0f,
                11f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.SPOT,
                new Vec3(0.15f, -1f, -0.2f),
                16f,
                30f
        );
        LightDesc spotC = new LightDesc(
                "spot-shadow-c",
                new Vec3(1.1f, 1.7f, 1.2f),
                new Vec3(0.85f, 0.95f, 1f),
                1.05f,
                13f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.SPOT,
                new Vec3(-0.2f, -1f, 0.1f),
                20f,
                34f
        );
        return new SceneDescriptor(
                "vulkan-multi-spot-shadow-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(spotA, spotB, spotC),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validMultiSpotShadowSceneReordered() {
        SceneDescriptor base = validMultiSpotShadowScene();
        List<LightDesc> lights = base.lights();
        return new SceneDescriptor(
                "vulkan-multi-spot-shadow-scene-reordered",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(lights.get(2), lights.get(0), lights.get(1)),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validMultiPointShadowScene() {
        SceneDescriptor base = validScene();
        LightDesc pointA = new LightDesc(
                "point-shadow-a",
                new Vec3(0.4f, 1.6f, 1.5f),
                new Vec3(0.9f, 0.9f, 1f),
                1.1f,
                12f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.POINT
        );
        LightDesc pointB = new LightDesc(
                "point-shadow-b",
                new Vec3(-0.8f, 1.4f, 1.7f),
                new Vec3(1f, 0.92f, 0.8f),
                1.0f,
                11f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.POINT
        );
        LightDesc pointC = new LightDesc(
                "point-shadow-c",
                new Vec3(1.1f, 1.7f, 1.2f),
                new Vec3(0.85f, 0.95f, 1f),
                1.05f,
                13f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.POINT
        );
        return new SceneDescriptor(
                "vulkan-multi-point-shadow-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(pointA, pointB, pointC),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static SceneDescriptor validBalancedMultiPointShadowScene() {
        SceneDescriptor base = validScene();
        LightDesc pointA = new LightDesc(
                "point-shadow-balanced-a",
                new Vec3(0.4f, 1.6f, 1.5f),
                new Vec3(0.9f, 0.9f, 1f),
                1.0f,
                12f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.POINT
        );
        LightDesc pointB = new LightDesc(
                "point-shadow-balanced-b",
                new Vec3(-0.8f, 1.4f, 1.7f),
                new Vec3(0.9f, 0.9f, 1f),
                1.0f,
                12f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.POINT
        );
        LightDesc pointC = new LightDesc(
                "point-shadow-balanced-c",
                new Vec3(1.1f, 1.7f, 1.2f),
                new Vec3(0.9f, 0.9f, 1f),
                1.0f,
                12f,
                true,
                new ShadowDesc(1024, 0.0012f, 3, 1),
                LightType.POINT
        );
        return new SceneDescriptor(
                "vulkan-multi-point-shadow-balanced-scene",
                base.cameras(),
                base.activeCameraId(),
                base.transforms(),
                base.meshes(),
                base.materials(),
                List.of(pointA, pointB, pointC),
                base.environment(),
                base.fog(),
                base.smokeEmitters(),
                base.postProcess()
        );
    }

    private static EngineInput emptyInput() {
        return new EngineInput(0, 0, 0, 0, false, false, Set.<KeyCode>of(), 0.0);
    }

    private static int parseWarningIntField(String message, String field) {
        if (message == null || message.isBlank() || field == null || field.isBlank()) {
            return -1;
        }
        String prefix = field + "=";
        for (String token : message.split("\\s+")) {
            if (!token.startsWith(prefix)) {
                continue;
            }
            try {
                return Integer.parseInt(token.substring(prefix.length()));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String parseWarningStringField(String message, String field) {
        if (message == null || message.isBlank() || field == null || field.isBlank()) {
            return "";
        }
        String prefix = field + "=";
        for (String token : message.split("\\s+")) {
            if (token.startsWith(prefix)) {
                return token.substring(prefix.length());
            }
        }
        return "";
    }

    private static void writeKtx2Stub(Path path, int width, int height) throws Exception {
        byte[] header = new byte[68];
        byte[] identifier = new byte[]{
                (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
        };
        System.arraycopy(identifier, 0, header, 0, identifier.length);
        putIntLE(header, 12, 37);
        putIntLE(header, 16, 1);
        putIntLE(header, 20, Math.max(1, width));
        putIntLE(header, 24, Math.max(1, height));
        putIntLE(header, 28, 0);
        putIntLE(header, 32, 0);
        putIntLE(header, 36, 1);
        putIntLE(header, 40, 1);
        putIntLE(header, 44, 0);
        Files.write(path, header);
    }

    private static void writeKtx2Rgba8(Path path, int width, int height, byte r, byte g, byte b, byte a) throws Exception {
        int pixels = Math.max(1, width) * Math.max(1, height);
        byte[] rgba = new byte[pixels * 4];
        for (int i = 0; i < pixels; i++) {
            int idx = i * 4;
            rgba[idx] = r;
            rgba[idx + 1] = g;
            rgba[idx + 2] = b;
            rgba[idx + 3] = a;
        }

        int headerSize = 80;
        int levelIndexSize = 24;
        int dataOffset = headerSize + levelIndexSize;
        byte[] out = new byte[dataOffset + rgba.length];
        byte[] identifier = new byte[]{
                (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
        };
        System.arraycopy(identifier, 0, out, 0, identifier.length);
        putIntLE(out, 12, 37);
        putIntLE(out, 16, 1);
        putIntLE(out, 20, Math.max(1, width));
        putIntLE(out, 24, Math.max(1, height));
        putIntLE(out, 28, 0);
        putIntLE(out, 32, 0);
        putIntLE(out, 36, 1);
        putIntLE(out, 40, 1);
        putIntLE(out, 44, 0);
        putIntLE(out, 48, 0);
        putIntLE(out, 52, 0);
        putIntLE(out, 56, 0);
        putIntLE(out, 60, 0);
        putLongLE(out, 64, 0L);
        putLongLE(out, 72, 0L);
        putLongLE(out, 80, dataOffset);
        putLongLE(out, 88, rgba.length);
        putLongLE(out, 96, rgba.length);
        System.arraycopy(rgba, 0, out, dataOffset, rgba.length);
        Files.write(path, out);
    }

    private static void writeKtx2SupercompressedStub(Path path, int width, int height) throws Exception {
        byte[] header = new byte[104];
        byte[] identifier = new byte[]{
                (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
        };
        System.arraycopy(identifier, 0, header, 0, identifier.length);
        putIntLE(header, 12, 0);
        putIntLE(header, 16, 1);
        putIntLE(header, 20, Math.max(1, width));
        putIntLE(header, 24, Math.max(1, height));
        putIntLE(header, 28, 0);
        putIntLE(header, 32, 0);
        putIntLE(header, 36, 1);
        putIntLE(header, 40, 1);
        putIntLE(header, 44, 1); // BasisLZ supercompression
        Files.write(path, header);
    }

    private static void writeKtx2ZlibRgba8(Path path, int width, int height, byte r, byte g, byte b, byte a) throws Exception {
        int pixels = Math.max(1, width) * Math.max(1, height);
        byte[] rgba = new byte[pixels * 4];
        for (int i = 0; i < pixels; i++) {
            int idx = i * 4;
            rgba[idx] = r;
            rgba[idx + 1] = g;
            rgba[idx + 2] = b;
            rgba[idx + 3] = a;
        }
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION);
        byte[] compressed;
        try {
            deflater.setInput(rgba);
            deflater.finish();
            byte[] out = new byte[rgba.length + 64];
            int len = deflater.deflate(out);
            compressed = new byte[len];
            System.arraycopy(out, 0, compressed, 0, len);
        } finally {
            deflater.end();
        }
        int headerSize = 80;
        int levelIndexSize = 24;
        int dataOffset = headerSize + levelIndexSize;
        byte[] out = new byte[dataOffset + compressed.length];
        byte[] identifier = new byte[]{
                (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
        };
        System.arraycopy(identifier, 0, out, 0, identifier.length);
        putIntLE(out, 12, 37);
        putIntLE(out, 16, 1);
        putIntLE(out, 20, Math.max(1, width));
        putIntLE(out, 24, Math.max(1, height));
        putIntLE(out, 28, 0);
        putIntLE(out, 32, 0);
        putIntLE(out, 36, 1);
        putIntLE(out, 40, 1);
        putIntLE(out, 44, 3);
        putIntLE(out, 48, 0);
        putIntLE(out, 52, 0);
        putIntLE(out, 56, 0);
        putIntLE(out, 60, 0);
        putLongLE(out, 64, 0L);
        putLongLE(out, 72, 0L);
        putLongLE(out, 80, dataOffset);
        putLongLE(out, 88, compressed.length);
        putLongLE(out, 96, rgba.length);
        System.arraycopy(compressed, 0, out, dataOffset, compressed.length);
        Files.write(path, out);
    }

    private static void writeKtx2ZstdRgba8(Path path, int width, int height, byte r, byte g, byte b, byte a) throws Exception {
        int pixels = Math.max(1, width) * Math.max(1, height);
        byte[] rgba = new byte[pixels * 4];
        for (int i = 0; i < pixels; i++) {
            int idx = i * 4;
            rgba[idx] = r;
            rgba[idx + 1] = g;
            rgba[idx + 2] = b;
            rgba[idx + 3] = a;
        }
        byte[] compressed = com.github.luben.zstd.Zstd.compress(rgba, 3);
        int headerSize = 80;
        int levelIndexSize = 24;
        int dataOffset = headerSize + levelIndexSize;
        byte[] out = new byte[dataOffset + compressed.length];
        byte[] identifier = new byte[]{
                (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
        };
        System.arraycopy(identifier, 0, out, 0, identifier.length);
        putIntLE(out, 12, 37);
        putIntLE(out, 16, 1);
        putIntLE(out, 20, Math.max(1, width));
        putIntLE(out, 24, Math.max(1, height));
        putIntLE(out, 28, 0);
        putIntLE(out, 32, 0);
        putIntLE(out, 36, 1);
        putIntLE(out, 40, 1);
        putIntLE(out, 44, 2);
        putIntLE(out, 48, 0);
        putIntLE(out, 52, 0);
        putIntLE(out, 56, 0);
        putIntLE(out, 60, 0);
        putLongLE(out, 64, 0L);
        putLongLE(out, 72, 0L);
        putLongLE(out, 80, dataOffset);
        putLongLE(out, 88, compressed.length);
        putLongLE(out, 96, rgba.length);
        System.arraycopy(compressed, 0, out, dataOffset, compressed.length);
        Files.write(path, out);
    }

    private static boolean writeBasisLzKtx2(Path path, int width, int height, byte r, byte g, byte b, byte a) {
        java.nio.ByteBuffer source = null;
        long texturePtr = 0L;
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            int pixels = Math.max(1, width) * Math.max(1, height);
            byte[] rgba = new byte[pixels * 4];
            for (int i = 0; i < pixels; i++) {
                int idx = i * 4;
                rgba[idx] = r;
                rgba[idx + 1] = g;
                rgba[idx + 2] = b;
                rgba[idx + 3] = a;
            }
            int headerSize = 80;
            int levelIndexSize = 24;
            int dataOffset = headerSize + levelIndexSize;
            byte[] out = new byte[dataOffset + rgba.length];
            byte[] identifier = new byte[]{
                    (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
            };
            System.arraycopy(identifier, 0, out, 0, identifier.length);
            putIntLE(out, 12, 37);
            putIntLE(out, 16, 1);
            putIntLE(out, 20, Math.max(1, width));
            putIntLE(out, 24, Math.max(1, height));
            putIntLE(out, 28, 0);
            putIntLE(out, 32, 0);
            putIntLE(out, 36, 1);
            putIntLE(out, 40, 1);
            putIntLE(out, 44, 0);
            putIntLE(out, 48, 0);
            putIntLE(out, 52, 0);
            putIntLE(out, 56, 0);
            putIntLE(out, 60, 0);
            putLongLE(out, 64, 0L);
            putLongLE(out, 72, 0L);
            putLongLE(out, 80, dataOffset);
            putLongLE(out, 88, rgba.length);
            putLongLE(out, 96, rgba.length);
            System.arraycopy(rgba, 0, out, dataOffset, rgba.length);

            source = org.lwjgl.system.MemoryUtil.memAlloc(out.length);
            source.put(out).flip();
            org.lwjgl.PointerBuffer textureOut = stack.mallocPointer(1);
            int create = org.lwjgl.util.ktx.KTX.ktxTexture2_CreateFromMemory(
                    source,
                    org.lwjgl.util.ktx.KTX.KTX_TEXTURE_CREATE_LOAD_IMAGE_DATA_BIT,
                    textureOut
            );
            if (create != org.lwjgl.util.ktx.KTX.KTX_SUCCESS) {
                return false;
            }
            texturePtr = textureOut.get(0);
            if (texturePtr == 0L) {
                return false;
            }
            org.lwjgl.util.ktx.ktxTexture2 texture2 = org.lwjgl.util.ktx.ktxTexture2.create(texturePtr);
            int compress = org.lwjgl.util.ktx.KTX.ktxTexture2_CompressBasis(texture2, 0);
            if (compress != org.lwjgl.util.ktx.KTX.KTX_SUCCESS) {
                return false;
            }
            int write = org.lwjgl.util.ktx.KTX.ktxWriteToNamedFile(
                    org.lwjgl.util.ktx.ktxTexture.create(texturePtr),
                    path.toAbsolutePath().toString()
            );
            return write == org.lwjgl.util.ktx.KTX.KTX_SUCCESS;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (texturePtr != 0L) {
                try {
                    org.lwjgl.util.ktx.KTX.ktxTexture_Destroy(org.lwjgl.util.ktx.ktxTexture.create(texturePtr));
                } catch (Throwable ignored) {
                    // ignore
                }
            }
            if (source != null) {
                org.lwjgl.system.MemoryUtil.memFree(source);
            }
        }
    }

    private static void putIntLE(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static void putLongLE(byte[] buffer, int offset, long value) {
        for (int i = 0; i < 8; i++) {
            buffer[offset + i] = (byte) ((value >>> (8 * i)) & 0xFF);
        }
    }

    private static void assumeRealVulkanReady(String testLabel) {
        assumeTrue(Boolean.getBoolean("dle.test.vulkan.real"),
                "Set -Ddle.test.vulkan.real=true to run " + testLabel);
        try {
            boolean init = GLFW.glfwInit();
            assumeTrue(init, "Skipping " + testLabel + ": GLFW init failed on this machine");
            GLFW.glfwTerminate();
        } catch (Throwable t) {
            assumeTrue(false, "Skipping " + testLabel + ": LWJGL native runtime unavailable (" + t.getClass().getSimpleName() + ")");
        }
    }

    private static void assumeRealVulkanLongReady(String testLabel) {
        assumeRealVulkanReady(testLabel);
        assumeTrue(Boolean.getBoolean("dle.test.vulkan.real.long"),
                "Set -Ddle.test.vulkan.real.long=true to run " + testLabel);
    }

    private static boolean canDecodeViaStb(Path path) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var x = stack.mallocInt(1);
            var y = stack.mallocInt(1);
            var channels = stack.mallocInt(1);
            java.nio.ByteBuffer pixels = org.lwjgl.stb.STBImage.stbi_load(
                    path.toAbsolutePath().toString(),
                    x,
                    y,
                    channels,
                    4
            );
            if (pixels == null || x.get(0) <= 0 || y.get(0) <= 0) {
                return false;
            }
            org.lwjgl.stb.STBImage.stbi_image_free(pixels);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
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
}
