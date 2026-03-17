package org.dynamisengine.light.impl.opengl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.dynamisengine.light.api.runtime.EngineCapabilities;
import org.dynamisengine.light.api.config.EngineConfig;
import org.dynamisengine.light.api.error.EngineErrorCode;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.api.event.EngineWarning;
import org.dynamisengine.light.api.scene.CameraDesc;
import org.dynamisengine.light.api.scene.AntiAliasingDesc;
import org.dynamisengine.light.api.scene.EnvironmentDesc;
import org.dynamisengine.light.api.scene.FogDesc;
import org.dynamisengine.light.api.scene.FogMode;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.scene.LightDesc;
import org.dynamisengine.light.api.scene.PostProcessDesc;
import org.dynamisengine.light.api.scene.ReflectionAdvancedDesc;
import org.dynamisengine.light.api.scene.ReflectionDesc;
import org.dynamisengine.light.api.scene.SceneDescriptor;
import org.dynamisengine.light.api.scene.SmokeEmitterDesc;
import org.dynamisengine.light.api.scene.Vec3;
import org.dynamisengine.light.impl.common.AbstractEngineRuntime;
import org.dynamisengine.light.impl.common.framegraph.FrameGraph;
import org.dynamisengine.light.impl.common.framegraph.FrameGraphBuilder;
import org.dynamisengine.light.impl.common.framegraph.FrameGraphExecutor;
import org.dynamisengine.light.impl.common.framegraph.FrameGraphPass;
import org.dynamisengine.light.impl.common.upscale.ExternalUpscalerBridge;
import org.dynamisengine.light.impl.common.upscale.ExternalUpscalerIntegration;

import org.dynamisengine.light.impl.opengl.OpenGlRenderingOptions.*;
import org.dynamisengine.light.impl.opengl.OpenGlIblResolver.IblRenderConfig;
import org.dynamisengine.light.impl.opengl.OpenGlLightingMapper.LightingConfig;
import org.dynamisengine.light.impl.opengl.OpenGlLightingMapper.ShadowRenderConfig;

public final class OpenGlEngineRuntime extends AbstractEngineRuntime {

    private record FogRenderConfig(boolean enabled, float r, float g, float b, float density, int steps) {
    }

    private record SmokeRenderConfig(boolean enabled, float r, float g, float b, float intensity, boolean degraded) {
    }

    private record PostProcessRenderConfig(
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength,
            boolean ssaoEnabled,
            float ssaoStrength,
            float ssaoRadius,
            float ssaoBias,
            float ssaoPower,
            boolean smaaEnabled,
            float smaaStrength,
            boolean taaEnabled,
            float taaBlend,
            float taaClipScale,
            boolean taaLumaClipEnabled,
            float taaSharpenStrength,
            float taaRenderScale,
            boolean reflectionsEnabled,
            int reflectionsMode,
            float reflectionsSsrStrength,
            float reflectionsSsrMaxRoughness,
            float reflectionsSsrStepScale,
            float reflectionsTemporalWeight,
            float reflectionsPlanarStrength
    ) {
    }

    static record CameraMatrices(float[] view, float[] proj) {
    }

