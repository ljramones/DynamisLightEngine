package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.AntiAliasingDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.impl.common.AbstractEngineRuntime;
import org.dynamislight.impl.vulkan.asset.VulkanGltfMeshParser;
import org.dynamislight.impl.vulkan.asset.VulkanMeshAssetLoader;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.profile.FrameResourceProfile;
import org.dynamislight.impl.vulkan.profile.PostProcessPipelineProfile;
import org.dynamislight.impl.vulkan.profile.SceneReuseStats;
import org.dynamislight.impl.vulkan.profile.ShadowCascadeProfile;
import org.dynamislight.impl.vulkan.profile.VulkanFrameMetrics;
import org.dynamislight.impl.common.upscale.ExternalUpscalerBridge;
import org.dynamislight.impl.common.upscale.ExternalUpscalerIntegration;

public final class VulkanEngineRuntime extends AbstractEngineRuntime {
    enum AaPreset {
        PERFORMANCE,
        BALANCED,
        QUALITY,
        STABILITY
    }

    enum AaMode {
        TAA,
        TSR,
        TUUA,
        MSAA_SELECTIVE,
        HYBRID_TUUA_MSAA,
        DLAA,
        FXAA_LOW
    }

    enum UpscalerMode {
        NONE,
        FSR,
        XESS,
        DLSS
    }

    enum UpscalerQuality {
        PERFORMANCE,
        BALANCED,
        QUALITY,
        ULTRA_QUALITY
    }

    enum ReflectionProfile {
        PERFORMANCE,
        BALANCED,
        QUALITY,
        STABILITY
    }

    record TsrControls(
            float historyWeight,
            float responsiveMask,
            float neighborhoodClamp,
            float reprojectionConfidence,
            float sharpen,
            float antiRinging,
            float tsrRenderScale,
            float tuuaRenderScale
    ) {
    }

