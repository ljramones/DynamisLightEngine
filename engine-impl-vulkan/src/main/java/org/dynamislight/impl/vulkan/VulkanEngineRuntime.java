package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
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
    private static final int REFLECTION_PROBE_CHURN_WARN_MIN_DELTA = 1;
    private static final int REFLECTION_PROBE_CHURN_WARN_MIN_STREAK = 3;
    private static final int REFLECTION_PROBE_CHURN_WARN_COOLDOWN_FRAMES = 120;
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
    private ShadowRenderConfig currentShadows = new ShadowRenderConfig(
            false, 0.45f, 0.0015f, 1.0f, 1.0f, 1, 1, 1024,
            0, 0, "none", "none",
            0, 0, 0.0f, 0,
            0L, 0L, 0L,
            0L,
            0, 0, 0,
            "",
            0,
            "",
            0,
            "pcf", "pcf", false, false, false, false, "off", false,
            false
    );
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
    private String shadowFilterPath = "pcf";
    private boolean shadowContactShadows;
    private String shadowRtMode = "off";
    private boolean shadowRtBvhStrict;
    private float shadowRtDenoiseStrength = 0.65f;
    private float shadowRtRayLength = 80.0f;
    private int shadowRtSampleCount = 2;
    private float shadowRtDedicatedDenoiseStrength = -1.0f;
    private float shadowRtDedicatedRayLength = -1.0f;
    private int shadowRtDedicatedSampleCount = -1;
    private float shadowRtProductionDenoiseStrength = -1.0f;
    private float shadowRtProductionRayLength = -1.0f;
    private int shadowRtProductionSampleCount = -1;
    private float shadowPcssSoftness = 1.0f;
    private float shadowMomentBlend = 1.0f;
    private float shadowMomentBleedReduction = 1.0f;
    private float shadowContactStrength = 1.0f;
    private float shadowContactTemporalMotionScale = 1.0f;
    private float shadowContactTemporalMinStability = 0.42f;
    private int shadowMaxShadowedLocalLights;
    private int shadowMaxLocalLayers;
    private int shadowMaxFacesPerFrame;
    private boolean shadowSchedulerEnabled = true;
    private int shadowSchedulerHeroPeriod = 1;
    private int shadowSchedulerMidPeriod = 2;
    private int shadowSchedulerDistantPeriod = 4;
    private boolean shadowDirectionalTexelSnapEnabled = true;
    private float shadowDirectionalTexelSnapScale = 1.0f;
    private long shadowSchedulerFrameTick;
    private List<LightDesc> currentSceneLights = List.of();
    private final Map<String, Long> shadowSchedulerLastRenderedTicks = new HashMap<>();
    private final Map<String, Integer> shadowLayerAllocatorAssignments = new HashMap<>();
    private int shadowAllocatorAssignedLights;
    private int shadowAllocatorReusedAssignments;
    private int shadowAllocatorEvictions;
    private boolean shadowRtTraversalSupported;
    private boolean shadowRtBvhSupported;
    private int lastActiveReflectionProbeCount = -1;
    private int reflectionProbeLastDelta;
    private int reflectionProbeActiveChurnEvents;
    private long reflectionProbeActiveDeltaAccum;
    private int reflectionProbeChurnHighStreak;
    private int reflectionProbeChurnWarnCooldownRemaining;
    private int reflectionProbeChurnWarnMinDelta = REFLECTION_PROBE_CHURN_WARN_MIN_DELTA;
    private int reflectionProbeChurnWarnMinStreak = REFLECTION_PROBE_CHURN_WARN_MIN_STREAK;
    private int reflectionProbeChurnWarnCooldownFrames = REFLECTION_PROBE_CHURN_WARN_COOLDOWN_FRAMES;
    private double reflectionSsrTaaInstabilityRejectMin = 0.35;
    private double reflectionSsrTaaInstabilityConfidenceMax = 0.70;
    private long reflectionSsrTaaInstabilityDropEventsMin;
    private int reflectionSsrTaaInstabilityWarnMinFrames = 3;
    private int reflectionSsrTaaInstabilityWarnCooldownFrames = 120;
    private double reflectionSsrTaaRiskEmaAlpha = 0.25;
    private boolean reflectionSsrTaaAdaptiveEnabled;
    private double reflectionSsrTaaAdaptiveTemporalBoostMax = 0.12;
    private double reflectionSsrTaaAdaptiveSsrStrengthScaleMin = 0.70;
    private double reflectionSsrTaaAdaptiveStepScaleBoostMax = 0.15;
    private int reflectionSsrTaaRiskHighStreak;
    private int reflectionSsrTaaRiskWarnCooldownRemaining;
    private double reflectionSsrTaaEmaReject = -1.0;
    private double reflectionSsrTaaEmaConfidence = -1.0;
    private float reflectionAdaptiveTemporalWeightActive = 0.80f;
    private float reflectionAdaptiveSsrStrengthActive = 0.6f;
    private float reflectionAdaptiveSsrStepScaleActive = 1.0f;

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
        shadowFilterPath = options.shadowFilterPath();
        shadowContactShadows = options.shadowContactShadows();
        shadowRtMode = options.shadowRtMode();
        shadowRtBvhStrict = options.shadowRtBvhStrict();
        shadowRtDenoiseStrength = options.shadowRtDenoiseStrength();
        shadowRtRayLength = options.shadowRtRayLength();
        shadowRtSampleCount = options.shadowRtSampleCount();
        shadowRtDedicatedDenoiseStrength = options.shadowRtDedicatedDenoiseStrength();
        shadowRtDedicatedRayLength = options.shadowRtDedicatedRayLength();
        shadowRtDedicatedSampleCount = options.shadowRtDedicatedSampleCount();
        shadowRtProductionDenoiseStrength = options.shadowRtProductionDenoiseStrength();
        shadowRtProductionRayLength = options.shadowRtProductionRayLength();
        shadowRtProductionSampleCount = options.shadowRtProductionSampleCount();
        shadowPcssSoftness = options.shadowPcssSoftness();
        shadowMomentBlend = options.shadowMomentBlend();
        shadowMomentBleedReduction = options.shadowMomentBleedReduction();
        shadowContactStrength = options.shadowContactStrength();
        shadowContactTemporalMotionScale = options.shadowContactTemporalMotionScale();
        shadowContactTemporalMinStability = options.shadowContactTemporalMinStability();
        shadowMaxShadowedLocalLights = options.shadowMaxShadowedLocalLights();
        shadowMaxLocalLayers = options.shadowMaxLocalLayers();
        shadowMaxFacesPerFrame = options.shadowMaxFacesPerFrame();
        shadowSchedulerEnabled = options.shadowSchedulerEnabled();
        shadowSchedulerHeroPeriod = options.shadowSchedulerHeroPeriod();
        shadowSchedulerMidPeriod = options.shadowSchedulerMidPeriod();
        shadowSchedulerDistantPeriod = options.shadowSchedulerDistantPeriod();
        shadowDirectionalTexelSnapEnabled = options.shadowDirectionalTexelSnapEnabled();
        shadowDirectionalTexelSnapScale = options.shadowDirectionalTexelSnapScale();
        reflectionProbeChurnWarnMinDelta = options.reflectionProbeChurnWarnMinDelta();
        reflectionProbeChurnWarnMinStreak = options.reflectionProbeChurnWarnMinStreak();
        reflectionProbeChurnWarnCooldownFrames = options.reflectionProbeChurnWarnCooldownFrames();
        reflectionSsrTaaInstabilityRejectMin = options.reflectionSsrTaaInstabilityRejectMin();
        reflectionSsrTaaInstabilityConfidenceMax = options.reflectionSsrTaaInstabilityConfidenceMax();
        reflectionSsrTaaInstabilityDropEventsMin = options.reflectionSsrTaaInstabilityDropEventsMin();
        reflectionSsrTaaInstabilityWarnMinFrames = options.reflectionSsrTaaInstabilityWarnMinFrames();
        reflectionSsrTaaInstabilityWarnCooldownFrames = options.reflectionSsrTaaInstabilityWarnCooldownFrames();
        reflectionSsrTaaRiskEmaAlpha = options.reflectionSsrTaaRiskEmaAlpha();
        reflectionSsrTaaAdaptiveEnabled = options.reflectionSsrTaaAdaptiveEnabled();
        reflectionSsrTaaAdaptiveTemporalBoostMax = options.reflectionSsrTaaAdaptiveTemporalBoostMax();
        reflectionSsrTaaAdaptiveSsrStrengthScaleMin = options.reflectionSsrTaaAdaptiveSsrStrengthScaleMin();
        reflectionSsrTaaAdaptiveStepScaleBoostMax = options.reflectionSsrTaaAdaptiveStepScaleBoostMax();
        shadowSchedulerFrameTick = 0L;
        currentSceneLights = List.of();
        shadowSchedulerLastRenderedTicks.clear();
        shadowLayerAllocatorAssignments.clear();
        shadowAllocatorAssignedLights = 0;
        shadowAllocatorReusedAssignments = 0;
        shadowAllocatorEvictions = 0;
        resetReflectionProbeChurnDiagnostics();
        resetReflectionSsrTaaRiskDiagnostics();
        resetReflectionAdaptiveState();
        context.setTaaDebugView(taaDebugView);
        taaLumaClipEnabledDefault = Boolean.parseBoolean(config.backendOptions().getOrDefault("vulkan.taaLumaClip", "false"));
        aaPreset = parseAaPreset(config.backendOptions().get("vulkan.aaPreset"));
        aaMode = parseAaMode(config.backendOptions().get("vulkan.aaMode"));
        upscalerMode = parseUpscalerMode(config.backendOptions().get("vulkan.upscalerMode"));
        upscalerQuality = parseUpscalerQuality(config.backendOptions().get("vulkan.upscalerQuality"));
        reflectionProfile = parseReflectionProfile(config.backendOptions().get("vulkan.reflectionsProfile"));
        applyReflectionProfileTelemetryDefaults(config.backendOptions());
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
        shadowRtTraversalSupported = !mockContext && context.isHardwareRtShadowTraversalSupported();
        shadowRtBvhSupported = !mockContext && context.isHardwareRtShadowBvhSupported();
        if (shadowRtBvhStrict) {
            if ("bvh".equals(shadowRtMode) && !shadowRtBvhSupported) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Strict BVH RT shadow mode requested but BVH capability is unavailable "
                                + "(rtMode=bvh, strict=true, mockContext=" + mockContext + ")",
                        false
                );
            }
            if ("bvh_production".equals(shadowRtMode) && !shadowRtBvhSupported) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Strict production BVH RT shadow mode requested but BVH capability is unavailable "
                                + "(rtMode=bvh_production, strict=true, mockContext=" + mockContext + ")",
                        false
                );
            }
            if ("bvh_dedicated".equals(shadowRtMode)) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Strict dedicated BVH RT shadow mode requested but dedicated BVH traversal pipeline is unavailable "
                                + "(rtMode=bvh_dedicated, strict=true, mockContext=" + mockContext + ")",
                        false
                );
            }
            if (("rt_native".equals(shadowRtMode) || "rt_native_denoised".equals(shadowRtMode)) && !shadowRtTraversalSupported) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Strict native RT shadow mode requested but ray traversal capability is unavailable "
                                + "(rtMode=" + shadowRtMode + ", strict=true, mockContext=" + mockContext + ")",
                        false
                );
            }
        }
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
                reflectionProfile,
                shadowFilterPath,
                shadowContactShadows,
                shadowRtMode,
                shadowMaxShadowedLocalLights,
                shadowMaxLocalLayers,
                shadowMaxFacesPerFrame,
                shadowRtTraversalSupported,
                shadowRtBvhSupported,
                shadowSchedulerEnabled,
                shadowSchedulerHeroPeriod,
                shadowSchedulerMidPeriod,
                shadowSchedulerDistantPeriod,
                shadowSchedulerFrameTick,
                shadowSchedulerLastRenderedTicks,
                shadowLayerAllocatorAssignments
        );
        currentFog = sceneState.fog();
        currentSmoke = sceneState.smoke();
        currentShadows = sceneState.shadows();
        currentPost = sceneState.post();
        currentPost = applyExternalUpscalerDecision(currentPost);
        resetReflectionAdaptiveState();
        currentIbl = sceneState.ibl();
        nonDirectionalShadowRequested = sceneState.nonDirectionalShadowRequested();
        meshGeometryCacheProfile = sceneState.meshGeometryCacheProfile();
        plannedDrawCalls = sceneState.plannedDrawCalls();
        plannedTriangles = sceneState.plannedTriangles();
        plannedVisibleObjects = sceneState.plannedVisibleObjects();
        currentSceneLights = scene == null || scene.lights() == null ? List.of() : new ArrayList<>(scene.lights());
        shadowSchedulerLastRenderedTicks.clear();
        shadowLayerAllocatorAssignments.clear();
        shadowLayerAllocatorAssignments.putAll(sceneState.lighting().shadowLayerAssignments());
        shadowAllocatorAssignedLights = sceneState.lighting().shadowAllocatorAssignedLights();
        shadowAllocatorReusedAssignments = sceneState.lighting().shadowAllocatorReusedAssignments();
        shadowAllocatorEvictions = sceneState.lighting().shadowAllocatorEvictions();
        updateShadowSchedulerTicks(currentShadows.renderedShadowLightIdsCsv());
        VulkanRuntimeLifecycle.applySceneToContext(context, sceneState);
        if (!mockContext) {
            currentShadows = withRuntimeMomentPipelineState(currentShadows);
            context.setShadowQualityModes(
                    currentShadows.runtimeFilterPath(),
                    currentShadows.contactShadowsRequested(),
                    currentShadows.rtShadowMode(),
                    currentShadows.filterPath()
            );
            context.setShadowQualityTuning(
                    shadowPcssSoftness,
                    shadowMomentBlend,
                    shadowMomentBleedReduction,
                    shadowContactStrength,
                    shadowContactTemporalMotionScale,
                    shadowContactTemporalMinStability
            );
            context.setShadowRtTuning(
                    effectiveShadowRtDenoiseStrength(currentShadows.rtShadowMode()),
                    effectiveShadowRtRayLength(currentShadows.rtShadowMode()),
                    effectiveShadowRtSampleCount(currentShadows.rtShadowMode())
            );
        }
    }

    @Override
    protected RenderMetrics onRender() throws EngineException {
        shadowSchedulerFrameTick++;
        if (!mockContext && !currentSceneLights.isEmpty()) {
            VulkanRuntimeLifecycle.ShadowRefreshState refresh = VulkanRuntimeLifecycle.refreshShadows(
                    currentSceneLights,
                    qualityTier,
                    shadowFilterPath,
                    shadowContactShadows,
                    shadowRtMode,
                    shadowMaxShadowedLocalLights,
                    shadowMaxLocalLayers,
                    shadowMaxFacesPerFrame,
                    shadowRtTraversalSupported,
                    shadowRtBvhSupported,
                    shadowSchedulerEnabled,
                    shadowSchedulerHeroPeriod,
                    shadowSchedulerMidPeriod,
                    shadowSchedulerDistantPeriod,
                    shadowSchedulerFrameTick,
                    shadowSchedulerLastRenderedTicks,
                    shadowLayerAllocatorAssignments
            );
            currentShadows = refresh.shadows();
            updateShadowSchedulerTicks(currentShadows.renderedShadowLightIdsCsv());
            shadowLayerAllocatorAssignments.clear();
            shadowLayerAllocatorAssignments.putAll(refresh.lighting().shadowLayerAssignments());
            shadowAllocatorAssignedLights = refresh.lighting().shadowAllocatorAssignedLights();
            shadowAllocatorReusedAssignments = refresh.lighting().shadowAllocatorReusedAssignments();
            shadowAllocatorEvictions = refresh.lighting().shadowAllocatorEvictions();
            currentShadows = withRuntimeMomentPipelineState(currentShadows);
            context.setLightingParameters(
                    refresh.lighting().directionalDirection(),
                    refresh.lighting().directionalColor(),
                    refresh.lighting().directionalIntensity(),
                    refresh.lighting().shadowPointPosition(),
                    refresh.lighting().shadowPointDirection(),
                    refresh.lighting().shadowPointIsSpot(),
                    refresh.lighting().shadowPointOuterCos(),
                    refresh.lighting().shadowPointRange(),
                    refresh.lighting().shadowPointCastsShadows(),
                    refresh.lighting().localLightCount(),
                    refresh.lighting().localLightPosRange(),
                    refresh.lighting().localLightColorIntensity(),
                    refresh.lighting().localLightDirInner(),
                    refresh.lighting().localLightOuterTypeShadow()
            );
            context.setShadowParameters(
                    currentShadows.enabled(),
                    currentShadows.strength(),
                    currentShadows.bias(),
                    currentShadows.normalBiasScale(),
                    currentShadows.slopeBiasScale(),
                    currentShadows.pcfRadius(),
                    currentShadows.cascadeCount(),
                    currentShadows.mapResolution()
            );
            context.setShadowQualityModes(
                    currentShadows.runtimeFilterPath(),
                    currentShadows.contactShadowsRequested(),
                    currentShadows.rtShadowMode(),
                    currentShadows.filterPath()
            );
            context.setShadowQualityTuning(
                    shadowPcssSoftness,
                    shadowMomentBlend,
                    shadowMomentBleedReduction,
                    shadowContactStrength,
                    shadowContactTemporalMotionScale,
                    shadowContactTemporalMinStability
            );
            context.setShadowRtTuning(
                    effectiveShadowRtDenoiseStrength(currentShadows.rtShadowMode()),
                    effectiveShadowRtRayLength(currentShadows.rtShadowMode()),
                    effectiveShadowRtSampleCount(currentShadows.rtShadowMode())
            );
            context.setShadowDirectionalTexelSnap(
                    shadowDirectionalTexelSnapEnabled,
                    shadowDirectionalTexelSnapScale
            );
        }
        applyAdaptiveReflectionPostParameters();
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
            int reflectionBaseMode = currentPost.reflectionsMode() & 0x7;
            ReflectionOverrideSummary overrideSummary = summarizeReflectionOverrides(context.debugGpuMeshReflectionOverrideModes());
            VulkanContext.ReflectionProbeDiagnostics probeDiagnostics = context.debugReflectionProbeDiagnostics();
            ReflectionProbeChurnDiagnostics churnDiagnostics = updateReflectionProbeChurnDiagnostics(probeDiagnostics);
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
                            + ", ssrStrength=" + currentPost.reflectionsSsrStrength()
                            + ", planarStrength=" + currentPost.reflectionsPlanarStrength()
                            + ", overrideAuto=" + overrideSummary.autoCount()
                            + ", overrideProbeOnly=" + overrideSummary.probeOnlyCount()
                            + ", overrideSsrOnly=" + overrideSummary.ssrOnlyCount()
                            + ", overrideOther=" + overrideSummary.otherCount()
                            + ", probeConfigured=" + probeDiagnostics.configuredProbeCount()
                            + ", probeActive=" + probeDiagnostics.activeProbeCount()
                            + ", probeSlots=" + probeDiagnostics.slotCount()
                            + ", probeCapacity=" + probeDiagnostics.metadataCapacity()
                            + ", probeDelta=" + churnDiagnostics.lastDelta()
                            + ", probeChurnEvents=" + churnDiagnostics.churnEvents()
                            + ")"
            ));
            warnings.add(new EngineWarning(
                    "REFLECTION_PROBE_BLEND_DIAGNOSTICS",
                    "Probe blend diagnostics (configured=" + probeDiagnostics.configuredProbeCount()
                            + ", active=" + probeDiagnostics.activeProbeCount()
                            + ", slots=" + probeDiagnostics.slotCount()
                            + ", capacity=" + probeDiagnostics.metadataCapacity()
                            + ", delta=" + churnDiagnostics.lastDelta()
                            + ", churnEvents=" + churnDiagnostics.churnEvents()
                            + ", meanDelta=" + churnDiagnostics.meanDelta()
                            + ", highStreak=" + churnDiagnostics.highStreak()
                            + ", warnMinDelta=" + reflectionProbeChurnWarnMinDelta
                            + ", warnMinStreak=" + reflectionProbeChurnWarnMinStreak
                            + ", warnCooldownFrames=" + reflectionProbeChurnWarnCooldownFrames
                            + ", cooldownRemaining=" + churnDiagnostics.warnCooldownRemaining()
                            + ")"
            ));
            warnings.add(new EngineWarning(
                    "REFLECTION_TELEMETRY_PROFILE_ACTIVE",
                    "Reflection telemetry profile active (profile=" + reflectionProfile.name().toLowerCase()
                            + ", probeWarnMinDelta=" + reflectionProbeChurnWarnMinDelta
                            + ", probeWarnMinStreak=" + reflectionProbeChurnWarnMinStreak
                            + ", probeWarnCooldownFrames=" + reflectionProbeChurnWarnCooldownFrames
                            + ", ssrTaaRejectMin=" + reflectionSsrTaaInstabilityRejectMin
                            + ", ssrTaaConfidenceMax=" + reflectionSsrTaaInstabilityConfidenceMax
                            + ", ssrTaaDropEventsMin=" + reflectionSsrTaaInstabilityDropEventsMin
                            + ", ssrTaaWarnMinFrames=" + reflectionSsrTaaInstabilityWarnMinFrames
                            + ", ssrTaaWarnCooldownFrames=" + reflectionSsrTaaInstabilityWarnCooldownFrames
                            + ", ssrTaaRiskEmaAlpha=" + reflectionSsrTaaRiskEmaAlpha
                            + ", ssrTaaAdaptiveEnabled=" + reflectionSsrTaaAdaptiveEnabled
                            + ", ssrTaaAdaptiveTemporalBoostMax=" + reflectionSsrTaaAdaptiveTemporalBoostMax
                            + ", ssrTaaAdaptiveSsrStrengthScaleMin=" + reflectionSsrTaaAdaptiveSsrStrengthScaleMin
                            + ", ssrTaaAdaptiveStepScaleBoostMax=" + reflectionSsrTaaAdaptiveStepScaleBoostMax
                            + ")"
            ));
            boolean ssrPathActive = reflectionBaseMode == 1 || reflectionBaseMode == 3 || reflectionBaseMode == 4;
            if (ssrPathActive && currentPost.taaEnabled()) {
                double taaReject = context.taaHistoryRejectRate();
                double taaConfidence = context.taaConfidenceMean();
                long taaDrops = context.taaConfidenceDropEvents();
                ReflectionSsrTaaRiskDiagnostics ssrTaaRisk = updateReflectionSsrTaaRiskDiagnostics(taaReject, taaConfidence, taaDrops);
                warnings.add(new EngineWarning(
                        "REFLECTION_SSR_TAA_DIAGNOSTICS",
                        "SSR/TAA diagnostics (mode="
                                + switch (reflectionBaseMode) {
                            case 1 -> "ssr";
                            case 3 -> "hybrid";
                            case 4 -> "rt_hybrid_fallback";
                            default -> "unknown";
                        }
                                + ", ssrStrength=" + currentPost.reflectionsSsrStrength()
                                + ", ssrMaxRoughness=" + currentPost.reflectionsSsrMaxRoughness()
                                + ", ssrStepScale=" + currentPost.reflectionsSsrStepScale()
                                + ", reflectionTemporalWeight=" + currentPost.reflectionsTemporalWeight()
                                + ", taaBlend=" + currentPost.taaBlend()
                                + ", taaClipScale=" + currentPost.taaClipScale()
                                + ", taaLumaClip=" + currentPost.taaLumaClipEnabled()
                                + ", historyRejectRate=" + taaReject
                                + ", confidenceMean=" + taaConfidence
                                + ", confidenceDropEvents=" + taaDrops
                                + ", instabilityRejectMin=" + reflectionSsrTaaInstabilityRejectMin
                                + ", instabilityConfidenceMax=" + reflectionSsrTaaInstabilityConfidenceMax
                                + ", instabilityDropEventsMin=" + reflectionSsrTaaInstabilityDropEventsMin
                                + ", instabilityWarnMinFrames=" + reflectionSsrTaaInstabilityWarnMinFrames
                                + ", instabilityWarnCooldownFrames=" + reflectionSsrTaaInstabilityWarnCooldownFrames
                                + ", instabilityRiskEmaAlpha=" + reflectionSsrTaaRiskEmaAlpha
                                + ", instabilityRiskHighStreak=" + ssrTaaRisk.highStreak()
                                + ", instabilityRiskCooldownRemaining=" + ssrTaaRisk.warnCooldownRemaining()
                                + ", instabilityRiskEmaReject=" + ssrTaaRisk.emaReject()
                                + ", instabilityRiskEmaConfidence=" + ssrTaaRisk.emaConfidence()
                                + ", instabilityRiskInstant=" + ssrTaaRisk.instantRisk()
                                + ", adaptiveTemporalWeightActive=" + reflectionAdaptiveTemporalWeightActive
                                + ", adaptiveSsrStrengthActive=" + reflectionAdaptiveSsrStrengthActive
                                + ", adaptiveSsrStepScaleActive=" + reflectionAdaptiveSsrStepScaleActive
                                + ")"
                ));
                warnings.add(new EngineWarning(
                        "REFLECTION_SSR_TAA_ADAPTIVE_POLICY_ACTIVE",
                        "SSR/TAA adaptive policy (enabled=" + reflectionSsrTaaAdaptiveEnabled
                                + ", baseTemporalWeight=" + currentPost.reflectionsTemporalWeight()
                                + ", activeTemporalWeight=" + reflectionAdaptiveTemporalWeightActive
                                + ", baseSsrStrength=" + currentPost.reflectionsSsrStrength()
                                + ", activeSsrStrength=" + reflectionAdaptiveSsrStrengthActive
                                + ", baseSsrStepScale=" + currentPost.reflectionsSsrStepScale()
                                + ", activeSsrStepScale=" + reflectionAdaptiveSsrStepScaleActive
                                + ", riskHighStreak=" + ssrTaaRisk.highStreak()
                                + ", riskWarnCooldownRemaining=" + ssrTaaRisk.warnCooldownRemaining()
                                + ")"
                ));
                if (ssrTaaRisk.warningTriggered()) {
                    warnings.add(new EngineWarning(
                            "REFLECTION_SSR_TAA_INSTABILITY_RISK",
                            "SSR/TAA instability risk detected (historyRejectRate="
                                    + taaReject
                                    + ", confidenceMean=" + taaConfidence
                                    + ", confidenceDropEvents=" + taaDrops
                                    + ", riskHighStreak=" + ssrTaaRisk.highStreak()
                                    + ", riskEmaReject=" + ssrTaaRisk.emaReject()
                                    + ", riskEmaConfidence=" + ssrTaaRisk.emaConfidence() + ")"
                    ));
                }
            } else {
                resetReflectionSsrTaaRiskDiagnostics();
                resetReflectionAdaptiveState();
            }
            if (churnDiagnostics.warningTriggered()) {
                warnings.add(new EngineWarning(
                        "REFLECTION_PROBE_CHURN_HIGH",
                        "Active reflection probe set changed repeatedly across frames "
                                + "(delta=" + churnDiagnostics.lastDelta()
                                + ", churnEvents=" + churnDiagnostics.churnEvents()
                                + ", meanDelta=" + churnDiagnostics.meanDelta()
                                + ", highStreak=" + churnDiagnostics.highStreak() + ")"
                ));
            }
            if (qualityTier == QualityTier.MEDIUM) {
                warnings.add(new EngineWarning(
                        "REFLECTIONS_QUALITY_DEGRADED",
                        "Reflections quality is reduced at MEDIUM tier to stabilize frame time"
                ));
            }
        } else {
            resetReflectionProbeChurnDiagnostics();
        }
        if (currentShadows.enabled()) {
            String momentPhase = "pending";
            if (context.hasShadowMomentResources()) {
                momentPhase = context.isShadowMomentInitialized() ? "active" : "initializing";
            }
            warnings.add(new EngineWarning(
                    "SHADOW_POLICY_ACTIVE",
                    "Shadow policy active: primary=" + currentShadows.primaryShadowLightId()
                            + " type=" + currentShadows.primaryShadowType()
                            + " localBudget=" + currentShadows.maxShadowedLocalLights()
                            + " localSelected=" + currentShadows.selectedLocalShadowLights()
                            + " atlasTiles=" + currentShadows.atlasAllocatedTiles() + "/" + currentShadows.atlasCapacityTiles()
                            + " atlasUtilization=" + currentShadows.atlasUtilization()
                            + " atlasEvictions=" + currentShadows.atlasEvictions()
                            + " atlasMemoryD16Bytes=" + currentShadows.atlasMemoryBytesD16()
                            + " atlasMemoryD32Bytes=" + currentShadows.atlasMemoryBytesD32()
                            + " shadowUpdateBytesEstimate=" + currentShadows.shadowUpdateBytesEstimate()
                            + " shadowMomentAtlasBytesEstimate=" + currentShadows.shadowMomentAtlasBytesEstimate()
                            + " shadowDepthFormat=" + context.shadowDepthFormatTag()
                            + " cadencePolicy=hero:1 mid:2 distant:4"
                            + " renderedLocalShadows=" + currentShadows.renderedLocalShadowLights()
                            + " renderedSpotShadows=" + currentShadows.renderedSpotShadowLights()
                            + " renderedPointShadowCubemaps=" + currentShadows.renderedPointShadowCubemaps()
                            + " maxShadowedLocalLightsConfigured=" + (shadowMaxShadowedLocalLights > 0 ? Integer.toString(shadowMaxShadowedLocalLights) : "auto")
                            + " maxLocalShadowLayersConfigured=" + (shadowMaxLocalLayers > 0 ? Integer.toString(shadowMaxLocalLayers) : "auto")
                            + " maxShadowFacesPerFrameConfigured=" + (shadowMaxFacesPerFrame > 0 ? Integer.toString(shadowMaxFacesPerFrame) : "auto")
                            + " schedulerEnabled=" + shadowSchedulerEnabled
                            + " schedulerPeriodHero=" + shadowSchedulerHeroPeriod
                            + " schedulerPeriodMid=" + shadowSchedulerMidPeriod
                            + " schedulerPeriodDistant=" + shadowSchedulerDistantPeriod
                            + " directionalTexelSnapEnabled=" + shadowDirectionalTexelSnapEnabled
                            + " directionalTexelSnapScale=" + shadowDirectionalTexelSnapScale
                            + " shadowSchedulerFrameTick=" + shadowSchedulerFrameTick
                            + " renderedShadowLightIds=" + currentShadows.renderedShadowLightIdsCsv()
                            + " deferredShadowLightCount=" + currentShadows.deferredShadowLightCount()
                            + " deferredShadowLightIds=" + currentShadows.deferredShadowLightIdsCsv()
                            + " staleBypassShadowLightCount=" + currentShadows.staleBypassShadowLightCount()
                            + " shadowAllocatorAssignedLights=" + shadowAllocatorAssignedLights
                            + " shadowAllocatorReusedAssignments=" + shadowAllocatorReusedAssignments
                            + " shadowAllocatorEvictions=" + shadowAllocatorEvictions
                            + " filterPath=" + currentShadows.filterPath()
                            + " runtimeFilterPath=" + currentShadows.runtimeFilterPath()
                            + " shadowPcssSoftness=" + shadowPcssSoftness
                            + " shadowMomentBlend=" + shadowMomentBlend
                            + " shadowMomentBleedReduction=" + shadowMomentBleedReduction
                            + " shadowContactStrength=" + shadowContactStrength
                            + " shadowContactTemporalMotionScale=" + shadowContactTemporalMotionScale
                            + " shadowContactTemporalMinStability=" + shadowContactTemporalMinStability
                            + " momentFilterEstimateOnly=" + currentShadows.momentFilterEstimateOnly()
                            + " momentPipelineRequested=" + currentShadows.momentPipelineRequested()
                            + " momentPipelineActive=" + currentShadows.momentPipelineActive()
                            + " momentResourceAllocated=" + context.hasShadowMomentResources()
                            + " momentResourceFormat=" + context.shadowMomentFormatTag()
                            + " momentInitialized=" + context.isShadowMomentInitialized()
                            + " momentPhase=" + momentPhase
                            + " contactShadows=" + currentShadows.contactShadowsRequested()
                            + " rtMode=" + currentShadows.rtShadowMode()
                            + " rtActive=" + currentShadows.rtShadowActive()
                            + " rtTraversalSupported=" + shadowRtTraversalSupported
                            + " rtBvhSupported=" + shadowRtBvhSupported
                            + " rtBvhStrict=" + shadowRtBvhStrict
                            + " rtDenoiseStrength=" + shadowRtDenoiseStrength
                            + " rtRayLength=" + shadowRtRayLength
                            + " rtSampleCount=" + shadowRtSampleCount
                            + " rtDedicatedDenoiseStrength=" + shadowRtDedicatedDenoiseStrength
                            + " rtDedicatedRayLength=" + shadowRtDedicatedRayLength
                            + " rtDedicatedSampleCount=" + shadowRtDedicatedSampleCount
                            + " rtProductionDenoiseStrength=" + shadowRtProductionDenoiseStrength
                            + " rtProductionRayLength=" + shadowRtProductionRayLength
                            + " rtProductionSampleCount=" + shadowRtProductionSampleCount
                            + " rtEffectiveDenoiseStrength=" + effectiveShadowRtDenoiseStrength(currentShadows.rtShadowMode())
                            + " rtEffectiveRayLength=" + effectiveShadowRtRayLength(currentShadows.rtShadowMode())
                            + " rtEffectiveSampleCount=" + effectiveShadowRtSampleCount(currentShadows.rtShadowMode())
                            + " normalBiasScale=" + currentShadows.normalBiasScale()
                            + " slopeBiasScale=" + currentShadows.slopeBiasScale()
            ));
            if (currentShadows.renderedLocalShadowLights() < currentShadows.selectedLocalShadowLights()) {
                warnings.add(new EngineWarning(
                        "SHADOW_LOCAL_RENDER_BASELINE",
                        "Vulkan local-shadow render path is still primary-local baseline "
                                + "(requestedLocalShadows=" + currentShadows.selectedLocalShadowLights()
                                + ", renderedLocalShadows=" + currentShadows.renderedLocalShadowLights()
                                + ", atlas/cubemap multi-local rollout pending)"
                ));
            }
            if (currentShadows.momentFilterEstimateOnly()) {
                warnings.add(new EngineWarning(
                        "SHADOW_FILTER_MOMENT_ESTIMATE_ONLY",
                        "Shadow moment filter requested: " + currentShadows.filterPath()
                                + " (runtime active filter path=" + currentShadows.runtimeFilterPath()
                                + ", moment atlas sizing/telemetry is estimate-only)"
                ));
            }
            if (currentShadows.momentPipelineRequested() && !currentShadows.momentPipelineActive()) {
                if (context.hasShadowMomentResources() && !context.isShadowMomentInitialized()) {
                    warnings.add(new EngineWarning(
                            "SHADOW_MOMENT_PIPELINE_INITIALIZING",
                            "Shadow moment resources are allocated but awaiting first-use initialization "
                                    + "(requested=" + currentShadows.momentPipelineRequested()
                                    + ", active=" + currentShadows.momentPipelineActive() + ")"
                    ));
                } else {
                    warnings.add(new EngineWarning(
                            "SHADOW_MOMENT_PIPELINE_PENDING",
                            "Shadow moment pipeline requested but not yet active "
                                    + "(requested=" + currentShadows.momentPipelineRequested()
                                    + ", active=" + currentShadows.momentPipelineActive() + ")"
                    ));
                }
            } else if (currentShadows.momentPipelineActive()) {
                warnings.add(new EngineWarning(
                        "SHADOW_MOMENT_APPROX_ACTIVE",
                        "Shadow moment pipeline active with provisional "
                                + currentShadows.runtimeFilterPath()
                                + " approximation path (full production filter chain pending)"
                ));
            } else if (!currentShadows.filterPath().equals(currentShadows.runtimeFilterPath())) {
                warnings.add(new EngineWarning(
                        "SHADOW_FILTER_PATH_REQUESTED",
                        "Shadow filter path requested: " + currentShadows.filterPath()
                                + " (runtime active filter path=" + currentShadows.runtimeFilterPath() + ")"
                ));
            }
            if (!"off".equals(currentShadows.rtShadowMode())) {
                warnings.add(new EngineWarning(
                        "SHADOW_RT_PATH_REQUESTED",
                        "RT shadow mode requested: " + currentShadows.rtShadowMode()
                                + " (active=" + currentShadows.rtShadowActive()
                                + ", fallback stack in use"
                                + ", denoiseStrength=" + shadowRtDenoiseStrength
                                + ", rayLength=" + shadowRtRayLength
                                + ", sampleCount=" + shadowRtSampleCount
                                + ", effectiveDenoiseStrength=" + effectiveShadowRtDenoiseStrength(currentShadows.rtShadowMode())
                                + ", effectiveRayLength=" + effectiveShadowRtRayLength(currentShadows.rtShadowMode())
                                + ", effectiveSampleCount=" + effectiveShadowRtSampleCount(currentShadows.rtShadowMode())
                                + ")"
                ));
                if (!currentShadows.rtShadowActive()) {
                    warnings.add(new EngineWarning(
                            "SHADOW_RT_PATH_FALLBACK_ACTIVE",
                            "RT shadow traversal/denoise path unavailable; using non-RT shadow fallback stack"
                                    + ("bvh".equals(currentShadows.rtShadowMode())
                                    ? " (BVH mode requested but dedicated BVH traversal pipeline is not active)"
                                    : ("bvh_production".equals(currentShadows.rtShadowMode())
                                    ? " (Production BVH mode requested but hardware BVH traversal pipeline is not active)"
                                    : ("bvh_dedicated".equals(currentShadows.rtShadowMode())
                                    ? " (Dedicated BVH mode requested but dedicated BVH traversal pipeline is not active)"
                                    : ("rt_native".equals(currentShadows.rtShadowMode()) || "rt_native_denoised".equals(currentShadows.rtShadowMode())
                                    ? " (Native RT mode requested but hardware ray traversal support is not active)"
                                    : "")
                                    )
                                    )
                                    )
                    ));
                }
                if ("bvh".equals(currentShadows.rtShadowMode())
                        || "bvh_dedicated".equals(currentShadows.rtShadowMode())
                        || "bvh_production".equals(currentShadows.rtShadowMode())) {
                    String activePath = "bvh_dedicated".equals(currentShadows.rtShadowMode())
                            ? (currentShadows.rtShadowActive() ? "dedicated-preview traversal" : "fallback")
                            : ("bvh_production".equals(currentShadows.rtShadowMode())
                            ? (currentShadows.rtShadowActive() ? "production-preview traversal+denoise" : "fallback")
                            : "hybrid traversal");
                    warnings.add(new EngineWarning(
                            "SHADOW_RT_BVH_PIPELINE_PENDING",
                            "BVH RT shadow mode requested, but runtime is using the "
                                    + activePath
                                    + " path "
                                    + "(rtActive=" + currentShadows.rtShadowActive()
                                    + ", rtBvhSupported=" + shadowRtBvhSupported
                                    + "); dedicated BVH traversal/denoise pipeline remains pending"
                    ));
                }
                if ("rt_native".equals(currentShadows.rtShadowMode())
                        || "rt_native_denoised".equals(currentShadows.rtShadowMode())) {
                    warnings.add(new EngineWarning(
                            "SHADOW_RT_NATIVE_PATH_ACTIVE",
                            "Native RT shadow traversal path requested "
                                    + "(mode=" + currentShadows.rtShadowMode()
                                    + ", active=" + currentShadows.rtShadowActive()
                                    + ", traversalSupported=" + shadowRtTraversalSupported
                                    + ", denoiseMode=" + ("rt_native_denoised".equals(currentShadows.rtShadowMode()) ? "dedicated" : "standard")
                                    + ")"
                    ));
                }
            }
        }
        return warnings;
    }

    private static ReflectionOverrideSummary summarizeReflectionOverrides(List<Integer> modes) {
        int autoCount = 0;
        int probeOnlyCount = 0;
        int ssrOnlyCount = 0;
        int otherCount = 0;
        for (Integer rawMode : modes) {
            int mode = rawMode == null ? 0 : rawMode;
            switch (mode) {
                case 0 -> autoCount++;
                case 1 -> probeOnlyCount++;
                case 2 -> ssrOnlyCount++;
                default -> otherCount++;
            }
        }
        return new ReflectionOverrideSummary(autoCount, probeOnlyCount, ssrOnlyCount, otherCount);
    }

    private ReflectionProbeChurnDiagnostics updateReflectionProbeChurnDiagnostics(VulkanContext.ReflectionProbeDiagnostics diagnostics) {
        int active = Math.max(0, diagnostics.activeProbeCount());
        boolean warningTriggered = false;
        if (lastActiveReflectionProbeCount < 0) {
            lastActiveReflectionProbeCount = active;
            reflectionProbeLastDelta = 0;
            if (reflectionProbeChurnWarnCooldownRemaining > 0) {
                reflectionProbeChurnWarnCooldownRemaining--;
            }
            return snapshotReflectionProbeChurnDiagnostics(false);
        }
        int delta = Math.abs(active - lastActiveReflectionProbeCount);
        reflectionProbeLastDelta = delta;
        if (delta > 0) {
            reflectionProbeActiveChurnEvents++;
            reflectionProbeActiveDeltaAccum += delta;
        }
        if (delta >= reflectionProbeChurnWarnMinDelta) {
            reflectionProbeChurnHighStreak++;
            if (reflectionProbeChurnHighStreak >= reflectionProbeChurnWarnMinStreak
                    && reflectionProbeChurnWarnCooldownRemaining <= 0) {
                reflectionProbeChurnWarnCooldownRemaining = reflectionProbeChurnWarnCooldownFrames;
                warningTriggered = true;
            }
        } else {
            reflectionProbeChurnHighStreak = 0;
        }
        if (reflectionProbeChurnWarnCooldownRemaining > 0) {
            reflectionProbeChurnWarnCooldownRemaining--;
        }
        lastActiveReflectionProbeCount = active;
        return snapshotReflectionProbeChurnDiagnostics(warningTriggered);
    }

    private ReflectionProbeChurnDiagnostics snapshotReflectionProbeChurnDiagnostics(boolean warningTriggered) {
        double meanDelta = reflectionProbeActiveChurnEvents <= 0
                ? 0.0
                : (double) reflectionProbeActiveDeltaAccum / (double) reflectionProbeActiveChurnEvents;
        return new ReflectionProbeChurnDiagnostics(
                lastActiveReflectionProbeCount,
                reflectionProbeLastDelta,
                reflectionProbeActiveChurnEvents,
                meanDelta,
                reflectionProbeChurnHighStreak,
                reflectionProbeChurnWarnCooldownRemaining,
                warningTriggered
        );
    }

    private void resetReflectionProbeChurnDiagnostics() {
        lastActiveReflectionProbeCount = -1;
        reflectionProbeLastDelta = 0;
        reflectionProbeActiveChurnEvents = 0;
        reflectionProbeActiveDeltaAccum = 0L;
        reflectionProbeChurnHighStreak = 0;
        reflectionProbeChurnWarnCooldownRemaining = 0;
    }

    private ReflectionSsrTaaRiskDiagnostics updateReflectionSsrTaaRiskDiagnostics(
            double taaReject,
            double taaConfidence,
            long taaDrops
    ) {
        boolean instantRisk = taaReject > reflectionSsrTaaInstabilityRejectMin
                && taaConfidence < reflectionSsrTaaInstabilityConfidenceMax
                && taaDrops >= reflectionSsrTaaInstabilityDropEventsMin;
        if (reflectionSsrTaaEmaReject < 0.0 || reflectionSsrTaaEmaConfidence < 0.0) {
            reflectionSsrTaaEmaReject = taaReject;
            reflectionSsrTaaEmaConfidence = taaConfidence;
        } else {
            double alpha = Math.max(0.01, Math.min(1.0, reflectionSsrTaaRiskEmaAlpha));
            reflectionSsrTaaEmaReject = (taaReject * alpha) + (reflectionSsrTaaEmaReject * (1.0 - alpha));
            reflectionSsrTaaEmaConfidence = (taaConfidence * alpha) + (reflectionSsrTaaEmaConfidence * (1.0 - alpha));
        }
        boolean warningTriggered = false;
        if (instantRisk) {
            reflectionSsrTaaRiskHighStreak++;
            if (reflectionSsrTaaRiskHighStreak >= reflectionSsrTaaInstabilityWarnMinFrames
                    && reflectionSsrTaaRiskWarnCooldownRemaining <= 0) {
                reflectionSsrTaaRiskWarnCooldownRemaining = reflectionSsrTaaInstabilityWarnCooldownFrames;
                warningTriggered = true;
            }
        } else {
            reflectionSsrTaaRiskHighStreak = 0;
        }
        if (reflectionSsrTaaRiskWarnCooldownRemaining > 0) {
            reflectionSsrTaaRiskWarnCooldownRemaining--;
        }
        return new ReflectionSsrTaaRiskDiagnostics(
                instantRisk,
                reflectionSsrTaaRiskHighStreak,
                reflectionSsrTaaRiskWarnCooldownRemaining,
                reflectionSsrTaaEmaReject,
                reflectionSsrTaaEmaConfidence,
                warningTriggered
        );
    }

    private void resetReflectionSsrTaaRiskDiagnostics() {
        reflectionSsrTaaRiskHighStreak = 0;
        reflectionSsrTaaRiskWarnCooldownRemaining = 0;
        reflectionSsrTaaEmaReject = -1.0;
        reflectionSsrTaaEmaConfidence = -1.0;
    }

    private void applyAdaptiveReflectionPostParameters() {
        float baseTemporalWeight = currentPost.reflectionsTemporalWeight();
        float baseSsrStrength = currentPost.reflectionsSsrStrength();
        float baseSsrStepScale = currentPost.reflectionsSsrStepScale();
        reflectionAdaptiveTemporalWeightActive = baseTemporalWeight;
        reflectionAdaptiveSsrStrengthActive = baseSsrStrength;
        reflectionAdaptiveSsrStepScaleActive = baseSsrStepScale;

        if (reflectionSsrTaaAdaptiveEnabled
                && currentPost.reflectionsEnabled()
                && currentPost.taaEnabled()
                && isReflectionSsrPathActive(currentPost.reflectionsMode())) {
            double severity = computeReflectionAdaptiveSeverity();
            reflectionAdaptiveTemporalWeightActive = clamp(
                    (float) (baseTemporalWeight + severity * reflectionSsrTaaAdaptiveTemporalBoostMax),
                    0.0f,
                    0.98f
            );
            double strengthScale = 1.0 - severity * (1.0 - reflectionSsrTaaAdaptiveSsrStrengthScaleMin);
            reflectionAdaptiveSsrStrengthActive = clamp(
                    (float) (baseSsrStrength * strengthScale),
                    0.0f,
                    1.0f
            );
            reflectionAdaptiveSsrStepScaleActive = clamp(
                    (float) (baseSsrStepScale * (1.0 + severity * reflectionSsrTaaAdaptiveStepScaleBoostMax)),
                    0.5f,
                    3.0f
            );
        }

        context.setPostProcessParameters(
                currentPost.tonemapEnabled(),
                currentPost.exposure(),
                currentPost.gamma(),
                currentPost.bloomEnabled(),
                currentPost.bloomThreshold(),
                currentPost.bloomStrength(),
                currentPost.ssaoEnabled(),
                currentPost.ssaoStrength(),
                currentPost.ssaoRadius(),
                currentPost.ssaoBias(),
                currentPost.ssaoPower(),
                currentPost.smaaEnabled(),
                currentPost.smaaStrength(),
                currentPost.taaEnabled(),
                currentPost.taaBlend(),
                currentPost.taaClipScale(),
                currentPost.taaLumaClipEnabled(),
                currentPost.taaSharpenStrength(),
                currentPost.taaRenderScale(),
                currentPost.reflectionsEnabled(),
                currentPost.reflectionsMode(),
                reflectionAdaptiveSsrStrengthActive,
                currentPost.reflectionsSsrMaxRoughness(),
                reflectionAdaptiveSsrStepScaleActive,
                reflectionAdaptiveTemporalWeightActive,
                currentPost.reflectionsPlanarStrength()
        );
    }

    private double computeReflectionAdaptiveSeverity() {
        if (reflectionSsrTaaEmaReject < 0.0 || reflectionSsrTaaEmaConfidence < 0.0) {
            return 0.0;
        }
        double rejectRange = Math.max(1e-6, 1.0 - reflectionSsrTaaInstabilityRejectMin);
        double rejectSeverity = Math.max(0.0, Math.min(1.0,
                (reflectionSsrTaaEmaReject - reflectionSsrTaaInstabilityRejectMin) / rejectRange));
        double confidenceRange = Math.max(1e-6, reflectionSsrTaaInstabilityConfidenceMax);
        double confidenceSeverity = Math.max(0.0, Math.min(1.0,
                (reflectionSsrTaaInstabilityConfidenceMax - reflectionSsrTaaEmaConfidence) / confidenceRange));
        double streakDenominator = Math.max(1, reflectionSsrTaaInstabilityWarnMinFrames);
        double streakSeverity = Math.max(0.0, Math.min(1.0, reflectionSsrTaaRiskHighStreak / streakDenominator));
        return Math.max(streakSeverity, Math.max(rejectSeverity, confidenceSeverity));
    }

    private static boolean isReflectionSsrPathActive(int reflectionsMode) {
        int baseMode = reflectionsMode & 0x7;
        return baseMode == 1 || baseMode == 3 || baseMode == 4;
    }

    private void resetReflectionAdaptiveState() {
        reflectionAdaptiveTemporalWeightActive = currentPost == null ? 0.80f : currentPost.reflectionsTemporalWeight();
        reflectionAdaptiveSsrStrengthActive = currentPost == null ? 0.6f : currentPost.reflectionsSsrStrength();
        reflectionAdaptiveSsrStepScaleActive = currentPost == null ? 1.0f : currentPost.reflectionsSsrStepScale();
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

    List<Integer> debugReflectionOverrideModes() {
        return context.debugGpuMeshReflectionOverrideModes();
    }

    ReflectionProbeDiagnostics debugReflectionProbeDiagnostics() {
        VulkanContext.ReflectionProbeDiagnostics diagnostics = context.debugReflectionProbeDiagnostics();
        return new ReflectionProbeDiagnostics(
                diagnostics.configuredProbeCount(),
                diagnostics.activeProbeCount(),
                diagnostics.slotCount(),
                diagnostics.metadataCapacity()
        );
    }

    ReflectionProbeChurnDiagnostics debugReflectionProbeChurnDiagnostics() {
        return snapshotReflectionProbeChurnDiagnostics(false);
    }

    ReflectionSsrTaaRiskDiagnostics debugReflectionSsrTaaRiskDiagnostics() {
        return new ReflectionSsrTaaRiskDiagnostics(
                false,
                reflectionSsrTaaRiskHighStreak,
                reflectionSsrTaaRiskWarnCooldownRemaining,
                reflectionSsrTaaEmaReject < 0.0 ? 0.0 : reflectionSsrTaaEmaReject,
                reflectionSsrTaaEmaConfidence < 0.0 ? 1.0 : reflectionSsrTaaEmaConfidence,
                false
        );
    }

    ReflectionAdaptivePolicyDiagnostics debugReflectionAdaptivePolicyDiagnostics() {
        return new ReflectionAdaptivePolicyDiagnostics(
                reflectionSsrTaaAdaptiveEnabled,
                currentPost.reflectionsTemporalWeight(),
                reflectionAdaptiveTemporalWeightActive,
                currentPost.reflectionsSsrStrength(),
                reflectionAdaptiveSsrStrengthActive,
                currentPost.reflectionsSsrStepScale(),
                reflectionAdaptiveSsrStepScaleActive,
                reflectionSsrTaaAdaptiveTemporalBoostMax,
                reflectionSsrTaaAdaptiveSsrStrengthScaleMin,
                reflectionSsrTaaAdaptiveStepScaleBoostMax
        );
    }

    static record MeshGeometryCacheProfile(long hits, long misses, long evictions, int entries, int maxEntries) {
    }

    record ReflectionProbeDiagnostics(
            int configuredProbeCount,
            int activeProbeCount,
            int slotCount,
            int metadataCapacity
    ) {
    }

    record ReflectionProbeChurnDiagnostics(
            int lastActiveCount,
            int lastDelta,
            int churnEvents,
            double meanDelta,
            int highStreak,
            int warnCooldownRemaining,
            boolean warningTriggered
    ) {
    }

    record ReflectionSsrTaaRiskDiagnostics(
            boolean instantRisk,
            int highStreak,
            int warnCooldownRemaining,
            double emaReject,
            double emaConfidence,
            boolean warningTriggered
    ) {
    }

    record ReflectionAdaptivePolicyDiagnostics(
            boolean enabled,
            float baseTemporalWeight,
            float activeTemporalWeight,
            float baseSsrStrength,
            float activeSsrStrength,
            float baseSsrStepScale,
            float activeSsrStepScale,
            double temporalBoostMax,
            double ssrStrengthScaleMin,
            double stepScaleBoostMax
    ) {
    }

    private record ReflectionOverrideSummary(int autoCount, int probeOnlyCount, int ssrOnlyCount, int otherCount) {
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

    private void applyReflectionProfileTelemetryDefaults(Map<String, String> backendOptions) {
        if (reflectionProfile == ReflectionProfile.BALANCED) {
            return;
        }
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        switch (reflectionProfile) {
            case PERFORMANCE -> {
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinDelta")) reflectionProbeChurnWarnMinDelta = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinStreak")) reflectionProbeChurnWarnMinStreak = 4;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnCooldownFrames")) reflectionProbeChurnWarnCooldownFrames = 180;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityRejectMin")) reflectionSsrTaaInstabilityRejectMin = 0.45;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityConfidenceMax")) reflectionSsrTaaInstabilityConfidenceMax = 0.60;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityDropEventsMin")) reflectionSsrTaaInstabilityDropEventsMin = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityWarnMinFrames")) reflectionSsrTaaInstabilityWarnMinFrames = 4;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityWarnCooldownFrames")) reflectionSsrTaaInstabilityWarnCooldownFrames = 240;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaRiskEmaAlpha")) reflectionSsrTaaRiskEmaAlpha = 0.20;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveEnabled")) reflectionSsrTaaAdaptiveEnabled = false;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTemporalBoostMax")) reflectionSsrTaaAdaptiveTemporalBoostMax = 0.08;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveSsrStrengthScaleMin")) reflectionSsrTaaAdaptiveSsrStrengthScaleMin = 0.80;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveStepScaleBoostMax")) reflectionSsrTaaAdaptiveStepScaleBoostMax = 0.10;
            }
            case QUALITY -> {
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinDelta")) reflectionProbeChurnWarnMinDelta = 1;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinStreak")) reflectionProbeChurnWarnMinStreak = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnCooldownFrames")) reflectionProbeChurnWarnCooldownFrames = 90;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityRejectMin")) reflectionSsrTaaInstabilityRejectMin = 0.32;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityConfidenceMax")) reflectionSsrTaaInstabilityConfidenceMax = 0.74;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityDropEventsMin")) reflectionSsrTaaInstabilityDropEventsMin = 0;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityWarnMinFrames")) reflectionSsrTaaInstabilityWarnMinFrames = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityWarnCooldownFrames")) reflectionSsrTaaInstabilityWarnCooldownFrames = 90;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaRiskEmaAlpha")) reflectionSsrTaaRiskEmaAlpha = 0.30;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveEnabled")) reflectionSsrTaaAdaptiveEnabled = false;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTemporalBoostMax")) reflectionSsrTaaAdaptiveTemporalBoostMax = 0.12;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveSsrStrengthScaleMin")) reflectionSsrTaaAdaptiveSsrStrengthScaleMin = 0.70;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveStepScaleBoostMax")) reflectionSsrTaaAdaptiveStepScaleBoostMax = 0.15;
            }
            case STABILITY -> {
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinDelta")) reflectionProbeChurnWarnMinDelta = 1;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinStreak")) reflectionProbeChurnWarnMinStreak = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnCooldownFrames")) reflectionProbeChurnWarnCooldownFrames = 60;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityRejectMin")) reflectionSsrTaaInstabilityRejectMin = 0.28;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityConfidenceMax")) reflectionSsrTaaInstabilityConfidenceMax = 0.78;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityDropEventsMin")) reflectionSsrTaaInstabilityDropEventsMin = 0;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityWarnMinFrames")) reflectionSsrTaaInstabilityWarnMinFrames = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaInstabilityWarnCooldownFrames")) reflectionSsrTaaInstabilityWarnCooldownFrames = 60;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaRiskEmaAlpha")) reflectionSsrTaaRiskEmaAlpha = 0.45;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveEnabled")) reflectionSsrTaaAdaptiveEnabled = true;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTemporalBoostMax")) reflectionSsrTaaAdaptiveTemporalBoostMax = 0.18;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveSsrStrengthScaleMin")) reflectionSsrTaaAdaptiveSsrStrengthScaleMin = 0.60;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveStepScaleBoostMax")) reflectionSsrTaaAdaptiveStepScaleBoostMax = 0.25;
            }
            default -> {
            }
        }
    }

    private static boolean hasBackendOption(Map<String, String> backendOptions, String key) {
        if (backendOptions == null || key == null || key.isBlank()) {
            return false;
        }
        String value = backendOptions.get(key);
        return value != null && !value.isBlank();
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
            float normalBiasScale,
            float slopeBiasScale,
            int pcfRadius,
            int cascadeCount,
            int mapResolution,
            int maxShadowedLocalLights,
            int selectedLocalShadowLights,
            String primaryShadowType,
            String primaryShadowLightId,
            int atlasCapacityTiles,
            int atlasAllocatedTiles,
            float atlasUtilization,
            int atlasEvictions,
            long atlasMemoryBytesD16,
            long atlasMemoryBytesD32,
            long shadowUpdateBytesEstimate,
            long shadowMomentAtlasBytesEstimate,
            int renderedLocalShadowLights,
            int renderedSpotShadowLights,
            int renderedPointShadowCubemaps,
            String renderedShadowLightIdsCsv,
            int deferredShadowLightCount,
            String deferredShadowLightIdsCsv,
            int staleBypassShadowLightCount,
            String filterPath,
            String runtimeFilterPath,
            boolean momentFilterEstimateOnly,
            boolean momentPipelineRequested,
            boolean momentPipelineActive,
            boolean contactShadowsRequested,
            String rtShadowMode,
            boolean rtShadowActive,
            boolean degraded
    ) {
    }

    static record CameraMatrices(float[] view, float[] proj) {
    }

    static record LightingConfig(
            float[] directionalDirection,
            float[] directionalColor,
            float directionalIntensity,
            float[] shadowPointPosition,
            float[] shadowPointDirection,
            boolean shadowPointIsSpot,
            float shadowPointOuterCos,
            float shadowPointRange,
            boolean shadowPointCastsShadows,
            int localLightCount,
            float[] localLightPosRange,
            float[] localLightColorIntensity,
            float[] localLightDirInner,
            float[] localLightOuterTypeShadow,
            Map<String, Integer> shadowLayerAssignments,
            int shadowAllocatorAssignedLights,
            int shadowAllocatorReusedAssignments,
            int shadowAllocatorEvictions
    ) {
    }

    private ShadowRenderConfig withRuntimeMomentPipelineState(ShadowRenderConfig base) {
        if (base == null || !base.momentPipelineRequested()) {
            return base;
        }
        boolean resourcesActive = context.isShadowMomentPipelineActive();
        boolean initialized = context.isShadowMomentInitialized();
        boolean active = resourcesActive && initialized;
        String runtimeFilterPath = active ? base.filterPath() : base.runtimeFilterPath();
        boolean momentFilterEstimateOnly = base.momentFilterEstimateOnly() && !active;
        if (base.momentPipelineActive() == active
                && base.runtimeFilterPath().equals(runtimeFilterPath)
                && base.momentFilterEstimateOnly() == momentFilterEstimateOnly) {
            return base;
        }
        return new ShadowRenderConfig(
                base.enabled(),
                base.strength(),
                base.bias(),
                base.normalBiasScale(),
                base.slopeBiasScale(),
                base.pcfRadius(),
                base.cascadeCount(),
                base.mapResolution(),
                base.maxShadowedLocalLights(),
                base.selectedLocalShadowLights(),
                base.primaryShadowType(),
                base.primaryShadowLightId(),
                base.atlasCapacityTiles(),
                base.atlasAllocatedTiles(),
                base.atlasUtilization(),
                base.atlasEvictions(),
                base.atlasMemoryBytesD16(),
                base.atlasMemoryBytesD32(),
                base.shadowUpdateBytesEstimate(),
                base.shadowMomentAtlasBytesEstimate(),
                base.renderedLocalShadowLights(),
                base.renderedSpotShadowLights(),
                base.renderedPointShadowCubemaps(),
                base.renderedShadowLightIdsCsv(),
                base.deferredShadowLightCount(),
                base.deferredShadowLightIdsCsv(),
                base.staleBypassShadowLightCount(),
                base.filterPath(),
                runtimeFilterPath,
                momentFilterEstimateOnly,
                base.momentPipelineRequested(),
                active,
                base.contactShadowsRequested(),
                base.rtShadowMode(),
                base.rtShadowActive(),
                base.degraded()
        );
    }

    private float effectiveShadowRtDenoiseStrength(String rtMode) {
        if ("bvh_production".equals(rtMode) && shadowRtProductionDenoiseStrength >= 0.0f) {
            return Math.max(0.0f, Math.min(1.0f, shadowRtProductionDenoiseStrength));
        }
        if (("bvh_dedicated".equals(rtMode) || "rt_native_denoised".equals(rtMode)) && shadowRtDedicatedDenoiseStrength >= 0.0f) {
            return Math.max(0.0f, Math.min(1.0f, shadowRtDedicatedDenoiseStrength));
        }
        return shadowRtDenoiseStrength;
    }

    private float effectiveShadowRtRayLength(String rtMode) {
        if ("bvh_production".equals(rtMode) && shadowRtProductionRayLength >= 0.0f) {
            return Math.max(1.0f, Math.min(500.0f, shadowRtProductionRayLength));
        }
        if (("bvh_dedicated".equals(rtMode) || "rt_native_denoised".equals(rtMode)) && shadowRtDedicatedRayLength >= 0.0f) {
            return Math.max(1.0f, Math.min(500.0f, shadowRtDedicatedRayLength));
        }
        return shadowRtRayLength;
    }

    private int effectiveShadowRtSampleCount(String rtMode) {
        if ("bvh_production".equals(rtMode) && shadowRtProductionSampleCount > 0) {
            return Math.max(1, Math.min(16, shadowRtProductionSampleCount));
        }
        if (("bvh_dedicated".equals(rtMode) || "rt_native_denoised".equals(rtMode)) && shadowRtDedicatedSampleCount > 0) {
            return Math.max(1, Math.min(16, shadowRtDedicatedSampleCount));
        }
        return shadowRtSampleCount;
    }

    private void updateShadowSchedulerTicks(String renderedShadowLightIdsCsv) {
        if (renderedShadowLightIdsCsv == null || renderedShadowLightIdsCsv.isBlank()) {
            return;
        }
        String[] ids = renderedShadowLightIdsCsv.split(",");
        for (String id : ids) {
            String normalized = id == null ? "" : id.trim();
            if (!normalized.isEmpty()) {
                shadowSchedulerLastRenderedTicks.put(normalized, shadowSchedulerFrameTick);
            }
        }
    }

}