    private final OpenGlContext context = new OpenGlContext();
    private final FrameGraphExecutor frameGraphExecutor = new FrameGraphExecutor();
    private FrameGraph frameGraph;
    private boolean mockContext;
    private boolean windowVisible;
    private QualityTier qualityTier = QualityTier.MEDIUM;
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private long plannedVisibleObjects = 1;
    private OpenGlMeshAssetLoader meshLoader = new OpenGlMeshAssetLoader(Path.of("."));
    private Path assetRoot = Path.of(".");
    private int viewportWidth = 1280;
    private int viewportHeight = 720;
    private SceneDescriptor activeScene;
    private FogRenderConfig fog = new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0);
    private SmokeRenderConfig smoke = new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
    private ShadowRenderConfig shadows = OpenGlLightingMapper.SHADOWS_DISABLED;
    private PostProcessRenderConfig postProcess = new PostProcessRenderConfig(true, 1.0f, 2.2f, false, 1.0f, 0.8f, false, 0f, 1.0f, 0.02f, 1.0f, false, 0f, false, 0f, 1.0f, false, 0.16f, 1.0f, false, ReflectionMode.IBL_ONLY.ordinal(), 0.6f, 0.78f, 1.0f, 0.80f, 0.35f);
    private IblRenderConfig ibl = OpenGlIblResolver.DISABLED;
    private boolean nonDirectionalShadowRequested;
    private int taaDebugView;
    private boolean taaLumaClipEnabledDefault;
    private AaPreset aaPreset = AaPreset.BALANCED;
    private AaMode aaMode = AaMode.TAA;
    private UpscalerMode upscalerMode = UpscalerMode.NONE;
    private UpscalerQuality upscalerQuality = UpscalerQuality.QUALITY;
    private ReflectionProfile reflectionProfile = ReflectionProfile.BALANCED;
    private TsrControls tsrControls = new TsrControls(0.90f, 0.65f, 0.88f, 0.85f, 0.14f, 0.75f, 0.60f, 0.72f);
    private ExternalUpscalerIntegration externalUpscaler = ExternalUpscalerIntegration.inactive("not initialized");
    private boolean nativeUpscalerActive;
    private String nativeUpscalerProvider = "none";
    private String nativeUpscalerDetail = "inactive";

    public OpenGlEngineRuntime() {
        super(
                "OpenGL",
                new EngineCapabilities(
                        Set.of("opengl"),
                        true,
                        false,
                        false,
                        false,
                        7680,
                        4320,
                        Set.of(QualityTier.LOW, QualityTier.MEDIUM, QualityTier.HIGH)
                ),
                16.6,
                8.3
        );
    }

    @Override
    protected void onInitialize(EngineConfig config) throws EngineException {
        String mock = config.backendOptions().getOrDefault("opengl.mockContext", "false");
        mockContext = Boolean.parseBoolean(mock);
        windowVisible = Boolean.parseBoolean(config.backendOptions().getOrDefault("opengl.windowVisible", "false"));
        try {
            taaDebugView = Math.max(0, Math.min(5, Integer.parseInt(config.backendOptions().getOrDefault("opengl.taaDebugView", "0"))));
        } catch (NumberFormatException ignored) {
            taaDebugView = 0;
        }
        taaLumaClipEnabledDefault = Boolean.parseBoolean(config.backendOptions().getOrDefault("opengl.taaLumaClip", "false"));
        aaPreset = OpenGlRenderingOptions.parseAaPreset(config.backendOptions().get("opengl.aaPreset"));
        aaMode = OpenGlRenderingOptions.parseAaMode(config.backendOptions().get("opengl.aaMode"));
        upscalerMode = OpenGlRenderingOptions.parseUpscalerMode(config.backendOptions().get("opengl.upscalerMode"));
        upscalerQuality = OpenGlRenderingOptions.parseUpscalerQuality(config.backendOptions().get("opengl.upscalerQuality"));
        reflectionProfile = OpenGlRenderingOptions.parseReflectionProfile(config.backendOptions().get("opengl.reflectionsProfile"));
        tsrControls = OpenGlRenderingOptions.parseTsrControls(config.backendOptions(), "opengl.");
        externalUpscaler = ExternalUpscalerIntegration.create("opengl", "opengl.", config.backendOptions());
        nativeUpscalerActive = false;
        nativeUpscalerProvider = externalUpscaler.providerId();
        nativeUpscalerDetail = externalUpscaler.statusDetail();
        qualityTier = config.qualityTier();
        assetRoot = config.assetRoot() == null ? Path.of(".") : config.assetRoot();
        meshLoader = new OpenGlMeshAssetLoader(assetRoot);
        viewportWidth = config.initialWidthPx();
        viewportHeight = config.initialHeightPx();
        if (Boolean.parseBoolean(config.backendOptions().getOrDefault("opengl.forceInitFailure", "false"))) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Forced OpenGL init failure", false);
        }
        if (mockContext) {
            return;
        }
        context.initialize(config.appName(), config.initialWidthPx(), config.initialHeightPx(), config.vsyncEnabled(), windowVisible);
        context.setFogParameters(fog.enabled(), fog.r(), fog.g(), fog.b(), fog.density(), fog.steps());
        context.setSmokeParameters(smoke.enabled(), smoke.r(), smoke.g(), smoke.b(), smoke.intensity());
        context.setIblParameters(ibl.enabled(), ibl.diffuseStrength(), ibl.specularStrength(), ibl.prefilterStrength());
        context.setIblTexturePaths(null, null, null);
        pushPostProcessToContext();
        context.setTaaDebugView(taaDebugView);
        frameGraph = buildFrameGraph();
    }

    @Override
    protected void onLoadScene(SceneDescriptor scene) throws EngineException {
        activeScene = scene;
        aaMode = OpenGlRenderingOptions.resolveAaMode(scene.postProcess(), aaMode);
        taaDebugView = OpenGlRenderingOptions.resolveTaaDebugView(scene.postProcess(), taaDebugView);
        fog = mapFog(scene.fog(), qualityTier);
        smoke = mapSmoke(scene.smokeEmitters(), qualityTier);
        shadows = OpenGlLightingMapper.mapShadows(scene.lights(), qualityTier);
        nonDirectionalShadowRequested = OpenGlLightingMapper.hasNonDirectionalShadowRequest(scene.lights());
        postProcess = mapPostProcess(scene.postProcess(), qualityTier, taaLumaClipEnabledDefault, aaPreset, aaMode, upscalerMode, upscalerQuality, tsrControls, reflectionProfile);
        postProcess = applyExternalUpscalerDecision(postProcess);
        ibl = OpenGlIblResolver.mapIbl(scene.environment(), qualityTier, assetRoot);

        List<OpenGlContext.SceneMesh> sceneMeshes = OpenGlSceneMeshMapper.mapSceneMeshes(scene, meshLoader, context, assetRoot, mockContext);
        plannedDrawCalls = sceneMeshes.size();
        plannedTriangles = sceneMeshes.stream().mapToLong(mesh -> mesh.geometry().vertexCount() / 3).sum();
        plannedVisibleObjects = plannedDrawCalls;

        CameraDesc camera = selectActiveCamera(scene);
        CameraMatrices cameraMatrices = cameraMatricesFor(camera, safeAspect(viewportWidth, viewportHeight));

        if (!mockContext) {
            context.setSceneMeshes(sceneMeshes);
            context.setCameraMatrices(cameraMatrices.view(), cameraMatrices.proj());
            LightingConfig lighting = OpenGlLightingMapper.mapLighting(scene.lights());
            context.setLightingParameters(
                    lighting.directionalDirection(),
                    lighting.directionalColor(),
                    lighting.directionalIntensity(),
                    lighting.shadowPointPosition(),
                    lighting.shadowPointDirection(),
                    lighting.shadowPointIsSpot(),
                    lighting.shadowPointOuterCos(),
                    lighting.shadowPointRange(),
                    lighting.shadowPointCastsShadows(),
                    lighting.localLightCount(),
                    lighting.localLightPosRange(),
                    lighting.localLightColorIntensity(),
                    lighting.localLightDirInner(),
                    lighting.localLightOuterTypeShadow()
            );
            context.setShadowParameters(
                    shadows.enabled(),
                    shadows.strength(),
                    shadows.bias(),
                    shadows.normalBiasScale(),
                    shadows.slopeBiasScale(),
                    shadows.pcfRadius(),
                    shadows.cascadeCount(),
                    shadows.mapResolution(),
                    shadows.selectedLocalShadowLights()
            );
            float shadowRange = OpenGlLightingMapper.directionalLightRange(scene.lights());
            if (shadowRange > 0f) {
                context.setShadowOrthoSize(shadowRange * 0.3f, shadowRange);
            }
            EnvironmentDesc env = scene.environment();
            if (env != null && env.ambientColor() != null) {
                context.setAmbientLight(
                        env.ambientColor().x(), env.ambientColor().y(), env.ambientColor().z(),
                        env.ambientIntensity()
                );
                context.setClearColor(
                        env.ambientColor().x() * 1.8f,
                        env.ambientColor().y() * 1.8f,
                        env.ambientColor().z() * 1.8f
                );
            }
            context.setFogParameters(fog.enabled(), fog.r(), fog.g(), fog.b(), fog.density(), fog.steps());
            context.setSmokeParameters(smoke.enabled(), smoke.r(), smoke.g(), smoke.b(), smoke.intensity());
            context.setIblParameters(ibl.enabled(), ibl.diffuseStrength(), ibl.specularStrength(), ibl.prefilterStrength());
            context.setIblTexturePaths(
                    ibl.irradiancePath(),
                    ibl.radiancePath(),
                    ibl.brdfLutPath()
            );
            pushPostProcessToContext();
            context.setTaaDebugView(taaDebugView);
            frameGraph = buildFrameGraph();
        }
    }

    @Override
    protected RenderMetrics onRender() throws EngineException {
        if (mockContext) {
            return renderMetrics(0.2, 0.1, plannedDrawCalls, plannedTriangles, plannedVisibleObjects, 0);
        }
        long startNs = System.nanoTime();
        context.beginFrame();
        frameGraphExecutor.execute(frameGraph);
        context.endFrame();
        double cpuMs = (System.nanoTime() - startNs) / 1_000_000.0;
        double gpuMs = context.lastGpuFrameMs() > 0.0 ? context.lastGpuFrameMs() : cpuMs * 0.8;
        return renderMetrics(
                cpuMs,
                gpuMs,
                context.lastDrawCalls(),
                context.lastTriangles(),
                context.lastVisibleObjects(),
                context.estimatedGpuMemoryBytes()
        );
    }

    @Override
    protected void onResize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        viewportWidth = widthPx;
        viewportHeight = heightPx;
        if (!mockContext) {
            context.resize(widthPx, heightPx);
            if (activeScene != null) {
                CameraDesc camera = selectActiveCamera(activeScene);
                CameraMatrices matrices = cameraMatricesFor(camera, safeAspect(widthPx, heightPx));
                context.setCameraMatrices(matrices.view(), matrices.proj());
            }
        }
    }

    @Override
    protected void onShutdown() {
        if (!mockContext) {
            context.shutdown();
        }
    }

    @Override
    protected List<EngineWarning> frameWarnings() {
        List<EngineWarning> warnings = new ArrayList<>();
        if (smoke.enabled() && smoke.degraded()) {
            warnings.add(new EngineWarning(
                    "SMOKE_QUALITY_DEGRADED",
                    "Smoke rendering quality reduced for tier " + qualityTier + " to maintain performance"
            ));
        }
        if (fog.enabled() && qualityTier == QualityTier.LOW) {
            warnings.add(new EngineWarning(
                    "FOG_QUALITY_DEGRADED",
                    "Fog sampling reduced at LOW quality tier"
            ));
        }
        if (shadows.enabled() && shadows.degraded()) {
            warnings.add(new EngineWarning(
                    "SHADOW_QUALITY_DEGRADED",
                    "Shadow quality reduced for tier " + qualityTier + " to maintain performance"
            ));
        }
        if (shadows.enabled()) {
            warnings.add(new EngineWarning(
                    "SHADOW_POLICY_ACTIVE",
                    "Shadow policy active: primary=" + shadows.primaryShadowLightId()
                            + " type=" + shadows.primaryShadowType()
                            + " localBudget=" + shadows.maxShadowedLocalLights()
                            + " localSelected=" + shadows.selectedLocalShadowLights()
                            + " atlasTiles=" + shadows.atlasAllocatedTiles() + "/" + shadows.atlasCapacityTiles()
                            + " atlasUtilization=" + shadows.atlasUtilization()
                            + " atlasEvictions=" + shadows.atlasEvictions()
                            + " atlasMemoryD16Bytes=" + shadows.atlasMemoryBytesD16()
                            + " atlasMemoryD32Bytes=" + shadows.atlasMemoryBytesD32()
                            + " shadowUpdateBytesEstimate=" + shadows.shadowUpdateBytesEstimate()
                            + " cadencePolicy=hero:1 mid:2 distant:4"
                            + " normalBiasScale=" + shadows.normalBiasScale()
                            + " slopeBiasScale=" + shadows.slopeBiasScale()
            ));
        }
        if (postProcess.ssaoEnabled() && qualityTier == QualityTier.MEDIUM) {
            warnings.add(new EngineWarning(
                    "SSAO_QUALITY_DEGRADED",
                    "SSAO-lite strength reduced at MEDIUM tier to maintain stable frame cost"
            ));
        }
        if (postProcess.smaaEnabled() && qualityTier == QualityTier.MEDIUM) {
            warnings.add(new EngineWarning(
                    "SMAA_QUALITY_DEGRADED",
                    "SMAA-lite strength reduced at MEDIUM tier to maintain stable frame cost"
            ));
        }
        if (postProcess.taaEnabled() && qualityTier == QualityTier.MEDIUM) {
            warnings.add(new EngineWarning(
                    "TAA_QUALITY_DEGRADED",
                    "TAA blend reduced at MEDIUM tier to maintain stable frame cost"
            ));
        }
        if (postProcess.taaEnabled()) {
            warnings.add(new EngineWarning(
                    "TAA_BASELINE_ACTIVE",
                    "TAA baseline temporal blend path is active (OpenGL history-buffer mode)"
            ));
        }
        if (postProcess.reflectionsEnabled()) {
            int reflectionBaseMode = postProcess.reflectionsMode() & 0x7;
            warnings.add(new EngineWarning(
                    "REFLECTIONS_BASELINE_ACTIVE",
                    "Reflections baseline active (mode="
                            + switch (reflectionBaseMode) {
                        case 1 -> "ssr";
                        case 2 -> "planar";
                        case 3 -> "hybrid";
                        case 4 -> "rt_hybrid_fallback";
                        default -> "ibl_only";
                    }
                            + ", ssrStrength=" + postProcess.reflectionsSsrStrength()
                            + ", planarStrength=" + postProcess.reflectionsPlanarStrength() + ")"
            ));
            if (qualityTier == QualityTier.MEDIUM) {
                warnings.add(new EngineWarning(
                        "REFLECTIONS_QUALITY_DEGRADED",
                        "Reflections quality is reduced at MEDIUM tier to stabilize frame time"
                ));
            }
        }
        if (upscalerMode != UpscalerMode.NONE && postProcess.taaEnabled()) {
            warnings.add(new EngineWarning(
                    "UPSCALER_HOOK_ACTIVE",
                    "Upscaler hook requested (mode=" + upscalerMode.name().toLowerCase() + ", quality=" + upscalerQuality.name().toLowerCase() + ")"
            ));
            if (nativeUpscalerActive) {
                warnings.add(new EngineWarning(
                        "UPSCALER_NATIVE_ACTIVE",
                        "Native upscaler bridge active (provider=" + nativeUpscalerProvider + ", detail=" + nativeUpscalerDetail + ")"
                ));
            } else {
                warnings.add(new EngineWarning(
                        "UPSCALER_NATIVE_INACTIVE",
                        "Native upscaler bridge not active (detail=" + nativeUpscalerDetail + ")"
                ));
            }
        }
        if (ibl.enabled()) {
            warnings.add(new EngineWarning(
                    "IBL_BASELINE_ACTIVE",
                    "IBL baseline enabled using "
                            + (ibl.textureDriven() ? "texture-driven" : "path-driven")
                            + " environment diffuse/specular approximations"
            ));
            warnings.add(new EngineWarning(
                    "IBL_PREFILTER_APPROX_ACTIVE",
                    "IBL roughness-aware radiance prefilter approximation active (strength=" + ibl.prefilterStrength() + ")"
            ));
            warnings.add(new EngineWarning(
                    "IBL_MULTI_TAP_SPEC_ACTIVE",
                    "IBL specular radiance uses roughness-aware multi-tap filtering for improved highlight stability"
            ));
            warnings.add(new EngineWarning(
                    "IBL_MIP_LOD_PREFILTER_ACTIVE",
                    "IBL specular prefilter sampling uses roughness-driven mip/LOD selection"
            ));
            warnings.add(new EngineWarning(
                    "IBL_BRDF_ENERGY_COMP_ACTIVE",
                    "IBL diffuse/specular response uses BRDF energy-compensation and horizon weighting for improved roughness realism"
            ));
            if (ibl.skyboxDerived()) {
                warnings.add(new EngineWarning(
                        "IBL_SKYBOX_DERIVED_ACTIVE",
                        "IBL irradiance/radiance inputs are derived from EnvironmentDesc.skyboxAssetPath"
                ));
            }
            if (ibl.ktxSkyboxFallback()) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_SKYBOX_FALLBACK_ACTIVE",
                        "KTX IBL paths without decodable sources fell back to skybox-derived irradiance/radiance inputs"
                ));
            }
            if (ibl.ktxDecodeUnavailableCount() > 0) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_DECODE_UNAVAILABLE",
                        "KTX/KTX2 IBL assets detected but could not be decoded by current baseline path (channels=" + ibl.ktxDecodeUnavailableCount()
                                + "); runtime used sidecar/derived/default fallback inputs"
                ));
            }
            if (ibl.ktxTranscodeRequiredCount() > 0) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_TRANSCODE_REQUIRED",
                        "KTX2 IBL assets require BasisLZ/UASTC transcoding not yet enabled in this build (channels="
                                + ibl.ktxTranscodeRequiredCount()
                                + "); runtime used sidecar/derived/default fallback inputs"
                ));
            }
            if (ibl.ktxUnsupportedVariantCount() > 0) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_VARIANT_UNSUPPORTED",
                        "KTX/KTX2 IBL assets use unsupported compressed/supercompressed/format variants in baseline decoder (channels="
                                + ibl.ktxUnsupportedVariantCount() + ")"
                ));
            }
            if (ibl.missingAssetCount() > 0) {
                warnings.add(new EngineWarning(
                        "IBL_ASSET_FALLBACK_ACTIVE",
                        "IBL configured assets missing/unreadable (" + ibl.missingAssetCount()
                                + "); runtime used fallback/default lighting signals"
                ));
            }
            if (ibl.degraded()) {
                warnings.add(new EngineWarning(
                        "IBL_QUALITY_DEGRADED",
                        "IBL diffuse/specular quality reduced for tier " + qualityTier + " to maintain stable frame cost"
                ));
            }
            if (ibl.ktxContainerRequested()) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_CONTAINER_FALLBACK",
                        "KTX/KTX2 IBL assets are resolved through sidecar decode paths when available (.png/.hdr/.jpg/.jpeg)"
                ));
            }
        }
        return warnings;
    }

    @Override
    protected List<EngineWarning> baselineWarnings() {
        return List.of(new EngineWarning("FEATURE_BASELINE", "OpenGL backend active with baseline forward render path"));
    }

    @Override
    protected double aaHistoryRejectRate() {
        return context.taaHistoryRejectRate();
    }

    @Override
    protected double aaConfidenceMean() {
        return context.taaConfidenceMean();
    }

    @Override
    protected long aaConfidenceDropEvents() {
        return context.taaConfidenceDropEvents();
    }

    // ── fog / smoke (kept here — small, tightly coupled to local records) ──

    private static FogRenderConfig mapFog(FogDesc fogDesc, QualityTier qualityTier) {
        if (fogDesc == null || !fogDesc.enabled() || fogDesc.mode() == FogMode.NONE) {
            return new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0);
        }

        float tierDensityScale = fogDensityScale(qualityTier);
        int tierSteps = fogSteps(qualityTier);

        float density = Math.max(0f, fogDesc.density() * tierDensityScale);
        return new FogRenderConfig(
                true,
                fogDesc.color() == null ? 0.5f : fogDesc.color().x(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().y(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().z(),
                density,
                tierSteps
        );
    }

    static int fogSteps(QualityTier qualityTier) {
        return switch (qualityTier) {
            case LOW -> 4;
            case MEDIUM -> 8;
            case HIGH -> 16;
            case ULTRA -> 0;
        };
    }

    static float fogDensityScale(QualityTier qualityTier) {
        return switch (qualityTier) {
            case LOW -> 0.55f;
            case MEDIUM -> 0.75f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.2f;
        };
    }

    private static SmokeRenderConfig mapSmoke(List<SmokeEmitterDesc> emitters, QualityTier qualityTier) {
        if (emitters == null || emitters.isEmpty()) {
            return new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }

        int enabledCount = 0;
        float densityAccum = 0f;
        float r = 0f;
        float g = 0f;
        float b = 0f;

        for (SmokeEmitterDesc emitter : emitters) {
            if (!emitter.enabled()) {
                continue;
            }
            enabledCount++;
            densityAccum += Math.max(0f, emitter.density());
            r += emitter.albedo() == null ? 0.6f : emitter.albedo().x();
            g += emitter.albedo() == null ? 0.6f : emitter.albedo().y();
            b += emitter.albedo() == null ? 0.6f : emitter.albedo().z();
        }

        if (enabledCount == 0) {
            return new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }

        float avgR = r / enabledCount;
        float avgG = g / enabledCount;
        float avgB = b / enabledCount;
        float baseIntensity = Math.min(0.85f, densityAccum / enabledCount);

        float tierScale = switch (qualityTier) {
            case LOW -> 0.45f;
            case MEDIUM -> 0.7f;
            case HIGH -> 0.9f;
            case ULTRA -> 1.0f;
        };
        boolean degraded = qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM;
        return new SmokeRenderConfig(
                true,
                avgR,
                avgG,
                avgB,
                Math.min(0.85f, baseIntensity * tierScale),
                degraded
        );
    }

    // ── post-process (kept here — large but deeply coupled to local enums/records) ──

    private static PostProcessRenderConfig mapPostProcess(
            PostProcessDesc desc,
            QualityTier qualityTier,
            boolean taaLumaClipEnabledDefault,
            AaPreset aaPreset,
            AaMode aaMode,
            UpscalerMode upscalerMode,
            UpscalerQuality upscalerQuality,
            TsrControls tsrControls,
            ReflectionProfile reflectionProfile
    ) {
        if (desc == null || !desc.enabled()) {
            return new PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.8f, false, 0f, 1.0f, 0.02f, 1.0f, false, 0f, false, 0f, 1.0f, false, 0.12f, 1.0f, false, ReflectionMode.IBL_ONLY.ordinal(), 0.6f, 0.78f, 1.0f, 0.80f, 0.35f);
        }
        float tierExposureScale = switch (qualityTier) {
            case LOW -> 0.9f;
            case MEDIUM -> 1.0f;
            case HIGH -> 1.05f;
            case ULTRA -> 1.1f;
        };
        float exposure = Math.max(0.25f, Math.min(4.0f, desc.exposure() * tierExposureScale));
        float gamma = Math.max(1.6f, Math.min(2.6f, desc.gamma()));
        float bloomThreshold = Math.max(0.2f, Math.min(2.5f, desc.bloomThreshold()));
        float bloomStrength = Math.max(0f, Math.min(1.6f, desc.bloomStrength()));
        boolean bloomEnabled = desc.bloomEnabled() && qualityTier != QualityTier.LOW;
        boolean ssaoEnabled = desc.ssaoEnabled() && qualityTier != QualityTier.LOW;
        float ssaoStrength = Math.max(0f, Math.min(1.0f, desc.ssaoStrength()));
        float ssaoRadius = Math.max(0.2f, Math.min(3.0f, desc.ssaoRadius()));
        float ssaoBias = Math.max(0.0f, Math.min(0.2f, desc.ssaoBias()));
        float ssaoPower = Math.max(0.5f, Math.min(4.0f, desc.ssaoPower()));
        boolean smaaEnabled = desc.smaaEnabled() && qualityTier != QualityTier.LOW;
        float smaaStrength = Math.max(0f, Math.min(1.0f, desc.smaaStrength()));
        boolean taaEnabled = desc.taaEnabled() && qualityTier != QualityTier.LOW;
        float taaBlend = Math.max(0f, Math.min(0.95f, desc.taaBlend()));
        float taaClipScale = switch (qualityTier) {
            case LOW -> 1.35f;
            case MEDIUM -> 1.10f;
            case HIGH -> 0.92f;
            case ULTRA -> 0.78f;
        };
        float taaSharpenStrength = switch (qualityTier) {
            case LOW -> 0.08f;
            case MEDIUM -> 0.12f;
            case HIGH -> 0.16f;
            case ULTRA -> 0.20f;
        };
        float taaRenderScale = 1.0f;
        boolean taaLumaClipEnabled = desc.taaLumaClipEnabled() || taaLumaClipEnabledDefault;
        if (qualityTier == QualityTier.MEDIUM) {
            ssaoStrength *= 0.8f;
            ssaoRadius *= 0.9f;
            smaaStrength *= 0.8f;
            taaBlend *= 0.85f;
        }
        if (aaPreset != null) {
            switch (aaPreset) {
                case PERFORMANCE -> {
                    smaaStrength *= 0.80f;
                    taaBlend *= 0.82f;
                    taaClipScale = Math.min(1.6f, taaClipScale * 1.12f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.70f);
                }
                case QUALITY -> {
                    smaaStrength = Math.min(1.0f, smaaStrength * 1.12f);
                    taaBlend = Math.min(0.95f, taaBlend + 0.05f);
                    taaClipScale = Math.max(0.5f, taaClipScale * 0.94f);
                    taaSharpenStrength = Math.min(0.35f, taaSharpenStrength * 1.10f);
                    taaLumaClipEnabled = true;
                }
                case STABILITY -> {
                    smaaStrength = Math.min(1.0f, smaaStrength * 0.90f);
                    taaBlend = Math.min(0.95f, taaBlend + 0.08f);
                    taaClipScale = Math.min(1.6f, taaClipScale * 1.08f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.82f);
                    taaLumaClipEnabled = true;
                }
                case BALANCED -> {
                }
            }
        }
        if (aaMode != null) {
            switch (aaMode) {
                case TSR -> {
                    taaEnabled = qualityTier != QualityTier.LOW;
                    smaaEnabled = false;
                    smaaStrength = 0f;
                    taaRenderScale = qualityTier == QualityTier.LOW
                            ? 1.0f
                            : Math.max(0.5f, Math.min(1.0f, tsrControls.tsrRenderScale()));
                    float historyInfluence = clamp01(
                            tsrControls.historyWeight() * tsrControls.reprojectionConfidence() * (1.0f - tsrControls.responsiveMask() * 0.22f)
                    );
                    taaBlend = Math.max(taaBlend, Math.min(0.95f, 0.78f + 0.17f * historyInfluence));
                    taaClipScale = Math.max(0.5f, Math.min(1.6f, taaClipScale * (1.0f - (tsrControls.neighborhoodClamp() - 0.5f) * 0.45f)));
                    float antiRingingAttenuation = 1.0f - (0.35f * tsrControls.antiRinging());
                    taaSharpenStrength = Math.max(0f, Math.min(0.35f,
                            (tsrControls.sharpen() * antiRingingAttenuation) + (taaSharpenStrength * 0.22f)));
                    taaLumaClipEnabled = tsrControls.antiRinging() >= 0.35f;
                }
                case TUUA -> {
                    taaEnabled = qualityTier != QualityTier.LOW;
                    smaaEnabled = false;
                    smaaStrength = 0f;
                    taaRenderScale = qualityTier == QualityTier.LOW
                            ? 1.0f
                            : Math.max(0.5f, Math.min(1.0f, tsrControls.tuuaRenderScale()));
                    taaBlend = Math.min(0.95f, taaBlend + 0.10f);
                    taaClipScale = Math.max(0.5f, taaClipScale * 0.86f);
                    taaSharpenStrength = Math.min(0.35f, taaSharpenStrength * 1.16f);
                    taaLumaClipEnabled = true;
                }
                case MSAA_SELECTIVE -> {
                    smaaEnabled = qualityTier != QualityTier.LOW;
                    smaaStrength = Math.min(1.0f, smaaStrength + 0.12f);
                    taaBlend = Math.max(0.0f, taaBlend * 0.72f);
                    taaClipScale = Math.min(1.6f, taaClipScale * 1.10f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.75f);
                }
                case HYBRID_TUUA_MSAA -> {
                    taaEnabled = qualityTier != QualityTier.LOW;
                    smaaEnabled = qualityTier != QualityTier.LOW;
                    smaaStrength = Math.min(1.0f, smaaStrength * 1.05f);
                    taaRenderScale = switch (qualityTier) {
                        case LOW -> 1.0f;
                        case MEDIUM -> 0.90f;
                        case HIGH -> 0.84f;
                        case ULTRA -> 0.80f;
                    };
                    taaBlend = Math.min(0.95f, taaBlend + 0.06f);
                    taaClipScale = Math.max(0.5f, taaClipScale * 0.90f);
                    taaSharpenStrength = Math.min(0.35f, taaSharpenStrength * 0.95f);
                    taaLumaClipEnabled = true;
                }
                case DLAA -> {
                    taaEnabled = qualityTier != QualityTier.LOW;
                    smaaEnabled = qualityTier != QualityTier.LOW;
                    smaaStrength = Math.min(1.0f, smaaStrength * 0.55f);
                    taaBlend = Math.max(taaBlend, 0.90f);
                    taaClipScale = Math.max(0.5f, taaClipScale * 0.88f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.70f);
                    taaLumaClipEnabled = true;
                }
                case FXAA_LOW -> {
                    taaEnabled = false;
                    taaBlend = 0f;
                    smaaEnabled = qualityTier != QualityTier.LOW;
                    smaaStrength = Math.min(1.0f, Math.max(0.45f, smaaStrength * 0.90f));
                    taaClipScale = Math.min(1.6f, taaClipScale * 1.15f);
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.60f);
                    taaLumaClipEnabled = false;
                }
                case TAA -> {
                }
            }
        }
        if ((aaMode == AaMode.TSR || aaMode == AaMode.TUUA) && upscalerMode != UpscalerMode.NONE) {
            float qualityScale = switch (upscalerQuality) {
                case PERFORMANCE -> 0.88f;
                case BALANCED -> 0.94f;
                case QUALITY -> 1.0f;
                case ULTRA_QUALITY -> 1.05f;
            };
            switch (upscalerMode) {
                case FSR -> {
                    taaSharpenStrength = Math.min(0.35f, taaSharpenStrength + 0.05f * qualityScale);
                    taaBlend = Math.max(0.0f, taaBlend - 0.02f);
                    taaRenderScale = Math.max(taaRenderScale, 0.60f * qualityScale);
                }
                case XESS -> {
                    taaBlend = Math.min(0.95f, taaBlend + 0.03f * qualityScale);
                    taaClipScale = Math.max(0.5f, taaClipScale * (0.96f - ((qualityScale - 1.0f) * 0.05f)));
                    taaRenderScale = Math.max(taaRenderScale, 0.64f * qualityScale);
                }
                case DLSS -> {
                    taaBlend = Math.min(0.95f, taaBlend + 0.05f * qualityScale);
                    taaClipScale = Math.max(0.5f, taaClipScale * (0.92f - ((qualityScale - 1.0f) * 0.05f)));
                    taaSharpenStrength = Math.max(0f, taaSharpenStrength * 0.82f);
                    taaRenderScale = Math.max(taaRenderScale, 0.67f * qualityScale);
                }
                case NONE -> {
                }
            }
        }
        if (desc.antiAliasing() != null) {
            AntiAliasingDesc aa = desc.antiAliasing();
            taaBlend = clamp(aa.blend(), 0f, 0.95f);
            taaClipScale = clamp(aa.clipScale(), 0.5f, 1.6f);
            taaLumaClipEnabled = aa.lumaClipEnabled();
            taaSharpenStrength = clamp(aa.sharpenStrength(), 0f, 0.35f);
            taaRenderScale = clamp(aa.renderScale(), 0.5f, 1.0f);
        }
        ReflectionDesc reflectionDesc = desc.reflections();
        ReflectionAdvancedDesc reflectionAdvancedDesc = desc.reflectionAdvanced();
        ReflectionMode reflectionsMode = ReflectionMode.IBL_ONLY;
        boolean reflectionsEnabled = false;
        float reflectionsSsrStrength = 0.6f;
        float reflectionsSsrMaxRoughness = 0.78f;
        float reflectionsSsrStepScale = 1.0f;
        float reflectionsTemporalWeight = 0.80f;
        float reflectionsPlanarStrength = 0.35f;
        if (reflectionDesc != null) {
            reflectionsMode = OpenGlRenderingOptions.parseReflectionMode(reflectionDesc.mode());
            reflectionsEnabled = reflectionDesc.enabled() && reflectionsMode != ReflectionMode.IBL_ONLY;
            reflectionsSsrStrength = clamp(reflectionDesc.ssrStrength(), 0f, 1.0f);
            reflectionsSsrMaxRoughness = clamp(reflectionDesc.ssrMaxRoughness(), 0f, 1.0f);
            reflectionsSsrStepScale = clamp(reflectionDesc.ssrStepScale(), 0.5f, 3.0f);
            reflectionsTemporalWeight = clamp(reflectionDesc.temporalWeight(), 0f, 0.98f);
            reflectionsPlanarStrength = clamp(reflectionDesc.planarStrength(), 0f, 1.0f);
            if (qualityTier == QualityTier.LOW) {
                reflectionsEnabled = false;
            } else if (qualityTier == QualityTier.MEDIUM) {
                reflectionsSsrStrength *= 0.85f;
                reflectionsSsrStepScale = Math.min(3.0f, reflectionsSsrStepScale * 1.15f);
                reflectionsPlanarStrength *= 0.9f;
            }
        }
        if (reflectionAdvancedDesc != null && reflectionsEnabled) {
            if (!reflectionAdvancedDesc.hiZEnabled()) {
                reflectionsSsrStepScale = clamp(reflectionsSsrStepScale * 1.08f, 0.5f, 3.0f);
            } else {
                reflectionsSsrStepScale = clamp(reflectionsSsrStepScale * 0.92f, 0.5f, 3.0f);
            }
            int denoisePasses = Math.max(0, Math.min(6, reflectionAdvancedDesc.denoisePasses()));
            reflectionsTemporalWeight = clamp(reflectionsTemporalWeight + (denoisePasses * 0.02f), 0f, 0.98f);
            if (reflectionAdvancedDesc.planarClipPlaneEnabled()) {
                reflectionsPlanarStrength = clamp(reflectionsPlanarStrength + 0.05f, 0f, 1.0f);
            }
            if (reflectionAdvancedDesc.probeVolumeEnabled()) {
                reflectionsPlanarStrength = clamp(reflectionsPlanarStrength + 0.03f, 0f, 1.0f);
                reflectionsTemporalWeight = clamp(reflectionsTemporalWeight + 0.02f, 0f, 0.98f);
            }
            if (reflectionAdvancedDesc.rtEnabled()) {
                ReflectionMode fallbackMode = OpenGlRenderingOptions.parseReflectionMode(reflectionAdvancedDesc.rtFallbackMode());
                reflectionsMode = fallbackMode == ReflectionMode.IBL_ONLY ? ReflectionMode.HYBRID : fallbackMode;
                reflectionsSsrMaxRoughness = clamp(
                        Math.max(reflectionsSsrMaxRoughness, reflectionAdvancedDesc.rtMaxRoughness()),
                        0f,
                        1.0f
                );
                reflectionsTemporalWeight = clamp(reflectionsTemporalWeight + 0.04f, 0f, 0.98f);
            }
        }
        if (reflectionsEnabled && reflectionProfile != null) {
            switch (reflectionProfile) {
                case PERFORMANCE -> {
                    reflectionsSsrStrength = clamp(reflectionsSsrStrength * 0.80f, 0f, 1f);
                    reflectionsSsrStepScale = clamp(reflectionsSsrStepScale * 1.20f, 0.5f, 3f);
                    reflectionsTemporalWeight = clamp(reflectionsTemporalWeight * 0.75f, 0f, 0.98f);
                    reflectionsPlanarStrength = clamp(reflectionsPlanarStrength * 0.90f, 0f, 1f);
                }
                case QUALITY -> {
                    reflectionsSsrStrength = clamp(reflectionsSsrStrength * 1.10f, 0f, 1f);
                    reflectionsSsrStepScale = clamp(reflectionsSsrStepScale * 0.90f, 0.5f, 3f);
                    reflectionsTemporalWeight = clamp(reflectionsTemporalWeight + 0.08f, 0f, 0.98f);
                    reflectionsPlanarStrength = clamp(reflectionsPlanarStrength + 0.05f, 0f, 1f);
                }
                case STABILITY -> {
                    reflectionsSsrStrength = clamp(reflectionsSsrStrength * 0.95f, 0f, 1f);
                    reflectionsSsrStepScale = clamp(reflectionsSsrStepScale * 1.05f, 0.5f, 3f);
                    reflectionsTemporalWeight = clamp(reflectionsTemporalWeight + 0.12f, 0f, 0.98f);
                }
                case BALANCED -> {
                }
            }
        }
        return new PostProcessRenderConfig(
                desc.tonemapEnabled(),
                exposure,
                gamma,
                bloomEnabled,
                bloomThreshold,
                bloomStrength,
                ssaoEnabled,
                ssaoStrength,
                ssaoRadius,
                ssaoBias,
                ssaoPower,
                smaaEnabled,
                smaaStrength,
                taaEnabled,
                taaBlend,
                taaClipScale,
                taaLumaClipEnabled,
                taaSharpenStrength,
                taaRenderScale,
                reflectionsEnabled,
                packReflectionMode(reflectionsMode, reflectionAdvancedDesc),
                reflectionsSsrStrength,
                reflectionsSsrMaxRoughness,
                reflectionsSsrStepScale,
                reflectionsTemporalWeight,
                reflectionsPlanarStrength
        );
    }

    private PostProcessRenderConfig applyExternalUpscalerDecision(PostProcessRenderConfig base) {
        if (base == null) {
            nativeUpscalerActive = false;
            nativeUpscalerProvider = externalUpscaler.providerId();
            nativeUpscalerDetail = "no post-process config";
            return null;
        }
        nativeUpscalerProvider = externalUpscaler.providerId();
        if (!base.taaEnabled() || upscalerMode == UpscalerMode.NONE || (aaMode != AaMode.TSR && aaMode != AaMode.TUUA)) {
            nativeUpscalerActive = false;
            nativeUpscalerDetail = "inactive for current aaMode/upscaler selection";
            return base;
        }
        ExternalUpscalerBridge.Decision decision = externalUpscaler.evaluate(new ExternalUpscalerBridge.DecisionInput(
                "opengl",
                aaMode.name().toLowerCase(),
                upscalerMode.name().toLowerCase(),
                upscalerQuality.name().toLowerCase(),
                qualityTier.name().toLowerCase(),
                base.taaBlend(),
                base.taaClipScale(),
                base.taaSharpenStrength(),
                base.taaRenderScale(),
                base.taaLumaClipEnabled(),
                tsrControls.historyWeight(),
                tsrControls.responsiveMask(),
                tsrControls.neighborhoodClamp(),
                tsrControls.reprojectionConfidence(),
                tsrControls.sharpen(),
                tsrControls.antiRinging()
        ));
        if (decision == null || !decision.nativeActive()) {
            nativeUpscalerActive = false;
            nativeUpscalerDetail = decision == null ? "null external decision" : decision.detail();
            return base;
        }
        nativeUpscalerActive = true;
        nativeUpscalerDetail = decision.detail() == null || decision.detail().isBlank()
                ? "native overrides applied"
                : decision.detail();
        float taaBlend = decision.taaBlendOverride() == null ? base.taaBlend() : clamp(decision.taaBlendOverride(), 0f, 0.95f);
        float taaClipScale = decision.taaClipScaleOverride() == null ? base.taaClipScale() : clamp(decision.taaClipScaleOverride(), 0.5f, 1.6f);
        float taaSharpen = decision.taaSharpenStrengthOverride() == null ? base.taaSharpenStrength() : clamp(decision.taaSharpenStrengthOverride(), 0f, 0.35f);
        float taaRenderScale = decision.taaRenderScaleOverride() == null ? base.taaRenderScale() : clamp(decision.taaRenderScaleOverride(), 0.5f, 1.0f);
        boolean taaLumaClip = decision.taaLumaClipEnabledOverride() == null ? base.taaLumaClipEnabled() : decision.taaLumaClipEnabledOverride();
        return new PostProcessRenderConfig(
                base.tonemapEnabled(),
                base.exposure(),
                base.gamma(),
                base.bloomEnabled(),
                base.bloomThreshold(),
                base.bloomStrength(),
                base.ssaoEnabled(),
                base.ssaoStrength(),
                base.ssaoRadius(),
                base.ssaoBias(),
                base.ssaoPower(),
                base.smaaEnabled(),
                base.smaaStrength(),
                base.taaEnabled(),
                taaBlend,
                taaClipScale,
                taaLumaClip,
                taaSharpen,
                taaRenderScale,
                base.reflectionsEnabled(),
                base.reflectionsMode(),
                base.reflectionsSsrStrength(),
                base.reflectionsSsrMaxRoughness(),
                base.reflectionsSsrStepScale(),
                base.reflectionsTemporalWeight(),
                base.reflectionsPlanarStrength()
        );
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int packReflectionMode(ReflectionMode baseMode, ReflectionAdvancedDesc advanced) {
        int packed = baseMode.ordinal() & 0x7;
        if (advanced == null) {
            return packed;
        }
        if (advanced.hiZEnabled()) {
            packed |= 1 << 3;
        }
        int denoisePasses = Math.max(0, Math.min(6, advanced.denoisePasses()));
        packed |= (denoisePasses & 0x7) << 4;
        if (advanced.planarClipPlaneEnabled()) {
            packed |= 1 << 7;
        }
        if (advanced.probeVolumeEnabled()) {
            packed |= 1 << 8;
        }
        if (advanced.probeBoxProjectionEnabled()) {
            packed |= 1 << 9;
        }
        if (advanced.rtEnabled()) {
            packed |= 1 << 10;
        }
        return packed;
    }

    // ── camera ──

    private CameraDesc selectActiveCamera(SceneDescriptor scene) {
        if (scene == null || scene.cameras() == null || scene.cameras().isEmpty()) {
            return new CameraDesc("default", new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 100f);
        }
        if (scene.activeCameraId() != null && !scene.activeCameraId().isBlank()) {
            for (CameraDesc camera : scene.cameras()) {
                if (scene.activeCameraId().equals(camera.id())) {
                    return camera;
                }
            }
        }
        return scene.cameras().getFirst();
    }

    static CameraMatrices cameraMatricesFor(CameraDesc camera, float aspectRatio) {
        CameraDesc effective = camera == null
                ? new CameraDesc("default", new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 100f)
                : camera;

        Vec3 pos = effective.position() == null ? new Vec3(0f, 0f, 5f) : effective.position();
        Vec3 rot = effective.rotationEulerDeg() == null ? new Vec3(0f, 0f, 0f) : effective.rotationEulerDeg();
        float yaw = OpenGlSceneMeshMapper.radians(rot.y());
        float pitch = OpenGlSceneMeshMapper.radians(rot.x());

        float fx = (float) (Math.cos(pitch) * Math.sin(yaw));
        float fy = (float) Math.sin(pitch);
        float fz = (float) (-Math.cos(pitch) * Math.cos(yaw));

        float[] view = OpenGlSceneMeshMapper.lookAt(
                pos.x(), pos.y(), pos.z(),
                pos.x() + fx, pos.y() + fy, pos.z() + fz,
                0f, 1f, 0f
        );

        float near = effective.nearPlane() > 0f ? effective.nearPlane() : 0.1f;
        float far = effective.farPlane() > near ? effective.farPlane() : 100f;
        float fov = effective.fovDegrees() > 1f ? effective.fovDegrees() : 60f;
        float aspect = aspectRatio > 0.01f ? aspectRatio : (16f / 9f);
        float[] proj = OpenGlSceneMeshMapper.perspective(OpenGlSceneMeshMapper.radians(fov), aspect, near, far);
        return new CameraMatrices(view, proj);
    }

    // ── frame graph ──

    private FrameGraph buildFrameGraph() {
        FrameGraphBuilder builder = new FrameGraphBuilder()
                .addPass(pass("clear", Set.of(), Set.of(), Set.of("color"), context::renderClearPass))
                .addPass(pass("geometry", Set.of("clear"), Set.of(), Set.of("color"), context::renderGeometryPass));
        if (fog.enabled()) {
            builder.addPass(pass("fog", Set.of("geometry"), Set.of("color"), Set.of("color"), context::renderFogPass));
        }
        if (smoke.enabled()) {
            builder.addPass(pass("smoke", fog.enabled() ? Set.of("fog") : Set.of("geometry"),
                    Set.of("color"), Set.of("color"), context::renderSmokePass));
        }
        String postDependency = smoke.enabled() ? "smoke" : (fog.enabled() ? "fog" : "geometry");
        builder.addPass(pass("post", Set.of(postDependency), Set.of("color"), Set.of("color"), context::renderPostProcessPass));
        return builder.build();
    }

    private static FrameGraphPass pass(String id, Set<String> deps, Set<String> reads, Set<String> writes, Runnable work) {
        return new FrameGraphPass() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Set<String> dependsOn() {
                return deps;
            }

            @Override
            public Set<String> reads() {
                return reads;
            }

            @Override
            public Set<String> writes() {
                return writes;
            }

            @Override
            public void execute() {
                work.run();
            }
        };
    }

    // ── utilities ──

    private static float safeAspect(int width, int height) {
        if (height <= 0) {
            return 16f / 9f;
        }
        return Math.max(0.1f, (float) width / (float) height);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void pushPostProcessToContext() {
        context.setPostProcessParameters(
                postProcess.tonemapEnabled(),
                postProcess.exposure(),
                postProcess.gamma(),
                postProcess.bloomEnabled(),
                postProcess.bloomThreshold(),
                postProcess.bloomStrength(),
                postProcess.ssaoEnabled(),
                postProcess.ssaoStrength(),
                postProcess.ssaoRadius(),
                postProcess.ssaoBias(),
                postProcess.ssaoPower(),
                postProcess.smaaEnabled(),
                postProcess.smaaStrength(),
                postProcess.taaEnabled(),
                postProcess.taaBlend(),
                postProcess.taaClipScale(),
                postProcess.taaLumaClipEnabled(),
                postProcess.taaSharpenStrength(),
                postProcess.taaRenderScale(),
                postProcess.reflectionsEnabled(),
                postProcess.reflectionsMode(),
                postProcess.reflectionsSsrStrength(),
                postProcess.reflectionsSsrMaxRoughness(),
                postProcess.reflectionsSsrStepScale(),
                postProcess.reflectionsTemporalWeight(),
                postProcess.reflectionsPlanarStrength()
        );
    }
}
