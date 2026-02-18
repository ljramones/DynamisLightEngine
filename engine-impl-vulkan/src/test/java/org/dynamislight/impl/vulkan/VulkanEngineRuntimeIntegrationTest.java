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
import org.dynamislight.api.scene.ReflectionDesc;
import org.dynamislight.api.scene.ReflectionOverrideMode;
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

    private static String warningMessageByCode(org.dynamislight.api.runtime.EngineFrameResult frame, String code) {
        return frame.warnings().stream()
                .filter(w -> code.equals(w.code()))
                .findFirst()
                .map(org.dynamislight.api.event.EngineWarning::message)
                .orElse("");
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
