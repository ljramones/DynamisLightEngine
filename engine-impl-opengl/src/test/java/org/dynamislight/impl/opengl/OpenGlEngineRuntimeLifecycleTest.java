package org.dynamislight.impl.opengl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.dynamislight.api.scene.CameraDesc;
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
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.ResourceHotReloadedEvent;
import org.dynamislight.api.resource.ResourceState;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.event.SceneLoadFailedEvent;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.spi.EngineBackendProvider;
import org.junit.jupiter.api.Test;

class OpenGlEngineRuntimeLifecycleTest {
    @Test
    void serviceLoaderFindsOpenGlProvider() {
        var providers = ServiceLoader.load(EngineBackendProvider.class);
        boolean found = providers.stream().anyMatch(p -> p.get().backendId().equals("opengl"));
        assertTrue(found, "Expected opengl provider to be discoverable");
    }

    @Test
    void renderBeforeInitializeThrowsInvalidState() {
        var runtime = new OpenGlEngineRuntime();

        EngineException ex = assertThrows(EngineException.class, runtime::render);

        assertEquals(EngineErrorCode.INVALID_STATE, ex.code());
    }

    @Test
    void loadSceneBeforeInitializeThrowsInvalidState() {
        var runtime = new OpenGlEngineRuntime();

        EngineException ex = assertThrows(EngineException.class, () -> runtime.loadScene(validScene()));

        assertEquals(EngineErrorCode.INVALID_STATE, ex.code());
    }

    @Test
    void initializeThenRenderProducesFrameAndStats() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();

        runtime.initialize(validConfig(), callbacks);
        runtime.loadScene(validScene());
        EngineFrameResult update = runtime.update(1.0 / 60.0, emptyInput());
        EngineFrameResult render = runtime.render();

        assertNotNull(update);
        assertNotNull(render.frameHandle());
        assertEquals(1L, render.frameIndex());
        assertFalse(render.warnings().isEmpty());
        assertTrue(runtime.getStats().fps() > 0.0);
        assertFalse(callbacks.logs.isEmpty());

