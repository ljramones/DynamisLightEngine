package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.event.ReflectionAdaptiveTelemetryEvent;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.AntiAliasingDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.ReflectionProbeDesc;
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
    private static final int REFLECTION_MODE_BASE_MASK = 0x7;
    private static final int REFLECTION_MODE_HIZ_BIT = 1 << 3;
    private static final int REFLECTION_MODE_DENOISE_SHIFT = 4;
    private static final int REFLECTION_MODE_DENOISE_MASK = 0x7 << REFLECTION_MODE_DENOISE_SHIFT;
    private static final int REFLECTION_MODE_PLANAR_CLIP_BIT = 1 << 7;
    private static final int REFLECTION_MODE_PROBE_VOLUME_BIT = 1 << 8;
    private static final int REFLECTION_MODE_PROBE_BOX_BIT = 1 << 9;
    private static final int REFLECTION_MODE_RT_REQUEST_BIT = 1 << 10;
    private static final int REFLECTION_MODE_REPROJECTION_REFLECTION_SPACE_BIT = 1 << 11;
    private static final int REFLECTION_MODE_HISTORY_STRICT_REJECT_BIT = 1 << 12;
    private static final int REFLECTION_MODE_DISOCCLUSION_REJECT_BIT = 1 << 13;
    private static final int REFLECTION_MODE_PLANAR_SELECTIVE_EXEC_BIT = 1 << 14;
    private static final int REFLECTION_MODE_RT_ACTIVE_BIT = 1 << 15;
    private static final int REFLECTION_MODE_TRANSPARENCY_INTEGRATION_BIT = 1 << 16;
    private static final int REFLECTION_MODE_RT_MULTI_BOUNCE_BIT = 1 << 17;
    private static final int REFLECTION_MODE_PLANAR_CAPTURE_EXEC_BIT = 1 << 18;
    private static final int REFLECTION_MODE_RT_DEDICATED_DENOISE_BIT = 1 << 19;
    private static final int REFLECTION_MODE_PLANAR_GEOMETRY_CAPTURE_BIT = 1 << 20;
    private static final int REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_AUTO_BIT = 1 << 21;
    private static final int REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_PROBE_ONLY_BIT = 1 << 22;
    private static final int REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_SSR_ONLY_BIT = 1 << 23;
    private static final int REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_OTHER_BIT = 1 << 24;
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
    private PostProcessRenderConfig currentPost = new PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.8f, false, 0f, 1.0f, 0.02f, 1.0f, false, 0f, false, 0f, 1.0f, false, 0.16f, 1.0f, false, 0, 0.6f, 0.78f, 1.0f, 0.80f, 0.35f, 0.0f);
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
    private int reflectionProbeQualityOverlapWarnMaxPairs = 8;
    private int reflectionProbeQualityBleedRiskWarnMaxPairs = 0;
    private int reflectionProbeQualityMinOverlapPairsWhenMultiple = 1;
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
    private double reflectionAdaptiveSeverityInstant;
    private double reflectionAdaptiveSeverityPeak;
    private double reflectionAdaptiveSeverityAccum;
    private double reflectionAdaptiveTemporalDeltaAccum;
    private double reflectionAdaptiveSsrStrengthDeltaAccum;
    private double reflectionAdaptiveSsrStepScaleDeltaAccum;
    private long reflectionAdaptiveTelemetrySamples;
    private int reflectionSsrTaaAdaptiveTrendWindowFrames = 120;
    private double reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = 0.40;
    private int reflectionSsrTaaAdaptiveTrendWarnMinFrames = 3;
    private int reflectionSsrTaaAdaptiveTrendWarnCooldownFrames = 120;
    private int reflectionSsrTaaAdaptiveTrendWarnMinSamples = 24;
    private double reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = 0.50;
    private double reflectionSsrTaaAdaptiveTrendSloHighRatioMax = 0.40;
    private int reflectionSsrTaaAdaptiveTrendSloMinSamples = 24;
    private double reflectionSsrTaaHistoryRejectSeverityMin = 0.75;
    private double reflectionSsrTaaHistoryConfidenceDecaySeverityMin = 0.45;
    private int reflectionSsrTaaHistoryRejectRiskStreakMin = 2;
    private String reflectionSsrTaaHistoryPolicyActive = "inactive";
    private double reflectionSsrTaaHistoryRejectBiasActive;
    private double reflectionSsrTaaHistoryConfidenceDecayActive;
    private String reflectionSsrTaaReprojectionPolicyActive = "surface_motion_vectors";
    private long reflectionSsrTaaDisocclusionRejectDropEventsMin = 2L;
    private double reflectionSsrTaaDisocclusionRejectConfidenceMax = 0.60;
    private double reflectionSsrEnvelopeRejectWarnMax = 0.45;
    private double reflectionSsrEnvelopeConfidenceWarnMin = 0.55;
    private long reflectionSsrEnvelopeDropWarnMin = 2L;
    private int reflectionSsrEnvelopeWarnMinFrames = 3;
    private int reflectionSsrEnvelopeWarnCooldownFrames = 120;
    private int reflectionSsrEnvelopeHighStreak;
    private int reflectionSsrEnvelopeWarnCooldownRemaining;
    private boolean reflectionSsrEnvelopeBreachedLastFrame;
    private double reflectionPlanarEnvelopePlaneDeltaWarnMax = 0.35;
    private double reflectionPlanarEnvelopeCoverageRatioWarnMin = 0.25;
    private int reflectionPlanarEnvelopeWarnMinFrames = 3;
    private int reflectionPlanarEnvelopeWarnCooldownFrames = 120;
    private int reflectionPlanarEnvelopeHighStreak;
    private int reflectionPlanarEnvelopeWarnCooldownRemaining;
    private boolean reflectionPlanarEnvelopeBreachedLastFrame;
    private double reflectionPlanarPrevPlaneHeight = Double.NaN;
    private double reflectionPlanarLatestPlaneDelta;
    private double reflectionPlanarLatestCoverageRatio = 1.0;
    private boolean reflectionPlanarScopeIncludeAuto = true;
    private boolean reflectionPlanarScopeIncludeProbeOnly;
    private boolean reflectionPlanarScopeIncludeSsrOnly;
    private boolean reflectionPlanarScopeIncludeOther = true;
    private double reflectionPlanarPerfMaxGpuMsLow = 1.4;
    private double reflectionPlanarPerfMaxGpuMsMedium = 2.2;
    private double reflectionPlanarPerfMaxGpuMsHigh = 3.0;
    private double reflectionPlanarPerfMaxGpuMsUltra = 4.2;
    private double reflectionPlanarPerfDrawInflationWarnMax = 2.0;
    private double reflectionPlanarPerfMemoryBudgetMb = 32.0;
    private int reflectionPlanarPerfWarnMinFrames = 3;
    private int reflectionPlanarPerfWarnCooldownFrames = 120;
    private boolean reflectionPlanarPerfRequireGpuTimestamp;
    private int reflectionPlanarPerfHighStreak;
    private int reflectionPlanarPerfWarnCooldownRemaining;
    private boolean reflectionPlanarPerfBreachedLastFrame;
    private double reflectionPlanarPerfLastGpuMsEstimate;
    private double reflectionPlanarPerfLastGpuMsCap;
    private double reflectionPlanarPerfLastDrawInflation = 1.0;
    private long reflectionPlanarPerfLastMemoryBytes;
    private long reflectionPlanarPerfLastMemoryBudgetBytes;
    private String reflectionPlanarPerfLastTimingSource = "frame_estimate";
    private boolean reflectionPlanarPerfLastTimestampAvailable;
    private boolean reflectionPlanarPerfLastTimestampRequirementUnmet;
    private double lastFrameGpuMs;
    private long lastFrameDrawCalls = 1L;
    private double reflectionSsrTaaLatestRejectRate;
    private double reflectionSsrTaaLatestConfidenceMean = 1.0;
    private long reflectionSsrTaaLatestDropEvents;
    private boolean reflectionRtSingleBounceEnabled = true;
    private boolean reflectionRtMultiBounceEnabled;
    private boolean reflectionRtDedicatedDenoisePipelineEnabled = true;
    private double reflectionRtDenoiseStrength = 0.65;
    private boolean reflectionRtLaneRequested;
    private boolean reflectionRtLaneActive;
    private String reflectionRtFallbackChainActive = "probe";
    private String reflectionPlanarPassOrderContractStatus = "inactive";
    private int reflectionPlanarScopedMeshEligibleCount;
    private int reflectionPlanarScopedMeshExcludedCount;
    private boolean reflectionPlanarMirrorCameraActive;
    private boolean reflectionPlanarDedicatedCaptureLaneActive;
    private int reflectionTransparentCandidateCount;
    private String reflectionTransparencyStageGateStatus = "not_required";
    private String reflectionTransparencyFallbackPath = "none";
    private int reflectionProbeUpdateCadenceFrames = 1;
    private int reflectionProbeMaxVisible = 64;
    private double reflectionProbeLodDepthScale = 1.0;
    private ReflectionProbeQualityDiagnostics reflectionProbeQualityDiagnostics = ReflectionProbeQualityDiagnostics.zero();
    private List<ReflectionProbeDesc> currentReflectionProbes = List.of();
    private List<MaterialDesc> currentSceneMaterials = List.of();
    private int reflectionSsrTaaAdaptiveTrendWarnHighStreak;
    private int reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining;
    private boolean reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame;
    private final Deque<ReflectionAdaptiveWindowSample> reflectionAdaptiveTrendSamples = new ArrayDeque<>();

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
        reflectionProbeQualityOverlapWarnMaxPairs = options.reflectionProbeQualityOverlapWarnMaxPairs();
        reflectionProbeQualityBleedRiskWarnMaxPairs = options.reflectionProbeQualityBleedRiskWarnMaxPairs();
        reflectionProbeQualityMinOverlapPairsWhenMultiple = options.reflectionProbeQualityMinOverlapPairsWhenMultiple();
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
        reflectionSsrTaaAdaptiveTrendWindowFrames = options.reflectionSsrTaaAdaptiveTrendWindowFrames();
        reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = options.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin();
        reflectionSsrTaaAdaptiveTrendWarnMinFrames = options.reflectionSsrTaaAdaptiveTrendWarnMinFrames();
        reflectionSsrTaaAdaptiveTrendWarnCooldownFrames = options.reflectionSsrTaaAdaptiveTrendWarnCooldownFrames();
        reflectionSsrTaaAdaptiveTrendWarnMinSamples = options.reflectionSsrTaaAdaptiveTrendWarnMinSamples();
        reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = options.reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax();
        reflectionSsrTaaAdaptiveTrendSloHighRatioMax = options.reflectionSsrTaaAdaptiveTrendSloHighRatioMax();
        reflectionSsrTaaAdaptiveTrendSloMinSamples = options.reflectionSsrTaaAdaptiveTrendSloMinSamples();
        reflectionSsrTaaHistoryRejectSeverityMin = options.reflectionSsrTaaHistoryRejectSeverityMin();
        reflectionSsrTaaHistoryConfidenceDecaySeverityMin = options.reflectionSsrTaaHistoryConfidenceDecaySeverityMin();
        reflectionSsrTaaHistoryRejectRiskStreakMin = options.reflectionSsrTaaHistoryRejectRiskStreakMin();
        reflectionSsrTaaDisocclusionRejectDropEventsMin = options.reflectionSsrTaaDisocclusionRejectDropEventsMin();
        reflectionSsrTaaDisocclusionRejectConfidenceMax = options.reflectionSsrTaaDisocclusionRejectConfidenceMax();
        reflectionSsrEnvelopeRejectWarnMax = options.reflectionSsrEnvelopeRejectWarnMax();
        reflectionSsrEnvelopeConfidenceWarnMin = options.reflectionSsrEnvelopeConfidenceWarnMin();
        reflectionSsrEnvelopeDropWarnMin = options.reflectionSsrEnvelopeDropWarnMin();
        reflectionSsrEnvelopeWarnMinFrames = options.reflectionSsrEnvelopeWarnMinFrames();
        reflectionSsrEnvelopeWarnCooldownFrames = options.reflectionSsrEnvelopeWarnCooldownFrames();
        reflectionPlanarEnvelopePlaneDeltaWarnMax = options.reflectionPlanarEnvelopePlaneDeltaWarnMax();
        reflectionPlanarEnvelopeCoverageRatioWarnMin = options.reflectionPlanarEnvelopeCoverageRatioWarnMin();
        reflectionPlanarEnvelopeWarnMinFrames = options.reflectionPlanarEnvelopeWarnMinFrames();
        reflectionPlanarEnvelopeWarnCooldownFrames = options.reflectionPlanarEnvelopeWarnCooldownFrames();
        reflectionPlanarScopeIncludeAuto = options.reflectionPlanarScopeIncludeAuto();
        reflectionPlanarScopeIncludeProbeOnly = options.reflectionPlanarScopeIncludeProbeOnly();
        reflectionPlanarScopeIncludeSsrOnly = options.reflectionPlanarScopeIncludeSsrOnly();
        reflectionPlanarScopeIncludeOther = options.reflectionPlanarScopeIncludeOther();
        reflectionPlanarPerfMaxGpuMsLow = options.reflectionPlanarPerfMaxGpuMsLow();
        reflectionPlanarPerfMaxGpuMsMedium = options.reflectionPlanarPerfMaxGpuMsMedium();
        reflectionPlanarPerfMaxGpuMsHigh = options.reflectionPlanarPerfMaxGpuMsHigh();
        reflectionPlanarPerfMaxGpuMsUltra = options.reflectionPlanarPerfMaxGpuMsUltra();
        reflectionPlanarPerfDrawInflationWarnMax = options.reflectionPlanarPerfDrawInflationWarnMax();
        reflectionPlanarPerfMemoryBudgetMb = options.reflectionPlanarPerfMemoryBudgetMb();
        reflectionPlanarPerfWarnMinFrames = options.reflectionPlanarPerfWarnMinFrames();
        reflectionPlanarPerfWarnCooldownFrames = options.reflectionPlanarPerfWarnCooldownFrames();
        reflectionPlanarPerfRequireGpuTimestamp = options.reflectionPlanarPerfRequireGpuTimestamp();
        reflectionRtSingleBounceEnabled = options.reflectionRtSingleBounceEnabled();
        reflectionRtMultiBounceEnabled = options.reflectionRtMultiBounceEnabled();
        reflectionRtDedicatedDenoisePipelineEnabled = options.reflectionRtDedicatedDenoisePipelineEnabled();
        reflectionRtDenoiseStrength = options.reflectionRtDenoiseStrength();
        reflectionProbeUpdateCadenceFrames = options.reflectionProbeUpdateCadenceFrames();
        reflectionProbeMaxVisible = options.reflectionProbeMaxVisible();
        reflectionProbeLodDepthScale = options.reflectionProbeLodDepthScale();
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
        resetReflectionAdaptiveTelemetryMetrics();
        reflectionSsrEnvelopeHighStreak = 0;
        reflectionSsrEnvelopeWarnCooldownRemaining = 0;
        reflectionSsrEnvelopeBreachedLastFrame = false;
        reflectionPlanarEnvelopeHighStreak = 0;
        reflectionPlanarEnvelopeWarnCooldownRemaining = 0;
        reflectionPlanarEnvelopeBreachedLastFrame = false;
        reflectionPlanarPrevPlaneHeight = Double.NaN;
        reflectionPlanarLatestPlaneDelta = 0.0;
        reflectionPlanarLatestCoverageRatio = 1.0;
        reflectionPlanarPerfHighStreak = 0;
        reflectionPlanarPerfWarnCooldownRemaining = 0;
        reflectionPlanarPerfBreachedLastFrame = false;
        reflectionPlanarPerfLastGpuMsEstimate = 0.0;
        reflectionPlanarPerfLastGpuMsCap = 0.0;
        reflectionPlanarPerfLastDrawInflation = 1.0;
        reflectionPlanarPerfLastMemoryBytes = 0L;
        reflectionPlanarPerfLastMemoryBudgetBytes = 0L;
        reflectionPlanarPerfLastTimingSource = "frame_estimate";
        reflectionPlanarPerfLastTimestampAvailable = false;
        reflectionPlanarPerfLastTimestampRequirementUnmet = false;
        context.configureReflectionProbeStreaming(
                reflectionProbeUpdateCadenceFrames,
                reflectionProbeMaxVisible,
                (float) reflectionProbeLodDepthScale
        );
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
        resetReflectionAdaptiveTelemetryMetrics();
        currentIbl = sceneState.ibl();
        currentReflectionProbes = sceneState.reflectionProbes() == null ? List.of() : List.copyOf(sceneState.reflectionProbes());
        currentSceneMaterials = scene == null || scene.materials() == null ? List.of() : List.copyOf(scene.materials());
        reflectionProbeQualityDiagnostics = analyzeReflectionProbeQuality(currentReflectionProbes);
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
        lastFrameGpuMs = frame.gpuMs();
        lastFrameDrawCalls = frame.drawCalls();
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
    protected EngineEvent additionalTelemetryEvent(long frameIndex) {
        if (!currentPost.reflectionsEnabled()) {
            return null;
        }
        return new ReflectionAdaptiveTelemetryEvent(
                frameIndex,
                reflectionSsrTaaAdaptiveEnabled,
                reflectionAdaptiveSeverityInstant,
                reflectionAdaptiveTelemetrySamples <= 0L
                        ? 0.0
                        : reflectionAdaptiveSeverityAccum / (double) reflectionAdaptiveTelemetrySamples,
                reflectionAdaptiveSeverityPeak,
                reflectionAdaptiveTelemetrySamples <= 0L
                        ? 0.0
                        : reflectionAdaptiveTemporalDeltaAccum / (double) reflectionAdaptiveTelemetrySamples,
                reflectionAdaptiveTelemetrySamples <= 0L
                        ? 0.0
                        : reflectionAdaptiveSsrStrengthDeltaAccum / (double) reflectionAdaptiveTelemetrySamples,
                reflectionAdaptiveTelemetrySamples <= 0L
                        ? 0.0
                        : reflectionAdaptiveSsrStepScaleDeltaAccum / (double) reflectionAdaptiveTelemetrySamples,
                reflectionAdaptiveTelemetrySamples
        );
    }

    @Override
    protected org.dynamislight.api.runtime.ReflectionAdaptiveTrendSloDiagnostics backendReflectionAdaptiveTrendSloDiagnostics() {
        ReflectionAdaptiveTrendSloDiagnostics diagnostics = debugReflectionAdaptiveTrendSloDiagnostics();
        return new org.dynamislight.api.runtime.ReflectionAdaptiveTrendSloDiagnostics(
                diagnostics.status(),
                diagnostics.reason(),
                diagnostics.failed(),
                diagnostics.windowSamples(),
                diagnostics.meanSeverity(),
                diagnostics.highRatio(),
                diagnostics.sloMeanSeverityMax(),
                diagnostics.sloHighRatioMax(),
                diagnostics.sloMinSamples()
        );
    }

    @Override
    protected boolean shouldEmitPerformanceWarningEvent(EngineWarning warning) {
        if (warning == null || warning.code() == null) {
            return false;
        }
        return "REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_FAILED".equals(warning.code())
                || "REFLECTION_SSR_TAA_ADAPTIVE_TREND_HIGH_RISK".equals(warning.code());
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
            int reflectionBaseMode = currentPost.reflectionsMode() & REFLECTION_MODE_BASE_MASK;
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
                    "REFLECTION_OVERRIDE_POLICY",
                    "Reflection override policy (auto=" + overrideSummary.autoCount()
                            + ", probeOnly=" + overrideSummary.probeOnlyCount()
                            + ", ssrOnly=" + overrideSummary.ssrOnlyCount()
                            + ", other=" + overrideSummary.otherCount()
                            + ", planarSelectiveExcludes=probe_only|ssr_only)"
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
            int effectiveStreamingBudget = Math.max(1, Math.min(reflectionProbeMaxVisible, probeDiagnostics.metadataCapacity()));
            boolean streamingBudgetPressure = probeDiagnostics.configuredProbeCount() > probeDiagnostics.activeProbeCount()
                    && (probeDiagnostics.activeProbeCount() >= effectiveStreamingBudget || probeDiagnostics.activeProbeCount() == 0);
            warnings.add(new EngineWarning(
                    "REFLECTION_PROBE_STREAMING_DIAGNOSTICS",
                    "Probe streaming diagnostics (configured=" + probeDiagnostics.configuredProbeCount()
                            + ", active=" + probeDiagnostics.activeProbeCount()
                            + ", effectiveBudget=" + effectiveStreamingBudget
                            + ", cadenceFrames=" + reflectionProbeUpdateCadenceFrames
                            + ", maxVisible=" + reflectionProbeMaxVisible
                            + ", lodDepthScale=" + reflectionProbeLodDepthScale
                            + ", budgetPressure=" + streamingBudgetPressure + ")"
            ));
            if (streamingBudgetPressure) {
                warnings.add(new EngineWarning(
                        "REFLECTION_PROBE_STREAMING_BUDGET_PRESSURE",
                        "Reflection probe streaming budget pressure detected "
                                + "(configured=" + probeDiagnostics.configuredProbeCount()
                                + ", active=" + probeDiagnostics.activeProbeCount()
                                + ", effectiveBudget=" + effectiveStreamingBudget + ")"
                ));
            }
            warnings.add(new EngineWarning(
                    "REFLECTION_PROBE_QUALITY_SWEEP",
                    "Probe quality sweep (configured=" + reflectionProbeQualityDiagnostics.configuredProbeCount()
                            + ", overlapPairs=" + reflectionProbeQualityDiagnostics.overlapPairs()
                            + ", bleedRiskPairs=" + reflectionProbeQualityDiagnostics.bleedRiskPairs()
                            + ", transitionPairs=" + reflectionProbeQualityDiagnostics.transitionPairs()
                            + ", maxPriorityDelta=" + reflectionProbeQualityDiagnostics.maxPriorityDelta()
                            + ", overlapWarnMaxPairs=" + reflectionProbeQualityOverlapWarnMaxPairs
                            + ", bleedRiskWarnMaxPairs=" + reflectionProbeQualityBleedRiskWarnMaxPairs
                            + ", minOverlapPairsWhenMultiple=" + reflectionProbeQualityMinOverlapPairsWhenMultiple
                            + ")"
            ));
            if (reflectionProbeQualityDiagnostics.envelopeBreached()) {
                warnings.add(new EngineWarning(
                        "REFLECTION_PROBE_QUALITY_ENVELOPE_BREACH",
                        "Probe quality envelope breached (overlapPairs=" + reflectionProbeQualityDiagnostics.overlapPairs()
                                + ", bleedRiskPairs=" + reflectionProbeQualityDiagnostics.bleedRiskPairs()
                                + ", transitionPairs=" + reflectionProbeQualityDiagnostics.transitionPairs()
                                + ", reason=" + reflectionProbeQualityDiagnostics.breachReason() + ")"
                ));
            }
            int planarEligible = countPlanarEligibleFromOverrideSummary(overrideSummary);
            int planarExcluded = planarScopeExclusionCountFromOverrideSummary(overrideSummary);
            int planarTotalMeshes = Math.max(0, planarEligible + planarExcluded);
            boolean planarPathActive = reflectionBaseMode == 2 || reflectionBaseMode == 3 || reflectionBaseMode == 4;
            reflectionPlanarScopedMeshEligibleCount = planarEligible;
            reflectionPlanarScopedMeshExcludedCount = planarExcluded;
            reflectionPlanarPassOrderContractStatus = planarPathActive
                    ? "prepass_capture_then_main_sample"
                    : "inactive";
            reflectionPlanarMirrorCameraActive = planarPathActive;
            // In mock/offscreen-disabled contexts, surface the logical planar lane contract as active.
            reflectionPlanarDedicatedCaptureLaneActive = planarPathActive;
            String planarScopeIncludes = "auto=" + reflectionPlanarScopeIncludeAuto
                    + "|probe_only=" + reflectionPlanarScopeIncludeProbeOnly
                    + "|ssr_only=" + reflectionPlanarScopeIncludeSsrOnly
                    + "|other=" + reflectionPlanarScopeIncludeOther;
            warnings.add(new EngineWarning(
                    "REFLECTION_PLANAR_SCOPE_CONTRACT",
                    "Planar scope contract (status=" + reflectionPlanarPassOrderContractStatus
                            + ", planarPathActive=" + planarPathActive
                            + ", mirrorCameraActive=" + reflectionPlanarMirrorCameraActive
                            + ", dedicatedCaptureLaneActive=" + reflectionPlanarDedicatedCaptureLaneActive
                            + ", scopeIncludes=" + planarScopeIncludes
                            + ", eligibleMeshes=" + planarEligible
                            + ", excludedMeshes=" + planarExcluded
                            + ", totalMeshes=" + planarTotalMeshes
                            + ", planeHeight=" + currentPost.reflectionsPlanarPlaneHeight()
                            + ", requiredOrder=planar_capture_before_main_sample_before_post)"
            ));
            String captureResourceStatus = planarPathActive
                    ? (reflectionPlanarDedicatedCaptureLaneActive ? "capture_available_before_post_sample" : "fallback_scene_color")
                    : "fallback_scene_color";
            warnings.add(new EngineWarning(
                    "REFLECTION_PLANAR_RESOURCE_CONTRACT",
                    "Planar resource contract (status=" + captureResourceStatus
                            + ", planarPathActive=" + planarPathActive
                            + ", dedicatedCaptureLaneActive=" + reflectionPlanarDedicatedCaptureLaneActive + ")"
            ));
            if (planarPathActive && planarEligible <= 0) {
                warnings.add(new EngineWarning(
                        "REFLECTION_PLANAR_SCOPE_EMPTY",
                        "Planar path active but selective scope has zero eligible meshes "
                                + "(excluded=" + planarExcluded + ")"
                ));
            }
            if (planarPathActive) {
                double planarCoverageRatio = planarTotalMeshes > 0
                        ? ((double) planarEligible / (double) planarTotalMeshes)
                        : 1.0;
                reflectionPlanarLatestCoverageRatio = planarCoverageRatio;
                double currentPlaneHeight = currentPost.reflectionsPlanarPlaneHeight();
                double planeDelta = Double.isNaN(reflectionPlanarPrevPlaneHeight)
                        ? 0.0
                        : Math.abs(currentPlaneHeight - reflectionPlanarPrevPlaneHeight);
                reflectionPlanarLatestPlaneDelta = planeDelta;
                boolean deltaRisk = !Double.isNaN(reflectionPlanarPrevPlaneHeight)
                        && planeDelta > reflectionPlanarEnvelopePlaneDeltaWarnMax;
                boolean coverageRisk = planarTotalMeshes > 0
                        && planarCoverageRatio < reflectionPlanarEnvelopeCoverageRatioWarnMin;
                boolean contractRisk = !reflectionPlanarMirrorCameraActive || !reflectionPlanarDedicatedCaptureLaneActive;
                boolean emptyRisk = planarEligible <= 0;
                boolean planarEnvelopeRisk = deltaRisk || coverageRisk || contractRisk || emptyRisk;
                if (planarEnvelopeRisk) {
                    reflectionPlanarEnvelopeHighStreak++;
                } else {
                    reflectionPlanarEnvelopeHighStreak = 0;
                }
                boolean planarEnvelopeTriggered = false;
                if (reflectionPlanarEnvelopeWarnCooldownRemaining > 0) {
                    reflectionPlanarEnvelopeWarnCooldownRemaining--;
                }
                if (planarEnvelopeRisk
                        && reflectionPlanarEnvelopeHighStreak >= reflectionPlanarEnvelopeWarnMinFrames
                        && reflectionPlanarEnvelopeWarnCooldownRemaining <= 0) {
                    planarEnvelopeTriggered = true;
                    reflectionPlanarEnvelopeWarnCooldownRemaining = reflectionPlanarEnvelopeWarnCooldownFrames;
                }
                reflectionPlanarEnvelopeBreachedLastFrame = planarEnvelopeTriggered;
                warnings.add(new EngineWarning(
                        "REFLECTION_PLANAR_STABILITY_ENVELOPE",
                        "Planar stability envelope (risk=" + planarEnvelopeRisk
                                + ", planeDelta=" + planeDelta
                                + ", planeDeltaWarnMax=" + reflectionPlanarEnvelopePlaneDeltaWarnMax
                                + ", coverageRatio=" + planarCoverageRatio
                                + ", coverageRatioWarnMin=" + reflectionPlanarEnvelopeCoverageRatioWarnMin
                                + ", contractRisk=" + contractRisk
                                + ", emptyScopeRisk=" + emptyRisk
                                + ", highStreak=" + reflectionPlanarEnvelopeHighStreak
                                + ", warnMinFrames=" + reflectionPlanarEnvelopeWarnMinFrames
                                + ", warnCooldownFrames=" + reflectionPlanarEnvelopeWarnCooldownFrames
                                + ", cooldownRemaining=" + reflectionPlanarEnvelopeWarnCooldownRemaining
                                + ", breached=" + planarEnvelopeTriggered
                                + ")"
                ));
                if (planarEnvelopeTriggered) {
                    warnings.add(new EngineWarning(
                            "REFLECTION_PLANAR_STABILITY_ENVELOPE_BREACH",
                            "Planar stability envelope breach (planeDelta=" + planeDelta
                                    + ", coverageRatio=" + planarCoverageRatio
                                    + ", contractRisk=" + contractRisk
                                    + ", emptyScopeRisk=" + emptyRisk + ")"
                    ));
                }
                reflectionPlanarPrevPlaneHeight = currentPlaneHeight;
                double planarGpuMsCap = planarPerfGpuMsCapForTier(qualityTier);
                String planarTimingSource = context.gpuTimingSource();
                boolean planarTimestampAvailable = "gpu_timestamp".equalsIgnoreCase(planarTimingSource);
                boolean planarTimestampRequirementUnmet = reflectionPlanarPerfRequireGpuTimestamp && !planarTimestampAvailable;
                double planarGpuMsEstimate = planarTimestampAvailable
                        ? Math.max(0.0, lastFrameGpuMs)
                        : Math.max(0.0, lastFrameGpuMs) * (0.28 + 0.52 * planarCoverageRatio);
                double planarDrawInflation = 1.0 + planarCoverageRatio;
                long planarMemoryEstimate = (long) Math.max(0.0, lastFrameGpuMs * 0.0);
                long planarBudgetBytes = (long) (reflectionPlanarPerfMemoryBudgetMb * 1024.0 * 1024.0);
                // Approximate dedicated planar lane memory from active viewport dimensions.
                planarMemoryEstimate = (long) Math.max(0.0, viewportWidth) * Math.max(0L, viewportHeight) * 4L;
                boolean planarPerfRisk = planarGpuMsEstimate > planarGpuMsCap
                        || planarDrawInflation > reflectionPlanarPerfDrawInflationWarnMax
                        || planarMemoryEstimate > planarBudgetBytes
                        || planarTimestampRequirementUnmet;
                if (planarPerfRisk) {
                    reflectionPlanarPerfHighStreak++;
                } else {
                    reflectionPlanarPerfHighStreak = 0;
                }
                boolean planarPerfTriggered = false;
                if (reflectionPlanarPerfWarnCooldownRemaining > 0) {
                    reflectionPlanarPerfWarnCooldownRemaining--;
                }
                if (planarPerfRisk
                        && reflectionPlanarPerfHighStreak >= reflectionPlanarPerfWarnMinFrames
                        && reflectionPlanarPerfWarnCooldownRemaining <= 0) {
                    planarPerfTriggered = true;
                    reflectionPlanarPerfWarnCooldownRemaining = reflectionPlanarPerfWarnCooldownFrames;
                }
                reflectionPlanarPerfBreachedLastFrame = planarPerfTriggered;
                reflectionPlanarPerfLastGpuMsEstimate = planarGpuMsEstimate;
                reflectionPlanarPerfLastGpuMsCap = planarGpuMsCap;
                reflectionPlanarPerfLastDrawInflation = planarDrawInflation;
                reflectionPlanarPerfLastMemoryBytes = planarMemoryEstimate;
                reflectionPlanarPerfLastMemoryBudgetBytes = planarBudgetBytes;
                reflectionPlanarPerfLastTimingSource = planarTimingSource;
                reflectionPlanarPerfLastTimestampAvailable = planarTimestampAvailable;
                reflectionPlanarPerfLastTimestampRequirementUnmet = planarTimestampRequirementUnmet;
                warnings.add(new EngineWarning(
                        "REFLECTION_PLANAR_PERF_GATES",
                        "Planar perf gates (risk=" + planarPerfRisk
                                + ", gpuMsEstimate=" + planarGpuMsEstimate
                                + ", gpuMsCap=" + planarGpuMsCap
                                + ", timingSource=" + planarTimingSource
                                + ", timestampAvailable=" + planarTimestampAvailable
                                + ", requireGpuTimestamp=" + reflectionPlanarPerfRequireGpuTimestamp
                                + ", timestampRequirementUnmet=" + planarTimestampRequirementUnmet
                                + ", drawInflation=" + planarDrawInflation
                                + ", drawInflationWarnMax=" + reflectionPlanarPerfDrawInflationWarnMax
                                + ", memoryBytes=" + planarMemoryEstimate
                                + ", memoryBudgetBytes=" + planarBudgetBytes
                                + ", highStreak=" + reflectionPlanarPerfHighStreak
                                + ", warnMinFrames=" + reflectionPlanarPerfWarnMinFrames
                                + ", warnCooldownFrames=" + reflectionPlanarPerfWarnCooldownFrames
                                + ", cooldownRemaining=" + reflectionPlanarPerfWarnCooldownRemaining
                                + ", breached=" + planarPerfTriggered
                                + ")"
                ));
                if (planarPerfTriggered) {
                    warnings.add(new EngineWarning(
                            "REFLECTION_PLANAR_PERF_GATES_BREACH",
                            "Planar perf gates breached (gpuMsEstimate=" + planarGpuMsEstimate
                                    + ", gpuMsCap=" + planarGpuMsCap
                                    + ", timingSource=" + planarTimingSource
                                    + ", timestampRequirementUnmet=" + planarTimestampRequirementUnmet
                                    + ", drawInflation=" + planarDrawInflation
                                    + ", memoryBytes=" + planarMemoryEstimate
                                    + ", memoryBudgetBytes=" + planarBudgetBytes + ")"
                    ));
                }
            } else {
                reflectionPlanarEnvelopeHighStreak = 0;
                reflectionPlanarEnvelopeBreachedLastFrame = false;
                reflectionPlanarPrevPlaneHeight = Double.NaN;
                reflectionPlanarLatestPlaneDelta = 0.0;
                reflectionPlanarLatestCoverageRatio = 1.0;
                reflectionPlanarPerfHighStreak = 0;
                reflectionPlanarPerfBreachedLastFrame = false;
                reflectionPlanarPerfLastGpuMsEstimate = 0.0;
                reflectionPlanarPerfLastGpuMsCap = planarPerfGpuMsCapForTier(qualityTier);
                reflectionPlanarPerfLastDrawInflation = 1.0;
                reflectionPlanarPerfLastMemoryBytes = 0L;
                reflectionPlanarPerfLastMemoryBudgetBytes = (long) (reflectionPlanarPerfMemoryBudgetMb * 1024.0 * 1024.0);
                reflectionPlanarPerfLastTimingSource = context.gpuTimingSource();
                reflectionPlanarPerfLastTimestampAvailable = "gpu_timestamp".equalsIgnoreCase(reflectionPlanarPerfLastTimingSource);
                reflectionPlanarPerfLastTimestampRequirementUnmet = reflectionPlanarPerfRequireGpuTimestamp
                        && !reflectionPlanarPerfLastTimestampAvailable;
            }
            reflectionRtLaneRequested = (currentPost.reflectionsMode() & REFLECTION_MODE_RT_REQUEST_BIT) != 0 || reflectionBaseMode == 4;
            reflectionRtLaneActive = reflectionRtLaneRequested && reflectionRtSingleBounceEnabled;
            reflectionRtFallbackChainActive = reflectionRtLaneActive ? "rt->ssr->probe" : "ssr->probe";
            if (reflectionRtLaneRequested || reflectionBaseMode == 4) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_PATH_REQUESTED",
                        "RT reflection path requested (singleBounceEnabled=" + reflectionRtSingleBounceEnabled
                                + ", multiBounceEnabled=" + reflectionRtMultiBounceEnabled
                                + ", dedicatedDenoisePipelineEnabled=" + reflectionRtDedicatedDenoisePipelineEnabled
                                + ", denoiseStrength=" + reflectionRtDenoiseStrength
                                + ", laneActive=" + reflectionRtLaneActive
                                + ", fallbackChain=" + reflectionRtFallbackChainActive + ")"
                ));
                if (!reflectionRtLaneActive) {
                    warnings.add(new EngineWarning(
                            "REFLECTION_RT_PATH_FALLBACK_ACTIVE",
                            "RT reflection lane unavailable; fallback chain active (" + reflectionRtFallbackChainActive + ")"
                    ));
                }
            } else {
                reflectionRtFallbackChainActive = "probe";
            }
            reflectionTransparentCandidateCount = countReflectionTransparentCandidates(currentSceneMaterials);
            if (reflectionTransparentCandidateCount > 0) {
                if (reflectionRtLaneActive) {
                    reflectionTransparencyStageGateStatus = "preview_enabled";
                    reflectionTransparencyFallbackPath = "rt_or_probe";
                } else {
                    reflectionTransparencyStageGateStatus = "blocked_until_rt_minimal_stable";
                    reflectionTransparencyFallbackPath = "probe_only";
                }
                warnings.add(new EngineWarning(
                        "REFLECTION_TRANSPARENCY_STAGE_GATE",
                        "Transparency/refraction stage gate (status=" + reflectionTransparencyStageGateStatus
                                + ", transparentCandidates=" + reflectionTransparentCandidateCount
                                + ", fallbackPath=" + reflectionTransparencyFallbackPath + ")"
                ));
                if (!reflectionRtLaneActive) {
                    warnings.add(new EngineWarning(
                            "REFLECTION_TRANSPARENCY_REFRACTION_PENDING",
                            "Transparent/refractive reflection path pending RT minimal stability "
                                    + "(transparentCandidates=" + reflectionTransparentCandidateCount + ")"
                    ));
                }
            } else {
                reflectionTransparencyStageGateStatus = "not_required";
                reflectionTransparencyFallbackPath = "none";
            }
            warnings.add(new EngineWarning(
                    "REFLECTION_TELEMETRY_PROFILE_ACTIVE",
                    "Reflection telemetry profile active (profile=" + reflectionProfile.name().toLowerCase()
                            + ", probeWarnMinDelta=" + reflectionProbeChurnWarnMinDelta
                            + ", probeWarnMinStreak=" + reflectionProbeChurnWarnMinStreak
                            + ", probeWarnCooldownFrames=" + reflectionProbeChurnWarnCooldownFrames
                            + ", probeOverlapWarnMaxPairs=" + reflectionProbeQualityOverlapWarnMaxPairs
                            + ", probeBleedRiskWarnMaxPairs=" + reflectionProbeQualityBleedRiskWarnMaxPairs
                            + ", probeMinOverlapPairsWhenMultiple=" + reflectionProbeQualityMinOverlapPairsWhenMultiple
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
                            + ", ssrTaaAdaptiveTrendWindowFrames=" + reflectionSsrTaaAdaptiveTrendWindowFrames
                            + ", ssrTaaAdaptiveTrendHighRatioWarnMin=" + reflectionSsrTaaAdaptiveTrendHighRatioWarnMin
                            + ", ssrTaaAdaptiveTrendWarnMinFrames=" + reflectionSsrTaaAdaptiveTrendWarnMinFrames
                            + ", ssrTaaAdaptiveTrendWarnCooldownFrames=" + reflectionSsrTaaAdaptiveTrendWarnCooldownFrames
                            + ", ssrTaaAdaptiveTrendWarnMinSamples=" + reflectionSsrTaaAdaptiveTrendWarnMinSamples
                            + ", ssrTaaAdaptiveTrendSloMeanSeverityMax=" + reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax
                            + ", ssrTaaAdaptiveTrendSloHighRatioMax=" + reflectionSsrTaaAdaptiveTrendSloHighRatioMax
                            + ", ssrTaaAdaptiveTrendSloMinSamples=" + reflectionSsrTaaAdaptiveTrendSloMinSamples
                            + ", ssrTaaDisocclusionRejectDropEventsMin=" + reflectionSsrTaaDisocclusionRejectDropEventsMin
                            + ", ssrTaaDisocclusionRejectConfidenceMax=" + reflectionSsrTaaDisocclusionRejectConfidenceMax
                            + ", ssrEnvelopeRejectWarnMax=" + reflectionSsrEnvelopeRejectWarnMax
                            + ", ssrEnvelopeConfidenceWarnMin=" + reflectionSsrEnvelopeConfidenceWarnMin
                            + ", ssrEnvelopeDropWarnMin=" + reflectionSsrEnvelopeDropWarnMin
                            + ", ssrEnvelopeWarnMinFrames=" + reflectionSsrEnvelopeWarnMinFrames
                            + ", ssrEnvelopeWarnCooldownFrames=" + reflectionSsrEnvelopeWarnCooldownFrames
                            + ", planarEnvelopePlaneDeltaWarnMax=" + reflectionPlanarEnvelopePlaneDeltaWarnMax
                            + ", planarEnvelopeCoverageRatioWarnMin=" + reflectionPlanarEnvelopeCoverageRatioWarnMin
                            + ", planarEnvelopeWarnMinFrames=" + reflectionPlanarEnvelopeWarnMinFrames
                            + ", planarEnvelopeWarnCooldownFrames=" + reflectionPlanarEnvelopeWarnCooldownFrames
                            + ", planarPerfMaxGpuMsLow=" + reflectionPlanarPerfMaxGpuMsLow
                            + ", planarPerfMaxGpuMsMedium=" + reflectionPlanarPerfMaxGpuMsMedium
                            + ", planarPerfMaxGpuMsHigh=" + reflectionPlanarPerfMaxGpuMsHigh
                            + ", planarPerfMaxGpuMsUltra=" + reflectionPlanarPerfMaxGpuMsUltra
                            + ", planarPerfDrawInflationWarnMax=" + reflectionPlanarPerfDrawInflationWarnMax
                            + ", planarPerfMemoryBudgetMb=" + reflectionPlanarPerfMemoryBudgetMb
                            + ", planarPerfWarnMinFrames=" + reflectionPlanarPerfWarnMinFrames
                            + ", planarPerfWarnCooldownFrames=" + reflectionPlanarPerfWarnCooldownFrames
                            + ", planarPerfRequireGpuTimestamp=" + reflectionPlanarPerfRequireGpuTimestamp
                            + ", planarScopeIncludeAuto=" + reflectionPlanarScopeIncludeAuto
                            + ", planarScopeIncludeProbeOnly=" + reflectionPlanarScopeIncludeProbeOnly
                            + ", planarScopeIncludeSsrOnly=" + reflectionPlanarScopeIncludeSsrOnly
                            + ", planarScopeIncludeOther=" + reflectionPlanarScopeIncludeOther
                            + ", rtSingleBounceEnabled=" + reflectionRtSingleBounceEnabled
                            + ", rtMultiBounceEnabled=" + reflectionRtMultiBounceEnabled
                            + ", rtDedicatedDenoisePipelineEnabled=" + reflectionRtDedicatedDenoisePipelineEnabled
                            + ", rtDenoiseStrength=" + reflectionRtDenoiseStrength
                            + ", probeUpdateCadenceFrames=" + reflectionProbeUpdateCadenceFrames
                            + ", probeMaxVisible=" + reflectionProbeMaxVisible
                            + ", probeLodDepthScale=" + reflectionProbeLodDepthScale
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
                warnings.add(new EngineWarning(
                        "REFLECTION_SSR_TAA_HISTORY_POLICY",
                        "SSR/TAA history policy (policy=" + reflectionSsrTaaHistoryPolicyActive
                                + ", reprojectionPolicy=" + reflectionSsrTaaReprojectionPolicyActive
                                + ", severityInstant=" + reflectionAdaptiveSeverityInstant
                                + ", riskHighStreak=" + reflectionSsrTaaRiskHighStreak
                                + ", latestRejectRate=" + reflectionSsrTaaLatestRejectRate
                                + ", latestConfidenceMean=" + reflectionSsrTaaLatestConfidenceMean
                                + ", latestDropEvents=" + reflectionSsrTaaLatestDropEvents
                                + ", rejectBias=" + reflectionSsrTaaHistoryRejectBiasActive
                                + ", confidenceDecay=" + reflectionSsrTaaHistoryConfidenceDecayActive
                                + ", rejectSeverityMin=" + reflectionSsrTaaHistoryRejectSeverityMin
                                + ", decaySeverityMin=" + reflectionSsrTaaHistoryConfidenceDecaySeverityMin
                                + ", rejectRiskStreakMin=" + reflectionSsrTaaHistoryRejectRiskStreakMin
                                + ", disocclusionRejectDropEventsMin=" + reflectionSsrTaaDisocclusionRejectDropEventsMin
                                + ", disocclusionRejectConfidenceMax=" + reflectionSsrTaaDisocclusionRejectConfidenceMax
                                + ")"
                ));
                boolean envelopeRisk = taaReject >= reflectionSsrEnvelopeRejectWarnMax
                        || taaConfidence <= reflectionSsrEnvelopeConfidenceWarnMin
                        || taaDrops >= reflectionSsrEnvelopeDropWarnMin;
                if (envelopeRisk) {
                    reflectionSsrEnvelopeHighStreak++;
                } else {
                    reflectionSsrEnvelopeHighStreak = 0;
                }
                boolean envelopeTriggered = false;
                if (reflectionSsrEnvelopeHighStreak >= reflectionSsrEnvelopeWarnMinFrames
                        && reflectionSsrEnvelopeWarnCooldownRemaining <= 0) {
                    envelopeTriggered = true;
                    reflectionSsrEnvelopeWarnCooldownRemaining = reflectionSsrEnvelopeWarnCooldownFrames;
                }
                if (reflectionSsrEnvelopeWarnCooldownRemaining > 0) {
                    reflectionSsrEnvelopeWarnCooldownRemaining--;
                }
                reflectionSsrEnvelopeBreachedLastFrame = envelopeTriggered;
                warnings.add(new EngineWarning(
                        "REFLECTION_SSR_REPROJECTION_ENVELOPE",
                        "SSR reprojection envelope (risk=" + envelopeRisk
                                + ", rejectRate=" + taaReject
                                + ", confidenceMean=" + taaConfidence
                                + ", dropEvents=" + taaDrops
                                + ", rejectWarnMax=" + reflectionSsrEnvelopeRejectWarnMax
                                + ", confidenceWarnMin=" + reflectionSsrEnvelopeConfidenceWarnMin
                                + ", dropWarnMin=" + reflectionSsrEnvelopeDropWarnMin
                                + ", warnMinFrames=" + reflectionSsrEnvelopeWarnMinFrames
                                + ", warnCooldownFrames=" + reflectionSsrEnvelopeWarnCooldownFrames
                                + ", highStreak=" + reflectionSsrEnvelopeHighStreak
                                + ", cooldownRemaining=" + reflectionSsrEnvelopeWarnCooldownRemaining
                                + ", breached=" + envelopeTriggered
                                + ")"
                ));
                if (envelopeTriggered) {
                    warnings.add(new EngineWarning(
                            "REFLECTION_SSR_REPROJECTION_ENVELOPE_BREACH",
                            "SSR reprojection envelope breach (rejectRate=" + taaReject
                                    + ", confidenceMean=" + taaConfidence
                                    + ", dropEvents=" + taaDrops + ")"
                    ));
                }
                boolean adaptiveTrendWarningTriggered = updateReflectionAdaptiveTrendWarningGate();
                ReflectionAdaptiveTrendDiagnostics adaptiveTrend = snapshotReflectionAdaptiveTrendDiagnostics(adaptiveTrendWarningTriggered);
                warnings.add(new EngineWarning(
                        "REFLECTION_SSR_TAA_ADAPTIVE_TREND_REPORT",
                        "SSR/TAA adaptive trend report (windowFrames=" + reflectionSsrTaaAdaptiveTrendWindowFrames
                                + ", windowSamples=" + adaptiveTrend.windowSamples()
                                + ", meanSeverity=" + adaptiveTrend.meanSeverity()
                                + ", peakSeverity=" + adaptiveTrend.peakSeverity()
                                + ", severityLowCount=" + adaptiveTrend.lowCount()
                                + ", severityMediumCount=" + adaptiveTrend.mediumCount()
                                + ", severityHighCount=" + adaptiveTrend.highCount()
                                + ", severityLowRatio=" + adaptiveTrend.lowRatio()
                                + ", severityMediumRatio=" + adaptiveTrend.mediumRatio()
                                + ", severityHighRatio=" + adaptiveTrend.highRatio()
                                + ", meanTemporalDelta=" + adaptiveTrend.meanTemporalDelta()
                                + ", meanSsrStrengthDelta=" + adaptiveTrend.meanSsrStrengthDelta()
                                + ", meanSsrStepScaleDelta=" + adaptiveTrend.meanSsrStepScaleDelta()
                                + ", highRatioWarnMin=" + adaptiveTrend.highRatioWarnMin()
                                + ", highRatioWarnMinFrames=" + adaptiveTrend.highRatioWarnMinFrames()
                                + ", highRatioWarnCooldownFrames=" + adaptiveTrend.highRatioWarnCooldownFrames()
                                + ", highRatioWarnMinSamples=" + adaptiveTrend.highRatioWarnMinSamples()
                                + ", highRatioWarnHighStreak=" + adaptiveTrend.highRatioWarnHighStreak()
                                + ", highRatioWarnCooldownRemaining=" + adaptiveTrend.highRatioWarnCooldownRemaining()
                                + ", highRatioWarnTriggered=" + adaptiveTrend.highRatioWarnTriggered()
                                + ")"
                ));
                TrendSloAudit trendSloAudit = evaluateReflectionAdaptiveTrendSlo(adaptiveTrend);
                warnings.add(new EngineWarning(
                        "REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_AUDIT",
                        "SSR/TAA adaptive trend SLO audit (status=" + trendSloAudit.status()
                                + ", reason=" + trendSloAudit.reason()
                                + ", meanSeverity=" + adaptiveTrend.meanSeverity()
                                + ", highRatio=" + adaptiveTrend.highRatio()
                                + ", windowSamples=" + adaptiveTrend.windowSamples()
                                + ", sloMeanSeverityMax=" + reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax
                                + ", sloHighRatioMax=" + reflectionSsrTaaAdaptiveTrendSloHighRatioMax
                                + ", sloMinSamples=" + reflectionSsrTaaAdaptiveTrendSloMinSamples
                                + ")"
                ));
                if (trendSloAudit.failed()) {
                    warnings.add(new EngineWarning(
                            "REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_FAILED",
                            "SSR/TAA adaptive trend SLO failed (meanSeverity=" + adaptiveTrend.meanSeverity()
                                    + ", highRatio=" + adaptiveTrend.highRatio()
                                    + ", windowSamples=" + adaptiveTrend.windowSamples()
                                    + ", reason=" + trendSloAudit.reason()
                                    + ")"
                    ));
                }
                if (adaptiveTrendWarningTriggered) {
                    warnings.add(new EngineWarning(
                            "REFLECTION_SSR_TAA_ADAPTIVE_TREND_HIGH_RISK",
                            "SSR/TAA adaptive trend high-risk gate triggered (highRatio=" + adaptiveTrend.highRatio()
                                    + ", highRatioWarnMin=" + reflectionSsrTaaAdaptiveTrendHighRatioWarnMin
                                    + ", windowSamples=" + adaptiveTrend.windowSamples()
                                    + ", warnMinSamples=" + reflectionSsrTaaAdaptiveTrendWarnMinSamples
                                    + ", warnHighStreak=" + reflectionSsrTaaAdaptiveTrendWarnHighStreak
                                    + ")"
                    ));
                }
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
                reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame = false;
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
            resetReflectionSsrTaaRiskDiagnostics();
            resetReflectionAdaptiveState();
            resetReflectionAdaptiveTelemetryMetrics();
            reflectionPlanarPassOrderContractStatus = "inactive";
            reflectionPlanarScopedMeshEligibleCount = 0;
            reflectionPlanarScopedMeshExcludedCount = 0;
            reflectionPlanarMirrorCameraActive = false;
            reflectionPlanarDedicatedCaptureLaneActive = false;
            reflectionRtLaneRequested = false;
            reflectionRtLaneActive = false;
            reflectionRtFallbackChainActive = "probe";
            reflectionTransparentCandidateCount = 0;
            reflectionTransparencyStageGateStatus = "not_required";
            reflectionTransparencyFallbackPath = "none";
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

    private ReflectionProbeQualityDiagnostics analyzeReflectionProbeQuality(List<ReflectionProbeDesc> probes) {
        List<ReflectionProbeDesc> safe = probes == null ? List.of() : probes;
        if (safe.isEmpty()) {
            return ReflectionProbeQualityDiagnostics.zero();
        }
        int overlapPairs = 0;
        int bleedRiskPairs = 0;
        int transitionPairs = 0;
        int maxPriorityDelta = 0;
        for (int i = 0; i < safe.size(); i++) {
            ReflectionProbeDesc a = safe.get(i);
            if (a == null) {
                continue;
            }
            for (int j = i + 1; j < safe.size(); j++) {
                ReflectionProbeDesc b = safe.get(j);
                if (b == null) {
                    continue;
                }
                if (!overlapsAabb(a, b)) {
                    continue;
                }
                overlapPairs++;
                int priorityDelta = Math.abs(a.priority() - b.priority());
                maxPriorityDelta = Math.max(maxPriorityDelta, priorityDelta);
                if (priorityDelta == 0) {
                    bleedRiskPairs++;
                } else {
                    transitionPairs++;
                }
            }
        }
        boolean tooManyOverlaps = overlapPairs > reflectionProbeQualityOverlapWarnMaxPairs;
        boolean tooManyBleedRisks = bleedRiskPairs > reflectionProbeQualityBleedRiskWarnMaxPairs;
        boolean tooFewTransitions = safe.size() > 1
                && overlapPairs < reflectionProbeQualityMinOverlapPairsWhenMultiple;
        boolean envelopeBreached = tooManyOverlaps || tooManyBleedRisks || tooFewTransitions;
        String breachReason = !envelopeBreached
                ? "none"
                : (tooManyBleedRisks
                ? "bleed_risk_pairs_exceeded"
                : (tooManyOverlaps ? "overlap_pairs_exceeded" : "insufficient_overlap_pairs"));
        return new ReflectionProbeQualityDiagnostics(
                safe.size(),
                overlapPairs,
                bleedRiskPairs,
                transitionPairs,
                maxPriorityDelta,
                envelopeBreached,
                breachReason
        );
    }

    private static boolean overlapsAabb(ReflectionProbeDesc a, ReflectionProbeDesc b) {
        return a.extentsMin().x() <= b.extentsMax().x()
                && a.extentsMax().x() >= b.extentsMin().x()
                && a.extentsMin().y() <= b.extentsMax().y()
                && a.extentsMax().y() >= b.extentsMin().y()
                && a.extentsMin().z() <= b.extentsMax().z()
                && a.extentsMax().z() >= b.extentsMin().z();
    }

    private static int countReflectionTransparentCandidates(List<MaterialDesc> materials) {
        if (materials == null || materials.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (MaterialDesc material : materials) {
            if (material != null && material.alphaTested()) {
                count++;
            }
        }
        return count;
    }

    private int countPlanarEligibleFromOverrideSummary(ReflectionOverrideSummary summary) {
        int eligible = 0;
        if (reflectionPlanarScopeIncludeAuto) {
            eligible += summary.autoCount();
        }
        if (reflectionPlanarScopeIncludeProbeOnly) {
            eligible += summary.probeOnlyCount();
        }
        if (reflectionPlanarScopeIncludeSsrOnly) {
            eligible += summary.ssrOnlyCount();
        }
        if (reflectionPlanarScopeIncludeOther) {
            eligible += summary.otherCount();
        }
        return Math.max(0, eligible);
    }

    private int planarScopeExclusionCountFromOverrideSummary(ReflectionOverrideSummary summary) {
        int excluded = 0;
        if (!reflectionPlanarScopeIncludeAuto) {
            excluded += summary.autoCount();
        }
        if (!reflectionPlanarScopeIncludeProbeOnly) {
            excluded += summary.probeOnlyCount();
        }
        if (!reflectionPlanarScopeIncludeSsrOnly) {
            excluded += summary.ssrOnlyCount();
        }
        if (!reflectionPlanarScopeIncludeOther) {
            excluded += summary.otherCount();
        }
        return Math.max(0, excluded);
    }

    private double planarPerfGpuMsCapForTier(QualityTier tier) {
        return switch (tier) {
            case LOW -> reflectionPlanarPerfMaxGpuMsLow;
            case MEDIUM -> reflectionPlanarPerfMaxGpuMsMedium;
            case HIGH -> reflectionPlanarPerfMaxGpuMsHigh;
            case ULTRA -> reflectionPlanarPerfMaxGpuMsUltra;
        };
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
        reflectionSsrTaaLatestRejectRate = taaReject;
        reflectionSsrTaaLatestConfidenceMean = taaConfidence;
        reflectionSsrTaaLatestDropEvents = taaDrops;
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
        reflectionSsrTaaLatestRejectRate = 0.0;
        reflectionSsrTaaLatestConfidenceMean = 1.0;
        reflectionSsrTaaLatestDropEvents = 0L;
    }

    private void applyAdaptiveReflectionPostParameters() {
        float baseTemporalWeight = currentPost.reflectionsTemporalWeight();
        float baseSsrStrength = currentPost.reflectionsSsrStrength();
        float baseSsrStepScale = currentPost.reflectionsSsrStepScale();
        reflectionAdaptiveTemporalWeightActive = baseTemporalWeight;
        reflectionAdaptiveSsrStrengthActive = baseSsrStrength;
        reflectionAdaptiveSsrStepScaleActive = baseSsrStepScale;
        double severity = 0.0;

        if (reflectionSsrTaaAdaptiveEnabled
                && currentPost.reflectionsEnabled()
                && currentPost.taaEnabled()
                && isReflectionSsrPathActive(currentPost.reflectionsMode())) {
            severity = computeReflectionAdaptiveSeverity();
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
        updateReflectionSsrTaaHistoryPolicy(
                currentPost.reflectionsEnabled(),
                currentPost.taaEnabled(),
                isReflectionSsrPathActive(currentPost.reflectionsMode()),
                severity,
                reflectionSsrTaaLatestRejectRate,
                reflectionSsrTaaLatestConfidenceMean,
                reflectionSsrTaaLatestDropEvents
        );
        if (currentPost.reflectionsEnabled()) {
            recordReflectionAdaptiveTelemetrySample(baseTemporalWeight, baseSsrStrength, baseSsrStepScale, severity);
        } else {
            reflectionAdaptiveSeverityInstant = 0.0;
        }
        int reflectionBaseMode = currentPost.reflectionsMode() & REFLECTION_MODE_BASE_MASK;
        ReflectionOverrideSummary overrideSummary = summarizeReflectionOverrides(context.debugGpuMeshReflectionOverrideModes());
        int planarEligible = countPlanarEligibleFromOverrideSummary(overrideSummary);
        reflectionTransparentCandidateCount = countReflectionTransparentCandidates(currentSceneMaterials);
        reflectionRtLaneRequested = (currentPost.reflectionsMode() & REFLECTION_MODE_RT_REQUEST_BIT) != 0 || reflectionBaseMode == 4;
        reflectionRtLaneActive = reflectionRtLaneRequested && reflectionRtSingleBounceEnabled;
        int reflectionModeRuntime = composeReflectionExecutionMode(
                currentPost.reflectionsMode(),
                reflectionRtLaneActive,
                planarEligible > 0,
                reflectionTransparentCandidateCount > 0
        );

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
                reflectionModeRuntime,
                reflectionAdaptiveSsrStrengthActive,
                currentPost.reflectionsSsrMaxRoughness(),
                reflectionAdaptiveSsrStepScaleActive,
                reflectionAdaptiveTemporalWeightActive,
                currentPost.reflectionsPlanarStrength(),
                currentPost.reflectionsPlanarPlaneHeight(),
                (float) reflectionRtDenoiseStrength
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
        int baseMode = reflectionsMode & REFLECTION_MODE_BASE_MASK;
        return baseMode == 1 || baseMode == 3 || baseMode == 4;
    }

    private int composeReflectionExecutionMode(
            int configuredMode,
            boolean rtLaneActive,
            boolean planarSelectiveEligible,
            boolean transparencyCandidatesPresent
    ) {
        int mode = configuredMode & (REFLECTION_MODE_BASE_MASK
                | REFLECTION_MODE_HIZ_BIT
                | REFLECTION_MODE_DENOISE_MASK
                | REFLECTION_MODE_PLANAR_CLIP_BIT
                | REFLECTION_MODE_PROBE_VOLUME_BIT
                | REFLECTION_MODE_PROBE_BOX_BIT
                | REFLECTION_MODE_RT_REQUEST_BIT);
        if (reflectionRtMultiBounceEnabled) {
            mode |= REFLECTION_MODE_RT_MULTI_BOUNCE_BIT;
        }
        if (rtLaneActive) {
            mode |= REFLECTION_MODE_RT_ACTIVE_BIT;
            if (reflectionRtDedicatedDenoisePipelineEnabled) {
                mode |= REFLECTION_MODE_RT_DEDICATED_DENOISE_BIT;
            }
        }
        if (planarSelectiveEligible) {
            mode |= REFLECTION_MODE_PLANAR_SELECTIVE_EXEC_BIT;
            mode |= REFLECTION_MODE_PLANAR_CAPTURE_EXEC_BIT;
            mode |= REFLECTION_MODE_PLANAR_GEOMETRY_CAPTURE_BIT;
        }
        if (reflectionPlanarScopeIncludeAuto) {
            mode |= REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_AUTO_BIT;
        }
        if (reflectionPlanarScopeIncludeProbeOnly) {
            mode |= REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_PROBE_ONLY_BIT;
        }
        if (reflectionPlanarScopeIncludeSsrOnly) {
            mode |= REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_SSR_ONLY_BIT;
        }
        if (reflectionPlanarScopeIncludeOther) {
            mode |= REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_OTHER_BIT;
        }
        if (transparencyCandidatesPresent && rtLaneActive) {
            mode |= REFLECTION_MODE_TRANSPARENCY_INTEGRATION_BIT;
        }
        if (isReflectionSpaceReprojectionPolicyActive()) {
            mode |= REFLECTION_MODE_REPROJECTION_REFLECTION_SPACE_BIT;
        }
        if (isStrictReflectionHistoryRejectPolicyActive()) {
            mode |= REFLECTION_MODE_HISTORY_STRICT_REJECT_BIT;
        }
        if (isDisocclusionRejectPolicyActive()) {
            mode |= REFLECTION_MODE_DISOCCLUSION_REJECT_BIT;
        }
        return mode;
    }

    private boolean isReflectionSpaceReprojectionPolicyActive() {
        return "reflection_space_reject".equals(reflectionSsrTaaReprojectionPolicyActive)
                || "reflection_space_bias".equals(reflectionSsrTaaReprojectionPolicyActive);
    }

    private boolean isStrictReflectionHistoryRejectPolicyActive() {
        return "reflection_disocclusion_reject".equals(reflectionSsrTaaHistoryPolicyActive)
                || "reflection_region_reject".equals(reflectionSsrTaaHistoryPolicyActive);
    }

    private boolean isDisocclusionRejectPolicyActive() {
        return "reflection_disocclusion_reject".equals(reflectionSsrTaaHistoryPolicyActive);
    }

    private void resetReflectionAdaptiveState() {
        reflectionAdaptiveTemporalWeightActive = currentPost == null ? 0.80f : currentPost.reflectionsTemporalWeight();
        reflectionAdaptiveSsrStrengthActive = currentPost == null ? 0.6f : currentPost.reflectionsSsrStrength();
        reflectionAdaptiveSsrStepScaleActive = currentPost == null ? 1.0f : currentPost.reflectionsSsrStepScale();
        reflectionSsrTaaHistoryPolicyActive = "inactive";
        reflectionSsrTaaReprojectionPolicyActive = "surface_motion_vectors";
        reflectionSsrTaaHistoryRejectBiasActive = 0.0;
        reflectionSsrTaaHistoryConfidenceDecayActive = 0.0;
    }

    private void updateReflectionSsrTaaHistoryPolicy(
            boolean reflectionsEnabled,
            boolean taaEnabled,
            boolean ssrPathActive,
            double severity,
            double taaReject,
            double taaConfidence,
            long taaDrops
    ) {
        if (!(reflectionsEnabled && taaEnabled && ssrPathActive)) {
            reflectionSsrTaaHistoryPolicyActive = "inactive";
            reflectionSsrTaaReprojectionPolicyActive = "surface_motion_vectors";
            reflectionSsrTaaHistoryRejectBiasActive = 0.0;
            reflectionSsrTaaHistoryConfidenceDecayActive = 0.0;
            return;
        }
        double clampedSeverity = Math.max(0.0, Math.min(1.0, severity));
        boolean disocclusionReject = taaDrops >= reflectionSsrTaaDisocclusionRejectDropEventsMin
                && taaConfidence <= reflectionSsrTaaDisocclusionRejectConfidenceMax;
        boolean rejectMode = clampedSeverity >= reflectionSsrTaaHistoryRejectSeverityMin
                || reflectionSsrTaaRiskHighStreak >= reflectionSsrTaaHistoryRejectRiskStreakMin;
        boolean decayMode = clampedSeverity >= reflectionSsrTaaHistoryConfidenceDecaySeverityMin;
        if (disocclusionReject) {
            reflectionSsrTaaHistoryPolicyActive = "reflection_disocclusion_reject";
            reflectionSsrTaaReprojectionPolicyActive = "reflection_space_reject";
            reflectionSsrTaaHistoryRejectBiasActive = Math.max(0.0, Math.min(1.0, 0.50 + clampedSeverity * 0.50));
            reflectionSsrTaaHistoryConfidenceDecayActive = Math.max(0.0, Math.min(1.0, 0.60 + clampedSeverity * 0.40));
            return;
        }
        if (rejectMode) {
            reflectionSsrTaaHistoryPolicyActive = "reflection_region_reject";
            reflectionSsrTaaReprojectionPolicyActive = "reflection_space_reject";
            reflectionSsrTaaHistoryRejectBiasActive = Math.max(0.0, Math.min(1.0, 0.35 + clampedSeverity * 0.65));
            reflectionSsrTaaHistoryConfidenceDecayActive = Math.max(0.0, Math.min(1.0, 0.45 + clampedSeverity * 0.55));
            return;
        }
        if (decayMode) {
            reflectionSsrTaaHistoryPolicyActive = "reflection_region_decay";
            reflectionSsrTaaReprojectionPolicyActive = taaReject >= 0.25 ? "reflection_space_bias" : "surface_motion_vectors";
            reflectionSsrTaaHistoryRejectBiasActive = Math.max(0.0, Math.min(1.0, 0.15 + clampedSeverity * 0.45));
            reflectionSsrTaaHistoryConfidenceDecayActive = Math.max(0.0, Math.min(1.0, 0.25 + clampedSeverity * 0.50));
            return;
        }
        reflectionSsrTaaHistoryPolicyActive = "surface_motion_vectors";
        reflectionSsrTaaReprojectionPolicyActive = "surface_motion_vectors";
        reflectionSsrTaaHistoryRejectBiasActive = Math.max(0.0, Math.min(1.0, clampedSeverity * 0.20));
        reflectionSsrTaaHistoryConfidenceDecayActive = Math.max(0.0, Math.min(1.0, clampedSeverity * 0.30));
    }

    private void recordReflectionAdaptiveTelemetrySample(
            float baseTemporalWeight,
            float baseSsrStrength,
            float baseSsrStepScale,
            double severity
    ) {
        reflectionAdaptiveSeverityInstant = Math.max(0.0, Math.min(1.0, severity));
        reflectionAdaptiveSeverityPeak = Math.max(reflectionAdaptiveSeverityPeak, reflectionAdaptiveSeverityInstant);
        reflectionAdaptiveTelemetrySamples++;
        reflectionAdaptiveSeverityAccum += reflectionAdaptiveSeverityInstant;
        double temporalDelta = reflectionAdaptiveTemporalWeightActive - baseTemporalWeight;
        double ssrStrengthDelta = reflectionAdaptiveSsrStrengthActive - baseSsrStrength;
        double ssrStepScaleDelta = reflectionAdaptiveSsrStepScaleActive - baseSsrStepScale;
        reflectionAdaptiveTemporalDeltaAccum += temporalDelta;
        reflectionAdaptiveSsrStrengthDeltaAccum += ssrStrengthDelta;
        reflectionAdaptiveSsrStepScaleDeltaAccum += ssrStepScaleDelta;
        reflectionAdaptiveTrendSamples.addLast(new ReflectionAdaptiveWindowSample(
                reflectionAdaptiveSeverityInstant,
                temporalDelta,
                ssrStrengthDelta,
                ssrStepScaleDelta
        ));
        while (reflectionAdaptiveTrendSamples.size() > reflectionSsrTaaAdaptiveTrendWindowFrames) {
            reflectionAdaptiveTrendSamples.removeFirst();
        }
    }

    private void resetReflectionAdaptiveTelemetryMetrics() {
        reflectionAdaptiveSeverityInstant = 0.0;
        reflectionAdaptiveSeverityPeak = 0.0;
        reflectionAdaptiveSeverityAccum = 0.0;
        reflectionAdaptiveTemporalDeltaAccum = 0.0;
        reflectionAdaptiveSsrStrengthDeltaAccum = 0.0;
        reflectionAdaptiveSsrStepScaleDeltaAccum = 0.0;
        reflectionAdaptiveTelemetrySamples = 0L;
        reflectionSsrTaaAdaptiveTrendWarnHighStreak = 0;
        reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining = 0;
        reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame = false;
        reflectionAdaptiveTrendSamples.clear();
    }

    private ReflectionAdaptiveTrendDiagnostics snapshotReflectionAdaptiveTrendDiagnostics(boolean warningTriggered) {
        int windowSamples = reflectionAdaptiveTrendSamples.size();
        if (windowSamples <= 0) {
            return new ReflectionAdaptiveTrendDiagnostics(
                    0,
                    0.0,
                    0.0,
                    0,
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    reflectionSsrTaaAdaptiveTrendHighRatioWarnMin,
                    reflectionSsrTaaAdaptiveTrendWarnMinFrames,
                    reflectionSsrTaaAdaptiveTrendWarnCooldownFrames,
                    reflectionSsrTaaAdaptiveTrendWarnMinSamples,
                    reflectionSsrTaaAdaptiveTrendWarnHighStreak,
                    reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining,
                    warningTriggered
            );
        }
        double severityAccum = 0.0;
        double severityPeak = 0.0;
        double temporalDeltaAccum = 0.0;
        double ssrStrengthDeltaAccum = 0.0;
        double ssrStepScaleDeltaAccum = 0.0;
        int lowCount = 0;
        int mediumCount = 0;
        int highCount = 0;
        for (ReflectionAdaptiveWindowSample sample : reflectionAdaptiveTrendSamples) {
            double severity = sample.severity();
            severityAccum += severity;
            severityPeak = Math.max(severityPeak, severity);
            temporalDeltaAccum += sample.temporalDelta();
            ssrStrengthDeltaAccum += sample.ssrStrengthDelta();
            ssrStepScaleDeltaAccum += sample.ssrStepScaleDelta();
            if (severity < 0.25) {
                lowCount++;
            } else if (severity < 0.60) {
                mediumCount++;
            } else {
                highCount++;
            }
        }
        double denom = (double) windowSamples;
        return new ReflectionAdaptiveTrendDiagnostics(
                windowSamples,
                severityAccum / denom,
                severityPeak,
                lowCount,
                mediumCount,
                highCount,
                lowCount / denom,
                mediumCount / denom,
                highCount / denom,
                temporalDeltaAccum / denom,
                ssrStrengthDeltaAccum / denom,
                ssrStepScaleDeltaAccum / denom,
                reflectionSsrTaaAdaptiveTrendHighRatioWarnMin,
                reflectionSsrTaaAdaptiveTrendWarnMinFrames,
                reflectionSsrTaaAdaptiveTrendWarnCooldownFrames,
                reflectionSsrTaaAdaptiveTrendWarnMinSamples,
                reflectionSsrTaaAdaptiveTrendWarnHighStreak,
                reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining,
                warningTriggered
        );
    }

    private boolean updateReflectionAdaptiveTrendWarningGate() {
        ReflectionAdaptiveTrendDiagnostics trend = snapshotReflectionAdaptiveTrendDiagnostics(false);
        boolean highRisk = trend.windowSamples() >= reflectionSsrTaaAdaptiveTrendWarnMinSamples
                && trend.highRatio() >= reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
        boolean warningTriggered = false;
        if (highRisk) {
            reflectionSsrTaaAdaptiveTrendWarnHighStreak++;
            if (reflectionSsrTaaAdaptiveTrendWarnHighStreak >= reflectionSsrTaaAdaptiveTrendWarnMinFrames
                    && reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining <= 0) {
                reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining = reflectionSsrTaaAdaptiveTrendWarnCooldownFrames;
                warningTriggered = true;
            }
        } else {
            reflectionSsrTaaAdaptiveTrendWarnHighStreak = 0;
        }
        if (reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining > 0) {
            reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining--;
        }
        reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame = warningTriggered;
        return warningTriggered;
    }

    private TrendSloAudit evaluateReflectionAdaptiveTrendSlo(ReflectionAdaptiveTrendDiagnostics trend) {
        if (trend.windowSamples() < reflectionSsrTaaAdaptiveTrendSloMinSamples) {
            return new TrendSloAudit("pending", "insufficient_samples", false);
        }
        if (trend.meanSeverity() > reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax) {
            return new TrendSloAudit("fail", "mean_severity_exceeded", true);
        }
        if (trend.highRatio() > reflectionSsrTaaAdaptiveTrendSloHighRatioMax) {
            return new TrendSloAudit("fail", "high_ratio_exceeded", true);
        }
        return new TrendSloAudit("pass", "within_thresholds", false);
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

    int debugReflectionRuntimeMode() {
        return context.debugReflectionsMode();
    }

    float debugReflectionRuntimeRtDenoiseStrength() {
        return context.debugReflectionsRtDenoiseStrength();
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

    ReflectionProbeStreamingDiagnostics debugReflectionProbeStreamingDiagnostics() {
        VulkanContext.ReflectionProbeDiagnostics diagnostics = context.debugReflectionProbeDiagnostics();
        int effectiveStreamingBudget = Math.max(1, Math.min(reflectionProbeMaxVisible, diagnostics.metadataCapacity()));
        boolean budgetPressure = diagnostics.configuredProbeCount() > diagnostics.activeProbeCount()
                && (diagnostics.activeProbeCount() >= effectiveStreamingBudget || diagnostics.activeProbeCount() == 0);
        return new ReflectionProbeStreamingDiagnostics(
                diagnostics.configuredProbeCount(),
                diagnostics.activeProbeCount(),
                reflectionProbeMaxVisible,
                effectiveStreamingBudget,
                reflectionProbeUpdateCadenceFrames,
                reflectionProbeLodDepthScale,
                budgetPressure
        );
    }

    ReflectionProbeChurnDiagnostics debugReflectionProbeChurnDiagnostics() {
        return snapshotReflectionProbeChurnDiagnostics(false);
    }

    ReflectionProbeQualityDiagnostics debugReflectionProbeQualityDiagnostics() {
        return reflectionProbeQualityDiagnostics;
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

    ReflectionPlanarContractDiagnostics debugReflectionPlanarContractDiagnostics() {
        return new ReflectionPlanarContractDiagnostics(
                reflectionPlanarPassOrderContractStatus,
                reflectionPlanarScopedMeshEligibleCount,
                reflectionPlanarScopedMeshExcludedCount,
                reflectionPlanarMirrorCameraActive,
                reflectionPlanarDedicatedCaptureLaneActive
        );
    }

    ReflectionPlanarStabilityDiagnostics debugReflectionPlanarStabilityDiagnostics() {
        return new ReflectionPlanarStabilityDiagnostics(
                reflectionPlanarLatestPlaneDelta,
                reflectionPlanarLatestCoverageRatio,
                reflectionPlanarEnvelopePlaneDeltaWarnMax,
                reflectionPlanarEnvelopeCoverageRatioWarnMin,
                reflectionPlanarEnvelopeHighStreak,
                reflectionPlanarEnvelopeWarnMinFrames,
                reflectionPlanarEnvelopeWarnCooldownFrames,
                reflectionPlanarEnvelopeWarnCooldownRemaining,
                reflectionPlanarEnvelopeBreachedLastFrame
        );
    }

    ReflectionPlanarPerfDiagnostics debugReflectionPlanarPerfDiagnostics() {
        return new ReflectionPlanarPerfDiagnostics(
                reflectionPlanarPerfLastGpuMsEstimate,
                reflectionPlanarPerfLastGpuMsCap,
                reflectionPlanarPerfLastTimingSource,
                reflectionPlanarPerfLastTimestampAvailable,
                reflectionPlanarPerfRequireGpuTimestamp,
                reflectionPlanarPerfLastTimestampRequirementUnmet,
                reflectionPlanarPerfLastDrawInflation,
                reflectionPlanarPerfDrawInflationWarnMax,
                reflectionPlanarPerfLastMemoryBytes,
                reflectionPlanarPerfLastMemoryBudgetBytes,
                reflectionPlanarPerfHighStreak,
                reflectionPlanarPerfWarnMinFrames,
                reflectionPlanarPerfWarnCooldownFrames,
                reflectionPlanarPerfWarnCooldownRemaining,
                reflectionPlanarPerfBreachedLastFrame
        );
    }

    ReflectionOverridePolicyDiagnostics debugReflectionOverridePolicyDiagnostics() {
        ReflectionOverrideSummary summary = summarizeReflectionOverrides(context.debugGpuMeshReflectionOverrideModes());
        return new ReflectionOverridePolicyDiagnostics(
                summary.autoCount(),
                summary.probeOnlyCount(),
                summary.ssrOnlyCount(),
                summary.otherCount(),
                "probe_only|ssr_only"
        );
    }

    ReflectionRtPathDiagnostics debugReflectionRtPathDiagnostics() {
        return new ReflectionRtPathDiagnostics(
                reflectionRtLaneRequested,
                reflectionRtLaneActive,
                reflectionRtSingleBounceEnabled,
                reflectionRtMultiBounceEnabled,
                reflectionRtDedicatedDenoisePipelineEnabled,
                reflectionRtDenoiseStrength,
                reflectionRtFallbackChainActive
        );
    }

    ReflectionTransparencyDiagnostics debugReflectionTransparencyDiagnostics() {
        return new ReflectionTransparencyDiagnostics(
                reflectionTransparentCandidateCount,
                reflectionTransparencyStageGateStatus,
                reflectionTransparencyFallbackPath,
                reflectionRtLaneActive
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

    ReflectionSsrTaaHistoryPolicyDiagnostics debugReflectionSsrTaaHistoryPolicyDiagnostics() {
        return new ReflectionSsrTaaHistoryPolicyDiagnostics(
                reflectionSsrTaaHistoryPolicyActive,
                reflectionSsrTaaReprojectionPolicyActive,
                reflectionAdaptiveSeverityInstant,
                reflectionSsrTaaRiskHighStreak,
                reflectionSsrTaaLatestRejectRate,
                reflectionSsrTaaLatestConfidenceMean,
                reflectionSsrTaaLatestDropEvents,
                reflectionSsrTaaHistoryRejectBiasActive,
                reflectionSsrTaaHistoryConfidenceDecayActive,
                reflectionSsrTaaHistoryRejectSeverityMin,
                reflectionSsrTaaHistoryConfidenceDecaySeverityMin,
                reflectionSsrTaaHistoryRejectRiskStreakMin,
                reflectionSsrTaaDisocclusionRejectDropEventsMin,
                reflectionSsrTaaDisocclusionRejectConfidenceMax,
                reflectionSsrTaaAdaptiveEnabled
        );
    }

    ReflectionAdaptiveTrendDiagnostics debugReflectionAdaptiveTrendDiagnostics() {
        return snapshotReflectionAdaptiveTrendDiagnostics(reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame);
    }

    ReflectionAdaptiveTrendSloDiagnostics debugReflectionAdaptiveTrendSloDiagnostics() {
        ReflectionAdaptiveTrendDiagnostics trend = snapshotReflectionAdaptiveTrendDiagnostics(
                reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame
        );
        TrendSloAudit audit = evaluateReflectionAdaptiveTrendSlo(trend);
        return new ReflectionAdaptiveTrendSloDiagnostics(
                audit.status(),
                audit.reason(),
                audit.failed(),
                trend.windowSamples(),
                trend.meanSeverity(),
                trend.highRatio(),
                reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax,
                reflectionSsrTaaAdaptiveTrendSloHighRatioMax,
                reflectionSsrTaaAdaptiveTrendSloMinSamples
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

    record ReflectionProbeStreamingDiagnostics(
            int configuredProbeCount,
            int activeProbeCount,
            int maxVisibleBudget,
            int effectiveStreamingBudget,
            int updateCadenceFrames,
            double lodDepthScale,
            boolean budgetPressure
    ) {
    }

    record ReflectionProbeQualityDiagnostics(
            int configuredProbeCount,
            int overlapPairs,
            int bleedRiskPairs,
            int transitionPairs,
            int maxPriorityDelta,
            boolean envelopeBreached,
            String breachReason
    ) {
        static ReflectionProbeQualityDiagnostics zero() {
            return new ReflectionProbeQualityDiagnostics(0, 0, 0, 0, 0, false, "none");
        }
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

    record ReflectionSsrTaaHistoryPolicyDiagnostics(
            String policy,
            String reprojectionPolicy,
            double severityInstant,
            int riskHighStreak,
            double latestRejectRate,
            double latestConfidenceMean,
            long latestDropEvents,
            double rejectBias,
            double confidenceDecay,
            double rejectSeverityMin,
            double decaySeverityMin,
            int rejectRiskStreakMin,
            long disocclusionRejectDropEventsMin,
            double disocclusionRejectConfidenceMax,
            boolean adaptiveEnabled
    ) {
    }

    record ReflectionPlanarContractDiagnostics(
            String status,
            int scopedMeshEligibleCount,
            int scopedMeshExcludedCount,
            boolean mirrorCameraActive,
            boolean dedicatedCaptureLaneActive
    ) {
    }

    record ReflectionPlanarStabilityDiagnostics(
            double planeDelta,
            double coverageRatio,
            double planeDeltaWarnMax,
            double coverageRatioWarnMin,
            int highStreak,
            int warnMinFrames,
            int warnCooldownFrames,
            int warnCooldownRemaining,
            boolean breachedLastFrame
    ) {
    }

    record ReflectionPlanarPerfDiagnostics(
            double gpuMsEstimate,
            double gpuMsCap,
            String timingSource,
            boolean timestampAvailable,
            boolean requireGpuTimestamp,
            boolean timestampRequirementUnmet,
            double drawInflation,
            double drawInflationWarnMax,
            long memoryBytes,
            long memoryBudgetBytes,
            int highStreak,
            int warnMinFrames,
            int warnCooldownFrames,
            int warnCooldownRemaining,
            boolean breachedLastFrame
    ) {
    }

    record ReflectionOverridePolicyDiagnostics(
            int autoCount,
            int probeOnlyCount,
            int ssrOnlyCount,
            int otherCount,
            String planarSelectiveExcludes
    ) {
    }

    record ReflectionRtPathDiagnostics(
            boolean laneRequested,
            boolean laneActive,
            boolean singleBounceEnabled,
            boolean multiBounceEnabled,
            boolean dedicatedDenoisePipelineEnabled,
            double denoiseStrength,
            String fallbackChain
    ) {
    }

    record ReflectionTransparencyDiagnostics(
            int transparentCandidateCount,
            String stageGateStatus,
            String fallbackPath,
            boolean rtLaneActive
    ) {
    }

    record ReflectionAdaptiveTrendDiagnostics(
            int windowSamples,
            double meanSeverity,
            double peakSeverity,
            int lowCount,
            int mediumCount,
            int highCount,
            double lowRatio,
            double mediumRatio,
            double highRatio,
            double meanTemporalDelta,
            double meanSsrStrengthDelta,
            double meanSsrStepScaleDelta,
            double highRatioWarnMin,
            int highRatioWarnMinFrames,
            int highRatioWarnCooldownFrames,
            int highRatioWarnMinSamples,
            int highRatioWarnHighStreak,
            int highRatioWarnCooldownRemaining,
            boolean highRatioWarnTriggered
    ) {
    }

    record ReflectionAdaptiveTrendSloDiagnostics(
            String status,
            String reason,
            boolean failed,
            int windowSamples,
            double meanSeverity,
            double highRatio,
            double sloMeanSeverityMax,
            double sloHighRatioMax,
            int sloMinSamples
    ) {
    }

    private record ReflectionOverrideSummary(int autoCount, int probeOnlyCount, int ssrOnlyCount, int otherCount) {
    }

    private record ReflectionAdaptiveWindowSample(
            double severity,
            double temporalDelta,
            double ssrStrengthDelta,
            double ssrStepScaleDelta
    ) {
    }

    private record TrendSloAudit(
            String status,
            String reason,
            boolean failed
    ) {
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
                base.reflectionsPlanarStrength(),
                base.reflectionsPlanarPlaneHeight()
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
                if (!hasBackendOption(safe, "vulkan.reflections.probeQualityOverlapWarnMaxPairs")) reflectionProbeQualityOverlapWarnMaxPairs = 6;
                if (!hasBackendOption(safe, "vulkan.reflections.probeQualityBleedRiskWarnMaxPairs")) reflectionProbeQualityBleedRiskWarnMaxPairs = 0;
                if (!hasBackendOption(safe, "vulkan.reflections.probeQualityMinOverlapPairsWhenMultiple")) reflectionProbeQualityMinOverlapPairsWhenMultiple = 1;
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
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWindowFrames")) reflectionSsrTaaAdaptiveTrendWindowFrames = 150;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendHighRatioWarnMin")) reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = 0.55;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnMinFrames")) reflectionSsrTaaAdaptiveTrendWarnMinFrames = 4;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnCooldownFrames")) reflectionSsrTaaAdaptiveTrendWarnCooldownFrames = 240;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnMinSamples")) reflectionSsrTaaAdaptiveTrendWarnMinSamples = 30;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloMeanSeverityMax")) reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = 0.25;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloHighRatioMax")) reflectionSsrTaaAdaptiveTrendSloHighRatioMax = 0.20;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloMinSamples")) reflectionSsrTaaAdaptiveTrendSloMinSamples = 30;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaDisocclusionRejectDropEventsMin")) reflectionSsrTaaDisocclusionRejectDropEventsMin = 3;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaDisocclusionRejectConfidenceMax")) reflectionSsrTaaDisocclusionRejectConfidenceMax = 0.58;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopePlaneDeltaWarnMax")) reflectionPlanarEnvelopePlaneDeltaWarnMax = 0.45;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopeCoverageRatioWarnMin")) reflectionPlanarEnvelopeCoverageRatioWarnMin = 0.30;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopeWarnMinFrames")) reflectionPlanarEnvelopeWarnMinFrames = 4;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopeWarnCooldownFrames")) reflectionPlanarEnvelopeWarnCooldownFrames = 180;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsLow")) reflectionPlanarPerfMaxGpuMsLow = 1.2;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsMedium")) reflectionPlanarPerfMaxGpuMsMedium = 1.9;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsHigh")) reflectionPlanarPerfMaxGpuMsHigh = 2.6;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsUltra")) reflectionPlanarPerfMaxGpuMsUltra = 3.2;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfDrawInflationWarnMax")) reflectionPlanarPerfDrawInflationWarnMax = 1.7;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMemoryBudgetMb")) reflectionPlanarPerfMemoryBudgetMb = 20.0;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfWarnMinFrames")) reflectionPlanarPerfWarnMinFrames = 4;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfWarnCooldownFrames")) reflectionPlanarPerfWarnCooldownFrames = 180;
                if (!hasBackendOption(safe, "vulkan.reflections.rtSingleBounceEnabled")) reflectionRtSingleBounceEnabled = true;
                if (!hasBackendOption(safe, "vulkan.reflections.rtMultiBounceEnabled")) reflectionRtMultiBounceEnabled = false;
                if (!hasBackendOption(safe, "vulkan.reflections.rtDenoiseStrength")) reflectionRtDenoiseStrength = 0.58;
            }
            case QUALITY -> {
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinDelta")) reflectionProbeChurnWarnMinDelta = 1;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinStreak")) reflectionProbeChurnWarnMinStreak = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnCooldownFrames")) reflectionProbeChurnWarnCooldownFrames = 90;
                if (!hasBackendOption(safe, "vulkan.reflections.probeQualityOverlapWarnMaxPairs")) reflectionProbeQualityOverlapWarnMaxPairs = 10;
                if (!hasBackendOption(safe, "vulkan.reflections.probeQualityBleedRiskWarnMaxPairs")) reflectionProbeQualityBleedRiskWarnMaxPairs = 1;
                if (!hasBackendOption(safe, "vulkan.reflections.probeQualityMinOverlapPairsWhenMultiple")) reflectionProbeQualityMinOverlapPairsWhenMultiple = 1;
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
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWindowFrames")) reflectionSsrTaaAdaptiveTrendWindowFrames = 120;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendHighRatioWarnMin")) reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = 0.45;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnMinFrames")) reflectionSsrTaaAdaptiveTrendWarnMinFrames = 3;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnCooldownFrames")) reflectionSsrTaaAdaptiveTrendWarnCooldownFrames = 120;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnMinSamples")) reflectionSsrTaaAdaptiveTrendWarnMinSamples = 24;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloMeanSeverityMax")) reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = 0.40;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloHighRatioMax")) reflectionSsrTaaAdaptiveTrendSloHighRatioMax = 0.30;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloMinSamples")) reflectionSsrTaaAdaptiveTrendSloMinSamples = 24;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaDisocclusionRejectDropEventsMin")) reflectionSsrTaaDisocclusionRejectDropEventsMin = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaDisocclusionRejectConfidenceMax")) reflectionSsrTaaDisocclusionRejectConfidenceMax = 0.62;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopePlaneDeltaWarnMax")) reflectionPlanarEnvelopePlaneDeltaWarnMax = 0.30;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopeCoverageRatioWarnMin")) reflectionPlanarEnvelopeCoverageRatioWarnMin = 0.20;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopeWarnMinFrames")) reflectionPlanarEnvelopeWarnMinFrames = 3;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopeWarnCooldownFrames")) reflectionPlanarEnvelopeWarnCooldownFrames = 120;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsLow")) reflectionPlanarPerfMaxGpuMsLow = 1.5;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsMedium")) reflectionPlanarPerfMaxGpuMsMedium = 2.4;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsHigh")) reflectionPlanarPerfMaxGpuMsHigh = 3.3;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsUltra")) reflectionPlanarPerfMaxGpuMsUltra = 4.5;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfDrawInflationWarnMax")) reflectionPlanarPerfDrawInflationWarnMax = 2.1;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMemoryBudgetMb")) reflectionPlanarPerfMemoryBudgetMb = 36.0;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfWarnMinFrames")) reflectionPlanarPerfWarnMinFrames = 3;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfWarnCooldownFrames")) reflectionPlanarPerfWarnCooldownFrames = 120;
                if (!hasBackendOption(safe, "vulkan.reflections.rtSingleBounceEnabled")) reflectionRtSingleBounceEnabled = true;
                if (!hasBackendOption(safe, "vulkan.reflections.rtMultiBounceEnabled")) reflectionRtMultiBounceEnabled = true;
                if (!hasBackendOption(safe, "vulkan.reflections.rtDenoiseStrength")) reflectionRtDenoiseStrength = 0.72;
            }
            case STABILITY -> {
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinDelta")) reflectionProbeChurnWarnMinDelta = 1;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnMinStreak")) reflectionProbeChurnWarnMinStreak = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.probeChurnWarnCooldownFrames")) reflectionProbeChurnWarnCooldownFrames = 60;
                if (!hasBackendOption(safe, "vulkan.reflections.probeQualityOverlapWarnMaxPairs")) reflectionProbeQualityOverlapWarnMaxPairs = 12;
                if (!hasBackendOption(safe, "vulkan.reflections.probeQualityBleedRiskWarnMaxPairs")) reflectionProbeQualityBleedRiskWarnMaxPairs = 1;
                if (!hasBackendOption(safe, "vulkan.reflections.probeQualityMinOverlapPairsWhenMultiple")) reflectionProbeQualityMinOverlapPairsWhenMultiple = 1;
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
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWindowFrames")) reflectionSsrTaaAdaptiveTrendWindowFrames = 180;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendHighRatioWarnMin")) reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = 0.30;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnMinFrames")) reflectionSsrTaaAdaptiveTrendWarnMinFrames = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnCooldownFrames")) reflectionSsrTaaAdaptiveTrendWarnCooldownFrames = 90;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnMinSamples")) reflectionSsrTaaAdaptiveTrendWarnMinSamples = 16;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloMeanSeverityMax")) reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = 0.65;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloHighRatioMax")) reflectionSsrTaaAdaptiveTrendSloHighRatioMax = 0.45;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloMinSamples")) reflectionSsrTaaAdaptiveTrendSloMinSamples = 16;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaDisocclusionRejectDropEventsMin")) reflectionSsrTaaDisocclusionRejectDropEventsMin = 1;
                if (!hasBackendOption(safe, "vulkan.reflections.ssrTaaDisocclusionRejectConfidenceMax")) reflectionSsrTaaDisocclusionRejectConfidenceMax = 0.70;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopePlaneDeltaWarnMax")) reflectionPlanarEnvelopePlaneDeltaWarnMax = 0.22;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopeCoverageRatioWarnMin")) reflectionPlanarEnvelopeCoverageRatioWarnMin = 0.15;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopeWarnMinFrames")) reflectionPlanarEnvelopeWarnMinFrames = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.planarEnvelopeWarnCooldownFrames")) reflectionPlanarEnvelopeWarnCooldownFrames = 90;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsLow")) reflectionPlanarPerfMaxGpuMsLow = 1.8;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsMedium")) reflectionPlanarPerfMaxGpuMsMedium = 2.8;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsHigh")) reflectionPlanarPerfMaxGpuMsHigh = 3.8;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMaxGpuMsUltra")) reflectionPlanarPerfMaxGpuMsUltra = 5.0;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfDrawInflationWarnMax")) reflectionPlanarPerfDrawInflationWarnMax = 2.4;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfMemoryBudgetMb")) reflectionPlanarPerfMemoryBudgetMb = 48.0;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfWarnMinFrames")) reflectionPlanarPerfWarnMinFrames = 2;
                if (!hasBackendOption(safe, "vulkan.reflections.planarPerfWarnCooldownFrames")) reflectionPlanarPerfWarnCooldownFrames = 90;
                if (!hasBackendOption(safe, "vulkan.reflections.rtSingleBounceEnabled")) reflectionRtSingleBounceEnabled = true;
                if (!hasBackendOption(safe, "vulkan.reflections.rtMultiBounceEnabled")) reflectionRtMultiBounceEnabled = false;
                if (!hasBackendOption(safe, "vulkan.reflections.rtDenoiseStrength")) reflectionRtDenoiseStrength = 0.80;
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
            float reflectionsPlanarStrength,
            float reflectionsPlanarPlaneHeight
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