    private static final int DEFAULT_MESH_GEOMETRY_CACHE_ENTRIES = 256;
    private final VulkanContext context = new VulkanContext();
    private final VulkanRuntimeWarningPolicy warningPolicy = new VulkanRuntimeWarningPolicy();
    private final VulkanRuntimeWarningPolicy.State warningState = new VulkanRuntimeWarningPolicy.State();
    private final VulkanRuntimeWarningPolicy.Config warningConfig = new VulkanRuntimeWarningPolicy.Config();
    private boolean mockContext = true;
    private boolean windowVisible;
    private boolean forceDeviceLostOnRender;
    private boolean deviceLostRaised;
    private boolean postOffscreenRequested;
    private QualityTier qualityTier = QualityTier.MEDIUM;
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private long plannedVisibleObjects = 1;
    private Path assetRoot = Path.of(".");
    private VulkanMeshAssetLoader meshLoader = new VulkanMeshAssetLoader(assetRoot);
    private int meshGeometryCacheMaxEntries = DEFAULT_MESH_GEOMETRY_CACHE_ENTRIES;
    private MeshGeometryCacheProfile meshGeometryCacheProfile =
            new MeshGeometryCacheProfile(0, 0, 0, 0, DEFAULT_MESH_GEOMETRY_CACHE_ENTRIES);
    private int viewportWidth = 1280;
    private int viewportHeight = 720;
    private FogRenderConfig currentFog = new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0, false);
    private SmokeRenderConfig currentSmoke = new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
    private ShadowRenderConfig currentShadows = new ShadowRenderConfig(false, 0.45f, 0.0015f, 1, 1, 1024, false);
    private PostProcessRenderConfig currentPost = new PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.8f, false, 0f, 1.0f, 0.02f, 1.0f, false, 0f, false, 0f, 1.0f, false, 0.16f, 1.0f, false, 0, 0.6f, 0.78f, 1.0f, 0.80f, 0.35f);
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
    private IblRenderConfig currentIbl = new IblRenderConfig(false, 0f, 0f, false, false, false, false, 0, 0, 0, 0f, false, 0, null, null, null);
    private boolean nonDirectionalShadowRequested;

    public VulkanEngineRuntime() {
        super(
                "Vulkan",
                new EngineCapabilities(
                        Set.of("vulkan"),
                        true,
                        true,
                        true,
                        true,
                        7680,
                        4320,
                        Set.of(QualityTier.LOW, QualityTier.MEDIUM, QualityTier.HIGH, QualityTier.ULTRA)
                ),
                16.2,
                7.8
        );
    }

    @Override
    protected void onInitialize(EngineConfig config) throws EngineException {
        VulkanRuntimeOptions.Parsed options = VulkanRuntimeOptions.parse(config.backendOptions(), DEFAULT_MESH_GEOMETRY_CACHE_ENTRIES);
        mockContext = options.mockContext();
        windowVisible = options.windowVisible();
        forceDeviceLostOnRender = options.forceDeviceLostOnRender();
        postOffscreenRequested = options.postOffscreenRequested();
        meshGeometryCacheMaxEntries = options.meshGeometryCacheMaxEntries();
        warningConfig.descriptorRingWasteWarnRatio = options.descriptorRingWasteWarnRatio();
        warningConfig.descriptorRingWasteWarnMinFrames = options.descriptorRingWasteWarnMinFrames();
        warningConfig.descriptorRingWasteWarnMinCapacity = options.descriptorRingWasteWarnMinCapacity();
        warningConfig.descriptorRingWasteWarnCooldownFrames = options.descriptorRingWasteWarnCooldownFrames();
        warningConfig.descriptorRingCapPressureWarnMinBypasses = options.descriptorRingCapPressureWarnMinBypasses();
        warningConfig.descriptorRingCapPressureWarnMinFrames = options.descriptorRingCapPressureWarnMinFrames();
        warningConfig.descriptorRingCapPressureWarnCooldownFrames = options.descriptorRingCapPressureWarnCooldownFrames();
        warningConfig.uniformUploadSoftLimitBytes = options.uniformUploadSoftLimitBytes();
        warningConfig.uniformUploadWarnCooldownFrames = options.uniformUploadWarnCooldownFrames();
        warningConfig.pendingUploadRangeSoftLimit = options.pendingUploadRangeSoftLimit();
        warningConfig.pendingUploadRangeWarnCooldownFrames = options.pendingUploadRangeWarnCooldownFrames();
        warningConfig.descriptorRingActiveSoftLimit = options.descriptorRingActiveSoftLimit();
        warningConfig.descriptorRingActiveWarnCooldownFrames = options.descriptorRingActiveWarnCooldownFrames();
        taaDebugView = options.taaDebugView();
        context.setTaaDebugView(taaDebugView);
        taaLumaClipEnabledDefault = Boolean.parseBoolean(config.backendOptions().getOrDefault("vulkan.taaLumaClip", "false"));
        aaPreset = parseAaPreset(config.backendOptions().get("vulkan.aaPreset"));
        aaMode = parseAaMode(config.backendOptions().get("vulkan.aaMode"));
        upscalerMode = parseUpscalerMode(config.backendOptions().get("vulkan.upscalerMode"));
        upscalerQuality = parseUpscalerQuality(config.backendOptions().get("vulkan.upscalerQuality"));
        reflectionProfile = parseReflectionProfile(config.backendOptions().get("vulkan.reflectionsProfile"));
        tsrControls = parseTsrControls(config.backendOptions(), "vulkan.");
        externalUpscaler = ExternalUpscalerIntegration.create("vulkan", "vulkan.", config.backendOptions());
        nativeUpscalerActive = false;
        nativeUpscalerProvider = externalUpscaler.providerId();
        nativeUpscalerDetail = externalUpscaler.statusDetail();
        assetRoot = config.assetRoot() == null ? Path.of(".") : config.assetRoot();
        meshLoader = new VulkanMeshAssetLoader(assetRoot, meshGeometryCacheMaxEntries);
        qualityTier = config.qualityTier();
        viewportWidth = config.initialWidthPx();
        viewportHeight = config.initialHeightPx();
        deviceLostRaised = false;
        warningPolicy.reset(warningState);
        VulkanRuntimeLifecycle.initialize(context, config, options, plannedDrawCalls, plannedTriangles, plannedVisibleObjects);
    }

    @Override
    protected void onLoadScene(SceneDescriptor scene) throws EngineException {
        aaMode = resolveAaMode(scene.postProcess(), aaMode);
        taaDebugView = resolveTaaDebugView(scene.postProcess(), taaDebugView);
        context.setTaaDebugView(taaDebugView);
        VulkanRuntimeLifecycle.SceneLoadState sceneState = VulkanRuntimeLifecycle.prepareScene(
                scene,
                qualityTier,
                viewportWidth,
                viewportHeight,
                assetRoot,
                meshLoader,
                taaLumaClipEnabledDefault,
                aaPreset,
                aaMode,
                upscalerMode,
                upscalerQuality,
                tsrControls,
                reflectionProfile
        );
        currentFog = sceneState.fog();
        currentSmoke = sceneState.smoke();
        currentShadows = sceneState.shadows();
        currentPost = sceneState.post();
        currentPost = applyExternalUpscalerDecision(currentPost);
        currentIbl = sceneState.ibl();
        nonDirectionalShadowRequested = sceneState.nonDirectionalShadowRequested();
        meshGeometryCacheProfile = sceneState.meshGeometryCacheProfile();
        plannedDrawCalls = sceneState.plannedDrawCalls();
        plannedTriangles = sceneState.plannedTriangles();
        plannedVisibleObjects = sceneState.plannedVisibleObjects();
        if (!mockContext) {
            VulkanRuntimeLifecycle.applySceneToContext(context, sceneState);
        }
    }

    @Override
    protected RenderMetrics onRender() throws EngineException {
        VulkanRuntimeLifecycle.RenderState frame = VulkanRuntimeLifecycle.render(
                context,
                mockContext,
                forceDeviceLostOnRender,
                deviceLostRaised,
                plannedDrawCalls,
                plannedTriangles,
                plannedVisibleObjects
        );
        deviceLostRaised = frame.deviceLostRaised();
        return renderMetrics(
                frame.cpuMs(),
                frame.gpuMs(),
                frame.drawCalls(),
                frame.triangles(),
                frame.visibleObjects(),
                frame.gpuBytes()
        );
    }

    @Override
    protected void onResize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        viewportWidth = widthPx;
        viewportHeight = heightPx;
        VulkanRuntimeLifecycle.resize(context, mockContext, widthPx, heightPx);
    }

    @Override
    protected void onShutdown() {
        VulkanRuntimeLifecycle.shutdown(context, mockContext);
    }

    @Override
    protected java.util.List<EngineWarning> baselineWarnings() {
        return java.util.List.of(new EngineWarning("FEATURE_BASELINE", "Vulkan backend active with baseline indexed render path"));
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

    @Override
    protected java.util.List<EngineWarning> frameWarnings() {
        java.util.List<EngineWarning> warnings = new java.util.ArrayList<>(warningPolicy.frameWarnings(
                warningState,
                warningConfig,
                new VulkanRuntimeWarningPolicy.Inputs(
                        qualityTier,
                        currentFog,
                        currentSmoke,
                        currentShadows,
                        currentPost,
                        currentIbl,
                        upscalerMode,
                        upscalerQuality,
                        nativeUpscalerActive,
                        nativeUpscalerProvider,
                        nativeUpscalerDetail,
                        nonDirectionalShadowRequested,
                        mockContext,
                        postOffscreenRequested,
                        meshGeometryCacheProfile,
                        context
                )
        ));
        if (currentPost.reflectionsEnabled()) {
            warnings.add(new EngineWarning(
                    "REFLECTIONS_BASELINE_ACTIVE",
                    "Reflections baseline active (mode="
                            + switch (currentPost.reflectionsMode()) {
                        case 1 -> "ssr";
                        case 2 -> "planar";
                        case 3 -> "hybrid";
                        default -> "ibl_only";
                    }
                            + ", ssrStrength=" + currentPost.reflectionsSsrStrength()
                            + ", planarStrength=" + currentPost.reflectionsPlanarStrength() + ")"
            ));
            if (qualityTier == QualityTier.MEDIUM) {
                warnings.add(new EngineWarning(
                        "REFLECTIONS_QUALITY_DEGRADED",
                        "Reflections quality is reduced at MEDIUM tier to stabilize frame time"
                ));
            }
        }
        return warnings;
    }

    SceneReuseStats debugSceneReuseStats() {
        return context.sceneReuseStats();
    }

    FrameResourceProfile debugFrameResourceProfile() {
        return context.frameResourceProfile();
    }

    FrameResourceConfig debugFrameResourceConfig() {
        return new FrameResourceConfig(
                context.configuredFramesInFlight(),
                context.configuredMaxDynamicSceneObjects(),
                context.configuredMaxPendingUploadRanges(),
                context.configuredDescriptorRingMaxSetCapacity(),
                meshGeometryCacheMaxEntries
        );
    }

    static record MeshGeometryCacheProfile(long hits, long misses, long evictions, int entries, int maxEntries) {
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
                "vulkan",
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

    private static ReflectionProfile parseReflectionProfile(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReflectionProfile.BALANCED;
        }
        return switch (raw.trim().toLowerCase()) {
            case "performance" -> ReflectionProfile.PERFORMANCE;
            case "quality" -> ReflectionProfile.QUALITY;
            case "stability" -> ReflectionProfile.STABILITY;
            default -> ReflectionProfile.BALANCED;
        };
    }

    private AaMode resolveAaMode(PostProcessDesc postProcess, AaMode fallback) {
        if (postProcess == null || postProcess.antiAliasing() == null) {
            return fallback;
        }
        String raw = postProcess.antiAliasing().mode();
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return parseAaMode(raw);
    }

    private static int resolveTaaDebugView(PostProcessDesc postProcess, int fallback) {
        if (postProcess == null || postProcess.antiAliasing() == null) {
            return fallback;
        }
        AntiAliasingDesc aa = postProcess.antiAliasing();
        return Math.max(0, Math.min(5, aa.debugView()));
    }

    private static AaPreset parseAaPreset(String raw) {
        if (raw == null || raw.isBlank()) {
            return AaPreset.BALANCED;
        }
        try {
            return AaPreset.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AaPreset.BALANCED;
        }
    }

    private static AaMode parseAaMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return AaMode.TAA;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return AaMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return AaMode.TAA;
        }
    }

    private static UpscalerMode parseUpscalerMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return UpscalerMode.NONE;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return UpscalerMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return UpscalerMode.NONE;
        }
    }

    private static UpscalerQuality parseUpscalerQuality(String raw) {
        if (raw == null || raw.isBlank()) {
            return UpscalerQuality.QUALITY;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return UpscalerQuality.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return UpscalerQuality.QUALITY;
        }
    }

    private static TsrControls parseTsrControls(java.util.Map<String, String> options, String prefix) {
        return new TsrControls(
                parseFloatOption(options, prefix + "tsrHistoryWeight", 0.90f, 0.50f, 0.99f),
                parseFloatOption(options, prefix + "tsrResponsiveMask", 0.65f, 0.0f, 1.0f),
                parseFloatOption(options, prefix + "tsrNeighborhoodClamp", 0.88f, 0.50f, 1.20f),
                parseFloatOption(options, prefix + "tsrReprojectionConfidence", 0.85f, 0.10f, 1.0f),
                parseFloatOption(options, prefix + "tsrSharpen", 0.14f, 0.0f, 0.35f),
                parseFloatOption(options, prefix + "tsrAntiRinging", 0.75f, 0.0f, 1.0f),
                parseFloatOption(options, prefix + "tsrRenderScale", 0.60f, 0.50f, 1.0f),
                parseFloatOption(options, prefix + "tuuaRenderScale", 0.72f, 0.50f, 1.0f)
        );
    }

    private static float parseFloatOption(java.util.Map<String, String> options, String key, float fallback, float min, float max) {
        String raw = options == null ? null : options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Float.parseFloat(raw.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    record FrameResourceConfig(
            int framesInFlight,
            int maxDynamicSceneObjects,
            int maxPendingUploadRanges,
            int maxTextureDescriptorSets,
            int meshGeometryCacheEntries
    ) {
    }

    static record FogRenderConfig(boolean enabled, float r, float g, float b, float density, int steps, boolean degraded) {
    }

    static record SmokeRenderConfig(boolean enabled, float r, float g, float b, float intensity, boolean degraded) {
    }

    static record PostProcessRenderConfig(
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

    static record IblRenderConfig(
            boolean enabled,
            float diffuseStrength,
            float specularStrength,
            boolean textureDriven,
            boolean skyboxDerived,
            boolean ktxContainerRequested,
            boolean ktxSkyboxFallback,
            int ktxDecodeUnavailableCount,
            int ktxTranscodeRequiredCount,
            int ktxUnsupportedVariantCount,
            float prefilterStrength,
            boolean degraded,
            int missingAssetCount,
            Path irradiancePath,
            Path radiancePath,
            Path brdfLutPath
    ) {
    }


    static record ShadowRenderConfig(
            boolean enabled,
            float strength,
            float bias,
            int pcfRadius,
            int cascadeCount,
            int mapResolution,
            boolean degraded
    ) {
    }

    static record CameraMatrices(float[] view, float[] proj) {
    }

    static record LightingConfig(
            float[] directionalDirection,
            float[] directionalColor,
            float directionalIntensity,
            float[] pointPosition,
            float[] pointColor,
            float pointIntensity,
            float[] pointDirection,
            float pointInnerCos,
            float pointOuterCos,
            boolean pointIsSpot,
            float pointRange,
            boolean pointCastsShadows
    ) {
    }

}