        runtime.shutdown();
        runtime.shutdown();
    }

    @Test
    void fullLifecycleFlowSupportsResizeAndShutdown() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();

        runtime.initialize(validConfig(), callbacks);
        runtime.loadScene(validScene());
        runtime.update(1.0 / 60.0, emptyInput());
        runtime.resize(1920, 1080, 1.0f);
        EngineFrameResult frame = runtime.render();
        runtime.shutdown();

        assertEquals(1L, frame.frameIndex());
        assertTrue(callbacks.logs.stream().anyMatch(log -> "LIFECYCLE".equals(log.category())));
        assertTrue(callbacks.logs.stream().anyMatch(log -> "SCENE".equals(log.category())));
        assertTrue(callbacks.logs.stream().anyMatch(log -> "SHADER".equals(log.category())));
        assertTrue(callbacks.logs.stream().anyMatch(log -> "RENDER".equals(log.category())));
        assertTrue(callbacks.logs.stream().anyMatch(log -> "PERF".equals(log.category())));
        assertFalse(callbacks.events.isEmpty());
    }

    @Test
    void initializeTwiceThrowsInvalidState() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());

        EngineException ex = assertThrows(EngineException.class,
                () -> runtime.initialize(validConfig(), new RecordingCallbacks()));

        assertEquals(EngineErrorCode.INVALID_STATE, ex.code());
    }

    @Test
    void invalidResizeThrowsInvalidArgument() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());

        EngineException ex = assertThrows(EngineException.class, () -> runtime.resize(0, 720, 1.0f));

        assertEquals(EngineErrorCode.INVALID_ARGUMENT, ex.code());
    }

    @Test
    void negativeDeltaTimeThrowsInvalidArgument() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());

        EngineException ex = assertThrows(EngineException.class, () -> runtime.update(-0.1, emptyInput()));

        assertEquals(EngineErrorCode.INVALID_ARGUMENT, ex.code());
    }

    @Test
    void renderAfterShutdownThrowsInvalidState() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.shutdown();

        EngineException ex = assertThrows(EngineException.class, runtime::render);

        assertEquals(EngineErrorCode.INVALID_STATE, ex.code());
    }

    @Test
    void forcedInitFailureMapsToBackendInitFailed() {
        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();

        EngineException ex = assertThrows(EngineException.class,
                () -> runtime.initialize(validConfig(Map.of("opengl.forceInitFailure", "true")), callbacks));

        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, ex.code());
        assertTrue(callbacks.logs.stream().anyMatch(log -> "ERROR".equals(log.category())));
        assertFalse(callbacks.errors.isEmpty());
        assertEquals(EngineErrorCode.BACKEND_INIT_FAILED, callbacks.errors.getFirst().code());
    }

    @Test
    void fogQualityTierImpactsFogSamplingAndDensityScale() {
        assertEquals(4, OpenGlEngineRuntime.fogSteps(QualityTier.LOW));
        assertEquals(8, OpenGlEngineRuntime.fogSteps(QualityTier.MEDIUM));
        assertEquals(16, OpenGlEngineRuntime.fogSteps(QualityTier.HIGH));
        assertEquals(0, OpenGlEngineRuntime.fogSteps(QualityTier.ULTRA));

        assertTrue(OpenGlEngineRuntime.fogDensityScale(QualityTier.LOW) < OpenGlEngineRuntime.fogDensityScale(QualityTier.HIGH));
        assertTrue(OpenGlEngineRuntime.fogDensityScale(QualityTier.ULTRA) > OpenGlEngineRuntime.fogDensityScale(QualityTier.HIGH));
    }

    @Test
    void loadSceneWithFogEnabledRendersSuccessfully() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validFogScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.frameIndex() > 0);
    }

    @Test
    void lowTierSmokeEmitsQualityDegradedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validLowQualityConfig(), new RecordingCallbacks());
        runtime.loadScene(validSmokeScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "SMOKE_QUALITY_DEGRADED".equals(w.code())));
    }

    @Test
    void ultraTierSmokeDoesNotEmitQualityDegradedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validUltraQualityConfig(), new RecordingCallbacks());
        runtime.loadScene(validSmokeScene());

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SMOKE_QUALITY_DEGRADED".equals(w.code())));
    }

    @Test
    void bloomRequestedDoesNotEmitNotImplementedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validPostProcessScene(true));

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "BLOOM_NOT_IMPLEMENTED".equals(w.code())));
    }

    @Test
    void iblEnvironmentEmitsBaselineActiveWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_PREFILTER_APPROX_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_MULTI_TAP_SPEC_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BRDF_ENERGY_COMP_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_CONTAINER_FALLBACK".equals(w.code())));
    }

    @Test
    void iblMissingAssetsEmitFallbackWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validMissingIblScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
    }

    @Test
    void iblSkyboxOnlySceneEnablesBaselineWithFallbackWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validSkyboxOnlyIblScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_BASELINE_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_SKYBOX_DERIVED_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
    }

    @Test
    void iblKtxPathsCanFallbackToSkyboxInputs() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(Map.of("opengl.mockContext", "true"), QualityTier.MEDIUM, Path.of("..", "assets")), new RecordingCallbacks());
        runtime.loadScene(validKtxSkyboxFallbackScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_SKYBOX_FALLBACK_ACTIVE".equals(w.code())));
        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_SKYBOX_DERIVED_ACTIVE".equals(w.code())));
        assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
    }

    @Test
    void iblExistingKtxWithoutSidecarEmitsDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-irr-", ".ktx2");
        Path rad = Files.createTempFile("dle-rad-", ".ktx2");
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

            var runtime = new OpenGlEngineRuntime();
            runtime.initialize(validConfig(), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "ibl-ktx-decode-unavailable-scene",
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

            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_CONTAINER_FALLBACK".equals(w.code())));
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblDecodableKtx2DoesNotEmitDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-irr-decode-", ".ktx2");
        Path rad = Files.createTempFile("dle-rad-decode-", ".ktx2");
        try {
            writeKtx2Rgba8(irr, 2, 2, (byte) 200, (byte) 180, (byte) 120, (byte) 255);
            writeKtx2Rgba8(rad, 2, 2, (byte) 220, (byte) 210, (byte) 180, (byte) 255);
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
            var runtime = new OpenGlEngineRuntime();
            runtime.initialize(validConfig(), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "ibl-ktx-decode-available-scene",
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

            EngineFrameResult frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_ASSET_FALLBACK_ACTIVE".equals(w.code())));
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblZlibSupercompressedKtx2DoesNotEmitDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-irr-zlib-", ".ktx2");
        Path rad = Files.createTempFile("dle-rad-zlib-", ".ktx2");
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
            var runtime = new OpenGlEngineRuntime();
            runtime.initialize(validConfig(), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "ibl-ktx-zlib-decode-available-scene",
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

            EngineFrameResult frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblZstdSupercompressedKtx2DoesNotEmitDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-irr-zstd-", ".ktx2");
        Path rad = Files.createTempFile("dle-rad-zstd-", ".ktx2");
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
            var runtime = new OpenGlEngineRuntime();
            runtime.initialize(validConfig(), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "ibl-ktx-zstd-decode-available-scene",
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

            EngineFrameResult frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblBasisLzKtx2EmitsTranscodeRequiredWarning() throws Exception {
        Path irr = Files.createTempFile("dle-irr-super-", ".ktx2");
        Path rad = Files.createTempFile("dle-rad-super-", ".ktx2");
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
            var runtime = new OpenGlEngineRuntime();
            runtime.initialize(validConfig(), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "ibl-ktx-supercompressed-scene",
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

            EngineFrameResult frame = runtime.render();
            assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_KTX_TRANSCODE_REQUIRED".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblBasisLzKtx2TranscodedDoesNotEmitTranscodeRequiredWarning() throws Exception {
        Path irr = Files.createTempFile("dle-irr-basis-real-", ".ktx2");
        Path rad = Files.createTempFile("dle-rad-basis-real-", ".ktx2");
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
            var runtime = new OpenGlEngineRuntime();
            runtime.initialize(validConfig(), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "ibl-ktx-basis-transcoded-scene",
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

            EngineFrameResult frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_TRANSCODE_REQUIRED".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
    }

    @Test
    void iblKtxContainerDecodableViaStbDoesNotEmitDecodeUnavailableWarning() throws Exception {
        Path irr = Files.createTempFile("dle-irr-stb-", ".ktx2");
        Path rad = Files.createTempFile("dle-rad-stb-", ".ktx2");
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
            var runtime = new OpenGlEngineRuntime();
            runtime.initialize(validConfig(), new RecordingCallbacks());
            runtime.loadScene(new SceneDescriptor(
                    "ibl-ktx-stb-decode-available-scene",
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

            EngineFrameResult frame = runtime.render();
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_DECODE_UNAVAILABLE".equals(w.code())));
            assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_KTX_VARIANT_UNSUPPORTED".equals(w.code())));
        } finally {
            Files.deleteIfExists(irr);
            Files.deleteIfExists(rad);
        }
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

    @Test
    void iblLowTierEmitsQualityDegradedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validLowQualityConfig(), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        EngineFrameResult frame = runtime.render();

        assertTrue(frame.warnings().stream().anyMatch(w -> "IBL_QUALITY_DEGRADED".equals(w.code())));
    }

    @Test
    void iblUltraTierDoesNotEmitQualityDegradedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validUltraQualityConfig(), new RecordingCallbacks());
        runtime.loadScene(validIblScene());

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "IBL_QUALITY_DEGRADED".equals(w.code())));
    }

    @Test
    void pointShadowRequestDoesNotEmitShadowTypeUnsupportedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validPointShadowScene());

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_TYPE_UNSUPPORTED".equals(w.code())));
    }

    @Test
    void spotShadowRequestDoesNotEmitShadowTypeUnsupportedWarning() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validSpotShadowScene());

        EngineFrameResult frame = runtime.render();

        assertFalse(frame.warnings().stream().anyMatch(w -> "SHADOW_TYPE_UNSUPPORTED".equals(w.code())));
    }

    @Test
    void sceneMeshesDriveDrawCallAndTriangleStatsInMockMode() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(), new RecordingCallbacks());
        runtime.loadScene(validMultiMeshScene());
        runtime.render();

        assertEquals(2, runtime.getStats().drawCalls());
        assertEquals(3, runtime.getStats().triangles());
        assertEquals(2, runtime.getStats().visibleObjects());
    }

    @Test
    void resourceServiceTracksSceneAssetsAndReleasesOnShutdown() throws Exception {
        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(), callbacks);
        runtime.loadScene(validSceneWithResources());

        assertFalse(runtime.resources().loadedResources().isEmpty());
        assertTrue(runtime.resources().loadedResources().stream().allMatch(r -> r.state() == ResourceState.FAILED));
        assertTrue(callbacks.events.stream().anyMatch(SceneLoadFailedEvent.class::isInstance));
        runtime.shutdown();
        assertTrue(runtime.resources().loadedResources().isEmpty());
    }

    @Test
    void resourceHotReloadEmitsResourceReloadedEvent() throws Exception {
        Path root = Files.createTempDirectory("dle-resources");
        Files.createDirectories(root.resolve("meshes"));
        Files.createDirectories(root.resolve("textures"));
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v1");
        Files.writeString(root.resolve("textures/albedo.png"), "albedo-v1");
        Files.writeString(root.resolve("textures/normal.png"), "normal-v1");
        Files.writeString(root.resolve("textures/skybox.hdr"), "sky-v1");

        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.of("opengl.mockContext", "true"), QualityTier.MEDIUM, root), callbacks);
        runtime.loadScene(validSceneWithResources());

        var resource = runtime.resources().loadedResources().stream()
                .filter(r -> r.descriptor().sourcePath().equals("meshes/box.glb"))
                .findFirst()
                .orElseThrow();
        assertEquals(ResourceState.LOADED, resource.state());
        assertTrue(resource.resolvedPath().endsWith("meshes/box.glb"));
        assertNotNull(resource.lastChecksum());
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v2");
        var reloaded = runtime.resources().reload(resource.descriptor().id());

        assertTrue(callbacks.events.stream().anyMatch(ResourceHotReloadedEvent.class::isInstance));
        assertNotNull(reloaded.lastChecksum());
        assertFalse(resource.lastChecksum().equals(reloaded.lastChecksum()));
    }

    @Test
    void resourceHotReloadWithoutChecksumChangeStillEmitsEvent() throws Exception {
        Path root = Files.createTempDirectory("dle-resources-nochange");
        Files.createDirectories(root.resolve("meshes"));
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v1");

        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.of("opengl.mockContext", "true"), QualityTier.MEDIUM, root), callbacks);
        runtime.loadScene(new SceneDescriptor(
                "mesh-only-scene",
                List.of(new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f)),
                "cam",
                List.of(new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "xform", "mat", "meshes/box.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 0.5f, null, null)),
                List.of(),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.of()
        ));

        callbacks.events.clear();
        var resource = runtime.resources().loadedResources().getFirst();
        var reloaded = runtime.resources().reload(resource.descriptor().id());

        assertTrue(callbacks.events.stream().anyMatch(ResourceHotReloadedEvent.class::isInstance));
        assertEquals(resource.lastChecksum(), reloaded.lastChecksum());
    }

    @Test
    void resourceStatsTrackHitsMissesAndReloads() throws Exception {
        Path root = Files.createTempDirectory("dle-resource-stats");
        Files.createDirectories(root.resolve("meshes"));
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v1");

        var runtime = new OpenGlEngineRuntime();
        runtime.initialize(validConfig(Map.of("opengl.mockContext", "true"), QualityTier.MEDIUM, root), new RecordingCallbacks());
        runtime.loadScene(new SceneDescriptor(
                "mesh-only-scene-stats",
                List.of(new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f)),
                "cam",
                List.of(new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "xform", "mat", "meshes/box.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 0.5f, null, null)),
                List.of(),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.of()
        ));
        var id = runtime.resources().loadedResources().getFirst().descriptor().id();
        runtime.resources().reload(id);

        var stats = runtime.resources().stats();
        assertTrue(stats.cacheMisses() >= 1);
        assertTrue(stats.cacheHits() >= 0);
        assertTrue(stats.reloadRequests() >= 1);
    }

    @Test
    void resourceWatcherAutoReloadsOnFileChange() throws Exception {
        assumeTrue(Boolean.getBoolean("dle.test.resource.watch"),
                "Set -Ddle.test.resource.watch=true to run filesystem watcher integration test");
        Path root = Files.createTempDirectory("dle-resources-watch");
        Files.createDirectories(root.resolve("meshes"));
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v1");

        var runtime = new OpenGlEngineRuntime();
        var callbacks = new RecordingCallbacks();
        runtime.initialize(validConfig(Map.of(
                "opengl.mockContext", "true",
                "resource.watch.enabled", "true"
        ), QualityTier.MEDIUM, root), callbacks);
        runtime.loadScene(new SceneDescriptor(
                "mesh-only-scene-watch",
                List.of(new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f)),
                "cam",
                List.of(new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "xform", "mat", "meshes/box.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 0.5f, null, null)),
                List.of(),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.of()
        ));

        callbacks.events.clear();
        Files.writeString(root.resolve("meshes/box.glb"), "mesh-v2");

        boolean reloaded = false;
        for (int i = 0; i < 30; i++) {
            if (callbacks.events.stream().anyMatch(ResourceHotReloadedEvent.class::isInstance)) {
                reloaded = true;
                break;
            }
            Thread.sleep(50);
        }

        runtime.shutdown();
        assertTrue(reloaded);
    }

    private static EngineConfig validConfig() {
        return validConfig(Map.of("opengl.mockContext", "true"));
    }

    private static EngineConfig validLowQualityConfig() {
        return validConfig(Map.of("opengl.mockContext", "true"), QualityTier.LOW);
    }

    private static EngineConfig validUltraQualityConfig() {
        return validConfig(Map.of("opengl.mockContext", "true"), QualityTier.ULTRA);
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions) {
        return validConfig(backendOptions, QualityTier.MEDIUM);
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier qualityTier) {
        return validConfig(backendOptions, qualityTier, Path.of("."));
    }

    private static EngineConfig validConfig(Map<String, String> backendOptions, QualityTier qualityTier, Path assetRoot) {
        return new EngineConfig(
                "opengl",
                "test-host",
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
                "test-scene",
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

    private static SceneDescriptor validFogScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(true, FogMode.EXPONENTIAL, new Vec3(0.65f, 0.7f, 0.8f), 0.4f, 0.0f, 1.0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "fog-scene",
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

    private static SceneDescriptor validSmokeScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "mesh.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, null, null);
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        SmokeEmitterDesc emitter = new SmokeEmitterDesc(
                "smoke-1",
                new Vec3(0, 0, 0),
                new Vec3(1, 1, 1),
                10f,
                0.7f,
                new Vec3(0.65f, 0.66f, 0.7f),
                1f,
                new Vec3(0, 1, 0),
                0.3f,
                10f,
                true
        );

        return new SceneDescriptor(
                "smoke-scene",
                List.of(camera),
                "cam",
                List.of(transform),
                List.of(mesh),
                List.of(mat),
                List.of(light),
                env,
                fog,
                List.of(emitter)
        );
    }

    private static SceneDescriptor validSceneWithResources() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f);
        TransformDesc transform = new TransformDesc("xform", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh", "xform", "mat", "meshes/box.glb");
        MaterialDesc mat = new MaterialDesc("mat", new Vec3(1, 1, 1), 0.0f, 0.5f, "textures/albedo.png", "textures/normal.png");
        LightDesc light = new LightDesc("light", new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1.0f, 10f, false, null);
        EnvironmentDesc env = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, "textures/skybox.hdr");
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "resource-scene",
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
                "multi-mesh-scene",
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
                "ibl-scene",
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
                "ibl-missing-scene",
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
                "ibl-skybox-only-scene",
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
                "ibl-ktx-skybox-fallback-scene",
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
                "point-shadow-scene",
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
                "spot-shadow-scene",
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

    private static EngineInput emptyInput() {
        return new EngineInput(0, 0, 0, 0, false, false, java.util.Set.<KeyCode>of(), 0.0);
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
        putIntLE(header, 12, 0); // undefined vkFormat (BasisLZ/UASTC transcode-required family)
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
