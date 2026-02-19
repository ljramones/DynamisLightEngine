package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
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
import org.dynamislight.api.runtime.AaPostCapabilityDiagnostics;
import org.dynamislight.api.runtime.ShadowCapabilityDiagnostics;
import org.dynamislight.api.runtime.ShadowCacheDiagnostics;
import org.dynamislight.api.runtime.ShadowCadenceDiagnostics;
import org.dynamislight.api.runtime.ShadowPointBudgetDiagnostics;
import org.dynamislight.api.runtime.ShadowRtDiagnostics;
import org.dynamislight.api.runtime.ShadowHybridDiagnostics;
import org.dynamislight.api.runtime.ShadowSpotProjectedDiagnostics;
import org.dynamislight.api.runtime.ShadowTransparentReceiverDiagnostics;
import org.dynamislight.api.runtime.ShadowExtendedModeDiagnostics;
import org.dynamislight.api.runtime.ShadowTopologyDiagnostics;
import org.dynamislight.api.runtime.ShadowPhaseAPromotionDiagnostics;
import org.dynamislight.api.runtime.ShadowPhaseDPromotionDiagnostics;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.AntiAliasingDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.ReflectionProbeDesc;
import org.dynamislight.api.scene.ReflectionOverrideMode;
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
import org.dynamislight.impl.vulkan.capability.VulkanShadowCapabilityPlanner;
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityMode;
import org.dynamislight.impl.vulkan.warning.aa.VulkanAaPostWarningEmitter;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.profile.FrameResourceProfile;
import org.dynamislight.impl.vulkan.profile.PostProcessPipelineProfile;
import org.dynamislight.impl.vulkan.profile.SceneReuseStats;
import org.dynamislight.impl.vulkan.profile.ShadowCascadeProfile;
import org.dynamislight.impl.vulkan.profile.VulkanFrameMetrics;
import org.dynamislight.impl.common.upscale.ExternalUpscalerBridge;
import org.dynamislight.impl.common.upscale.ExternalUpscalerIntegration;

public final class VulkanEngineRuntime extends AbstractEngineRuntime {
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
    private static final int REFLECTION_MODE_RT_DEDICATED_ACTIVE_BIT = 1 << 25;
    private static final int REFLECTION_MODE_RT_PROMOTION_READY_BIT = 1 << 26;
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
    private String aaPostAaModeLastFrame = "taa";
    private boolean aaPostAaEnabledLastFrame = true;
    private boolean aaPostTemporalHistoryActiveLastFrame = true;
    private List<String> aaPostActiveCapabilitiesLastFrame = List.of();
    private List<String> aaPostPrunedCapabilitiesLastFrame = List.of();
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
    private double shadowCadenceWarnDeferredRatioMax = 0.55;
    private int shadowCadenceWarnMinFrames = 3;
    private int shadowCadenceWarnCooldownFrames = 120;
    private int shadowCadencePromotionReadyMinFrames = 6;
    private double shadowPointFaceBudgetWarnSaturationMin = 1.0;
    private int shadowPointFaceBudgetWarnMinFrames = 3;
    private int shadowPointFaceBudgetWarnCooldownFrames = 120;
    private int shadowPointFaceBudgetPromotionReadyMinFrames = 6;
    private double shadowCacheChurnWarnMax = 0.35;
    private int shadowCacheMissWarnMax = 2;
    private int shadowCacheWarnMinFrames = 3;
    private int shadowCacheWarnCooldownFrames = 120;
    private double shadowRtDenoiseWarnMin = 0.45;
    private int shadowRtSampleWarnMin = 2;
    private double shadowRtPerfMaxGpuMsLow = 1.2;
    private double shadowRtPerfMaxGpuMsMedium = 2.0;
    private double shadowRtPerfMaxGpuMsHigh = 2.8;
    private double shadowRtPerfMaxGpuMsUltra = 3.6;
    private int shadowRtWarnMinFrames = 3;
    private int shadowRtWarnCooldownFrames = 120;
    private double shadowHybridRtShareWarnMin = 0.20;
    private double shadowHybridContactShareWarnMin = 0.10;
    private int shadowHybridWarnMinFrames = 3;
    private int shadowHybridWarnCooldownFrames = 120;
    private boolean shadowTransparentReceiversRequested;
    private final boolean shadowTransparentReceiversSupported = false;
    private double shadowTransparentReceiverCandidateRatioWarnMax = 0.20;
    private int shadowTransparentReceiverWarnMinFrames = 3;
    private int shadowTransparentReceiverWarnCooldownFrames = 120;
    private boolean shadowAreaApproxRequested;
    private boolean shadowAreaApproxRequireActive;
    private final boolean shadowAreaApproxSupported = false;
    private boolean shadowDistanceFieldRequested;
    private boolean shadowDistanceFieldRequireActive;
    private final boolean shadowDistanceFieldSupported = false;
    private int shadowSpotProjectedPromotionReadyMinFrames = 6;
    private double shadowTopologyLocalCoverageWarnMin = 0.60;
    private double shadowTopologySpotCoverageWarnMin = 0.60;
    private double shadowTopologyPointCoverageWarnMin = 0.50;
    private int shadowTopologyWarnMinFrames = 3;
    private int shadowTopologyWarnCooldownFrames = 120;
    private int shadowTopologyPromotionReadyMinFrames = 6;
    private int shadowPhaseAPromotionReadyMinFrames = 3;
    private int shadowPhaseDPromotionReadyMinFrames = 3;
    private boolean shadowDirectionalTexelSnapEnabled = true;
    private float shadowDirectionalTexelSnapScale = 1.0f;
    private long shadowSchedulerFrameTick;
    private List<LightDesc> currentSceneLights = List.of();
    private final Map<String, Long> shadowSchedulerLastRenderedTicks = new HashMap<>();
    private final Map<String, Integer> shadowLayerAllocatorAssignments = new HashMap<>();
    private int shadowAllocatorAssignedLights;
    private int shadowAllocatorReusedAssignments;
    private int shadowAllocatorEvictions;
    private int shadowCadenceSelectedLocalLightsLastFrame;
    private int shadowCadenceDeferredLocalLightsLastFrame;
    private int shadowCadenceStaleBypassCountLastFrame;
    private double shadowCadenceDeferredRatioLastFrame;
    private int shadowCadenceHighStreak;
    private int shadowCadenceWarnCooldownRemaining;
    private int shadowCadenceStableStreak;
    private boolean shadowCadencePromotionReadyLastFrame;
    private boolean shadowCadenceEnvelopeBreachedLastFrame;
    private int shadowPointBudgetRenderedCubemapsLastFrame;
    private int shadowPointBudgetRenderedFacesLastFrame;
    private int shadowPointBudgetDeferredCountLastFrame;
    private double shadowPointBudgetSaturationRatioLastFrame;
    private int shadowPointBudgetHighStreak;
    private int shadowPointBudgetWarnCooldownRemaining;
    private int shadowPointBudgetStableStreak;
    private boolean shadowPointBudgetPromotionReadyLastFrame;
    private boolean shadowPointBudgetEnvelopeBreachedLastFrame;
    private int shadowCacheHitCountLastFrame;
    private int shadowCacheMissCountLastFrame;
    private int shadowCacheEvictionCountLastFrame;
    private double shadowCacheHitRatioLastFrame;
    private double shadowCacheChurnRatioLastFrame;
    private String shadowCacheInvalidationReasonLastFrame = "unavailable";
    private int shadowCacheHighStreak;
    private int shadowCacheWarnCooldownRemaining;
    private boolean shadowCacheEnvelopeBreachedLastFrame;
    private double shadowRtPerfGpuMsEstimateLastFrame;
    private double shadowRtPerfGpuMsWarnMaxLastFrame;
    private int shadowRtHighStreak;
    private int shadowRtWarnCooldownRemaining;
    private boolean shadowRtEnvelopeBreachedLastFrame;
    private double shadowHybridCascadeShareLastFrame = 1.0;
    private double shadowHybridContactShareLastFrame;
    private double shadowHybridRtShareLastFrame;
    private int shadowHybridHighStreak;
    private int shadowHybridWarnCooldownRemaining;
    private boolean shadowHybridEnvelopeBreachedLastFrame;
    private int shadowTransparentReceiverCandidateCountLastFrame;
    private double shadowTransparentReceiverCandidateRatioLastFrame;
    private String shadowTransparentReceiverPolicyLastFrame = "unavailable";
    private int shadowTransparentReceiverHighStreak;
    private int shadowTransparentReceiverWarnCooldownRemaining;
    private boolean shadowTransparentReceiverEnvelopeBreachedLastFrame;
    private boolean shadowAreaApproxBreachedLastFrame;
    private boolean shadowDistanceFieldBreachedLastFrame;
    private int shadowTopologyCandidateSpotLightsLastFrame;
    private int shadowTopologyCandidatePointLightsLastFrame;
    private double shadowTopologyLocalCoverageLastFrame;
    private double shadowTopologySpotCoverageLastFrame;
    private double shadowTopologyPointCoverageLastFrame;
    private int shadowTopologyHighStreak;
    private int shadowTopologyWarnCooldownRemaining;
    private int shadowTopologyStableStreak;
    private boolean shadowTopologyPromotionReadyLastFrame;
    private boolean shadowTopologyEnvelopeBreachedLastFrame;
    private int shadowPhaseAPromotionStableStreak;
    private boolean shadowPhaseAPromotionReadyLastFrame;
    private int shadowPhaseDPromotionStableStreak;
    private boolean shadowPhaseDPromotionReadyLastFrame;
    private boolean shadowSpotProjectedRequestedLastFrame;
    private boolean shadowSpotProjectedActiveLastFrame;
    private int shadowSpotProjectedRenderedCountLastFrame;
    private String shadowSpotProjectedContractStatusLastFrame = "unavailable";
    private boolean shadowSpotProjectedContractBreachedLastFrame;
    private int shadowSpotProjectedStableStreak;
    private boolean shadowSpotProjectedPromotionReadyLastFrame;
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
    private double reflectionOverrideProbeOnlyRatioWarnMax = 0.75;
    private double reflectionOverrideSsrOnlyRatioWarnMax = 0.75;
    private int reflectionOverrideOtherWarnMax;
    private int reflectionOverrideWarnMinFrames = 3;
    private int reflectionOverrideWarnCooldownFrames = 120;
    private int reflectionOverrideHighStreak;
    private int reflectionOverrideWarnCooldownRemaining;
    private boolean reflectionOverrideBreachedLastFrame;
    private double reflectionContactHardeningMinSsrStrength = 0.35;
    private double reflectionContactHardeningMinSsrMaxRoughness = 0.55;
    private int reflectionContactHardeningWarnMinFrames = 3;
    private int reflectionContactHardeningWarnCooldownFrames = 120;
    private int reflectionContactHardeningHighStreak;
    private int reflectionContactHardeningWarnCooldownRemaining;
    private boolean reflectionContactHardeningBreachedLastFrame;
    private boolean reflectionContactHardeningActiveLastFrame;
    private double reflectionContactHardeningEstimatedStrengthLastFrame;
    private double reflectionProbeQualityBoxProjectionMinRatio = 0.60;
    private int reflectionProbeQualityInvalidBlendDistanceWarnMax;
    private double reflectionProbeQualityOverlapCoverageWarnMin = 0.12;
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
    private double lastFramePlanarCaptureGpuMs = Double.NaN;
    private String lastFrameGpuTimingSource = "frame_estimate";
    private long lastFrameDrawCalls = 1L;
    private double reflectionSsrTaaLatestRejectRate;
    private double reflectionSsrTaaLatestConfidenceMean = 1.0;
    private long reflectionSsrTaaLatestDropEvents;
    private boolean reflectionRtSingleBounceEnabled = true;
    private boolean reflectionRtMultiBounceEnabled;
    private boolean reflectionRtRequireActive;
    private boolean reflectionRtRequireMultiBounce;
    private boolean reflectionRtRequireDedicatedPipeline;
    private boolean reflectionRtDedicatedPipelineEnabled;
    private boolean reflectionRtDedicatedDenoisePipelineEnabled = true;
    private double reflectionRtDenoiseStrength = 0.65;
    private double reflectionRtPerfMaxGpuMsLow = 1.6;
    private double reflectionRtPerfMaxGpuMsMedium = 2.6;
    private double reflectionRtPerfMaxGpuMsHigh = 3.6;
    private double reflectionRtPerfMaxGpuMsUltra = 4.8;
    private int reflectionRtPerfWarnMinFrames = 3;
    private int reflectionRtPerfWarnCooldownFrames = 120;
    private double reflectionRtHybridProbeShareWarnMax = 0.70;
    private int reflectionRtHybridWarnMinFrames = 3;
    private int reflectionRtHybridWarnCooldownFrames = 120;
    private double reflectionRtDenoiseSpatialVarianceWarnMax = 0.45;
    private double reflectionRtDenoiseTemporalLagWarnMax = 0.35;
    private int reflectionRtDenoiseWarnMinFrames = 3;
    private int reflectionRtDenoiseWarnCooldownFrames = 120;
    private double reflectionRtAsBuildGpuMsWarnMax = 1.2;
    private double reflectionRtAsMemoryBudgetMb = 64.0;
    private boolean reflectionRtLaneRequested;
    private boolean reflectionRtLaneActive;
    private boolean reflectionRtRequireActiveUnmetLastFrame;
    private boolean reflectionRtRequireMultiBounceUnmetLastFrame;
    private boolean reflectionRtRequireDedicatedPipelineUnmetLastFrame;
    private String reflectionRtFallbackChainActive = "probe";
    private boolean reflectionRtTraversalSupported;
    private boolean reflectionRtDedicatedCapabilitySupported;
    private boolean reflectionRtDedicatedHardwarePipelineActive;
    private String reflectionRtBlasLifecycleState = "disabled";
    private String reflectionRtTlasLifecycleState = "disabled";
    private String reflectionRtSbtLifecycleState = "disabled";
    private int reflectionRtBlasObjectCount;
    private int reflectionRtTlasInstanceCount;
    private int reflectionRtSbtRecordCount;
    private double reflectionRtHybridRtShare;
    private double reflectionRtHybridSsrShare;
    private double reflectionRtHybridProbeShare = 1.0;
    private boolean reflectionRtHybridBreachedLastFrame;
    private int reflectionRtHybridHighStreak;
    private int reflectionRtHybridWarnCooldownRemaining;
    private double reflectionRtDenoiseSpatialVariance;
    private double reflectionRtDenoiseTemporalLag;
    private boolean reflectionRtDenoiseBreachedLastFrame;
    private int reflectionRtDenoiseHighStreak;
    private int reflectionRtDenoiseWarnCooldownRemaining;
    private double reflectionRtAsBuildGpuMsEstimate;
    private double reflectionRtAsMemoryMbEstimate;
    private boolean reflectionRtAsBudgetBreachedLastFrame;
    private int reflectionRtAsBudgetHighStreak;
    private int reflectionRtAsBudgetWarnCooldownRemaining;
    private boolean reflectionRtPromotionReadyLastFrame;
    private int reflectionRtPromotionReadyHighStreak;
    private int reflectionRtPromotionReadyMinFrames = 3;
    private int reflectionRtPerfHighStreak;
    private int reflectionRtPerfWarnCooldownRemaining;
    private double reflectionRtPerfLastGpuMsEstimate;
    private double reflectionRtPerfLastGpuMsCap;
    private boolean reflectionRtPerfBreachedLastFrame;
    private String reflectionPlanarPassOrderContractStatus = "inactive";
    private int reflectionPlanarScopedMeshEligibleCount;
    private int reflectionPlanarScopedMeshExcludedCount;
    private boolean reflectionPlanarMirrorCameraActive;
    private boolean reflectionPlanarDedicatedCaptureLaneActive;
    private int reflectionTransparentCandidateCount;
    private int reflectionTransparencyAlphaTestedCandidateCount;
    private int reflectionTransparencyReactiveCandidateCount;
    private int reflectionTransparencyProbeOnlyCandidateCount;
    private String reflectionTransparencyStageGateStatus = "not_required";
    private String reflectionTransparencyFallbackPath = "none";
    private double reflectionTransparencyCandidateReactiveMin = 0.35;
    private double reflectionTransparencyProbeOnlyRatioWarnMax = 0.65;
    private int reflectionTransparencyWarnMinFrames = 3;
    private int reflectionTransparencyWarnCooldownFrames = 120;
    private int reflectionTransparencyHighStreak;
    private int reflectionTransparencyWarnCooldownRemaining;
    private boolean reflectionTransparencyBreachedLastFrame;
    private int reflectionProbeUpdateCadenceFrames = 1;
    private int reflectionProbeMaxVisible = 64;
    private double reflectionProbeLodDepthScale = 1.0;
    private int reflectionProbeStreamingWarnMinFrames = 3;
    private int reflectionProbeStreamingWarnCooldownFrames = 120;
    private double reflectionProbeStreamingMissRatioWarnMax = 0.35;
    private double reflectionProbeStreamingDeferredRatioWarnMax = 0.55;
    private double reflectionProbeStreamingLodSkewWarnMax = 0.70;
    private double reflectionProbeStreamingMemoryBudgetMb = 48.0;
    private int reflectionProbeStreamingHighStreak;
    private int reflectionProbeStreamingWarnCooldownRemaining;
    private boolean reflectionProbeStreamingBreachedLastFrame;
    private ReflectionProbeQualityDiagnostics reflectionProbeQualityDiagnostics = ReflectionProbeQualityDiagnostics.zero();
    private String shadowCapabilityFeatureIdLastFrame = "unavailable";
    private String shadowCapabilityModeLastFrame = "unavailable";
    private List<String> shadowCapabilitySignalsLastFrame = List.of();
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
        shadowCadenceWarnDeferredRatioMax = options.shadowCadenceWarnDeferredRatioMax();
        shadowCadenceWarnMinFrames = options.shadowCadenceWarnMinFrames();
        shadowCadenceWarnCooldownFrames = options.shadowCadenceWarnCooldownFrames();
        shadowPointFaceBudgetWarnSaturationMin = options.shadowPointFaceBudgetWarnSaturationMin();
        shadowPointFaceBudgetWarnMinFrames = options.shadowPointFaceBudgetWarnMinFrames();
        shadowPointFaceBudgetWarnCooldownFrames = options.shadowPointFaceBudgetWarnCooldownFrames();
        shadowCacheChurnWarnMax = options.shadowCacheChurnWarnMax();
        shadowCacheMissWarnMax = options.shadowCacheMissWarnMax();
        shadowCacheWarnMinFrames = options.shadowCacheWarnMinFrames();
        shadowCacheWarnCooldownFrames = options.shadowCacheWarnCooldownFrames();
        shadowRtDenoiseWarnMin = options.shadowRtDenoiseWarnMin();
        shadowRtSampleWarnMin = options.shadowRtSampleWarnMin();
        shadowRtPerfMaxGpuMsLow = options.shadowRtPerfMaxGpuMsLow();
        shadowRtPerfMaxGpuMsMedium = options.shadowRtPerfMaxGpuMsMedium();
        shadowRtPerfMaxGpuMsHigh = options.shadowRtPerfMaxGpuMsHigh();
        shadowRtPerfMaxGpuMsUltra = options.shadowRtPerfMaxGpuMsUltra();
        shadowRtWarnMinFrames = options.shadowRtWarnMinFrames();
        shadowRtWarnCooldownFrames = options.shadowRtWarnCooldownFrames();
        shadowHybridRtShareWarnMin = options.shadowHybridRtShareWarnMin();
        shadowHybridContactShareWarnMin = options.shadowHybridContactShareWarnMin();
        shadowHybridWarnMinFrames = options.shadowHybridWarnMinFrames();
        shadowHybridWarnCooldownFrames = options.shadowHybridWarnCooldownFrames();
        shadowTransparentReceiversRequested = options.shadowTransparentReceiversEnabled();
        shadowTransparentReceiverCandidateRatioWarnMax = options.shadowTransparentReceiverCandidateRatioWarnMax();
        shadowTransparentReceiverWarnMinFrames = options.shadowTransparentReceiverWarnMinFrames();
        shadowTransparentReceiverWarnCooldownFrames = options.shadowTransparentReceiverWarnCooldownFrames();
        shadowAreaApproxRequested = options.shadowAreaApproxEnabled();
        shadowAreaApproxRequireActive = options.shadowAreaApproxRequireActive();
        shadowDistanceFieldRequested = options.shadowDistanceFieldSoftEnabled();
        shadowDistanceFieldRequireActive = options.shadowDistanceFieldRequireActive();
        shadowTopologyLocalCoverageWarnMin = options.shadowTopologyLocalCoverageWarnMin();
        shadowTopologySpotCoverageWarnMin = options.shadowTopologySpotCoverageWarnMin();
        shadowTopologyPointCoverageWarnMin = options.shadowTopologyPointCoverageWarnMin();
        shadowTopologyWarnMinFrames = options.shadowTopologyWarnMinFrames();
        shadowTopologyWarnCooldownFrames = options.shadowTopologyWarnCooldownFrames();
        shadowTopologyPromotionReadyMinFrames = options.shadowTopologyPromotionReadyMinFrames();
        shadowDirectionalTexelSnapEnabled = options.shadowDirectionalTexelSnapEnabled();
        shadowDirectionalTexelSnapScale = options.shadowDirectionalTexelSnapScale();
        reflectionProbeChurnWarnMinDelta = options.reflectionProbeChurnWarnMinDelta();
        reflectionProbeChurnWarnMinStreak = options.reflectionProbeChurnWarnMinStreak();
        reflectionProbeChurnWarnCooldownFrames = options.reflectionProbeChurnWarnCooldownFrames();
        reflectionProbeQualityOverlapWarnMaxPairs = options.reflectionProbeQualityOverlapWarnMaxPairs();
        reflectionProbeQualityBleedRiskWarnMaxPairs = options.reflectionProbeQualityBleedRiskWarnMaxPairs();
        reflectionProbeQualityMinOverlapPairsWhenMultiple = options.reflectionProbeQualityMinOverlapPairsWhenMultiple();
        reflectionOverrideProbeOnlyRatioWarnMax = options.reflectionOverrideProbeOnlyRatioWarnMax();
        reflectionOverrideSsrOnlyRatioWarnMax = options.reflectionOverrideSsrOnlyRatioWarnMax();
        reflectionOverrideOtherWarnMax = options.reflectionOverrideOtherWarnMax();
        reflectionOverrideWarnMinFrames = options.reflectionOverrideWarnMinFrames();
        reflectionOverrideWarnCooldownFrames = options.reflectionOverrideWarnCooldownFrames();
        reflectionContactHardeningMinSsrStrength = options.reflectionContactHardeningMinSsrStrength();
        reflectionContactHardeningMinSsrMaxRoughness = options.reflectionContactHardeningMinSsrMaxRoughness();
        reflectionContactHardeningWarnMinFrames = options.reflectionContactHardeningWarnMinFrames();
        reflectionContactHardeningWarnCooldownFrames = options.reflectionContactHardeningWarnCooldownFrames();
        reflectionProbeQualityBoxProjectionMinRatio = options.reflectionProbeQualityBoxProjectionMinRatio();
        reflectionProbeQualityInvalidBlendDistanceWarnMax = options.reflectionProbeQualityInvalidBlendDistanceWarnMax();
        reflectionProbeQualityOverlapCoverageWarnMin = options.reflectionProbeQualityOverlapCoverageWarnMin();
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
        reflectionRtRequireActive = options.reflectionRtRequireActive();
        reflectionRtRequireMultiBounce = options.reflectionRtRequireMultiBounce();
        reflectionRtRequireDedicatedPipeline = options.reflectionRtRequireDedicatedPipeline();
        reflectionRtDedicatedPipelineEnabled = options.reflectionRtDedicatedPipelineEnabled();
        reflectionRtDedicatedDenoisePipelineEnabled = options.reflectionRtDedicatedDenoisePipelineEnabled();
        reflectionRtDenoiseStrength = options.reflectionRtDenoiseStrength();
        reflectionRtPerfMaxGpuMsLow = options.reflectionRtPerfMaxGpuMsLow();
        reflectionRtPerfMaxGpuMsMedium = options.reflectionRtPerfMaxGpuMsMedium();
        reflectionRtPerfMaxGpuMsHigh = options.reflectionRtPerfMaxGpuMsHigh();
        reflectionRtPerfMaxGpuMsUltra = options.reflectionRtPerfMaxGpuMsUltra();
        reflectionRtPerfWarnMinFrames = options.reflectionRtPerfWarnMinFrames();
        reflectionRtPerfWarnCooldownFrames = options.reflectionRtPerfWarnCooldownFrames();
        reflectionRtHybridProbeShareWarnMax = options.reflectionRtHybridProbeShareWarnMax();
        reflectionRtHybridWarnMinFrames = options.reflectionRtHybridWarnMinFrames();
        reflectionRtHybridWarnCooldownFrames = options.reflectionRtHybridWarnCooldownFrames();
        reflectionRtDenoiseSpatialVarianceWarnMax = options.reflectionRtDenoiseSpatialVarianceWarnMax();
        reflectionRtDenoiseTemporalLagWarnMax = options.reflectionRtDenoiseTemporalLagWarnMax();
        reflectionRtDenoiseWarnMinFrames = options.reflectionRtDenoiseWarnMinFrames();
        reflectionRtDenoiseWarnCooldownFrames = options.reflectionRtDenoiseWarnCooldownFrames();
        reflectionRtAsBuildGpuMsWarnMax = options.reflectionRtAsBuildGpuMsWarnMax();
        reflectionRtAsMemoryBudgetMb = options.reflectionRtAsMemoryBudgetMb();
        reflectionTransparencyCandidateReactiveMin = options.reflectionTransparencyCandidateReactiveMin();
        reflectionTransparencyProbeOnlyRatioWarnMax = options.reflectionTransparencyProbeOnlyRatioWarnMax();
        reflectionTransparencyWarnMinFrames = options.reflectionTransparencyWarnMinFrames();
        reflectionTransparencyWarnCooldownFrames = options.reflectionTransparencyWarnCooldownFrames();
        reflectionProbeUpdateCadenceFrames = options.reflectionProbeUpdateCadenceFrames();
        reflectionProbeMaxVisible = options.reflectionProbeMaxVisible();
        reflectionProbeLodDepthScale = options.reflectionProbeLodDepthScale();
        reflectionProbeStreamingWarnMinFrames = options.reflectionProbeStreamingWarnMinFrames();
        reflectionProbeStreamingWarnCooldownFrames = options.reflectionProbeStreamingWarnCooldownFrames();
        reflectionProbeStreamingMissRatioWarnMax = options.reflectionProbeStreamingMissRatioWarnMax();
        reflectionProbeStreamingDeferredRatioWarnMax = options.reflectionProbeStreamingDeferredRatioWarnMax();
        reflectionProbeStreamingLodSkewWarnMax = options.reflectionProbeStreamingLodSkewWarnMax();
        reflectionProbeStreamingMemoryBudgetMb = options.reflectionProbeStreamingMemoryBudgetMb();
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
        reflectionProbeStreamingHighStreak = 0;
        reflectionProbeStreamingWarnCooldownRemaining = 0;
        reflectionProbeStreamingBreachedLastFrame = false;
        reflectionRtHybridHighStreak = 0;
        reflectionRtHybridWarnCooldownRemaining = 0;
        reflectionRtHybridBreachedLastFrame = false;
        reflectionRtDenoiseHighStreak = 0;
        reflectionRtDenoiseWarnCooldownRemaining = 0;
        reflectionRtDenoiseBreachedLastFrame = false;
        reflectionRtDenoiseSpatialVariance = 0.0;
        reflectionRtDenoiseTemporalLag = 0.0;
        reflectionRtAsBudgetHighStreak = 0;
        reflectionRtAsBudgetWarnCooldownRemaining = 0;
        reflectionRtAsBudgetBreachedLastFrame = false;
        reflectionRtAsBuildGpuMsEstimate = 0.0;
        reflectionRtAsMemoryMbEstimate = 0.0;
        reflectionRtPromotionReadyLastFrame = false;
        reflectionRtPromotionReadyHighStreak = 0;
        reflectionOverrideHighStreak = 0;
        reflectionOverrideWarnCooldownRemaining = 0;
        reflectionOverrideBreachedLastFrame = false;
        reflectionContactHardeningHighStreak = 0;
        reflectionContactHardeningWarnCooldownRemaining = 0;
        reflectionContactHardeningBreachedLastFrame = false;
        reflectionContactHardeningActiveLastFrame = false;
        reflectionContactHardeningEstimatedStrengthLastFrame = 0.0;
        reflectionTransparencyAlphaTestedCandidateCount = 0;
        reflectionTransparencyReactiveCandidateCount = 0;
        reflectionTransparencyProbeOnlyCandidateCount = 0;
        reflectionTransparencyHighStreak = 0;
        reflectionTransparencyWarnCooldownRemaining = 0;
        reflectionTransparencyBreachedLastFrame = false;
        lastFramePlanarCaptureGpuMs = Double.NaN;
        lastFrameGpuTimingSource = "frame_estimate";
        context.configureReflectionProbeStreaming(
                reflectionProbeUpdateCadenceFrames,
                reflectionProbeMaxVisible,
                (float) reflectionProbeLodDepthScale
        );
        context.setTaaDebugView(taaDebugView);
        Map<String, String> safeBackendOptions = config.backendOptions() == null ? Map.of() : config.backendOptions();
        taaLumaClipEnabledDefault = Boolean.parseBoolean(safeBackendOptions.getOrDefault("vulkan.taaLumaClip", "false"));
        aaPreset = VulkanRuntimeOptionParsing.parseAaPreset(safeBackendOptions.get("vulkan.aaPreset"));
        aaMode = VulkanRuntimeOptionParsing.parseAaMode(safeBackendOptions.get("vulkan.aaMode"));
        upscalerMode = VulkanRuntimeOptionParsing.parseUpscalerMode(safeBackendOptions.get("vulkan.upscalerMode"));
        upscalerQuality = VulkanRuntimeOptionParsing.parseUpscalerQuality(safeBackendOptions.get("vulkan.upscalerQuality"));
        reflectionProfile = VulkanRuntimeOptionParsing.parseReflectionProfile(safeBackendOptions.get("vulkan.reflectionsProfile"));
        shadowCadencePromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safeBackendOptions,
                "vulkan.shadow.cadencePromotionReadyMinFrames",
                shadowCadencePromotionReadyMinFrames,
                1,
                10_000
        );
        shadowPointFaceBudgetPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safeBackendOptions,
                "vulkan.shadow.pointFaceBudgetPromotionReadyMinFrames",
                shadowPointFaceBudgetPromotionReadyMinFrames,
                1,
                10_000
        );
        shadowSpotProjectedPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safeBackendOptions,
                "vulkan.shadow.spotProjectedPromotionReadyMinFrames",
                shadowSpotProjectedPromotionReadyMinFrames,
                1,
                10_000
        );
        shadowPhaseAPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safeBackendOptions,
                "vulkan.shadow.phaseAPromotionReadyMinFrames",
                shadowPhaseAPromotionReadyMinFrames,
                1,
                10_000
        );
        shadowPhaseDPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safeBackendOptions,
                "vulkan.shadow.phaseDPromotionReadyMinFrames",
                shadowPhaseDPromotionReadyMinFrames,
                1,
                10_000
        );
        applyShadowTelemetryProfileDefaults(safeBackendOptions, config.qualityTier());
        applyReflectionProfileTelemetryDefaults(safeBackendOptions);
        tsrControls = VulkanRuntimeOptionParsing.parseTsrControls(safeBackendOptions, "vulkan.");
        externalUpscaler = ExternalUpscalerIntegration.create("vulkan", "vulkan.", safeBackendOptions);
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
        aaMode = VulkanRuntimeOptionParsing.resolveAaMode(scene.postProcess(), aaMode);
        taaDebugView = VulkanRuntimeOptionParsing.resolveTaaDebugView(scene.postProcess(), taaDebugView);
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
        reflectionProbeQualityDiagnostics = VulkanReflectionAnalysis.analyzeReflectionProbeQuality(
                currentReflectionProbes,
                new VulkanReflectionAnalysis.ProbeQualityThresholds(
                        reflectionProbeQualityOverlapWarnMaxPairs,
                        reflectionProbeQualityBleedRiskWarnMaxPairs,
                        reflectionProbeQualityMinOverlapPairsWhenMultiple,
                        reflectionProbeQualityBoxProjectionMinRatio,
                        reflectionProbeQualityInvalidBlendDistanceWarnMax,
                        reflectionProbeQualityOverlapCoverageWarnMin
                )
        );
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
        VulkanShadowRuntimeTuning.updateShadowSchedulerTicks(shadowSchedulerLastRenderedTicks, shadowSchedulerFrameTick, currentShadows.renderedShadowLightIdsCsv());
        VulkanRuntimeLifecycle.applySceneToContext(context, sceneState);
        currentShadows = VulkanShadowContextBindings.applySceneLoadShadowBindings(
                mockContext,
                context,
                currentShadows,
                shadowPcssSoftness,
                shadowMomentBlend,
                shadowMomentBleedReduction,
                shadowContactStrength,
                shadowContactTemporalMotionScale,
                shadowContactTemporalMinStability,
                shadowRtDenoiseStrength,
                shadowRtProductionDenoiseStrength,
                shadowRtDedicatedDenoiseStrength,
                shadowRtRayLength,
                shadowRtProductionRayLength,
                shadowRtDedicatedRayLength,
                shadowRtSampleCount,
                shadowRtProductionSampleCount,
                shadowRtDedicatedSampleCount
        );
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
            VulkanShadowRuntimeTuning.updateShadowSchedulerTicks(shadowSchedulerLastRenderedTicks, shadowSchedulerFrameTick, currentShadows.renderedShadowLightIdsCsv());
            shadowLayerAllocatorAssignments.clear();
            shadowLayerAllocatorAssignments.putAll(refresh.lighting().shadowLayerAssignments());
            shadowAllocatorAssignedLights = refresh.lighting().shadowAllocatorAssignedLights();
            shadowAllocatorReusedAssignments = refresh.lighting().shadowAllocatorReusedAssignments();
            shadowAllocatorEvictions = refresh.lighting().shadowAllocatorEvictions();
            currentShadows = VulkanShadowRuntimeTuning.withRuntimeMomentPipelineState(currentShadows, context);
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
                    VulkanShadowRuntimeTuning.effectiveShadowRtDenoiseStrength(currentShadows.rtShadowMode(), shadowRtDenoiseStrength, shadowRtProductionDenoiseStrength, shadowRtDedicatedDenoiseStrength),
                    VulkanShadowRuntimeTuning.effectiveShadowRtRayLength(currentShadows.rtShadowMode(), shadowRtRayLength, shadowRtProductionRayLength, shadowRtDedicatedRayLength),
                    VulkanShadowRuntimeTuning.effectiveShadowRtSampleCount(currentShadows.rtShadowMode(), shadowRtSampleCount, shadowRtProductionSampleCount, shadowRtDedicatedSampleCount)
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
        lastFramePlanarCaptureGpuMs = frame.planarCaptureGpuMs();
        lastFrameGpuTimingSource = frame.gpuTimingSource() == null ? "frame_estimate" : frame.gpuTimingSource();
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
    protected AaPostCapabilityDiagnostics backendAaPostCapabilityDiagnostics() {
        return new AaPostCapabilityDiagnostics(
                !aaPostAaModeLastFrame.isBlank(),
                aaPostAaModeLastFrame,
                aaPostAaEnabledLastFrame,
                aaPostTemporalHistoryActiveLastFrame,
                aaPostActiveCapabilitiesLastFrame,
                aaPostPrunedCapabilitiesLastFrame
        );
    }

    @Override
    protected ShadowCapabilityDiagnostics backendShadowCapabilityDiagnostics() {
        return VulkanShadowDiagnosticsMapper.capability(
                shadowCapabilityFeatureIdLastFrame,
                shadowCapabilityModeLastFrame,
                shadowCapabilitySignalsLastFrame
        );
    }

    @Override
    protected ShadowCadenceDiagnostics backendShadowCadenceDiagnostics() {
        return VulkanShadowDiagnosticsMapper.cadence(
                shadowCapabilityDiagnostics().available(),
                shadowCadenceSelectedLocalLightsLastFrame,
                shadowCadenceDeferredLocalLightsLastFrame,
                shadowCadenceStaleBypassCountLastFrame,
                shadowCadenceDeferredRatioLastFrame,
                shadowCadenceWarnDeferredRatioMax,
                shadowCadenceWarnMinFrames,
                shadowCadenceWarnCooldownFrames,
                shadowCadenceHighStreak,
                shadowCadenceWarnCooldownRemaining,
                shadowCadenceStableStreak,
                shadowCadencePromotionReadyMinFrames,
                shadowCadencePromotionReadyLastFrame,
                shadowCadenceEnvelopeBreachedLastFrame
        );
    }

    @Override
    protected ShadowPointBudgetDiagnostics backendShadowPointBudgetDiagnostics() {
        return VulkanShadowDiagnosticsMapper.pointBudget(
                shadowCapabilityDiagnostics().available(),
                shadowMaxFacesPerFrame,
                shadowPointBudgetRenderedCubemapsLastFrame,
                shadowPointBudgetRenderedFacesLastFrame,
                shadowPointBudgetDeferredCountLastFrame,
                shadowPointBudgetSaturationRatioLastFrame,
                shadowPointFaceBudgetWarnSaturationMin,
                shadowPointFaceBudgetWarnMinFrames,
                shadowPointFaceBudgetWarnCooldownFrames,
                shadowPointBudgetHighStreak,
                shadowPointBudgetWarnCooldownRemaining,
                shadowPointBudgetStableStreak,
                shadowPointFaceBudgetPromotionReadyMinFrames,
                shadowPointBudgetPromotionReadyLastFrame,
                shadowPointBudgetEnvelopeBreachedLastFrame
        );
    }

    @Override
    protected ShadowSpotProjectedDiagnostics backendShadowSpotProjectedDiagnostics() {
        return VulkanShadowDiagnosticsMapper.spotProjected(
                shadowCapabilityDiagnostics().available(),
                shadowSpotProjectedRequestedLastFrame,
                shadowSpotProjectedActiveLastFrame,
                shadowSpotProjectedRenderedCountLastFrame,
                shadowSpotProjectedContractStatusLastFrame,
                shadowSpotProjectedContractBreachedLastFrame,
                shadowSpotProjectedStableStreak,
                shadowSpotProjectedPromotionReadyMinFrames,
                shadowSpotProjectedPromotionReadyLastFrame
        );
    }

    @Override
    protected ShadowCacheDiagnostics backendShadowCacheDiagnostics() {
        return VulkanShadowDiagnosticsMapper.cache(
                shadowCapabilityDiagnostics().available(),
                shadowCapabilityModeLastFrame,
                shadowCacheMissCountLastFrame,
                shadowCadenceDeferredLocalLightsLastFrame,
                shadowCacheHitCountLastFrame,
                shadowCacheEvictionCountLastFrame,
                shadowCacheHitRatioLastFrame,
                shadowCacheChurnRatioLastFrame,
                shadowCacheInvalidationReasonLastFrame,
                shadowCacheChurnWarnMax,
                shadowCacheMissWarnMax,
                shadowCacheWarnMinFrames,
                shadowCacheWarnCooldownFrames,
                shadowCacheHighStreak,
                shadowCacheWarnCooldownRemaining,
                shadowCacheEnvelopeBreachedLastFrame
        );
    }

    @Override
    protected ShadowRtDiagnostics backendShadowRtDiagnostics() {
        return VulkanShadowDiagnosticsMapper.rt(
                shadowCapabilityDiagnostics().available(),
                currentShadows.rtShadowMode(),
                currentShadows.rtShadowActive(),
                VulkanShadowRuntimeTuning.effectiveShadowRtDenoiseStrength(currentShadows.rtShadowMode(), shadowRtDenoiseStrength, shadowRtProductionDenoiseStrength, shadowRtDedicatedDenoiseStrength),
                shadowRtDenoiseWarnMin,
                VulkanShadowRuntimeTuning.effectiveShadowRtSampleCount(currentShadows.rtShadowMode(), shadowRtSampleCount, shadowRtProductionSampleCount, shadowRtDedicatedSampleCount),
                shadowRtSampleWarnMin,
                shadowRtPerfGpuMsEstimateLastFrame,
                shadowRtPerfGpuMsWarnMaxLastFrame,
                shadowRtWarnMinFrames,
                shadowRtWarnCooldownFrames,
                shadowRtHighStreak,
                shadowRtWarnCooldownRemaining,
                shadowRtEnvelopeBreachedLastFrame
        );
    }

    @Override
    protected ShadowHybridDiagnostics backendShadowHybridDiagnostics() {
        return VulkanShadowDiagnosticsMapper.hybrid(
                shadowCapabilityDiagnostics().available(),
                shadowCapabilityModeLastFrame,
                shadowHybridCascadeShareLastFrame,
                shadowHybridContactShareLastFrame,
                shadowHybridRtShareLastFrame,
                shadowHybridRtShareWarnMin,
                shadowHybridContactShareWarnMin,
                shadowHybridWarnMinFrames,
                shadowHybridWarnCooldownFrames,
                shadowHybridHighStreak,
                shadowHybridWarnCooldownRemaining,
                shadowHybridEnvelopeBreachedLastFrame
        );
    }

    @Override
    protected ShadowTransparentReceiverDiagnostics backendShadowTransparentReceiverDiagnostics() {
        return VulkanShadowDiagnosticsMapper.transparentReceivers(
                shadowCapabilityDiagnostics().available(),
                shadowTransparentReceiversRequested,
                shadowTransparentReceiversSupported,
                shadowTransparentReceiverPolicyLastFrame,
                shadowTransparentReceiverCandidateCountLastFrame,
                shadowTransparentReceiverCandidateRatioLastFrame,
                shadowTransparentReceiverCandidateRatioWarnMax,
                shadowTransparentReceiverWarnMinFrames,
                shadowTransparentReceiverWarnCooldownFrames,
                shadowTransparentReceiverHighStreak,
                shadowTransparentReceiverWarnCooldownRemaining,
                shadowTransparentReceiverEnvelopeBreachedLastFrame
        );
    }

    @Override
    protected ShadowExtendedModeDiagnostics backendShadowExtendedModeDiagnostics() {
        return VulkanShadowDiagnosticsMapper.extendedModes(
                shadowCapabilityDiagnostics().available(),
                shadowAreaApproxRequested,
                shadowAreaApproxSupported,
                shadowAreaApproxBreachedLastFrame,
                shadowDistanceFieldRequested,
                shadowDistanceFieldSupported,
                shadowDistanceFieldBreachedLastFrame
        );
    }

    @Override
    protected ShadowTopologyDiagnostics backendShadowTopologyDiagnostics() {
        return VulkanShadowDiagnosticsMapper.topology(
                shadowCapabilityDiagnostics().available(),
                shadowCadenceSelectedLocalLightsLastFrame,
                currentShadows.renderedLocalShadowLights(),
                shadowTopologyCandidateSpotLightsLastFrame,
                shadowSpotProjectedRenderedCountLastFrame,
                shadowTopologyCandidatePointLightsLastFrame,
                shadowPointBudgetRenderedCubemapsLastFrame,
                shadowTopologyLocalCoverageLastFrame,
                shadowTopologySpotCoverageLastFrame,
                shadowTopologyPointCoverageLastFrame,
                shadowTopologyLocalCoverageWarnMin,
                shadowTopologySpotCoverageWarnMin,
                shadowTopologyPointCoverageWarnMin,
                shadowTopologyWarnMinFrames,
                shadowTopologyWarnCooldownFrames,
                shadowTopologyHighStreak,
                shadowTopologyWarnCooldownRemaining,
                shadowTopologyStableStreak,
                shadowTopologyPromotionReadyMinFrames,
                shadowTopologyPromotionReadyLastFrame,
                shadowTopologyEnvelopeBreachedLastFrame
        );
    }

    @Override
    protected ShadowPhaseAPromotionDiagnostics backendShadowPhaseAPromotionDiagnostics() {
        return VulkanShadowDiagnosticsMapper.phaseA(
                shadowCapabilityDiagnostics().available(),
                shadowCadencePromotionReadyLastFrame,
                shadowPointBudgetPromotionReadyLastFrame,
                shadowSpotProjectedPromotionReadyLastFrame,
                shadowPhaseAPromotionReadyMinFrames,
                shadowPhaseAPromotionStableStreak,
                shadowPhaseAPromotionReadyLastFrame
        );
    }

    @Override
    protected ShadowPhaseDPromotionDiagnostics backendShadowPhaseDPromotionDiagnostics() {
        return VulkanShadowDiagnosticsMapper.phaseD(
                shadowCapabilityDiagnostics().available(),
                shadowCacheEnvelopeBreachedLastFrame,
                shadowCacheWarnCooldownRemaining,
                shadowRtEnvelopeBreachedLastFrame,
                shadowRtWarnCooldownRemaining,
                shadowHybridEnvelopeBreachedLastFrame,
                shadowHybridWarnCooldownRemaining,
                shadowTransparentReceiverEnvelopeBreachedLastFrame,
                shadowTransparentReceiverWarnCooldownRemaining,
                shadowAreaApproxBreachedLastFrame,
                shadowDistanceFieldBreachedLastFrame,
                shadowPhaseDPromotionReadyMinFrames,
                shadowPhaseDPromotionStableStreak,
                shadowPhaseDPromotionReadyLastFrame
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
        VulkanAaCapabilityMode aaCapabilityMode = switch (aaMode) {
            case TAA -> VulkanAaCapabilityMode.TAA;
            case TSR -> VulkanAaCapabilityMode.TSR;
            case TUUA -> VulkanAaCapabilityMode.TUUA;
            case MSAA_SELECTIVE -> VulkanAaCapabilityMode.MSAA_SELECTIVE;
            case HYBRID_TUUA_MSAA -> VulkanAaCapabilityMode.HYBRID_TUUA_MSAA;
            case FXAA_LOW -> VulkanAaCapabilityMode.FXAA_LOW;
            case DLAA -> VulkanAaCapabilityMode.DLAA;
        };
        VulkanAaPostWarningEmitter.Result aaPostEmission = VulkanAaPostWarningEmitter.emit(
                qualityTier,
                aaCapabilityMode,
                currentPost.taaEnabled(),
                currentPost.smaaEnabled(),
                currentPost.tonemapEnabled(),
                currentPost.bloomEnabled(),
                currentPost.ssaoEnabled(),
                currentFog.enabled()
        );
        aaPostAaModeLastFrame = aaPostEmission.aaModeId();
        aaPostAaEnabledLastFrame = true;
        aaPostTemporalHistoryActiveLastFrame = aaPostEmission.temporalHistoryActive();
        aaPostActiveCapabilitiesLastFrame = aaPostEmission.activeCapabilities();
        aaPostPrunedCapabilitiesLastFrame = aaPostEmission.prunedCapabilities();
        warnings.add(aaPostEmission.warning());
        if (currentPost.reflectionsEnabled()) {
            int reflectionBaseMode = currentPost.reflectionsMode() & REFLECTION_MODE_BASE_MASK;
            ReflectionOverrideSummary overrideSummary = VulkanReflectionAnalysis.summarizeReflectionOverrides(context.debugGpuMeshReflectionOverrideModes());
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
            VulkanReflectionCoreWarningEmitter.State coreState = new VulkanReflectionCoreWarningEmitter.State();
            coreState.reflectionBaseMode = reflectionBaseMode;
            coreState.reflectionContactHardeningActiveLastFrame = reflectionContactHardeningActiveLastFrame;
            coreState.reflectionContactHardeningBreachedLastFrame = reflectionContactHardeningBreachedLastFrame;
            coreState.reflectionContactHardeningEstimatedStrengthLastFrame = reflectionContactHardeningEstimatedStrengthLastFrame;
            coreState.reflectionContactHardeningHighStreak = reflectionContactHardeningHighStreak;
            coreState.reflectionContactHardeningMinSsrMaxRoughness = reflectionContactHardeningMinSsrMaxRoughness;
            coreState.reflectionContactHardeningMinSsrStrength = reflectionContactHardeningMinSsrStrength;
            coreState.reflectionContactHardeningWarnCooldownFrames = reflectionContactHardeningWarnCooldownFrames;
            coreState.reflectionContactHardeningWarnCooldownRemaining = reflectionContactHardeningWarnCooldownRemaining;
            coreState.reflectionContactHardeningWarnMinFrames = reflectionContactHardeningWarnMinFrames;
            coreState.reflectionOverrideBreachedLastFrame = reflectionOverrideBreachedLastFrame;
            coreState.reflectionOverrideHighStreak = reflectionOverrideHighStreak;
            coreState.reflectionOverrideOtherWarnMax = reflectionOverrideOtherWarnMax;
            coreState.reflectionOverrideProbeOnlyRatioWarnMax = reflectionOverrideProbeOnlyRatioWarnMax;
            coreState.reflectionOverrideSsrOnlyRatioWarnMax = reflectionOverrideSsrOnlyRatioWarnMax;
            coreState.reflectionOverrideWarnCooldownFrames = reflectionOverrideWarnCooldownFrames;
            coreState.reflectionOverrideWarnCooldownRemaining = reflectionOverrideWarnCooldownRemaining;
            coreState.reflectionOverrideWarnMinFrames = reflectionOverrideWarnMinFrames;
            coreState.reflectionProbeChurnWarnCooldownFrames = reflectionProbeChurnWarnCooldownFrames;
            coreState.reflectionProbeChurnWarnMinDelta = reflectionProbeChurnWarnMinDelta;
            coreState.reflectionProbeChurnWarnMinStreak = reflectionProbeChurnWarnMinStreak;
            coreState.reflectionProbeLodDepthScale = reflectionProbeLodDepthScale;
            coreState.reflectionProbeMaxVisible = reflectionProbeMaxVisible;
            coreState.reflectionProbeQualityBleedRiskWarnMaxPairs = reflectionProbeQualityBleedRiskWarnMaxPairs;
            coreState.reflectionProbeQualityBoxProjectionMinRatio = reflectionProbeQualityBoxProjectionMinRatio;
            coreState.reflectionProbeQualityDiagnostics = reflectionProbeQualityDiagnostics;
            coreState.reflectionProbeQualityInvalidBlendDistanceWarnMax = reflectionProbeQualityInvalidBlendDistanceWarnMax;
            coreState.reflectionProbeQualityMinOverlapPairsWhenMultiple = reflectionProbeQualityMinOverlapPairsWhenMultiple;
            coreState.reflectionProbeQualityOverlapCoverageWarnMin = reflectionProbeQualityOverlapCoverageWarnMin;
            coreState.reflectionProbeQualityOverlapWarnMaxPairs = reflectionProbeQualityOverlapWarnMaxPairs;
            coreState.reflectionProbeStreamingBreachedLastFrame = reflectionProbeStreamingBreachedLastFrame;
            coreState.reflectionProbeStreamingDeferredRatioWarnMax = reflectionProbeStreamingDeferredRatioWarnMax;
            coreState.reflectionProbeStreamingHighStreak = reflectionProbeStreamingHighStreak;
            coreState.reflectionProbeStreamingLodSkewWarnMax = reflectionProbeStreamingLodSkewWarnMax;
            coreState.reflectionProbeStreamingMemoryBudgetMb = reflectionProbeStreamingMemoryBudgetMb;
            coreState.reflectionProbeStreamingMissRatioWarnMax = reflectionProbeStreamingMissRatioWarnMax;
            coreState.reflectionProbeStreamingWarnCooldownFrames = reflectionProbeStreamingWarnCooldownFrames;
            coreState.reflectionProbeStreamingWarnCooldownRemaining = reflectionProbeStreamingWarnCooldownRemaining;
            coreState.reflectionProbeStreamingWarnMinFrames = reflectionProbeStreamingWarnMinFrames;
            coreState.reflectionProbeUpdateCadenceFrames = reflectionProbeUpdateCadenceFrames;
            coreState.reflectionsSsrMaxRoughness = currentPost.reflectionsSsrMaxRoughness();
            coreState.reflectionsSsrStrength = currentPost.reflectionsSsrStrength();
            VulkanReflectionCoreWarningEmitter.emit(
                    warnings,
                    coreState,
                    overrideSummary,
                    churnDiagnostics,
                    probeDiagnostics
            );
            reflectionContactHardeningActiveLastFrame = coreState.reflectionContactHardeningActiveLastFrame;
            reflectionContactHardeningEstimatedStrengthLastFrame = coreState.reflectionContactHardeningEstimatedStrengthLastFrame;
            reflectionContactHardeningHighStreak = coreState.reflectionContactHardeningHighStreak;
            reflectionContactHardeningWarnCooldownRemaining = coreState.reflectionContactHardeningWarnCooldownRemaining;
            reflectionContactHardeningBreachedLastFrame = coreState.reflectionContactHardeningBreachedLastFrame;
            reflectionOverrideHighStreak = coreState.reflectionOverrideHighStreak;
            reflectionOverrideWarnCooldownRemaining = coreState.reflectionOverrideWarnCooldownRemaining;
            reflectionOverrideBreachedLastFrame = coreState.reflectionOverrideBreachedLastFrame;
            reflectionProbeStreamingHighStreak = coreState.reflectionProbeStreamingHighStreak;
            reflectionProbeStreamingWarnCooldownRemaining = coreState.reflectionProbeStreamingWarnCooldownRemaining;
            reflectionProbeStreamingBreachedLastFrame = coreState.reflectionProbeStreamingBreachedLastFrame;
            int planarEligible = countPlanarEligibleFromOverrideSummary(overrideSummary);
            int planarExcluded = planarScopeExclusionCountFromOverrideSummary(overrideSummary);
            VulkanReflectionPlanarWarningEmitter.State planarState = new VulkanReflectionPlanarWarningEmitter.State();
            planarState.reflectionBaseMode = reflectionBaseMode;
            planarState.reflectionPlanarScopeIncludeAuto = reflectionPlanarScopeIncludeAuto;
            planarState.reflectionPlanarScopeIncludeProbeOnly = reflectionPlanarScopeIncludeProbeOnly;
            planarState.reflectionPlanarScopeIncludeSsrOnly = reflectionPlanarScopeIncludeSsrOnly;
            planarState.reflectionPlanarScopeIncludeOther = reflectionPlanarScopeIncludeOther;
            planarState.reflectionPlanarScopedMeshEligibleCount = reflectionPlanarScopedMeshEligibleCount;
            planarState.reflectionPlanarScopedMeshExcludedCount = reflectionPlanarScopedMeshExcludedCount;
            planarState.reflectionPlanarPassOrderContractStatus = reflectionPlanarPassOrderContractStatus;
            planarState.reflectionPlanarMirrorCameraActive = reflectionPlanarMirrorCameraActive;
            planarState.reflectionPlanarDedicatedCaptureLaneActive = reflectionPlanarDedicatedCaptureLaneActive;
            planarState.reflectionPlanarLatestCoverageRatio = reflectionPlanarLatestCoverageRatio;
            planarState.reflectionPlanarPrevPlaneHeight = reflectionPlanarPrevPlaneHeight;
            planarState.reflectionPlanarLatestPlaneDelta = reflectionPlanarLatestPlaneDelta;
            planarState.reflectionPlanarEnvelopePlaneDeltaWarnMax = reflectionPlanarEnvelopePlaneDeltaWarnMax;
            planarState.reflectionPlanarEnvelopeCoverageRatioWarnMin = reflectionPlanarEnvelopeCoverageRatioWarnMin;
            planarState.reflectionPlanarEnvelopeHighStreak = reflectionPlanarEnvelopeHighStreak;
            planarState.reflectionPlanarEnvelopeWarnMinFrames = reflectionPlanarEnvelopeWarnMinFrames;
            planarState.reflectionPlanarEnvelopeWarnCooldownFrames = reflectionPlanarEnvelopeWarnCooldownFrames;
            planarState.reflectionPlanarEnvelopeWarnCooldownRemaining = reflectionPlanarEnvelopeWarnCooldownRemaining;
            planarState.reflectionPlanarEnvelopeBreachedLastFrame = reflectionPlanarEnvelopeBreachedLastFrame;
            planarState.reflectionPlanarPerfRequireGpuTimestamp = reflectionPlanarPerfRequireGpuTimestamp;
            planarState.lastFramePlanarCaptureGpuMs = lastFramePlanarCaptureGpuMs;
            planarState.lastFrameGpuMs = lastFrameGpuMs;
            planarState.lastFrameGpuTimingSource = lastFrameGpuTimingSource;
            planarState.viewportWidth = viewportWidth;
            planarState.viewportHeight = viewportHeight;
            planarState.reflectionPlanarPerfMemoryBudgetMb = reflectionPlanarPerfMemoryBudgetMb;
            planarState.reflectionPlanarPerfDrawInflationWarnMax = reflectionPlanarPerfDrawInflationWarnMax;
            planarState.reflectionPlanarPerfHighStreak = reflectionPlanarPerfHighStreak;
            planarState.reflectionPlanarPerfWarnMinFrames = reflectionPlanarPerfWarnMinFrames;
            planarState.reflectionPlanarPerfWarnCooldownFrames = reflectionPlanarPerfWarnCooldownFrames;
            planarState.reflectionPlanarPerfWarnCooldownRemaining = reflectionPlanarPerfWarnCooldownRemaining;
            planarState.reflectionPlanarPerfBreachedLastFrame = reflectionPlanarPerfBreachedLastFrame;
            planarState.reflectionPlanarPerfLastGpuMsEstimate = reflectionPlanarPerfLastGpuMsEstimate;
            planarState.reflectionPlanarPerfLastGpuMsCap = reflectionPlanarPerfLastGpuMsCap;
            planarState.reflectionPlanarPerfLastDrawInflation = reflectionPlanarPerfLastDrawInflation;
            planarState.reflectionPlanarPerfLastMemoryBytes = reflectionPlanarPerfLastMemoryBytes;
            planarState.reflectionPlanarPerfLastMemoryBudgetBytes = reflectionPlanarPerfLastMemoryBudgetBytes;
            planarState.reflectionPlanarPerfLastTimingSource = reflectionPlanarPerfLastTimingSource;
            planarState.reflectionPlanarPerfLastTimestampAvailable = reflectionPlanarPerfLastTimestampAvailable;
            planarState.reflectionPlanarPerfLastTimestampRequirementUnmet = reflectionPlanarPerfLastTimestampRequirementUnmet;
            VulkanReflectionPlanarWarningEmitter.emit(
                    warnings,
                    planarState,
                    planarEligible,
                    planarExcluded,
                    currentPost.reflectionsPlanarPlaneHeight(),
                    planarPerfGpuMsCapForTier(qualityTier)
            );
            reflectionPlanarScopedMeshEligibleCount = planarState.reflectionPlanarScopedMeshEligibleCount;
            reflectionPlanarScopedMeshExcludedCount = planarState.reflectionPlanarScopedMeshExcludedCount;
            reflectionPlanarPassOrderContractStatus = planarState.reflectionPlanarPassOrderContractStatus;
            reflectionPlanarMirrorCameraActive = planarState.reflectionPlanarMirrorCameraActive;
            reflectionPlanarDedicatedCaptureLaneActive = planarState.reflectionPlanarDedicatedCaptureLaneActive;
            reflectionPlanarLatestCoverageRatio = planarState.reflectionPlanarLatestCoverageRatio;
            reflectionPlanarPrevPlaneHeight = planarState.reflectionPlanarPrevPlaneHeight;
            reflectionPlanarLatestPlaneDelta = planarState.reflectionPlanarLatestPlaneDelta;
            reflectionPlanarEnvelopeHighStreak = planarState.reflectionPlanarEnvelopeHighStreak;
            reflectionPlanarEnvelopeWarnCooldownRemaining = planarState.reflectionPlanarEnvelopeWarnCooldownRemaining;
            reflectionPlanarEnvelopeBreachedLastFrame = planarState.reflectionPlanarEnvelopeBreachedLastFrame;
            reflectionPlanarPerfHighStreak = planarState.reflectionPlanarPerfHighStreak;
            reflectionPlanarPerfWarnCooldownRemaining = planarState.reflectionPlanarPerfWarnCooldownRemaining;
            reflectionPlanarPerfBreachedLastFrame = planarState.reflectionPlanarPerfBreachedLastFrame;
            reflectionPlanarPerfLastGpuMsEstimate = planarState.reflectionPlanarPerfLastGpuMsEstimate;
            reflectionPlanarPerfLastGpuMsCap = planarState.reflectionPlanarPerfLastGpuMsCap;
            reflectionPlanarPerfLastDrawInflation = planarState.reflectionPlanarPerfLastDrawInflation;
            reflectionPlanarPerfLastMemoryBytes = planarState.reflectionPlanarPerfLastMemoryBytes;
            reflectionPlanarPerfLastMemoryBudgetBytes = planarState.reflectionPlanarPerfLastMemoryBudgetBytes;
            reflectionPlanarPerfLastTimingSource = planarState.reflectionPlanarPerfLastTimingSource;
            reflectionPlanarPerfLastTimestampAvailable = planarState.reflectionPlanarPerfLastTimestampAvailable;
            reflectionPlanarPerfLastTimestampRequirementUnmet = planarState.reflectionPlanarPerfLastTimestampRequirementUnmet;
            refreshReflectionRtPathState(reflectionBaseMode);
            VulkanReflectionRtWarningEmitter.State rtWarningState = new VulkanReflectionRtWarningEmitter.State();
            rtWarningState.reflectionRtLaneActive = reflectionRtLaneActive;
            rtWarningState.reflectionRtMultiBounceEnabled = reflectionRtMultiBounceEnabled;
            rtWarningState.reflectionRtSingleBounceEnabled = reflectionRtSingleBounceEnabled;
            rtWarningState.reflectionRtRequireActive = reflectionRtRequireActive;
            rtWarningState.reflectionRtRequireMultiBounce = reflectionRtRequireMultiBounce;
            rtWarningState.reflectionRtRequireDedicatedPipeline = reflectionRtRequireDedicatedPipeline;
            rtWarningState.reflectionRtDedicatedPipelineEnabled = reflectionRtDedicatedPipelineEnabled;
            rtWarningState.reflectionRtTraversalSupported = reflectionRtTraversalSupported;
            rtWarningState.reflectionRtDedicatedCapabilitySupported = reflectionRtDedicatedCapabilitySupported;
            rtWarningState.reflectionRtDedicatedDenoisePipelineEnabled = reflectionRtDedicatedDenoisePipelineEnabled;
            rtWarningState.reflectionRtDenoiseStrength = reflectionRtDenoiseStrength;
            rtWarningState.reflectionRtFallbackChainActive = reflectionRtFallbackChainActive;
            rtWarningState.reflectionRtRequireActiveUnmetLastFrame = reflectionRtRequireActiveUnmetLastFrame;
            rtWarningState.reflectionRtRequireMultiBounceUnmetLastFrame = reflectionRtRequireMultiBounceUnmetLastFrame;
            rtWarningState.reflectionRtRequireDedicatedPipelineUnmetLastFrame = reflectionRtRequireDedicatedPipelineUnmetLastFrame;
            rtWarningState.reflectionRtDedicatedHardwarePipelineActive = reflectionRtDedicatedHardwarePipelineActive;
            rtWarningState.reflectionRtBlasLifecycleState = reflectionRtBlasLifecycleState;
            rtWarningState.reflectionRtTlasLifecycleState = reflectionRtTlasLifecycleState;
            rtWarningState.reflectionRtSbtLifecycleState = reflectionRtSbtLifecycleState;
            rtWarningState.reflectionRtBlasObjectCount = reflectionRtBlasObjectCount;
            rtWarningState.reflectionRtTlasInstanceCount = reflectionRtTlasInstanceCount;
            rtWarningState.reflectionRtSbtRecordCount = reflectionRtSbtRecordCount;
            rtWarningState.mockContext = mockContext;
            rtWarningState.reflectionRtPerfHighStreak = reflectionRtPerfHighStreak;
            rtWarningState.reflectionRtPerfWarnCooldownRemaining = reflectionRtPerfWarnCooldownRemaining;
            rtWarningState.reflectionRtPerfWarnMinFrames = reflectionRtPerfWarnMinFrames;
            rtWarningState.reflectionRtPerfWarnCooldownFrames = reflectionRtPerfWarnCooldownFrames;
            rtWarningState.reflectionRtPerfBreachedLastFrame = reflectionRtPerfBreachedLastFrame;
            rtWarningState.reflectionRtPerfLastGpuMsEstimate = reflectionRtPerfLastGpuMsEstimate;
            rtWarningState.reflectionRtPerfLastGpuMsCap = reflectionRtPerfLastGpuMsCap;
            rtWarningState.reflectionRtHybridRtShare = reflectionRtHybridRtShare;
            rtWarningState.reflectionRtHybridSsrShare = reflectionRtHybridSsrShare;
            rtWarningState.reflectionRtHybridProbeShare = reflectionRtHybridProbeShare;
            rtWarningState.reflectionRtHybridProbeShareWarnMax = reflectionRtHybridProbeShareWarnMax;
            rtWarningState.reflectionRtHybridHighStreak = reflectionRtHybridHighStreak;
            rtWarningState.reflectionRtHybridWarnCooldownRemaining = reflectionRtHybridWarnCooldownRemaining;
            rtWarningState.reflectionRtHybridWarnMinFrames = reflectionRtHybridWarnMinFrames;
            rtWarningState.reflectionRtHybridWarnCooldownFrames = reflectionRtHybridWarnCooldownFrames;
            rtWarningState.reflectionRtHybridBreachedLastFrame = reflectionRtHybridBreachedLastFrame;
            rtWarningState.reflectionRtDenoiseSpatialVariance = reflectionRtDenoiseSpatialVariance;
            rtWarningState.reflectionRtDenoiseTemporalLag = reflectionRtDenoiseTemporalLag;
            rtWarningState.reflectionRtDenoiseSpatialVarianceWarnMax = reflectionRtDenoiseSpatialVarianceWarnMax;
            rtWarningState.reflectionRtDenoiseTemporalLagWarnMax = reflectionRtDenoiseTemporalLagWarnMax;
            rtWarningState.reflectionRtDenoiseHighStreak = reflectionRtDenoiseHighStreak;
            rtWarningState.reflectionRtDenoiseWarnCooldownRemaining = reflectionRtDenoiseWarnCooldownRemaining;
            rtWarningState.reflectionRtDenoiseWarnMinFrames = reflectionRtDenoiseWarnMinFrames;
            rtWarningState.reflectionRtDenoiseWarnCooldownFrames = reflectionRtDenoiseWarnCooldownFrames;
            rtWarningState.reflectionRtDenoiseBreachedLastFrame = reflectionRtDenoiseBreachedLastFrame;
            rtWarningState.reflectionRtAsBuildGpuMsEstimate = reflectionRtAsBuildGpuMsEstimate;
            rtWarningState.reflectionRtAsMemoryMbEstimate = reflectionRtAsMemoryMbEstimate;
            rtWarningState.reflectionRtAsBuildGpuMsWarnMax = reflectionRtAsBuildGpuMsWarnMax;
            rtWarningState.reflectionRtAsMemoryBudgetMb = reflectionRtAsMemoryBudgetMb;
            rtWarningState.reflectionRtAsBudgetHighStreak = reflectionRtAsBudgetHighStreak;
            rtWarningState.reflectionRtAsBudgetWarnCooldownRemaining = reflectionRtAsBudgetWarnCooldownRemaining;
            rtWarningState.reflectionRtAsBudgetBreachedLastFrame = reflectionRtAsBudgetBreachedLastFrame;
            rtWarningState.reflectionRtPromotionReadyLastFrame = reflectionRtPromotionReadyLastFrame;
            rtWarningState.reflectionRtPromotionReadyHighStreak = reflectionRtPromotionReadyHighStreak;
            VulkanReflectionRtWarningEmitter.emit(
                    warnings,
                    rtWarningState,
                    reflectionRtLaneRequested,
                    reflectionBaseMode,
                    lastFrameGpuMs,
                    plannedVisibleObjects,
                    currentPost.reflectionsSsrStrength(),
                    currentPost.reflectionsSsrMaxRoughness(),
                    currentPost.reflectionsTemporalWeight(),
                    rtPerfGpuMsCapForTier(qualityTier)
            );
            reflectionRtFallbackChainActive = rtWarningState.reflectionRtFallbackChainActive;
            reflectionRtRequireActiveUnmetLastFrame = rtWarningState.reflectionRtRequireActiveUnmetLastFrame;
            reflectionRtRequireMultiBounceUnmetLastFrame = rtWarningState.reflectionRtRequireMultiBounceUnmetLastFrame;
            reflectionRtRequireDedicatedPipelineUnmetLastFrame = rtWarningState.reflectionRtRequireDedicatedPipelineUnmetLastFrame;
            reflectionRtTraversalSupported = rtWarningState.reflectionRtTraversalSupported;
            reflectionRtDedicatedCapabilitySupported = rtWarningState.reflectionRtDedicatedCapabilitySupported;
            reflectionRtDedicatedHardwarePipelineActive = rtWarningState.reflectionRtDedicatedHardwarePipelineActive;
            reflectionRtBlasLifecycleState = rtWarningState.reflectionRtBlasLifecycleState;
            reflectionRtTlasLifecycleState = rtWarningState.reflectionRtTlasLifecycleState;
            reflectionRtSbtLifecycleState = rtWarningState.reflectionRtSbtLifecycleState;
            reflectionRtBlasObjectCount = rtWarningState.reflectionRtBlasObjectCount;
            reflectionRtTlasInstanceCount = rtWarningState.reflectionRtTlasInstanceCount;
            reflectionRtSbtRecordCount = rtWarningState.reflectionRtSbtRecordCount;
            reflectionRtPerfHighStreak = rtWarningState.reflectionRtPerfHighStreak;
            reflectionRtPerfWarnCooldownRemaining = rtWarningState.reflectionRtPerfWarnCooldownRemaining;
            reflectionRtPerfBreachedLastFrame = rtWarningState.reflectionRtPerfBreachedLastFrame;
            reflectionRtPerfLastGpuMsEstimate = rtWarningState.reflectionRtPerfLastGpuMsEstimate;
            reflectionRtPerfLastGpuMsCap = rtWarningState.reflectionRtPerfLastGpuMsCap;
            reflectionRtHybridRtShare = rtWarningState.reflectionRtHybridRtShare;
            reflectionRtHybridSsrShare = rtWarningState.reflectionRtHybridSsrShare;
            reflectionRtHybridProbeShare = rtWarningState.reflectionRtHybridProbeShare;
            reflectionRtHybridHighStreak = rtWarningState.reflectionRtHybridHighStreak;
            reflectionRtHybridWarnCooldownRemaining = rtWarningState.reflectionRtHybridWarnCooldownRemaining;
            reflectionRtHybridBreachedLastFrame = rtWarningState.reflectionRtHybridBreachedLastFrame;
            reflectionRtDenoiseSpatialVariance = rtWarningState.reflectionRtDenoiseSpatialVariance;
            reflectionRtDenoiseTemporalLag = rtWarningState.reflectionRtDenoiseTemporalLag;
            reflectionRtDenoiseHighStreak = rtWarningState.reflectionRtDenoiseHighStreak;
            reflectionRtDenoiseWarnCooldownRemaining = rtWarningState.reflectionRtDenoiseWarnCooldownRemaining;
            reflectionRtDenoiseBreachedLastFrame = rtWarningState.reflectionRtDenoiseBreachedLastFrame;
            reflectionRtAsBuildGpuMsEstimate = rtWarningState.reflectionRtAsBuildGpuMsEstimate;
            reflectionRtAsMemoryMbEstimate = rtWarningState.reflectionRtAsMemoryMbEstimate;
            reflectionRtAsBudgetHighStreak = rtWarningState.reflectionRtAsBudgetHighStreak;
            reflectionRtAsBudgetWarnCooldownRemaining = rtWarningState.reflectionRtAsBudgetWarnCooldownRemaining;
            reflectionRtAsBudgetBreachedLastFrame = rtWarningState.reflectionRtAsBudgetBreachedLastFrame;
            reflectionRtPromotionReadyLastFrame = rtWarningState.reflectionRtPromotionReadyLastFrame;
            reflectionRtPromotionReadyHighStreak = rtWarningState.reflectionRtPromotionReadyHighStreak;
            TransparencyCandidateSummary transparencySummary =
                    VulkanReflectionAnalysis.summarizeReflectionTransparencyCandidates(currentSceneMaterials, reflectionTransparencyCandidateReactiveMin);
            VulkanReflectionRtTransparencyWarningEmitter.State rtTransparencyState = new VulkanReflectionRtTransparencyWarningEmitter.State();
            rtTransparencyState.reflectionRtLaneActive = reflectionRtLaneActive;
            rtTransparencyState.reflectionRtDedicatedHardwarePipelineActive = reflectionRtDedicatedHardwarePipelineActive;
            rtTransparencyState.mockContext = mockContext;
            rtTransparencyState.reflectionRtDedicatedPipelineEnabled = reflectionRtDedicatedPipelineEnabled;
            rtTransparencyState.reflectionRtRequireActiveUnmetLastFrame = reflectionRtRequireActiveUnmetLastFrame;
            rtTransparencyState.reflectionRtRequireMultiBounceUnmetLastFrame = reflectionRtRequireMultiBounceUnmetLastFrame;
            rtTransparencyState.reflectionRtRequireDedicatedPipelineUnmetLastFrame = reflectionRtRequireDedicatedPipelineUnmetLastFrame;
            rtTransparencyState.reflectionRtPerfBreachedLastFrame = reflectionRtPerfBreachedLastFrame;
            rtTransparencyState.reflectionRtHybridBreachedLastFrame = reflectionRtHybridBreachedLastFrame;
            rtTransparencyState.reflectionRtDenoiseBreachedLastFrame = reflectionRtDenoiseBreachedLastFrame;
            rtTransparencyState.reflectionRtAsBudgetBreachedLastFrame = reflectionRtAsBudgetBreachedLastFrame;
            rtTransparencyState.reflectionRtPromotionReadyHighStreak = reflectionRtPromotionReadyHighStreak;
            rtTransparencyState.reflectionRtPromotionReadyMinFrames = reflectionRtPromotionReadyMinFrames;
            rtTransparencyState.reflectionRtPromotionReadyLastFrame = reflectionRtPromotionReadyLastFrame;
            rtTransparencyState.reflectionTransparencyCandidateReactiveMin = reflectionTransparencyCandidateReactiveMin;
            rtTransparencyState.reflectionTransparencyProbeOnlyRatioWarnMax = reflectionTransparencyProbeOnlyRatioWarnMax;
            rtTransparencyState.reflectionTransparencyWarnMinFrames = reflectionTransparencyWarnMinFrames;
            rtTransparencyState.reflectionTransparencyWarnCooldownFrames = reflectionTransparencyWarnCooldownFrames;
            rtTransparencyState.reflectionTransparencyWarnCooldownRemaining = reflectionTransparencyWarnCooldownRemaining;
            rtTransparencyState.reflectionTransparencyHighStreak = reflectionTransparencyHighStreak;
            rtTransparencyState.reflectionTransparencyBreachedLastFrame = reflectionTransparencyBreachedLastFrame;
            rtTransparencyState.reflectionTransparencyStageGateStatus = reflectionTransparencyStageGateStatus;
            rtTransparencyState.reflectionTransparencyFallbackPath = reflectionTransparencyFallbackPath;
            rtTransparencyState.reflectionTransparentCandidateCount = reflectionTransparentCandidateCount;
            rtTransparencyState.reflectionTransparencyAlphaTestedCandidateCount = reflectionTransparencyAlphaTestedCandidateCount;
            rtTransparencyState.reflectionTransparencyReactiveCandidateCount = reflectionTransparencyReactiveCandidateCount;
            rtTransparencyState.reflectionTransparencyProbeOnlyCandidateCount = reflectionTransparencyProbeOnlyCandidateCount;
            VulkanReflectionRtTransparencyWarningEmitter.emit(warnings, rtTransparencyState, transparencySummary);
            reflectionRtPromotionReadyHighStreak = rtTransparencyState.reflectionRtPromotionReadyHighStreak;
            reflectionRtPromotionReadyLastFrame = rtTransparencyState.reflectionRtPromotionReadyLastFrame;
            reflectionTransparencyWarnCooldownRemaining = rtTransparencyState.reflectionTransparencyWarnCooldownRemaining;
            reflectionTransparencyHighStreak = rtTransparencyState.reflectionTransparencyHighStreak;
            reflectionTransparencyBreachedLastFrame = rtTransparencyState.reflectionTransparencyBreachedLastFrame;
            reflectionTransparencyStageGateStatus = rtTransparencyState.reflectionTransparencyStageGateStatus;
            reflectionTransparencyFallbackPath = rtTransparencyState.reflectionTransparencyFallbackPath;
            reflectionTransparentCandidateCount = rtTransparencyState.reflectionTransparentCandidateCount;
            reflectionTransparencyAlphaTestedCandidateCount = rtTransparencyState.reflectionTransparencyAlphaTestedCandidateCount;
            reflectionTransparencyReactiveCandidateCount = rtTransparencyState.reflectionTransparencyReactiveCandidateCount;
            reflectionTransparencyProbeOnlyCandidateCount = rtTransparencyState.reflectionTransparencyProbeOnlyCandidateCount;
            VulkanReflectionTelemetryProfileWarning.State telemetryProfileState = new VulkanReflectionTelemetryProfileWarning.State();
            telemetryProfileState.reflectionProfile = reflectionProfile;
            telemetryProfileState.reflectionProbeChurnWarnMinDelta = reflectionProbeChurnWarnMinDelta;
            telemetryProfileState.reflectionProbeChurnWarnMinStreak = reflectionProbeChurnWarnMinStreak;
            telemetryProfileState.reflectionProbeChurnWarnCooldownFrames = reflectionProbeChurnWarnCooldownFrames;
            telemetryProfileState.reflectionProbeQualityOverlapWarnMaxPairs = reflectionProbeQualityOverlapWarnMaxPairs;
            telemetryProfileState.reflectionProbeQualityBleedRiskWarnMaxPairs = reflectionProbeQualityBleedRiskWarnMaxPairs;
            telemetryProfileState.reflectionProbeQualityMinOverlapPairsWhenMultiple = reflectionProbeQualityMinOverlapPairsWhenMultiple;
            telemetryProfileState.reflectionOverrideProbeOnlyRatioWarnMax = reflectionOverrideProbeOnlyRatioWarnMax;
            telemetryProfileState.reflectionOverrideSsrOnlyRatioWarnMax = reflectionOverrideSsrOnlyRatioWarnMax;
            telemetryProfileState.reflectionOverrideOtherWarnMax = reflectionOverrideOtherWarnMax;
            telemetryProfileState.reflectionOverrideWarnMinFrames = reflectionOverrideWarnMinFrames;
            telemetryProfileState.reflectionOverrideWarnCooldownFrames = reflectionOverrideWarnCooldownFrames;
            telemetryProfileState.reflectionContactHardeningMinSsrStrength = reflectionContactHardeningMinSsrStrength;
            telemetryProfileState.reflectionContactHardeningMinSsrMaxRoughness = reflectionContactHardeningMinSsrMaxRoughness;
            telemetryProfileState.reflectionContactHardeningWarnMinFrames = reflectionContactHardeningWarnMinFrames;
            telemetryProfileState.reflectionContactHardeningWarnCooldownFrames = reflectionContactHardeningWarnCooldownFrames;
            telemetryProfileState.reflectionProbeQualityBoxProjectionMinRatio = reflectionProbeQualityBoxProjectionMinRatio;
            telemetryProfileState.reflectionProbeQualityInvalidBlendDistanceWarnMax = reflectionProbeQualityInvalidBlendDistanceWarnMax;
            telemetryProfileState.reflectionProbeQualityOverlapCoverageWarnMin = reflectionProbeQualityOverlapCoverageWarnMin;
            telemetryProfileState.reflectionSsrTaaInstabilityRejectMin = reflectionSsrTaaInstabilityRejectMin;
            telemetryProfileState.reflectionSsrTaaInstabilityConfidenceMax = reflectionSsrTaaInstabilityConfidenceMax;
            telemetryProfileState.reflectionSsrTaaInstabilityDropEventsMin = reflectionSsrTaaInstabilityDropEventsMin;
            telemetryProfileState.reflectionSsrTaaInstabilityWarnMinFrames = reflectionSsrTaaInstabilityWarnMinFrames;
            telemetryProfileState.reflectionSsrTaaInstabilityWarnCooldownFrames = reflectionSsrTaaInstabilityWarnCooldownFrames;
            telemetryProfileState.reflectionSsrTaaRiskEmaAlpha = reflectionSsrTaaRiskEmaAlpha;
            telemetryProfileState.reflectionSsrTaaAdaptiveEnabled = reflectionSsrTaaAdaptiveEnabled;
            telemetryProfileState.reflectionSsrTaaAdaptiveTemporalBoostMax = reflectionSsrTaaAdaptiveTemporalBoostMax;
            telemetryProfileState.reflectionSsrTaaAdaptiveSsrStrengthScaleMin = reflectionSsrTaaAdaptiveSsrStrengthScaleMin;
            telemetryProfileState.reflectionSsrTaaAdaptiveStepScaleBoostMax = reflectionSsrTaaAdaptiveStepScaleBoostMax;
            telemetryProfileState.reflectionSsrTaaAdaptiveTrendWindowFrames = reflectionSsrTaaAdaptiveTrendWindowFrames;
            telemetryProfileState.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
            telemetryProfileState.reflectionSsrTaaAdaptiveTrendWarnMinFrames = reflectionSsrTaaAdaptiveTrendWarnMinFrames;
            telemetryProfileState.reflectionSsrTaaAdaptiveTrendWarnCooldownFrames = reflectionSsrTaaAdaptiveTrendWarnCooldownFrames;
            telemetryProfileState.reflectionSsrTaaAdaptiveTrendWarnMinSamples = reflectionSsrTaaAdaptiveTrendWarnMinSamples;
            telemetryProfileState.reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax;
            telemetryProfileState.reflectionSsrTaaAdaptiveTrendSloHighRatioMax = reflectionSsrTaaAdaptiveTrendSloHighRatioMax;
            telemetryProfileState.reflectionSsrTaaAdaptiveTrendSloMinSamples = reflectionSsrTaaAdaptiveTrendSloMinSamples;
            telemetryProfileState.reflectionSsrTaaDisocclusionRejectDropEventsMin = reflectionSsrTaaDisocclusionRejectDropEventsMin;
            telemetryProfileState.reflectionSsrTaaDisocclusionRejectConfidenceMax = reflectionSsrTaaDisocclusionRejectConfidenceMax;
            telemetryProfileState.reflectionSsrEnvelopeRejectWarnMax = reflectionSsrEnvelopeRejectWarnMax;
            telemetryProfileState.reflectionSsrEnvelopeConfidenceWarnMin = reflectionSsrEnvelopeConfidenceWarnMin;
            telemetryProfileState.reflectionSsrEnvelopeDropWarnMin = reflectionSsrEnvelopeDropWarnMin;
            telemetryProfileState.reflectionSsrEnvelopeWarnMinFrames = reflectionSsrEnvelopeWarnMinFrames;
            telemetryProfileState.reflectionSsrEnvelopeWarnCooldownFrames = reflectionSsrEnvelopeWarnCooldownFrames;
            telemetryProfileState.reflectionPlanarEnvelopePlaneDeltaWarnMax = reflectionPlanarEnvelopePlaneDeltaWarnMax;
            telemetryProfileState.reflectionPlanarEnvelopeCoverageRatioWarnMin = reflectionPlanarEnvelopeCoverageRatioWarnMin;
            telemetryProfileState.reflectionPlanarEnvelopeWarnMinFrames = reflectionPlanarEnvelopeWarnMinFrames;
            telemetryProfileState.reflectionPlanarEnvelopeWarnCooldownFrames = reflectionPlanarEnvelopeWarnCooldownFrames;
            telemetryProfileState.reflectionPlanarPerfMaxGpuMsLow = reflectionPlanarPerfMaxGpuMsLow;
            telemetryProfileState.reflectionPlanarPerfMaxGpuMsMedium = reflectionPlanarPerfMaxGpuMsMedium;
            telemetryProfileState.reflectionPlanarPerfMaxGpuMsHigh = reflectionPlanarPerfMaxGpuMsHigh;
            telemetryProfileState.reflectionPlanarPerfMaxGpuMsUltra = reflectionPlanarPerfMaxGpuMsUltra;
            telemetryProfileState.reflectionPlanarPerfDrawInflationWarnMax = reflectionPlanarPerfDrawInflationWarnMax;
            telemetryProfileState.reflectionPlanarPerfMemoryBudgetMb = reflectionPlanarPerfMemoryBudgetMb;
            telemetryProfileState.reflectionPlanarPerfWarnMinFrames = reflectionPlanarPerfWarnMinFrames;
            telemetryProfileState.reflectionPlanarPerfWarnCooldownFrames = reflectionPlanarPerfWarnCooldownFrames;
            telemetryProfileState.reflectionPlanarPerfRequireGpuTimestamp = reflectionPlanarPerfRequireGpuTimestamp;
            telemetryProfileState.reflectionPlanarScopeIncludeAuto = reflectionPlanarScopeIncludeAuto;
            telemetryProfileState.reflectionPlanarScopeIncludeProbeOnly = reflectionPlanarScopeIncludeProbeOnly;
            telemetryProfileState.reflectionPlanarScopeIncludeSsrOnly = reflectionPlanarScopeIncludeSsrOnly;
            telemetryProfileState.reflectionPlanarScopeIncludeOther = reflectionPlanarScopeIncludeOther;
            telemetryProfileState.reflectionRtSingleBounceEnabled = reflectionRtSingleBounceEnabled;
            telemetryProfileState.reflectionRtMultiBounceEnabled = reflectionRtMultiBounceEnabled;
            telemetryProfileState.reflectionRtRequireActive = reflectionRtRequireActive;
            telemetryProfileState.reflectionRtRequireMultiBounce = reflectionRtRequireMultiBounce;
            telemetryProfileState.reflectionRtRequireDedicatedPipeline = reflectionRtRequireDedicatedPipeline;
            telemetryProfileState.reflectionRtDedicatedPipelineEnabled = reflectionRtDedicatedPipelineEnabled;
            telemetryProfileState.reflectionRtDedicatedCapabilitySupported = reflectionRtDedicatedCapabilitySupported;
            telemetryProfileState.reflectionRtDedicatedDenoisePipelineEnabled = reflectionRtDedicatedDenoisePipelineEnabled;
            telemetryProfileState.reflectionRtDenoiseStrength = reflectionRtDenoiseStrength;
            telemetryProfileState.reflectionRtPerfMaxGpuMsLow = reflectionRtPerfMaxGpuMsLow;
            telemetryProfileState.reflectionRtPerfMaxGpuMsMedium = reflectionRtPerfMaxGpuMsMedium;
            telemetryProfileState.reflectionRtPerfMaxGpuMsHigh = reflectionRtPerfMaxGpuMsHigh;
            telemetryProfileState.reflectionRtPerfMaxGpuMsUltra = reflectionRtPerfMaxGpuMsUltra;
            telemetryProfileState.reflectionRtPerfWarnMinFrames = reflectionRtPerfWarnMinFrames;
            telemetryProfileState.reflectionRtPerfWarnCooldownFrames = reflectionRtPerfWarnCooldownFrames;
            telemetryProfileState.reflectionRtHybridProbeShareWarnMax = reflectionRtHybridProbeShareWarnMax;
            telemetryProfileState.reflectionRtHybridWarnMinFrames = reflectionRtHybridWarnMinFrames;
            telemetryProfileState.reflectionRtHybridWarnCooldownFrames = reflectionRtHybridWarnCooldownFrames;
            telemetryProfileState.reflectionRtDenoiseSpatialVarianceWarnMax = reflectionRtDenoiseSpatialVarianceWarnMax;
            telemetryProfileState.reflectionRtDenoiseTemporalLagWarnMax = reflectionRtDenoiseTemporalLagWarnMax;
            telemetryProfileState.reflectionRtDenoiseWarnMinFrames = reflectionRtDenoiseWarnMinFrames;
            telemetryProfileState.reflectionRtDenoiseWarnCooldownFrames = reflectionRtDenoiseWarnCooldownFrames;
            telemetryProfileState.reflectionRtAsBuildGpuMsWarnMax = reflectionRtAsBuildGpuMsWarnMax;
            telemetryProfileState.reflectionRtAsMemoryBudgetMb = reflectionRtAsMemoryBudgetMb;
            telemetryProfileState.reflectionRtPromotionReadyMinFrames = reflectionRtPromotionReadyMinFrames;
            telemetryProfileState.reflectionTransparencyCandidateReactiveMin = reflectionTransparencyCandidateReactiveMin;
            telemetryProfileState.reflectionTransparencyProbeOnlyRatioWarnMax = reflectionTransparencyProbeOnlyRatioWarnMax;
            telemetryProfileState.reflectionTransparencyWarnMinFrames = reflectionTransparencyWarnMinFrames;
            telemetryProfileState.reflectionTransparencyWarnCooldownFrames = reflectionTransparencyWarnCooldownFrames;
            telemetryProfileState.reflectionProbeUpdateCadenceFrames = reflectionProbeUpdateCadenceFrames;
            telemetryProfileState.reflectionProbeMaxVisible = reflectionProbeMaxVisible;
            telemetryProfileState.reflectionProbeLodDepthScale = reflectionProbeLodDepthScale;
            telemetryProfileState.reflectionProbeStreamingWarnMinFrames = reflectionProbeStreamingWarnMinFrames;
            telemetryProfileState.reflectionProbeStreamingWarnCooldownFrames = reflectionProbeStreamingWarnCooldownFrames;
            telemetryProfileState.reflectionProbeStreamingMissRatioWarnMax = reflectionProbeStreamingMissRatioWarnMax;
            telemetryProfileState.reflectionProbeStreamingDeferredRatioWarnMax = reflectionProbeStreamingDeferredRatioWarnMax;
            telemetryProfileState.reflectionProbeStreamingLodSkewWarnMax = reflectionProbeStreamingLodSkewWarnMax;
            telemetryProfileState.reflectionProbeStreamingMemoryBudgetMb = reflectionProbeStreamingMemoryBudgetMb;
            warnings.add(VulkanReflectionTelemetryProfileWarning.warning(telemetryProfileState));
            boolean ssrPathActive = reflectionBaseMode == 1 || reflectionBaseMode == 3 || reflectionBaseMode == 4;
            if (ssrPathActive && currentPost.taaEnabled()) {
                double taaReject = context.taaHistoryRejectRate();
                double taaConfidence = context.taaConfidenceMean();
                long taaDrops = context.taaConfidenceDropEvents();
                ReflectionSsrTaaRiskDiagnostics ssrTaaRisk = updateReflectionSsrTaaRiskDiagnostics(taaReject, taaConfidence, taaDrops);
                boolean adaptiveTrendWarningTriggered = updateReflectionAdaptiveTrendWarningGate();
                ReflectionAdaptiveTrendDiagnostics adaptiveTrend = snapshotReflectionAdaptiveTrendDiagnostics(adaptiveTrendWarningTriggered);
                TrendSloAudit trendSloAudit = evaluateReflectionAdaptiveTrendSlo(adaptiveTrend);
                VulkanReflectionSsrTaaWarningEmitter.State ssrTaaState = new VulkanReflectionSsrTaaWarningEmitter.State();
                ssrTaaState.reflectionSsrTaaAdaptiveEnabled = reflectionSsrTaaAdaptiveEnabled;
                ssrTaaState.reflectionSsrTaaInstabilityRejectMin = reflectionSsrTaaInstabilityRejectMin;
                ssrTaaState.reflectionSsrTaaInstabilityConfidenceMax = reflectionSsrTaaInstabilityConfidenceMax;
                ssrTaaState.reflectionSsrTaaInstabilityDropEventsMin = reflectionSsrTaaInstabilityDropEventsMin;
                ssrTaaState.reflectionSsrTaaInstabilityWarnMinFrames = reflectionSsrTaaInstabilityWarnMinFrames;
                ssrTaaState.reflectionSsrTaaInstabilityWarnCooldownFrames = reflectionSsrTaaInstabilityWarnCooldownFrames;
                ssrTaaState.reflectionSsrTaaRiskEmaAlpha = reflectionSsrTaaRiskEmaAlpha;
                ssrTaaState.reflectionSsrTaaHistoryPolicyActive = reflectionSsrTaaHistoryPolicyActive;
                ssrTaaState.reflectionSsrTaaReprojectionPolicyActive = reflectionSsrTaaReprojectionPolicyActive;
                ssrTaaState.reflectionAdaptiveSeverityInstant = reflectionAdaptiveSeverityInstant;
                ssrTaaState.reflectionSsrTaaRiskHighStreak = reflectionSsrTaaRiskHighStreak;
                ssrTaaState.reflectionSsrTaaLatestRejectRate = reflectionSsrTaaLatestRejectRate;
                ssrTaaState.reflectionSsrTaaLatestConfidenceMean = reflectionSsrTaaLatestConfidenceMean;
                ssrTaaState.reflectionSsrTaaLatestDropEvents = reflectionSsrTaaLatestDropEvents;
                ssrTaaState.reflectionSsrTaaHistoryRejectBiasActive = reflectionSsrTaaHistoryRejectBiasActive;
                ssrTaaState.reflectionSsrTaaHistoryConfidenceDecayActive = reflectionSsrTaaHistoryConfidenceDecayActive;
                ssrTaaState.reflectionSsrTaaHistoryRejectSeverityMin = reflectionSsrTaaHistoryRejectSeverityMin;
                ssrTaaState.reflectionSsrTaaHistoryConfidenceDecaySeverityMin = reflectionSsrTaaHistoryConfidenceDecaySeverityMin;
                ssrTaaState.reflectionSsrTaaHistoryRejectRiskStreakMin = reflectionSsrTaaHistoryRejectRiskStreakMin;
                ssrTaaState.reflectionSsrTaaDisocclusionRejectDropEventsMin = reflectionSsrTaaDisocclusionRejectDropEventsMin;
                ssrTaaState.reflectionSsrTaaDisocclusionRejectConfidenceMax = reflectionSsrTaaDisocclusionRejectConfidenceMax;
                ssrTaaState.reflectionAdaptiveTemporalWeightActive = reflectionAdaptiveTemporalWeightActive;
                ssrTaaState.reflectionAdaptiveSsrStrengthActive = reflectionAdaptiveSsrStrengthActive;
                ssrTaaState.reflectionAdaptiveSsrStepScaleActive = reflectionAdaptiveSsrStepScaleActive;
                ssrTaaState.reflectionSsrEnvelopeRejectWarnMax = reflectionSsrEnvelopeRejectWarnMax;
                ssrTaaState.reflectionSsrEnvelopeConfidenceWarnMin = reflectionSsrEnvelopeConfidenceWarnMin;
                ssrTaaState.reflectionSsrEnvelopeDropWarnMin = reflectionSsrEnvelopeDropWarnMin;
                ssrTaaState.reflectionSsrEnvelopeWarnMinFrames = reflectionSsrEnvelopeWarnMinFrames;
                ssrTaaState.reflectionSsrEnvelopeWarnCooldownFrames = reflectionSsrEnvelopeWarnCooldownFrames;
                ssrTaaState.reflectionSsrEnvelopeHighStreak = reflectionSsrEnvelopeHighStreak;
                ssrTaaState.reflectionSsrEnvelopeWarnCooldownRemaining = reflectionSsrEnvelopeWarnCooldownRemaining;
                ssrTaaState.reflectionSsrEnvelopeBreachedLastFrame = reflectionSsrEnvelopeBreachedLastFrame;
                ssrTaaState.reflectionSsrTaaAdaptiveTrendWindowFrames = reflectionSsrTaaAdaptiveTrendWindowFrames;
                ssrTaaState.reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax;
                ssrTaaState.reflectionSsrTaaAdaptiveTrendSloHighRatioMax = reflectionSsrTaaAdaptiveTrendSloHighRatioMax;
                ssrTaaState.reflectionSsrTaaAdaptiveTrendSloMinSamples = reflectionSsrTaaAdaptiveTrendSloMinSamples;
                ssrTaaState.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
                ssrTaaState.reflectionSsrTaaAdaptiveTrendWarnMinSamples = reflectionSsrTaaAdaptiveTrendWarnMinSamples;
                ssrTaaState.reflectionSsrTaaAdaptiveTrendWarnHighStreak = reflectionSsrTaaAdaptiveTrendWarnHighStreak;
                VulkanReflectionSsrTaaWarningEmitter.emit(
                        warnings,
                        ssrTaaState,
                        reflectionBaseMode,
                        currentPost.reflectionsSsrStrength(),
                        currentPost.reflectionsSsrMaxRoughness(),
                        currentPost.reflectionsSsrStepScale(),
                        currentPost.reflectionsTemporalWeight(),
                        currentPost.taaBlend(),
                        currentPost.taaClipScale(),
                        currentPost.taaLumaClipEnabled(),
                        taaReject,
                        taaConfidence,
                        taaDrops,
                        ssrTaaRisk,
                        adaptiveTrend,
                        adaptiveTrendWarningTriggered,
                        trendSloAudit.status(),
                        trendSloAudit.reason(),
                        trendSloAudit.failed()
                );
                reflectionSsrEnvelopeHighStreak = ssrTaaState.reflectionSsrEnvelopeHighStreak;
                reflectionSsrEnvelopeWarnCooldownRemaining = ssrTaaState.reflectionSsrEnvelopeWarnCooldownRemaining;
                reflectionSsrEnvelopeBreachedLastFrame = ssrTaaState.reflectionSsrEnvelopeBreachedLastFrame;
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
            resetReflectionWarningStateWhenDisabled();
        }
        resetShadowCapabilityFrameStateDefaults();
        if (shadowCadenceWarnCooldownRemaining > 0) {
            shadowCadenceWarnCooldownRemaining--;
        }
        if (shadowPointBudgetWarnCooldownRemaining > 0) {
            shadowPointBudgetWarnCooldownRemaining--;
        }
        if (shadowCacheWarnCooldownRemaining > 0) {
            shadowCacheWarnCooldownRemaining--;
        }
        if (shadowRtWarnCooldownRemaining > 0) {
            shadowRtWarnCooldownRemaining--;
        }
        if (shadowHybridWarnCooldownRemaining > 0) {
            shadowHybridWarnCooldownRemaining--;
        }
        if (shadowTransparentReceiverWarnCooldownRemaining > 0) {
            shadowTransparentReceiverWarnCooldownRemaining--;
        }
        if (shadowTopologyWarnCooldownRemaining > 0) {
            shadowTopologyWarnCooldownRemaining--;
        }
        if (!currentShadows.enabled()) {
            shadowCadenceHighStreak = 0;
            shadowCadenceWarnCooldownRemaining = 0;
            shadowCadenceStableStreak = 0;
            shadowCadencePromotionReadyLastFrame = false;
            shadowPointBudgetHighStreak = 0;
            shadowPointBudgetWarnCooldownRemaining = 0;
            shadowPointBudgetStableStreak = 0;
            shadowPointBudgetPromotionReadyLastFrame = false;
            shadowCacheHighStreak = 0;
            shadowCacheWarnCooldownRemaining = 0;
            shadowRtHighStreak = 0;
            shadowRtWarnCooldownRemaining = 0;
            shadowHybridHighStreak = 0;
            shadowHybridWarnCooldownRemaining = 0;
            shadowTransparentReceiverHighStreak = 0;
            shadowTransparentReceiverWarnCooldownRemaining = 0;
            shadowTopologyHighStreak = 0;
            shadowTopologyWarnCooldownRemaining = 0;
            shadowTopologyStableStreak = 0;
            shadowTopologyPromotionReadyLastFrame = false;
            shadowSpotProjectedStableStreak = 0;
            shadowSpotProjectedPromotionReadyLastFrame = false;
            shadowPhaseAPromotionStableStreak = 0;
            shadowPhaseAPromotionReadyLastFrame = false;
            shadowPhaseDPromotionStableStreak = 0;
            shadowPhaseDPromotionReadyLastFrame = false;
        }
        if (currentShadows.enabled()) {
            VulkanShadowCapabilityPlanner.Plan shadowCapabilityPlan = VulkanShadowCapabilityPlanner.plan(
                    new VulkanShadowCapabilityPlanner.PlanInput(
                            qualityTier,
                            currentShadows.filterPath(),
                            currentShadows.contactShadowsRequested(),
                            currentShadows.rtShadowMode(),
                            shadowMaxShadowedLocalLights > 0 ? shadowMaxShadowedLocalLights : currentShadows.maxShadowedLocalLights(),
                            shadowMaxLocalLayers,
                            shadowMaxFacesPerFrame,
                            currentShadows.selectedLocalShadowLights(),
                            currentShadows.deferredShadowLightCount(),
                            currentShadows.renderedSpotShadowLights(),
                            currentShadows.renderedPointShadowCubemaps(),
                            shadowSchedulerEnabled,
                            false,
                            false,
                            currentShadows.renderedSpotShadowLights() > 0,
                            false,
                            false
                    )
            );
            shadowCapabilityFeatureIdLastFrame = shadowCapabilityPlan.capability().featureId();
            shadowCapabilityModeLastFrame = shadowCapabilityPlan.mode().id();
            shadowCapabilitySignalsLastFrame = shadowCapabilityPlan.signals();
            shadowSpotProjectedRenderedCountLastFrame = currentShadows.renderedSpotShadowLights();
            shadowSpotProjectedRequestedLastFrame = "spot_projected".equals(shadowCapabilityModeLastFrame)
                    || shadowCapabilitySignalsLastFrame.stream().anyMatch(s -> "spotProjected=true".equals(s));
            shadowSpotProjectedActiveLastFrame = shadowSpotProjectedRenderedCountLastFrame > 0;
            if (shadowSpotProjectedRequestedLastFrame && shadowSpotProjectedActiveLastFrame) {
                shadowSpotProjectedContractStatusLastFrame = "active";
            } else if (shadowSpotProjectedRequestedLastFrame) {
                shadowSpotProjectedContractStatusLastFrame = "requested_unavailable";
            } else if (shadowSpotProjectedActiveLastFrame) {
                shadowSpotProjectedContractStatusLastFrame = "active_not_selected";
            } else {
                shadowSpotProjectedContractStatusLastFrame = "inactive";
            }
            shadowSpotProjectedContractBreachedLastFrame =
                    shadowSpotProjectedRequestedLastFrame && !shadowSpotProjectedActiveLastFrame;
            if (shadowSpotProjectedContractBreachedLastFrame) {
                shadowSpotProjectedStableStreak = 0;
                shadowSpotProjectedPromotionReadyLastFrame = false;
            } else {
                shadowSpotProjectedStableStreak = Math.min(10_000, shadowSpotProjectedStableStreak + 1);
                shadowSpotProjectedPromotionReadyLastFrame =
                        shadowSpotProjectedRequestedLastFrame
                                && shadowSpotProjectedActiveLastFrame
                                && shadowSpotProjectedStableStreak >= shadowSpotProjectedPromotionReadyMinFrames;
            }
            shadowCadenceSelectedLocalLightsLastFrame = currentShadows.selectedLocalShadowLights();
            shadowCadenceDeferredLocalLightsLastFrame = currentShadows.deferredShadowLightCount();
            shadowCadenceStaleBypassCountLastFrame = currentShadows.staleBypassShadowLightCount();
            shadowCadenceDeferredRatioLastFrame = shadowCadenceSelectedLocalLightsLastFrame <= 0
                    ? 0.0
                    : (double) shadowCadenceDeferredLocalLightsLastFrame / (double) shadowCadenceSelectedLocalLightsLastFrame;
            boolean cadenceEnvelopeNow = shadowCadenceSelectedLocalLightsLastFrame >= 2
                    && shadowCadenceDeferredRatioLastFrame > shadowCadenceWarnDeferredRatioMax;
            if (cadenceEnvelopeNow) {
                shadowCadenceHighStreak = Math.min(10_000, shadowCadenceHighStreak + 1);
                shadowCadenceStableStreak = 0;
                shadowCadencePromotionReadyLastFrame = false;
                shadowCadenceEnvelopeBreachedLastFrame = true;
            } else {
                shadowCadenceHighStreak = 0;
                shadowCadenceStableStreak = Math.min(10_000, shadowCadenceStableStreak + 1);
                shadowCadencePromotionReadyLastFrame =
                        shadowCadenceStableStreak >= shadowCadencePromotionReadyMinFrames;
            }
            warnings.add(new EngineWarning(
                    "SHADOW_CAPABILITY_MODE_ACTIVE",
                    "Shadow capability mode active: featureId="
                            + shadowCapabilityPlan.capability().featureId()
                            + " mode=" + shadowCapabilityPlan.mode().id()
                            + " signals=[" + String.join(", ", shadowCapabilityPlan.signals()) + "]"
            ));
            warnings.add(new EngineWarning(
                    "SHADOW_TELEMETRY_PROFILE_ACTIVE",
                    "Shadow telemetry profile active (tier=" + qualityTier.name().toLowerCase(Locale.ROOT)
                            + ", cadenceDeferredRatioWarnMax=" + shadowCadenceWarnDeferredRatioMax
                            + ", cadenceWarnMinFrames=" + shadowCadenceWarnMinFrames
                            + ", cadenceWarnCooldownFrames=" + shadowCadenceWarnCooldownFrames
                            + ", cadencePromotionReadyMinFrames=" + shadowCadencePromotionReadyMinFrames
                            + ", pointFaceBudgetSaturationWarnMin=" + shadowPointFaceBudgetWarnSaturationMin
                            + ", pointFaceBudgetWarnMinFrames=" + shadowPointFaceBudgetWarnMinFrames
                            + ", pointFaceBudgetWarnCooldownFrames=" + shadowPointFaceBudgetWarnCooldownFrames
                            + ", pointFaceBudgetPromotionReadyMinFrames=" + shadowPointFaceBudgetPromotionReadyMinFrames
                            + ", cacheChurnWarnMax=" + shadowCacheChurnWarnMax
                            + ", cacheMissWarnMax=" + shadowCacheMissWarnMax
                            + ", cacheWarnMinFrames=" + shadowCacheWarnMinFrames
                            + ", cacheWarnCooldownFrames=" + shadowCacheWarnCooldownFrames
                            + ", rtDenoiseWarnMin=" + shadowRtDenoiseWarnMin
                            + ", rtSampleWarnMin=" + shadowRtSampleWarnMin
                            + ", rtPerfMaxGpuMsLow=" + shadowRtPerfMaxGpuMsLow
                            + ", rtPerfMaxGpuMsMedium=" + shadowRtPerfMaxGpuMsMedium
                            + ", rtPerfMaxGpuMsHigh=" + shadowRtPerfMaxGpuMsHigh
                            + ", rtPerfMaxGpuMsUltra=" + shadowRtPerfMaxGpuMsUltra
                            + ", rtWarnMinFrames=" + shadowRtWarnMinFrames
                            + ", rtWarnCooldownFrames=" + shadowRtWarnCooldownFrames
                            + ", hybridRtShareWarnMin=" + shadowHybridRtShareWarnMin
                            + ", hybridContactShareWarnMin=" + shadowHybridContactShareWarnMin
                            + ", hybridWarnMinFrames=" + shadowHybridWarnMinFrames
                            + ", hybridWarnCooldownFrames=" + shadowHybridWarnCooldownFrames
                            + ", transparentReceiversRequested=" + shadowTransparentReceiversRequested
                            + ", transparentReceiversSupported=" + shadowTransparentReceiversSupported
                            + ", transparentReceiverCandidateRatioWarnMax=" + shadowTransparentReceiverCandidateRatioWarnMax
                            + ", transparentReceiverWarnMinFrames=" + shadowTransparentReceiverWarnMinFrames
                            + ", transparentReceiverWarnCooldownFrames=" + shadowTransparentReceiverWarnCooldownFrames
                            + ", areaApproxRequested=" + shadowAreaApproxRequested
                            + ", areaApproxSupported=" + shadowAreaApproxSupported
                            + ", areaApproxRequireActive=" + shadowAreaApproxRequireActive
                            + ", distanceFieldRequested=" + shadowDistanceFieldRequested
                            + ", distanceFieldSupported=" + shadowDistanceFieldSupported
                            + ", distanceFieldRequireActive=" + shadowDistanceFieldRequireActive
                            + ", spotProjectedPromotionReadyMinFrames=" + shadowSpotProjectedPromotionReadyMinFrames
                            + ", topologyLocalCoverageWarnMin=" + shadowTopologyLocalCoverageWarnMin
                            + ", topologySpotCoverageWarnMin=" + shadowTopologySpotCoverageWarnMin
                            + ", topologyPointCoverageWarnMin=" + shadowTopologyPointCoverageWarnMin
                            + ", topologyWarnMinFrames=" + shadowTopologyWarnMinFrames
                            + ", topologyWarnCooldownFrames=" + shadowTopologyWarnCooldownFrames
                            + ", topologyPromotionReadyMinFrames=" + shadowTopologyPromotionReadyMinFrames
                            + ", phaseAPromotionReadyMinFrames=" + shadowPhaseAPromotionReadyMinFrames
                            + ", phaseDPromotionReadyMinFrames=" + shadowPhaseDPromotionReadyMinFrames
                            + ")"
            ));
            warnings.add(new EngineWarning(
                    "SHADOW_SPOT_PROJECTED_CONTRACT",
                    "Shadow spot projected contract (requested="
                            + shadowSpotProjectedRequestedLastFrame
                            + ", active=" + shadowSpotProjectedActiveLastFrame
                            + ", renderedSpotShadows=" + shadowSpotProjectedRenderedCountLastFrame
                            + ", status=" + shadowSpotProjectedContractStatusLastFrame + ")"
            ));
            if (shadowSpotProjectedContractBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "SHADOW_SPOT_PROJECTED_CONTRACT_BREACH",
                        "Shadow spot projected contract breached (requested=true, renderedSpotShadows="
                                + shadowSpotProjectedRenderedCountLastFrame
                                + ", status=" + shadowSpotProjectedContractStatusLastFrame + ")"
                ));
            }
            if (shadowSpotProjectedPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "SHADOW_SPOT_PROJECTED_PROMOTION_READY",
                        "Shadow spot projected promotion-ready (status=" + shadowSpotProjectedContractStatusLastFrame
                                + ", renderedSpotShadows=" + shadowSpotProjectedRenderedCountLastFrame
                                + ", stableStreak=" + shadowSpotProjectedStableStreak
                                + ", promotionReadyMinFrames=" + shadowSpotProjectedPromotionReadyMinFrames + ")"
                ));
            }
            warnings.add(new EngineWarning(
                    "SHADOW_CADENCE_ENVELOPE",
                    "Shadow cadence envelope (selectedLocal="
                            + shadowCadenceSelectedLocalLightsLastFrame
                            + ", deferredLocal=" + shadowCadenceDeferredLocalLightsLastFrame
                            + ", staleBypass=" + shadowCadenceStaleBypassCountLastFrame
                            + ", deferredRatio=" + shadowCadenceDeferredRatioLastFrame
                            + ", deferredRatioWarnMax=" + shadowCadenceWarnDeferredRatioMax
                            + ", highStreak=" + shadowCadenceHighStreak
                            + ", warnMinFrames=" + shadowCadenceWarnMinFrames
                            + ", cooldownRemaining=" + shadowCadenceWarnCooldownRemaining
                            + ", stableStreak=" + shadowCadenceStableStreak
                            + ", promotionReadyMinFrames=" + shadowCadencePromotionReadyMinFrames
                            + ", promotionReady=" + shadowCadencePromotionReadyLastFrame + ")"
            ));
            if (cadenceEnvelopeNow
                    && shadowCadenceHighStreak >= shadowCadenceWarnMinFrames
                    && shadowCadenceWarnCooldownRemaining == 0) {
                warnings.add(new EngineWarning(
                        "SHADOW_CADENCE_ENVELOPE_BREACH",
                        "Shadow cadence deferred-ratio envelope breached (selectedLocal="
                                + shadowCadenceSelectedLocalLightsLastFrame
                                + ", deferredLocal=" + shadowCadenceDeferredLocalLightsLastFrame
                                + ", deferredRatio=" + shadowCadenceDeferredRatioLastFrame
                                + ", deferredRatioWarnMax=" + shadowCadenceWarnDeferredRatioMax
                                + ", highStreak=" + shadowCadenceHighStreak
                                + ", warnMinFrames=" + shadowCadenceWarnMinFrames
                                + ", cooldownFrames=" + shadowCadenceWarnCooldownFrames + ")"
                ));
                shadowCadenceWarnCooldownRemaining = shadowCadenceWarnCooldownFrames;
            }
            if (shadowCadencePromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "SHADOW_CADENCE_PROMOTION_READY",
                        "Shadow cadence promotion-ready (selectedLocal=" + shadowCadenceSelectedLocalLightsLastFrame
                                + ", deferredLocal=" + shadowCadenceDeferredLocalLightsLastFrame
                                + ", deferredRatio=" + shadowCadenceDeferredRatioLastFrame
                                + ", stableStreak=" + shadowCadenceStableStreak
                                + ", promotionReadyMinFrames=" + shadowCadencePromotionReadyMinFrames + ")"
                ));
            }
            shadowPointBudgetRenderedCubemapsLastFrame = currentShadows.renderedPointShadowCubemaps();
            shadowPointBudgetRenderedFacesLastFrame = shadowPointBudgetRenderedCubemapsLastFrame * 6;
            shadowPointBudgetDeferredCountLastFrame = currentShadows.deferredShadowLightCount();
            int pointFaceBudgetConfigured = Math.max(0, shadowMaxFacesPerFrame);
            shadowPointBudgetSaturationRatioLastFrame = pointFaceBudgetConfigured <= 0
                    ? 0.0
                    : Math.min(1.0, (double) shadowPointBudgetRenderedFacesLastFrame / (double) pointFaceBudgetConfigured);
            boolean pointBudgetEnvelopeNow = pointFaceBudgetConfigured > 0
                    && shadowPointBudgetRenderedFacesLastFrame > 0
                    && shadowPointBudgetSaturationRatioLastFrame >= shadowPointFaceBudgetWarnSaturationMin
                    && shadowPointBudgetDeferredCountLastFrame > 0;
            if (pointBudgetEnvelopeNow) {
                shadowPointBudgetHighStreak = Math.min(10_000, shadowPointBudgetHighStreak + 1);
                shadowPointBudgetStableStreak = 0;
                shadowPointBudgetPromotionReadyLastFrame = false;
                shadowPointBudgetEnvelopeBreachedLastFrame = true;
            } else {
                shadowPointBudgetHighStreak = 0;
                shadowPointBudgetStableStreak = Math.min(10_000, shadowPointBudgetStableStreak + 1);
                shadowPointBudgetPromotionReadyLastFrame =
                        shadowPointBudgetStableStreak >= shadowPointFaceBudgetPromotionReadyMinFrames;
            }
            warnings.add(new EngineWarning(
                    "SHADOW_POINT_FACE_BUDGET_ENVELOPE",
                    "Shadow point face-budget envelope (configuredMaxFacesPerFrame="
                            + pointFaceBudgetConfigured
                            + ", renderedPointCubemaps=" + shadowPointBudgetRenderedCubemapsLastFrame
                            + ", renderedPointFaces=" + shadowPointBudgetRenderedFacesLastFrame
                            + ", deferredShadowLightCount=" + shadowPointBudgetDeferredCountLastFrame
                            + ", saturationRatio=" + shadowPointBudgetSaturationRatioLastFrame
                            + ", saturationWarnMin=" + shadowPointFaceBudgetWarnSaturationMin
                            + ", highStreak=" + shadowPointBudgetHighStreak
                            + ", warnMinFrames=" + shadowPointFaceBudgetWarnMinFrames
                            + ", cooldownRemaining=" + shadowPointBudgetWarnCooldownRemaining
                            + ", stableStreak=" + shadowPointBudgetStableStreak
                            + ", promotionReadyMinFrames=" + shadowPointFaceBudgetPromotionReadyMinFrames
                            + ", promotionReady=" + shadowPointBudgetPromotionReadyLastFrame + ")"
            ));
            if (pointBudgetEnvelopeNow
                    && shadowPointBudgetHighStreak >= shadowPointFaceBudgetWarnMinFrames
                    && shadowPointBudgetWarnCooldownRemaining == 0) {
                warnings.add(new EngineWarning(
                        "SHADOW_POINT_FACE_BUDGET_ENVELOPE_BREACH",
                        "Shadow point face-budget envelope breached (configuredMaxFacesPerFrame="
                                + pointFaceBudgetConfigured
                                + ", renderedPointFaces=" + shadowPointBudgetRenderedFacesLastFrame
                                + ", saturationRatio=" + shadowPointBudgetSaturationRatioLastFrame
                                + ", saturationWarnMin=" + shadowPointFaceBudgetWarnSaturationMin
                                + ", deferredShadowLightCount=" + shadowPointBudgetDeferredCountLastFrame
                                + ", highStreak=" + shadowPointBudgetHighStreak
                                + ", warnMinFrames=" + shadowPointFaceBudgetWarnMinFrames
                                + ", cooldownFrames=" + shadowPointFaceBudgetWarnCooldownFrames + ")"
                ));
                shadowPointBudgetWarnCooldownRemaining = shadowPointFaceBudgetWarnCooldownFrames;
            }
            if (shadowPointBudgetPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "SHADOW_POINT_FACE_BUDGET_PROMOTION_READY",
                        "Shadow point face-budget promotion-ready (configuredMaxFacesPerFrame="
                                + pointFaceBudgetConfigured
                                + ", renderedPointFaces=" + shadowPointBudgetRenderedFacesLastFrame
                                + ", deferredShadowLightCount=" + shadowPointBudgetDeferredCountLastFrame
                                + ", stableStreak=" + shadowPointBudgetStableStreak
                                + ", promotionReadyMinFrames=" + shadowPointFaceBudgetPromotionReadyMinFrames + ")"
                ));
            }
            boolean phaseAPromotionNow = shadowCadencePromotionReadyLastFrame
                    && shadowPointBudgetPromotionReadyLastFrame
                    && shadowSpotProjectedPromotionReadyLastFrame;
            if (phaseAPromotionNow) {
                shadowPhaseAPromotionStableStreak = Math.min(10_000, shadowPhaseAPromotionStableStreak + 1);
                shadowPhaseAPromotionReadyLastFrame =
                        shadowPhaseAPromotionStableStreak >= shadowPhaseAPromotionReadyMinFrames;
            } else {
                shadowPhaseAPromotionStableStreak = 0;
                shadowPhaseAPromotionReadyLastFrame = false;
            }
            if (shadowPhaseAPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "SHADOW_PHASEA_PROMOTION_READY",
                        "Shadow Phase A promotion-ready (cadenceReady=" + shadowCadencePromotionReadyLastFrame
                                + ", pointBudgetReady=" + shadowPointBudgetPromotionReadyLastFrame
                                + ", spotProjectedReady=" + shadowSpotProjectedPromotionReadyLastFrame
                                + ", stableStreak=" + shadowPhaseAPromotionStableStreak
                                + ", promotionReadyMinFrames=" + shadowPhaseAPromotionReadyMinFrames + ")"
                ));
            }
            int candidateSpotLights = 0;
            int candidatePointLights = 0;
            if (currentSceneLights != null) {
                for (LightDesc light : currentSceneLights) {
                    if (light == null || !light.castsShadows() || light.shadow() == null) {
                        continue;
                    }
                    if (light.type() == LightType.SPOT) {
                        candidateSpotLights++;
                    } else if (light.type() == LightType.POINT) {
                        candidatePointLights++;
                    }
                }
            }
            shadowTopologyCandidateSpotLightsLastFrame = candidateSpotLights;
            shadowTopologyCandidatePointLightsLastFrame = candidatePointLights;
            int renderedLocalLights = Math.max(0, currentShadows.renderedLocalShadowLights());
            shadowTopologyLocalCoverageLastFrame = shadowCadenceSelectedLocalLightsLastFrame <= 0
                    ? 1.0
                    : Math.min(1.0, (double) renderedLocalLights / (double) shadowCadenceSelectedLocalLightsLastFrame);
            shadowTopologySpotCoverageLastFrame = candidateSpotLights <= 0
                    ? 1.0
                    : Math.min(1.0, (double) shadowSpotProjectedRenderedCountLastFrame / (double) candidateSpotLights);
            shadowTopologyPointCoverageLastFrame = candidatePointLights <= 0
                    ? 1.0
                    : Math.min(1.0, (double) shadowPointBudgetRenderedCubemapsLastFrame / (double) candidatePointLights);
            boolean topologyEnvelopeNow = shadowTopologyLocalCoverageLastFrame < shadowTopologyLocalCoverageWarnMin
                    || shadowTopologySpotCoverageLastFrame < shadowTopologySpotCoverageWarnMin
                    || shadowTopologyPointCoverageLastFrame < shadowTopologyPointCoverageWarnMin;
            if (topologyEnvelopeNow) {
                shadowTopologyHighStreak = Math.min(10_000, shadowTopologyHighStreak + 1);
                shadowTopologyStableStreak = 0;
                shadowTopologyPromotionReadyLastFrame = false;
                shadowTopologyEnvelopeBreachedLastFrame = true;
            } else {
                shadowTopologyHighStreak = 0;
                shadowTopologyStableStreak = Math.min(10_000, shadowTopologyStableStreak + 1);
                shadowTopologyPromotionReadyLastFrame = shadowTopologyStableStreak >= shadowTopologyPromotionReadyMinFrames;
            }
            warnings.add(new EngineWarning(
                    "SHADOW_TOPOLOGY_CONTRACT",
                    "Shadow topology contract (selectedLocal=" + shadowCadenceSelectedLocalLightsLastFrame
                            + ", renderedLocal=" + renderedLocalLights
                            + ", candidateSpot=" + candidateSpotLights
                            + ", renderedSpot=" + shadowSpotProjectedRenderedCountLastFrame
                            + ", candidatePoint=" + candidatePointLights
                            + ", renderedPointCubemaps=" + shadowPointBudgetRenderedCubemapsLastFrame
                            + ", localCoverage=" + shadowTopologyLocalCoverageLastFrame
                            + ", spotCoverage=" + shadowTopologySpotCoverageLastFrame
                            + ", pointCoverage=" + shadowTopologyPointCoverageLastFrame
                            + ", localCoverageWarnMin=" + shadowTopologyLocalCoverageWarnMin
                            + ", spotCoverageWarnMin=" + shadowTopologySpotCoverageWarnMin
                            + ", pointCoverageWarnMin=" + shadowTopologyPointCoverageWarnMin
                            + ", warnMinFrames=" + shadowTopologyWarnMinFrames
                            + ", cooldownRemaining=" + shadowTopologyWarnCooldownRemaining
                            + ", stableStreak=" + shadowTopologyStableStreak
                            + ", promotionReadyMinFrames=" + shadowTopologyPromotionReadyMinFrames
                            + ", promotionReady=" + shadowTopologyPromotionReadyLastFrame + ")"
            ));
            if (topologyEnvelopeNow
                    && shadowTopologyHighStreak >= shadowTopologyWarnMinFrames
                    && shadowTopologyWarnCooldownRemaining == 0) {
                warnings.add(new EngineWarning(
                        "SHADOW_TOPOLOGY_CONTRACT_BREACH",
                        "Shadow topology contract breached (localCoverage=" + shadowTopologyLocalCoverageLastFrame
                                + ", spotCoverage=" + shadowTopologySpotCoverageLastFrame
                                + ", pointCoverage=" + shadowTopologyPointCoverageLastFrame
                                + ", localCoverageWarnMin=" + shadowTopologyLocalCoverageWarnMin
                                + ", spotCoverageWarnMin=" + shadowTopologySpotCoverageWarnMin
                                + ", pointCoverageWarnMin=" + shadowTopologyPointCoverageWarnMin
                                + ", highStreak=" + shadowTopologyHighStreak
                                + ", warnMinFrames=" + shadowTopologyWarnMinFrames
                                + ", cooldownFrames=" + shadowTopologyWarnCooldownFrames + ")"
                ));
                shadowTopologyWarnCooldownRemaining = shadowTopologyWarnCooldownFrames;
            }
            if (shadowTopologyPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "SHADOW_TOPOLOGY_PROMOTION_READY",
                        "Shadow topology promotion-ready (localCoverage=" + shadowTopologyLocalCoverageLastFrame
                                + ", spotCoverage=" + shadowTopologySpotCoverageLastFrame
                                + ", pointCoverage=" + shadowTopologyPointCoverageLastFrame
                                + ", stableStreak=" + shadowTopologyStableStreak
                                + ", promotionReadyMinFrames=" + shadowTopologyPromotionReadyMinFrames + ")"
                ));
            }
            shadowCacheHitCountLastFrame = Math.max(0, shadowAllocatorReusedAssignments);
            shadowCacheMissCountLastFrame = Math.max(0,
                    shadowCadenceSelectedLocalLightsLastFrame - shadowCacheHitCountLastFrame);
            shadowCacheEvictionCountLastFrame = Math.max(0, shadowAllocatorEvictions);
            int cacheTotalOps = shadowCacheHitCountLastFrame + shadowCacheMissCountLastFrame;
            shadowCacheHitRatioLastFrame = cacheTotalOps <= 0
                    ? 1.0
                    : (double) shadowCacheHitCountLastFrame / (double) cacheTotalOps;
            shadowCacheChurnRatioLastFrame = Math.min(1.0,
                    (double) shadowCacheEvictionCountLastFrame
                            / (double) Math.max(1, shadowCadenceSelectedLocalLightsLastFrame));
            if (shadowCacheEvictionCountLastFrame > 0) {
                shadowCacheInvalidationReasonLastFrame = "atlas_eviction";
            } else if (shadowCacheMissCountLastFrame > 0) {
                shadowCacheInvalidationReasonLastFrame = "new_assignment";
            } else if (shadowCadenceDeferredLocalLightsLastFrame > 0) {
                shadowCacheInvalidationReasonLastFrame = "deferred_overlay";
            } else {
                shadowCacheInvalidationReasonLastFrame = "none";
            }
            boolean cacheEnvelopeNow = shadowCacheChurnRatioLastFrame > shadowCacheChurnWarnMax
                    || shadowCacheMissCountLastFrame > shadowCacheMissWarnMax;
            if (cacheEnvelopeNow) {
                shadowCacheHighStreak = Math.min(10_000, shadowCacheHighStreak + 1);
                shadowCacheEnvelopeBreachedLastFrame = true;
            } else {
                shadowCacheHighStreak = 0;
            }
            warnings.add(new EngineWarning(
                    "SHADOW_CACHE_POLICY_ACTIVE",
                    "Shadow cache policy (mode=" + shadowCapabilityModeLastFrame
                            + ", staticCacheActive=" + "cached_static_dynamic".equals(shadowCapabilityModeLastFrame)
                            + ", dynamicOverlayActive=" + (shadowCacheMissCountLastFrame > 0 || shadowCadenceDeferredLocalLightsLastFrame > 0)
                            + ", cacheHitCount=" + shadowCacheHitCountLastFrame
                            + ", cacheMissCount=" + shadowCacheMissCountLastFrame
                            + ", cacheEvictions=" + shadowCacheEvictionCountLastFrame
                            + ", cacheHitRatio=" + shadowCacheHitRatioLastFrame
                            + ", churnRatio=" + shadowCacheChurnRatioLastFrame
                            + ", invalidationReason=" + shadowCacheInvalidationReasonLastFrame
                            + ", churnWarnMax=" + shadowCacheChurnWarnMax
                            + ", missWarnMax=" + shadowCacheMissWarnMax
                            + ", warnMinFrames=" + shadowCacheWarnMinFrames
                            + ", cooldownRemaining=" + shadowCacheWarnCooldownRemaining + ")"
            ));
            if (cacheEnvelopeNow
                    && shadowCacheHighStreak >= shadowCacheWarnMinFrames
                    && shadowCacheWarnCooldownRemaining == 0) {
                warnings.add(new EngineWarning(
                        "SHADOW_CACHE_CHURN_HIGH",
                        "Shadow cache envelope breached (cacheMissCount=" + shadowCacheMissCountLastFrame
                                + ", cacheEvictions=" + shadowCacheEvictionCountLastFrame
                                + ", cacheHitRatio=" + shadowCacheHitRatioLastFrame
                                + ", churnRatio=" + shadowCacheChurnRatioLastFrame
                                + ", churnWarnMax=" + shadowCacheChurnWarnMax
                                + ", missWarnMax=" + shadowCacheMissWarnMax
                                + ", highStreak=" + shadowCacheHighStreak
                                + ", warnMinFrames=" + shadowCacheWarnMinFrames
                                + ", cooldownFrames=" + shadowCacheWarnCooldownFrames
                                + ", invalidationReason=" + shadowCacheInvalidationReasonLastFrame + ")"
                ));
                shadowCacheWarnCooldownRemaining = shadowCacheWarnCooldownFrames;
            }
            boolean hybridModeActive = "hybrid_cascade_contact_rt".equals(shadowCapabilityModeLastFrame);
            double cascadeWeight = 1.0;
            double contactWeight = currentShadows.contactShadowsRequested() ? 0.6 : 0.0;
            double rtWeight = "off".equals(currentShadows.rtShadowMode()) ? 0.0 : (currentShadows.rtShadowActive() ? 0.8 : 0.2);
            double hybridWeightTotal = Math.max(1e-6, cascadeWeight + contactWeight + rtWeight);
            shadowHybridCascadeShareLastFrame = cascadeWeight / hybridWeightTotal;
            shadowHybridContactShareLastFrame = contactWeight / hybridWeightTotal;
            shadowHybridRtShareLastFrame = rtWeight / hybridWeightTotal;
            boolean hybridEnvelopeNow = hybridModeActive
                    && (shadowHybridRtShareLastFrame < shadowHybridRtShareWarnMin
                    || shadowHybridContactShareLastFrame < shadowHybridContactShareWarnMin);
            if (hybridEnvelopeNow) {
                shadowHybridHighStreak = Math.min(10_000, shadowHybridHighStreak + 1);
                shadowHybridEnvelopeBreachedLastFrame = true;
            } else {
                shadowHybridHighStreak = 0;
            }
            warnings.add(new EngineWarning(
                    "SHADOW_HYBRID_COMPOSITION",
                    "Shadow hybrid composition (modeActive=" + hybridModeActive
                            + ", cascadeShare=" + shadowHybridCascadeShareLastFrame
                            + ", contactShare=" + shadowHybridContactShareLastFrame
                            + ", rtShare=" + shadowHybridRtShareLastFrame
                            + ", rtShareWarnMin=" + shadowHybridRtShareWarnMin
                            + ", contactShareWarnMin=" + shadowHybridContactShareWarnMin
                            + ", warnMinFrames=" + shadowHybridWarnMinFrames
                            + ", cooldownRemaining=" + shadowHybridWarnCooldownRemaining + ")"
            ));
            if (hybridEnvelopeNow
                    && shadowHybridHighStreak >= shadowHybridWarnMinFrames
                    && shadowHybridWarnCooldownRemaining == 0) {
                warnings.add(new EngineWarning(
                        "SHADOW_HYBRID_COMPOSITION_BREACH",
                        "Shadow hybrid composition envelope breached (cascadeShare=" + shadowHybridCascadeShareLastFrame
                                + ", contactShare=" + shadowHybridContactShareLastFrame
                                + ", rtShare=" + shadowHybridRtShareLastFrame
                                + ", rtShareWarnMin=" + shadowHybridRtShareWarnMin
                                + ", contactShareWarnMin=" + shadowHybridContactShareWarnMin
                                + ", highStreak=" + shadowHybridHighStreak
                                + ", warnMinFrames=" + shadowHybridWarnMinFrames
                                + ", cooldownFrames=" + shadowHybridWarnCooldownFrames + ")"
                ));
                shadowHybridWarnCooldownRemaining = shadowHybridWarnCooldownFrames;
            }
            int transparentCandidateCount = 0;
            if (currentSceneMaterials != null) {
                for (MaterialDesc material : currentSceneMaterials) {
                    if (material != null && material.alphaTested()) {
                        transparentCandidateCount++;
                    }
                }
            }
            shadowTransparentReceiverCandidateCountLastFrame = transparentCandidateCount;
            shadowTransparentReceiverCandidateRatioLastFrame = currentSceneMaterials == null || currentSceneMaterials.isEmpty()
                    ? 0.0
                    : (double) transparentCandidateCount / (double) currentSceneMaterials.size();
            shadowTransparentReceiverPolicyLastFrame = shadowTransparentReceiversRequested
                    ? (shadowTransparentReceiversSupported ? "enabled" : "fallback_opaque_only")
                    : "disabled";
            boolean transparentEnvelopeNow = shadowTransparentReceiversRequested
                    && !shadowTransparentReceiversSupported
                    && shadowTransparentReceiverCandidateRatioLastFrame > shadowTransparentReceiverCandidateRatioWarnMax;
            if (transparentEnvelopeNow) {
                shadowTransparentReceiverHighStreak = Math.min(10_000, shadowTransparentReceiverHighStreak + 1);
                shadowTransparentReceiverEnvelopeBreachedLastFrame = true;
            } else {
                shadowTransparentReceiverHighStreak = 0;
            }
            warnings.add(new EngineWarning(
                    "SHADOW_TRANSPARENT_RECEIVER_POLICY",
                    "Shadow transparent receiver policy (requested=" + shadowTransparentReceiversRequested
                            + ", supported=" + shadowTransparentReceiversSupported
                            + ", activePolicy=" + shadowTransparentReceiverPolicyLastFrame
                            + ", candidateMaterials=" + shadowTransparentReceiverCandidateCountLastFrame
                            + ", candidateRatio=" + shadowTransparentReceiverCandidateRatioLastFrame
                            + ", candidateRatioWarnMax=" + shadowTransparentReceiverCandidateRatioWarnMax
                            + ", warnMinFrames=" + shadowTransparentReceiverWarnMinFrames
                            + ", cooldownRemaining=" + shadowTransparentReceiverWarnCooldownRemaining + ")"
            ));
            if (transparentEnvelopeNow
                    && shadowTransparentReceiverHighStreak >= shadowTransparentReceiverWarnMinFrames
                    && shadowTransparentReceiverWarnCooldownRemaining == 0) {
                warnings.add(new EngineWarning(
                        "SHADOW_TRANSPARENT_RECEIVER_ENVELOPE_BREACH",
                        "Shadow transparent receiver envelope breached (requested=true, supported=false"
                                + ", candidateMaterials=" + shadowTransparentReceiverCandidateCountLastFrame
                                + ", candidateRatio=" + shadowTransparentReceiverCandidateRatioLastFrame
                                + ", candidateRatioWarnMax=" + shadowTransparentReceiverCandidateRatioWarnMax
                                + ", highStreak=" + shadowTransparentReceiverHighStreak
                                + ", warnMinFrames=" + shadowTransparentReceiverWarnMinFrames
                                + ", cooldownFrames=" + shadowTransparentReceiverWarnCooldownFrames + ")"
                ));
                shadowTransparentReceiverWarnCooldownRemaining = shadowTransparentReceiverWarnCooldownFrames;
            }
            warnings.add(new EngineWarning(
                    "SHADOW_AREA_APPROX_POLICY",
                    "Shadow area-approx policy (requested=" + shadowAreaApproxRequested
                            + ", supported=" + shadowAreaApproxSupported
                            + ", activePolicy=" + (shadowAreaApproxSupported ? "enabled" : "fallback_standard_shadow")
                            + ", requireActive=" + shadowAreaApproxRequireActive + ")"
            ));
            shadowAreaApproxBreachedLastFrame = shadowAreaApproxRequested
                    && shadowAreaApproxRequireActive
                    && !shadowAreaApproxSupported;
            if (shadowAreaApproxBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "SHADOW_AREA_APPROX_REQUIRED_UNAVAILABLE_BREACH",
                        "Shadow area-approx required but unavailable (requested=true, supported=false)"
                ));
            }
            warnings.add(new EngineWarning(
                    "SHADOW_DISTANCE_FIELD_SOFT_POLICY",
                    "Shadow distance-field policy (requested=" + shadowDistanceFieldRequested
                            + ", supported=" + shadowDistanceFieldSupported
                            + ", activePolicy=" + (shadowDistanceFieldSupported ? "enabled" : "fallback_standard_shadow")
                            + ", requireActive=" + shadowDistanceFieldRequireActive + ")"
            ));
            shadowDistanceFieldBreachedLastFrame = shadowDistanceFieldRequested
                    && shadowDistanceFieldRequireActive
                    && !shadowDistanceFieldSupported;
            if (shadowDistanceFieldBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "SHADOW_DISTANCE_FIELD_REQUIRED_UNAVAILABLE_BREACH",
                        "Shadow distance-field soft shadows required but unavailable (requested=true, supported=false)"
                ));
            }
            boolean phaseDPromotionNow =
                    !shadowCacheEnvelopeBreachedLastFrame
                            && shadowCacheWarnCooldownRemaining == 0
                            && !shadowRtEnvelopeBreachedLastFrame
                            && shadowRtWarnCooldownRemaining == 0
                            && !shadowHybridEnvelopeBreachedLastFrame
                            && shadowHybridWarnCooldownRemaining == 0
                            && !shadowTransparentReceiverEnvelopeBreachedLastFrame
                            && shadowTransparentReceiverWarnCooldownRemaining == 0
                            && !shadowAreaApproxBreachedLastFrame
                            && !shadowDistanceFieldBreachedLastFrame;
            if (phaseDPromotionNow) {
                shadowPhaseDPromotionStableStreak = Math.min(10_000, shadowPhaseDPromotionStableStreak + 1);
                shadowPhaseDPromotionReadyLastFrame =
                        shadowPhaseDPromotionStableStreak >= shadowPhaseDPromotionReadyMinFrames;
            } else {
                shadowPhaseDPromotionStableStreak = 0;
                shadowPhaseDPromotionReadyLastFrame = false;
            }
            if (shadowPhaseDPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "SHADOW_PHASED_PROMOTION_READY",
                        "Shadow Phase D promotion-ready (cacheStable=" + (!shadowCacheEnvelopeBreachedLastFrame
                                && shadowCacheWarnCooldownRemaining == 0)
                                + ", rtStable=" + (!shadowRtEnvelopeBreachedLastFrame
                                && shadowRtWarnCooldownRemaining == 0)
                                + ", hybridStable=" + (!shadowHybridEnvelopeBreachedLastFrame
                                && shadowHybridWarnCooldownRemaining == 0)
                                + ", transparentReceiverStable=" + (!shadowTransparentReceiverEnvelopeBreachedLastFrame
                                && shadowTransparentReceiverWarnCooldownRemaining == 0)
                                + ", areaApproxStable=" + (!shadowAreaApproxBreachedLastFrame)
                                + ", distanceFieldStable=" + (!shadowDistanceFieldBreachedLastFrame)
                                + ", stableStreak=" + shadowPhaseDPromotionStableStreak
                                + ", promotionReadyMinFrames=" + shadowPhaseDPromotionReadyMinFrames + ")"
                ));
            }
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
                            + " rtEffectiveDenoiseStrength=" + VulkanShadowRuntimeTuning.effectiveShadowRtDenoiseStrength(currentShadows.rtShadowMode(), shadowRtDenoiseStrength, shadowRtProductionDenoiseStrength, shadowRtDedicatedDenoiseStrength)
                            + " rtEffectiveRayLength=" + VulkanShadowRuntimeTuning.effectiveShadowRtRayLength(currentShadows.rtShadowMode(), shadowRtRayLength, shadowRtProductionRayLength, shadowRtDedicatedRayLength)
                            + " rtEffectiveSampleCount=" + VulkanShadowRuntimeTuning.effectiveShadowRtSampleCount(currentShadows.rtShadowMode(), shadowRtSampleCount, shadowRtProductionSampleCount, shadowRtDedicatedSampleCount)
                            + " normalBiasScale=" + currentShadows.normalBiasScale()
                            + " slopeBiasScale=" + currentShadows.slopeBiasScale()
            ));
            VulkanShadowCoverageMomentWarningEmitter.State shadowCoverageMomentState = new VulkanShadowCoverageMomentWarningEmitter.State();
            shadowCoverageMomentState.currentShadows = currentShadows;
            shadowCoverageMomentState.shadowSchedulerEnabled = shadowSchedulerEnabled;
            shadowCoverageMomentState.shadowMaxFacesPerFrame = shadowMaxFacesPerFrame;
            shadowCoverageMomentState.shadowPointBudgetRenderedFacesLastFrame = shadowPointBudgetRenderedFacesLastFrame;
            shadowCoverageMomentState.shadowMaxLocalLayers = shadowMaxLocalLayers;
            shadowCoverageMomentState.shadowMomentResourcesAvailable = context.hasShadowMomentResources();
            shadowCoverageMomentState.shadowMomentInitialized = context.isShadowMomentInitialized();
            VulkanShadowCoverageMomentWarningEmitter.emit(warnings, shadowCoverageMomentState);
            VulkanShadowRtWarningEmitter.State shadowRtState = new VulkanShadowRtWarningEmitter.State();
            shadowRtState.currentShadows = currentShadows;
            shadowRtState.qualityTier = qualityTier;
            shadowRtState.lastFrameGpuMs = lastFrameGpuMs;
            shadowRtState.shadowRtBvhSupported = shadowRtBvhSupported;
            shadowRtState.shadowRtTraversalSupported = shadowRtTraversalSupported;
            shadowRtState.shadowRtDenoiseStrength = shadowRtDenoiseStrength;
            shadowRtState.shadowRtProductionDenoiseStrength = shadowRtProductionDenoiseStrength;
            shadowRtState.shadowRtDedicatedDenoiseStrength = shadowRtDedicatedDenoiseStrength;
            shadowRtState.shadowRtSampleCount = shadowRtSampleCount;
            shadowRtState.shadowRtProductionSampleCount = shadowRtProductionSampleCount;
            shadowRtState.shadowRtDedicatedSampleCount = shadowRtDedicatedSampleCount;
            shadowRtState.shadowRtRayLength = shadowRtRayLength;
            shadowRtState.shadowRtProductionRayLength = shadowRtProductionRayLength;
            shadowRtState.shadowRtDedicatedRayLength = shadowRtDedicatedRayLength;
            shadowRtState.shadowRtDenoiseWarnMin = shadowRtDenoiseWarnMin;
            shadowRtState.shadowRtSampleWarnMin = shadowRtSampleWarnMin;
            shadowRtState.shadowRtPerfMaxGpuMsLow = shadowRtPerfMaxGpuMsLow;
            shadowRtState.shadowRtPerfMaxGpuMsMedium = shadowRtPerfMaxGpuMsMedium;
            shadowRtState.shadowRtPerfMaxGpuMsHigh = shadowRtPerfMaxGpuMsHigh;
            shadowRtState.shadowRtPerfMaxGpuMsUltra = shadowRtPerfMaxGpuMsUltra;
            shadowRtState.shadowRtWarnMinFrames = shadowRtWarnMinFrames;
            shadowRtState.shadowRtWarnCooldownFrames = shadowRtWarnCooldownFrames;
            shadowRtState.shadowRtWarnCooldownRemaining = shadowRtWarnCooldownRemaining;
            shadowRtState.shadowRtHighStreak = shadowRtHighStreak;
            shadowRtState.shadowRtEnvelopeBreachedLastFrame = shadowRtEnvelopeBreachedLastFrame;
            shadowRtState.shadowRtPerfGpuMsEstimateLastFrame = shadowRtPerfGpuMsEstimateLastFrame;
            shadowRtState.shadowRtPerfGpuMsWarnMaxLastFrame = shadowRtPerfGpuMsWarnMaxLastFrame;
            VulkanShadowRtWarningEmitter.emit(warnings, shadowRtState);
            shadowRtWarnCooldownRemaining = shadowRtState.shadowRtWarnCooldownRemaining;
            shadowRtHighStreak = shadowRtState.shadowRtHighStreak;
            shadowRtEnvelopeBreachedLastFrame = shadowRtState.shadowRtEnvelopeBreachedLastFrame;
            shadowRtPerfGpuMsEstimateLastFrame = shadowRtState.shadowRtPerfGpuMsEstimateLastFrame;
            shadowRtPerfGpuMsWarnMaxLastFrame = shadowRtState.shadowRtPerfGpuMsWarnMaxLastFrame;
        }
        return warnings;
    }

    private void resetReflectionWarningStateWhenDisabled() {
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
        reflectionRtRequireActiveUnmetLastFrame = false;
        reflectionRtRequireMultiBounceUnmetLastFrame = false;
        reflectionRtRequireDedicatedPipelineUnmetLastFrame = false;
        reflectionRtFallbackChainActive = "probe";
        reflectionRtTraversalSupported = false;
        reflectionRtDedicatedCapabilitySupported = false;
        reflectionRtDedicatedHardwarePipelineActive = false;
        reflectionRtBlasLifecycleState = "disabled";
        reflectionRtTlasLifecycleState = "disabled";
        reflectionRtSbtLifecycleState = "disabled";
        reflectionRtBlasObjectCount = 0;
        reflectionRtTlasInstanceCount = 0;
        reflectionRtSbtRecordCount = 0;
        reflectionRtHybridRtShare = 0.0;
        reflectionRtHybridSsrShare = 0.0;
        reflectionRtHybridProbeShare = 1.0;
        reflectionRtHybridBreachedLastFrame = false;
        reflectionRtPerfHighStreak = 0;
        reflectionRtPerfWarnCooldownRemaining = 0;
        reflectionRtPerfLastGpuMsEstimate = 0.0;
        reflectionRtPerfLastGpuMsCap = rtPerfGpuMsCapForTier(qualityTier);
        reflectionRtPerfBreachedLastFrame = false;
        reflectionRtPromotionReadyLastFrame = false;
        reflectionRtPromotionReadyHighStreak = 0;
        reflectionProbeStreamingHighStreak = 0;
        reflectionProbeStreamingWarnCooldownRemaining = 0;
        reflectionProbeStreamingBreachedLastFrame = false;
        reflectionTransparentCandidateCount = 0;
        reflectionTransparencyStageGateStatus = "not_required";
        reflectionTransparencyFallbackPath = "none";
    }

    private void resetShadowCapabilityFrameStateDefaults() {
        shadowCapabilityFeatureIdLastFrame = "unavailable";
        shadowCapabilityModeLastFrame = "unavailable";
        shadowCapabilitySignalsLastFrame = List.of();
        shadowCadenceSelectedLocalLightsLastFrame = 0;
        shadowCadenceDeferredLocalLightsLastFrame = 0;
        shadowCadenceStaleBypassCountLastFrame = 0;
        shadowCadenceDeferredRatioLastFrame = 0.0;
        shadowCadencePromotionReadyLastFrame = false;
        shadowCadenceEnvelopeBreachedLastFrame = false;
        shadowPointBudgetRenderedCubemapsLastFrame = 0;
        shadowPointBudgetRenderedFacesLastFrame = 0;
        shadowPointBudgetDeferredCountLastFrame = 0;
        shadowPointBudgetSaturationRatioLastFrame = 0.0;
        shadowPointBudgetPromotionReadyLastFrame = false;
        shadowPointBudgetEnvelopeBreachedLastFrame = false;
        shadowCacheHitCountLastFrame = 0;
        shadowCacheMissCountLastFrame = 0;
        shadowCacheEvictionCountLastFrame = 0;
        shadowCacheHitRatioLastFrame = 0.0;
        shadowCacheChurnRatioLastFrame = 0.0;
        shadowCacheInvalidationReasonLastFrame = "inactive";
        shadowCacheEnvelopeBreachedLastFrame = false;
        shadowRtPerfGpuMsEstimateLastFrame = 0.0;
        shadowRtPerfGpuMsWarnMaxLastFrame = 0.0;
        shadowRtEnvelopeBreachedLastFrame = false;
        shadowHybridCascadeShareLastFrame = 1.0;
        shadowHybridContactShareLastFrame = 0.0;
        shadowHybridRtShareLastFrame = 0.0;
        shadowHybridEnvelopeBreachedLastFrame = false;
        shadowTransparentReceiverCandidateCountLastFrame = 0;
        shadowTransparentReceiverCandidateRatioLastFrame = 0.0;
        shadowTransparentReceiverPolicyLastFrame = shadowTransparentReceiversSupported ? "enabled" : "fallback_opaque_only";
        shadowTransparentReceiverEnvelopeBreachedLastFrame = false;
        shadowAreaApproxBreachedLastFrame = false;
        shadowDistanceFieldBreachedLastFrame = false;
        shadowTopologyCandidateSpotLightsLastFrame = 0;
        shadowTopologyCandidatePointLightsLastFrame = 0;
        shadowTopologyLocalCoverageLastFrame = 0.0;
        shadowTopologySpotCoverageLastFrame = 0.0;
        shadowTopologyPointCoverageLastFrame = 0.0;
        shadowTopologyPromotionReadyLastFrame = false;
        shadowTopologyEnvelopeBreachedLastFrame = false;
        shadowSpotProjectedRequestedLastFrame = false;
        shadowSpotProjectedActiveLastFrame = false;
        shadowSpotProjectedRenderedCountLastFrame = 0;
        shadowSpotProjectedContractStatusLastFrame = "unavailable";
        shadowSpotProjectedContractBreachedLastFrame = false;
        shadowSpotProjectedPromotionReadyLastFrame = false;
        shadowPhaseAPromotionReadyLastFrame = false;
        shadowPhaseDPromotionReadyLastFrame = false;
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

    private double rtPerfGpuMsCapForTier(QualityTier tier) {
        return switch (tier) {
            case LOW -> reflectionRtPerfMaxGpuMsLow;
            case MEDIUM -> reflectionRtPerfMaxGpuMsMedium;
            case HIGH -> reflectionRtPerfMaxGpuMsHigh;
            case ULTRA -> reflectionRtPerfMaxGpuMsUltra;
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
        ReflectionOverrideSummary overrideSummary = VulkanReflectionAnalysis.summarizeReflectionOverrides(context.debugGpuMeshReflectionOverrideModes());
        int planarEligible = countPlanarEligibleFromOverrideSummary(overrideSummary);
        reflectionTransparentCandidateCount = VulkanReflectionAnalysis.summarizeReflectionTransparencyCandidates(
                currentSceneMaterials,
                reflectionTransparencyCandidateReactiveMin
        ).totalCount();
        refreshReflectionRtPathState(reflectionBaseMode);
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
        return VulkanReflectionAdaptiveMath.computeAdaptiveSeverity(
                reflectionSsrTaaEmaReject,
                reflectionSsrTaaEmaConfidence,
                reflectionSsrTaaInstabilityRejectMin,
                reflectionSsrTaaInstabilityConfidenceMax,
                reflectionSsrTaaRiskHighStreak,
                reflectionSsrTaaInstabilityWarnMinFrames
        );
    }

    private static boolean isReflectionSsrPathActive(int reflectionsMode) {
        int baseMode = reflectionsMode & REFLECTION_MODE_BASE_MASK;
        return baseMode == 1 || baseMode == 3 || baseMode == 4;
    }

    private void refreshReflectionRtPathState(int reflectionBaseMode) {
        reflectionRtLaneRequested = (currentPost.reflectionsMode() & REFLECTION_MODE_RT_REQUEST_BIT) != 0 || reflectionBaseMode == 4;
        reflectionRtTraversalSupported = mockContext || context.isHardwareRtShadowTraversalSupported();
        reflectionRtDedicatedCapabilitySupported = mockContext || context.isHardwareRtShadowBvhSupported();
        reflectionRtLaneActive = reflectionRtLaneRequested && reflectionRtSingleBounceEnabled && reflectionRtTraversalSupported;
        boolean reflectionRtMultiBounceActive = reflectionRtLaneActive && reflectionRtMultiBounceEnabled;
        reflectionRtDedicatedHardwarePipelineActive = reflectionRtLaneActive
                && reflectionRtDedicatedPipelineEnabled
                && reflectionRtDedicatedCapabilitySupported;
        reflectionRtFallbackChainActive = reflectionRtLaneActive ? "rt->ssr->probe" : "ssr->probe";
        reflectionRtRequireActiveUnmetLastFrame = reflectionRtRequireActive && reflectionRtLaneRequested && !reflectionRtLaneActive;
        reflectionRtRequireMultiBounceUnmetLastFrame =
                reflectionRtRequireMultiBounce && reflectionRtLaneRequested && !reflectionRtMultiBounceActive;
        reflectionRtRequireDedicatedPipelineUnmetLastFrame =
                reflectionRtRequireDedicatedPipeline && reflectionRtLaneRequested && !reflectionRtDedicatedHardwarePipelineActive;
        if (!(reflectionRtLaneRequested || reflectionBaseMode == 4)) {
            reflectionRtBlasLifecycleState = "disabled";
            reflectionRtTlasLifecycleState = "disabled";
            reflectionRtSbtLifecycleState = "disabled";
            reflectionRtBlasObjectCount = 0;
            reflectionRtTlasInstanceCount = 0;
            reflectionRtSbtRecordCount = 0;
            return;
        }
        if (reflectionRtDedicatedHardwarePipelineActive) {
            reflectionRtBlasLifecycleState = mockContext ? "mock_active" : "active";
            reflectionRtTlasLifecycleState = mockContext ? "mock_active" : "active";
            reflectionRtSbtLifecycleState = mockContext ? "mock_active" : "active";
            int sceneObjectEstimate = (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, plannedVisibleObjects));
            reflectionRtBlasObjectCount = sceneObjectEstimate;
            reflectionRtTlasInstanceCount = sceneObjectEstimate;
            reflectionRtSbtRecordCount = Math.max(1, sceneObjectEstimate + 2);
        } else {
            reflectionRtBlasLifecycleState = "pending";
            reflectionRtTlasLifecycleState = "pending";
            reflectionRtSbtLifecycleState = "pending";
            reflectionRtBlasObjectCount = 0;
            reflectionRtTlasInstanceCount = 0;
            reflectionRtSbtRecordCount = 0;
        }
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
        if (rtLaneActive && reflectionRtMultiBounceEnabled) {
            mode |= REFLECTION_MODE_RT_MULTI_BOUNCE_BIT;
        }
        if (rtLaneActive) {
            mode |= REFLECTION_MODE_RT_ACTIVE_BIT;
            if (reflectionRtDedicatedDenoisePipelineEnabled) {
                mode |= REFLECTION_MODE_RT_DEDICATED_DENOISE_BIT;
            }
        }
        if (reflectionRtDedicatedHardwarePipelineActive
                || (mockContext && rtLaneActive && reflectionRtDedicatedPipelineEnabled)) {
            mode |= REFLECTION_MODE_RT_DEDICATED_ACTIVE_BIT;
        }
        if (reflectionRtPromotionReadyLastFrame) {
            mode |= REFLECTION_MODE_RT_PROMOTION_READY_BIT;
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
        if (transparencyCandidatesPresent) {
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
        VulkanReflectionAdaptiveMath.ReflectionSsrTaaHistoryPolicy policy =
                VulkanReflectionAdaptiveMath.computeHistoryPolicy(
                        reflectionsEnabled,
                        taaEnabled,
                        ssrPathActive,
                        severity,
                        taaReject,
                        taaConfidence,
                        taaDrops,
                        reflectionSsrTaaDisocclusionRejectDropEventsMin,
                        reflectionSsrTaaDisocclusionRejectConfidenceMax,
                        reflectionSsrTaaHistoryRejectSeverityMin,
                        reflectionSsrTaaHistoryRejectRiskStreakMin,
                        reflectionSsrTaaRiskHighStreak,
                        reflectionSsrTaaHistoryConfidenceDecaySeverityMin
                );
        reflectionSsrTaaHistoryPolicyActive = policy.historyPolicy();
        reflectionSsrTaaReprojectionPolicyActive = policy.reprojectionPolicy();
        reflectionSsrTaaHistoryRejectBiasActive = policy.historyRejectBias();
        reflectionSsrTaaHistoryConfidenceDecayActive = policy.historyConfidenceDecay();
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
        return VulkanReflectionAdaptiveMath.snapshotTrendDiagnostics(
                reflectionAdaptiveTrendSamples,
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
                diagnostics.metadataCapacity(),
                diagnostics.frustumVisibleCount(),
                diagnostics.deferredProbeCount(),
                diagnostics.visibleUniquePathCount(),
                diagnostics.missingSlotPathCount(),
                diagnostics.lodTier0Count(),
                diagnostics.lodTier1Count(),
                diagnostics.lodTier2Count(),
                diagnostics.lodTier3Count()
        );
    }

    ReflectionProbeStreamingDiagnostics debugReflectionProbeStreamingDiagnostics() {
        VulkanContext.ReflectionProbeDiagnostics diagnostics = context.debugReflectionProbeDiagnostics();
        int effectiveStreamingBudget = Math.max(1, Math.min(reflectionProbeMaxVisible, diagnostics.metadataCapacity()));
        boolean budgetPressure = diagnostics.configuredProbeCount() > diagnostics.activeProbeCount()
                && (diagnostics.activeProbeCount() >= effectiveStreamingBudget || diagnostics.activeProbeCount() == 0);
        double missingSlotRatio = diagnostics.visibleUniquePathCount() <= 0
                ? 0.0
                : (double) diagnostics.missingSlotPathCount() / (double) diagnostics.visibleUniquePathCount();
        double deferredRatio = diagnostics.frustumVisibleCount() <= 0
                ? 0.0
                : (double) diagnostics.deferredProbeCount() / (double) diagnostics.frustumVisibleCount();
        int active = Math.max(1, diagnostics.activeProbeCount());
        double lodSkewRatio = (double) diagnostics.lodTier3Count() / (double) active;
        double memoryEstimateMb = diagnostics.activeProbeCount() * 1.5;
        return new ReflectionProbeStreamingDiagnostics(
                diagnostics.configuredProbeCount(),
                diagnostics.activeProbeCount(),
                reflectionProbeMaxVisible,
                effectiveStreamingBudget,
                reflectionProbeUpdateCadenceFrames,
                reflectionProbeLodDepthScale,
                diagnostics.frustumVisibleCount(),
                diagnostics.deferredProbeCount(),
                diagnostics.visibleUniquePathCount(),
                diagnostics.missingSlotPathCount(),
                missingSlotRatio,
                deferredRatio,
                lodSkewRatio,
                reflectionProbeStreamingMemoryBudgetMb,
                memoryEstimateMb,
                reflectionProbeStreamingHighStreak,
                reflectionProbeStreamingWarnMinFrames,
                reflectionProbeStreamingWarnCooldownFrames,
                reflectionProbeStreamingWarnCooldownRemaining,
                budgetPressure,
                reflectionProbeStreamingBreachedLastFrame
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
        ReflectionOverrideSummary summary = VulkanReflectionAnalysis.summarizeReflectionOverrides(context.debugGpuMeshReflectionOverrideModes());
        int total = Math.max(1, summary.totalCount());
        return new ReflectionOverridePolicyDiagnostics(
                summary.autoCount(),
                summary.probeOnlyCount(),
                summary.ssrOnlyCount(),
                summary.otherCount(),
                (double) summary.probeOnlyCount() / (double) total,
                (double) summary.ssrOnlyCount() / (double) total,
                reflectionOverrideProbeOnlyRatioWarnMax,
                reflectionOverrideSsrOnlyRatioWarnMax,
                reflectionOverrideOtherWarnMax,
                reflectionOverrideHighStreak,
                reflectionOverrideWarnMinFrames,
                reflectionOverrideWarnCooldownFrames,
                reflectionOverrideWarnCooldownRemaining,
                reflectionOverrideBreachedLastFrame,
                "probe_only|ssr_only"
        );
    }

    ReflectionContactHardeningDiagnostics debugReflectionContactHardeningDiagnostics() {
        return new ReflectionContactHardeningDiagnostics(
                reflectionContactHardeningActiveLastFrame,
                reflectionContactHardeningEstimatedStrengthLastFrame,
                reflectionContactHardeningMinSsrStrength,
                reflectionContactHardeningMinSsrMaxRoughness,
                reflectionContactHardeningHighStreak,
                reflectionContactHardeningWarnMinFrames,
                reflectionContactHardeningWarnCooldownFrames,
                reflectionContactHardeningWarnCooldownRemaining,
                reflectionContactHardeningBreachedLastFrame
        );
    }

    ReflectionRtPathDiagnostics debugReflectionRtPathDiagnostics() {
        return new ReflectionRtPathDiagnostics(
                reflectionRtLaneRequested,
                reflectionRtLaneActive,
                reflectionRtSingleBounceEnabled,
                reflectionRtMultiBounceEnabled,
                reflectionRtRequireActive,
                reflectionRtRequireActiveUnmetLastFrame,
                reflectionRtRequireMultiBounce,
                reflectionRtRequireMultiBounceUnmetLastFrame,
                reflectionRtRequireDedicatedPipeline,
                reflectionRtRequireDedicatedPipelineUnmetLastFrame,
                reflectionRtDedicatedPipelineEnabled,
                reflectionRtTraversalSupported,
                reflectionRtDedicatedCapabilitySupported,
                reflectionRtDedicatedHardwarePipelineActive,
                reflectionRtDedicatedDenoisePipelineEnabled,
                reflectionRtDenoiseStrength,
                reflectionRtFallbackChainActive
        );
    }

    ReflectionRtPerfDiagnostics debugReflectionRtPerfDiagnostics() {
        return new ReflectionRtPerfDiagnostics(
                reflectionRtPerfLastGpuMsEstimate,
                reflectionRtPerfLastGpuMsCap,
                reflectionRtPerfHighStreak,
                reflectionRtPerfWarnMinFrames,
                reflectionRtPerfWarnCooldownFrames,
                reflectionRtPerfWarnCooldownRemaining,
                reflectionRtPerfBreachedLastFrame
        );
    }

    ReflectionRtPipelineDiagnostics debugReflectionRtPipelineDiagnostics() {
        return new ReflectionRtPipelineDiagnostics(
                reflectionRtBlasLifecycleState,
                reflectionRtTlasLifecycleState,
                reflectionRtSbtLifecycleState,
                reflectionRtBlasObjectCount,
                reflectionRtTlasInstanceCount,
                reflectionRtSbtRecordCount
        );
    }

    ReflectionRtHybridDiagnostics debugReflectionRtHybridDiagnostics() {
        return new ReflectionRtHybridDiagnostics(
                reflectionRtHybridRtShare,
                reflectionRtHybridSsrShare,
                reflectionRtHybridProbeShare,
                reflectionRtHybridProbeShareWarnMax,
                reflectionRtHybridHighStreak,
                reflectionRtHybridWarnMinFrames,
                reflectionRtHybridWarnCooldownFrames,
                reflectionRtHybridWarnCooldownRemaining,
                reflectionRtHybridBreachedLastFrame
        );
    }

    ReflectionRtDenoiseDiagnostics debugReflectionRtDenoiseDiagnostics() {
        return new ReflectionRtDenoiseDiagnostics(
                reflectionRtDenoiseSpatialVariance,
                reflectionRtDenoiseSpatialVarianceWarnMax,
                reflectionRtDenoiseTemporalLag,
                reflectionRtDenoiseTemporalLagWarnMax,
                reflectionRtDenoiseHighStreak,
                reflectionRtDenoiseWarnMinFrames,
                reflectionRtDenoiseWarnCooldownFrames,
                reflectionRtDenoiseWarnCooldownRemaining,
                reflectionRtDenoiseBreachedLastFrame
        );
    }

    ReflectionRtAsBudgetDiagnostics debugReflectionRtAsBudgetDiagnostics() {
        return new ReflectionRtAsBudgetDiagnostics(
                reflectionRtAsBuildGpuMsEstimate,
                reflectionRtAsBuildGpuMsWarnMax,
                reflectionRtAsMemoryMbEstimate,
                reflectionRtAsMemoryBudgetMb,
                reflectionRtAsBudgetHighStreak,
                reflectionRtPerfWarnMinFrames,
                reflectionRtPerfWarnCooldownFrames,
                reflectionRtAsBudgetWarnCooldownRemaining,
                reflectionRtAsBudgetBreachedLastFrame
        );
    }

    ReflectionRtPromotionDiagnostics debugReflectionRtPromotionDiagnostics() {
        return new ReflectionRtPromotionDiagnostics(
                reflectionRtPromotionReadyLastFrame,
                reflectionRtPromotionReadyHighStreak,
                reflectionRtPromotionReadyMinFrames,
                reflectionRtDedicatedHardwarePipelineActive,
                reflectionRtPerfBreachedLastFrame,
                reflectionRtHybridBreachedLastFrame,
                reflectionRtDenoiseBreachedLastFrame,
                reflectionRtAsBudgetBreachedLastFrame,
                reflectionTransparencyStageGateStatus
        );
    }

    ReflectionTransparencyDiagnostics debugReflectionTransparencyDiagnostics() {
        return new ReflectionTransparencyDiagnostics(
                reflectionTransparentCandidateCount,
                reflectionTransparencyAlphaTestedCandidateCount,
                reflectionTransparencyReactiveCandidateCount,
                reflectionTransparencyProbeOnlyCandidateCount,
                reflectionTransparencyStageGateStatus,
                reflectionTransparencyFallbackPath,
                reflectionRtLaneActive,
                reflectionTransparencyCandidateReactiveMin,
                reflectionTransparencyProbeOnlyRatioWarnMax,
                reflectionTransparencyHighStreak,
                reflectionTransparencyWarnMinFrames,
                reflectionTransparencyWarnCooldownFrames,
                reflectionTransparencyWarnCooldownRemaining,
                reflectionTransparencyBreachedLastFrame
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

    private void applyReflectionProfileTelemetryDefaults(Map<String, String> backendOptions) {
        VulkanReflectionTelemetryDefaults.State state = new VulkanReflectionTelemetryDefaults.State();
        state.reflectionContactHardeningMinSsrMaxRoughness = reflectionContactHardeningMinSsrMaxRoughness;
        state.reflectionContactHardeningMinSsrStrength = reflectionContactHardeningMinSsrStrength;
        state.reflectionContactHardeningWarnCooldownFrames = reflectionContactHardeningWarnCooldownFrames;
        state.reflectionContactHardeningWarnMinFrames = reflectionContactHardeningWarnMinFrames;
        state.reflectionOverrideOtherWarnMax = reflectionOverrideOtherWarnMax;
        state.reflectionOverrideProbeOnlyRatioWarnMax = reflectionOverrideProbeOnlyRatioWarnMax;
        state.reflectionOverrideSsrOnlyRatioWarnMax = reflectionOverrideSsrOnlyRatioWarnMax;
        state.reflectionOverrideWarnCooldownFrames = reflectionOverrideWarnCooldownFrames;
        state.reflectionOverrideWarnMinFrames = reflectionOverrideWarnMinFrames;
        state.reflectionPlanarEnvelopeCoverageRatioWarnMin = reflectionPlanarEnvelopeCoverageRatioWarnMin;
        state.reflectionPlanarEnvelopePlaneDeltaWarnMax = reflectionPlanarEnvelopePlaneDeltaWarnMax;
        state.reflectionPlanarEnvelopeWarnCooldownFrames = reflectionPlanarEnvelopeWarnCooldownFrames;
        state.reflectionPlanarEnvelopeWarnMinFrames = reflectionPlanarEnvelopeWarnMinFrames;
        state.reflectionPlanarPerfDrawInflationWarnMax = reflectionPlanarPerfDrawInflationWarnMax;
        state.reflectionPlanarPerfMaxGpuMsHigh = reflectionPlanarPerfMaxGpuMsHigh;
        state.reflectionPlanarPerfMaxGpuMsLow = reflectionPlanarPerfMaxGpuMsLow;
        state.reflectionPlanarPerfMaxGpuMsMedium = reflectionPlanarPerfMaxGpuMsMedium;
        state.reflectionPlanarPerfMaxGpuMsUltra = reflectionPlanarPerfMaxGpuMsUltra;
        state.reflectionPlanarPerfMemoryBudgetMb = reflectionPlanarPerfMemoryBudgetMb;
        state.reflectionPlanarPerfWarnCooldownFrames = reflectionPlanarPerfWarnCooldownFrames;
        state.reflectionPlanarPerfWarnMinFrames = reflectionPlanarPerfWarnMinFrames;
        state.reflectionProbeChurnWarnCooldownFrames = reflectionProbeChurnWarnCooldownFrames;
        state.reflectionProbeChurnWarnMinDelta = reflectionProbeChurnWarnMinDelta;
        state.reflectionProbeChurnWarnMinStreak = reflectionProbeChurnWarnMinStreak;
        state.reflectionProbeQualityBleedRiskWarnMaxPairs = reflectionProbeQualityBleedRiskWarnMaxPairs;
        state.reflectionProbeQualityBoxProjectionMinRatio = reflectionProbeQualityBoxProjectionMinRatio;
        state.reflectionProbeQualityInvalidBlendDistanceWarnMax = reflectionProbeQualityInvalidBlendDistanceWarnMax;
        state.reflectionProbeQualityMinOverlapPairsWhenMultiple = reflectionProbeQualityMinOverlapPairsWhenMultiple;
        state.reflectionProbeQualityOverlapCoverageWarnMin = reflectionProbeQualityOverlapCoverageWarnMin;
        state.reflectionProbeQualityOverlapWarnMaxPairs = reflectionProbeQualityOverlapWarnMaxPairs;
        state.reflectionProbeStreamingDeferredRatioWarnMax = reflectionProbeStreamingDeferredRatioWarnMax;
        state.reflectionProbeStreamingLodSkewWarnMax = reflectionProbeStreamingLodSkewWarnMax;
        state.reflectionProbeStreamingMemoryBudgetMb = reflectionProbeStreamingMemoryBudgetMb;
        state.reflectionProbeStreamingMissRatioWarnMax = reflectionProbeStreamingMissRatioWarnMax;
        state.reflectionProbeStreamingWarnCooldownFrames = reflectionProbeStreamingWarnCooldownFrames;
        state.reflectionProbeStreamingWarnMinFrames = reflectionProbeStreamingWarnMinFrames;
        state.reflectionRtAsBuildGpuMsWarnMax = reflectionRtAsBuildGpuMsWarnMax;
        state.reflectionRtAsMemoryBudgetMb = reflectionRtAsMemoryBudgetMb;
        state.reflectionRtDenoiseSpatialVarianceWarnMax = reflectionRtDenoiseSpatialVarianceWarnMax;
        state.reflectionRtDenoiseStrength = reflectionRtDenoiseStrength;
        state.reflectionRtDenoiseTemporalLagWarnMax = reflectionRtDenoiseTemporalLagWarnMax;
        state.reflectionRtDenoiseWarnCooldownFrames = reflectionRtDenoiseWarnCooldownFrames;
        state.reflectionRtDenoiseWarnMinFrames = reflectionRtDenoiseWarnMinFrames;
        state.reflectionRtHybridProbeShareWarnMax = reflectionRtHybridProbeShareWarnMax;
        state.reflectionRtHybridWarnCooldownFrames = reflectionRtHybridWarnCooldownFrames;
        state.reflectionRtHybridWarnMinFrames = reflectionRtHybridWarnMinFrames;
        state.reflectionRtMultiBounceEnabled = reflectionRtMultiBounceEnabled;
        state.reflectionRtPerfMaxGpuMsHigh = reflectionRtPerfMaxGpuMsHigh;
        state.reflectionRtPerfMaxGpuMsLow = reflectionRtPerfMaxGpuMsLow;
        state.reflectionRtPerfMaxGpuMsMedium = reflectionRtPerfMaxGpuMsMedium;
        state.reflectionRtPerfMaxGpuMsUltra = reflectionRtPerfMaxGpuMsUltra;
        state.reflectionRtPerfWarnCooldownFrames = reflectionRtPerfWarnCooldownFrames;
        state.reflectionRtPerfWarnMinFrames = reflectionRtPerfWarnMinFrames;
        state.reflectionRtSingleBounceEnabled = reflectionRtSingleBounceEnabled;
        state.reflectionSsrTaaAdaptiveEnabled = reflectionSsrTaaAdaptiveEnabled;
        state.reflectionSsrTaaAdaptiveSsrStrengthScaleMin = reflectionSsrTaaAdaptiveSsrStrengthScaleMin;
        state.reflectionSsrTaaAdaptiveStepScaleBoostMax = reflectionSsrTaaAdaptiveStepScaleBoostMax;
        state.reflectionSsrTaaAdaptiveTemporalBoostMax = reflectionSsrTaaAdaptiveTemporalBoostMax;
        state.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
        state.reflectionSsrTaaAdaptiveTrendSloHighRatioMax = reflectionSsrTaaAdaptiveTrendSloHighRatioMax;
        state.reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax;
        state.reflectionSsrTaaAdaptiveTrendSloMinSamples = reflectionSsrTaaAdaptiveTrendSloMinSamples;
        state.reflectionSsrTaaAdaptiveTrendWarnCooldownFrames = reflectionSsrTaaAdaptiveTrendWarnCooldownFrames;
        state.reflectionSsrTaaAdaptiveTrendWarnMinFrames = reflectionSsrTaaAdaptiveTrendWarnMinFrames;
        state.reflectionSsrTaaAdaptiveTrendWarnMinSamples = reflectionSsrTaaAdaptiveTrendWarnMinSamples;
        state.reflectionSsrTaaAdaptiveTrendWindowFrames = reflectionSsrTaaAdaptiveTrendWindowFrames;
        state.reflectionSsrTaaDisocclusionRejectConfidenceMax = reflectionSsrTaaDisocclusionRejectConfidenceMax;
        state.reflectionSsrTaaDisocclusionRejectDropEventsMin = reflectionSsrTaaDisocclusionRejectDropEventsMin;
        state.reflectionSsrTaaInstabilityConfidenceMax = reflectionSsrTaaInstabilityConfidenceMax;
        state.reflectionSsrTaaInstabilityDropEventsMin = reflectionSsrTaaInstabilityDropEventsMin;
        state.reflectionSsrTaaInstabilityRejectMin = reflectionSsrTaaInstabilityRejectMin;
        state.reflectionSsrTaaInstabilityWarnCooldownFrames = reflectionSsrTaaInstabilityWarnCooldownFrames;
        state.reflectionSsrTaaInstabilityWarnMinFrames = reflectionSsrTaaInstabilityWarnMinFrames;
        state.reflectionSsrTaaRiskEmaAlpha = reflectionSsrTaaRiskEmaAlpha;
        state.reflectionTransparencyCandidateReactiveMin = reflectionTransparencyCandidateReactiveMin;
        state.reflectionTransparencyProbeOnlyRatioWarnMax = reflectionTransparencyProbeOnlyRatioWarnMax;
        state.reflectionTransparencyWarnCooldownFrames = reflectionTransparencyWarnCooldownFrames;
        state.reflectionTransparencyWarnMinFrames = reflectionTransparencyWarnMinFrames;

        VulkanReflectionTelemetryDefaults.apply(state, reflectionProfile, backendOptions);

        reflectionContactHardeningMinSsrMaxRoughness = state.reflectionContactHardeningMinSsrMaxRoughness;
        reflectionContactHardeningMinSsrStrength = state.reflectionContactHardeningMinSsrStrength;
        reflectionContactHardeningWarnCooldownFrames = state.reflectionContactHardeningWarnCooldownFrames;
        reflectionContactHardeningWarnMinFrames = state.reflectionContactHardeningWarnMinFrames;
        reflectionOverrideOtherWarnMax = state.reflectionOverrideOtherWarnMax;
        reflectionOverrideProbeOnlyRatioWarnMax = state.reflectionOverrideProbeOnlyRatioWarnMax;
        reflectionOverrideSsrOnlyRatioWarnMax = state.reflectionOverrideSsrOnlyRatioWarnMax;
        reflectionOverrideWarnCooldownFrames = state.reflectionOverrideWarnCooldownFrames;
        reflectionOverrideWarnMinFrames = state.reflectionOverrideWarnMinFrames;
        reflectionPlanarEnvelopeCoverageRatioWarnMin = state.reflectionPlanarEnvelopeCoverageRatioWarnMin;
        reflectionPlanarEnvelopePlaneDeltaWarnMax = state.reflectionPlanarEnvelopePlaneDeltaWarnMax;
        reflectionPlanarEnvelopeWarnCooldownFrames = state.reflectionPlanarEnvelopeWarnCooldownFrames;
        reflectionPlanarEnvelopeWarnMinFrames = state.reflectionPlanarEnvelopeWarnMinFrames;
        reflectionPlanarPerfDrawInflationWarnMax = state.reflectionPlanarPerfDrawInflationWarnMax;
        reflectionPlanarPerfMaxGpuMsHigh = state.reflectionPlanarPerfMaxGpuMsHigh;
        reflectionPlanarPerfMaxGpuMsLow = state.reflectionPlanarPerfMaxGpuMsLow;
        reflectionPlanarPerfMaxGpuMsMedium = state.reflectionPlanarPerfMaxGpuMsMedium;
        reflectionPlanarPerfMaxGpuMsUltra = state.reflectionPlanarPerfMaxGpuMsUltra;
        reflectionPlanarPerfMemoryBudgetMb = state.reflectionPlanarPerfMemoryBudgetMb;
        reflectionPlanarPerfWarnCooldownFrames = state.reflectionPlanarPerfWarnCooldownFrames;
        reflectionPlanarPerfWarnMinFrames = state.reflectionPlanarPerfWarnMinFrames;
        reflectionProbeChurnWarnCooldownFrames = state.reflectionProbeChurnWarnCooldownFrames;
        reflectionProbeChurnWarnMinDelta = state.reflectionProbeChurnWarnMinDelta;
        reflectionProbeChurnWarnMinStreak = state.reflectionProbeChurnWarnMinStreak;
        reflectionProbeQualityBleedRiskWarnMaxPairs = state.reflectionProbeQualityBleedRiskWarnMaxPairs;
        reflectionProbeQualityBoxProjectionMinRatio = state.reflectionProbeQualityBoxProjectionMinRatio;
        reflectionProbeQualityInvalidBlendDistanceWarnMax = state.reflectionProbeQualityInvalidBlendDistanceWarnMax;
        reflectionProbeQualityMinOverlapPairsWhenMultiple = state.reflectionProbeQualityMinOverlapPairsWhenMultiple;
        reflectionProbeQualityOverlapCoverageWarnMin = state.reflectionProbeQualityOverlapCoverageWarnMin;
        reflectionProbeQualityOverlapWarnMaxPairs = state.reflectionProbeQualityOverlapWarnMaxPairs;
        reflectionProbeStreamingDeferredRatioWarnMax = state.reflectionProbeStreamingDeferredRatioWarnMax;
        reflectionProbeStreamingLodSkewWarnMax = state.reflectionProbeStreamingLodSkewWarnMax;
        reflectionProbeStreamingMemoryBudgetMb = state.reflectionProbeStreamingMemoryBudgetMb;
        reflectionProbeStreamingMissRatioWarnMax = state.reflectionProbeStreamingMissRatioWarnMax;
        reflectionProbeStreamingWarnCooldownFrames = state.reflectionProbeStreamingWarnCooldownFrames;
        reflectionProbeStreamingWarnMinFrames = state.reflectionProbeStreamingWarnMinFrames;
        reflectionRtAsBuildGpuMsWarnMax = state.reflectionRtAsBuildGpuMsWarnMax;
        reflectionRtAsMemoryBudgetMb = state.reflectionRtAsMemoryBudgetMb;
        reflectionRtDenoiseSpatialVarianceWarnMax = state.reflectionRtDenoiseSpatialVarianceWarnMax;
        reflectionRtDenoiseStrength = state.reflectionRtDenoiseStrength;
        reflectionRtDenoiseTemporalLagWarnMax = state.reflectionRtDenoiseTemporalLagWarnMax;
        reflectionRtDenoiseWarnCooldownFrames = state.reflectionRtDenoiseWarnCooldownFrames;
        reflectionRtDenoiseWarnMinFrames = state.reflectionRtDenoiseWarnMinFrames;
        reflectionRtHybridProbeShareWarnMax = state.reflectionRtHybridProbeShareWarnMax;
        reflectionRtHybridWarnCooldownFrames = state.reflectionRtHybridWarnCooldownFrames;
        reflectionRtHybridWarnMinFrames = state.reflectionRtHybridWarnMinFrames;
        reflectionRtMultiBounceEnabled = state.reflectionRtMultiBounceEnabled;
        reflectionRtPerfMaxGpuMsHigh = state.reflectionRtPerfMaxGpuMsHigh;
        reflectionRtPerfMaxGpuMsLow = state.reflectionRtPerfMaxGpuMsLow;
        reflectionRtPerfMaxGpuMsMedium = state.reflectionRtPerfMaxGpuMsMedium;
        reflectionRtPerfMaxGpuMsUltra = state.reflectionRtPerfMaxGpuMsUltra;
        reflectionRtPerfWarnCooldownFrames = state.reflectionRtPerfWarnCooldownFrames;
        reflectionRtPerfWarnMinFrames = state.reflectionRtPerfWarnMinFrames;
        reflectionRtSingleBounceEnabled = state.reflectionRtSingleBounceEnabled;
        reflectionSsrTaaAdaptiveEnabled = state.reflectionSsrTaaAdaptiveEnabled;
        reflectionSsrTaaAdaptiveSsrStrengthScaleMin = state.reflectionSsrTaaAdaptiveSsrStrengthScaleMin;
        reflectionSsrTaaAdaptiveStepScaleBoostMax = state.reflectionSsrTaaAdaptiveStepScaleBoostMax;
        reflectionSsrTaaAdaptiveTemporalBoostMax = state.reflectionSsrTaaAdaptiveTemporalBoostMax;
        reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = state.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
        reflectionSsrTaaAdaptiveTrendSloHighRatioMax = state.reflectionSsrTaaAdaptiveTrendSloHighRatioMax;
        reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = state.reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax;
        reflectionSsrTaaAdaptiveTrendSloMinSamples = state.reflectionSsrTaaAdaptiveTrendSloMinSamples;
        reflectionSsrTaaAdaptiveTrendWarnCooldownFrames = state.reflectionSsrTaaAdaptiveTrendWarnCooldownFrames;
        reflectionSsrTaaAdaptiveTrendWarnMinFrames = state.reflectionSsrTaaAdaptiveTrendWarnMinFrames;
        reflectionSsrTaaAdaptiveTrendWarnMinSamples = state.reflectionSsrTaaAdaptiveTrendWarnMinSamples;
        reflectionSsrTaaAdaptiveTrendWindowFrames = state.reflectionSsrTaaAdaptiveTrendWindowFrames;
        reflectionSsrTaaDisocclusionRejectConfidenceMax = state.reflectionSsrTaaDisocclusionRejectConfidenceMax;
        reflectionSsrTaaDisocclusionRejectDropEventsMin = state.reflectionSsrTaaDisocclusionRejectDropEventsMin;
        reflectionSsrTaaInstabilityConfidenceMax = state.reflectionSsrTaaInstabilityConfidenceMax;
        reflectionSsrTaaInstabilityDropEventsMin = state.reflectionSsrTaaInstabilityDropEventsMin;
        reflectionSsrTaaInstabilityRejectMin = state.reflectionSsrTaaInstabilityRejectMin;
        reflectionSsrTaaInstabilityWarnCooldownFrames = state.reflectionSsrTaaInstabilityWarnCooldownFrames;
        reflectionSsrTaaInstabilityWarnMinFrames = state.reflectionSsrTaaInstabilityWarnMinFrames;
        reflectionSsrTaaRiskEmaAlpha = state.reflectionSsrTaaRiskEmaAlpha;
        reflectionTransparencyCandidateReactiveMin = state.reflectionTransparencyCandidateReactiveMin;
        reflectionTransparencyProbeOnlyRatioWarnMax = state.reflectionTransparencyProbeOnlyRatioWarnMax;
        reflectionTransparencyWarnCooldownFrames = state.reflectionTransparencyWarnCooldownFrames;
        reflectionTransparencyWarnMinFrames = state.reflectionTransparencyWarnMinFrames;
    }

    private void applyShadowTelemetryProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        VulkanShadowTelemetryDefaults.State state = new VulkanShadowTelemetryDefaults.State();
        state.shadowCadenceWarnDeferredRatioMax = shadowCadenceWarnDeferredRatioMax;
        state.shadowCadenceWarnMinFrames = shadowCadenceWarnMinFrames;
        state.shadowCadenceWarnCooldownFrames = shadowCadenceWarnCooldownFrames;
        state.shadowCadencePromotionReadyMinFrames = shadowCadencePromotionReadyMinFrames;
        state.shadowPointFaceBudgetWarnSaturationMin = shadowPointFaceBudgetWarnSaturationMin;
        state.shadowPointFaceBudgetWarnMinFrames = shadowPointFaceBudgetWarnMinFrames;
        state.shadowPointFaceBudgetWarnCooldownFrames = shadowPointFaceBudgetWarnCooldownFrames;
        state.shadowPointFaceBudgetPromotionReadyMinFrames = shadowPointFaceBudgetPromotionReadyMinFrames;
        state.shadowCacheChurnWarnMax = shadowCacheChurnWarnMax;
        state.shadowCacheMissWarnMax = shadowCacheMissWarnMax;
        state.shadowCacheWarnMinFrames = shadowCacheWarnMinFrames;
        state.shadowCacheWarnCooldownFrames = shadowCacheWarnCooldownFrames;
        state.shadowRtDenoiseWarnMin = shadowRtDenoiseWarnMin;
        state.shadowRtSampleWarnMin = shadowRtSampleWarnMin;
        state.shadowRtPerfMaxGpuMsLow = shadowRtPerfMaxGpuMsLow;
        state.shadowRtPerfMaxGpuMsMedium = shadowRtPerfMaxGpuMsMedium;
        state.shadowRtPerfMaxGpuMsHigh = shadowRtPerfMaxGpuMsHigh;
        state.shadowRtPerfMaxGpuMsUltra = shadowRtPerfMaxGpuMsUltra;
        state.shadowRtWarnMinFrames = shadowRtWarnMinFrames;
        state.shadowRtWarnCooldownFrames = shadowRtWarnCooldownFrames;
        state.shadowHybridRtShareWarnMin = shadowHybridRtShareWarnMin;
        state.shadowHybridContactShareWarnMin = shadowHybridContactShareWarnMin;
        state.shadowHybridWarnMinFrames = shadowHybridWarnMinFrames;
        state.shadowHybridWarnCooldownFrames = shadowHybridWarnCooldownFrames;
        state.shadowTransparentReceiverCandidateRatioWarnMax = shadowTransparentReceiverCandidateRatioWarnMax;
        state.shadowTransparentReceiverWarnMinFrames = shadowTransparentReceiverWarnMinFrames;
        state.shadowTransparentReceiverWarnCooldownFrames = shadowTransparentReceiverWarnCooldownFrames;
        state.shadowSpotProjectedPromotionReadyMinFrames = shadowSpotProjectedPromotionReadyMinFrames;
        state.shadowTopologyLocalCoverageWarnMin = shadowTopologyLocalCoverageWarnMin;
        state.shadowTopologySpotCoverageWarnMin = shadowTopologySpotCoverageWarnMin;
        state.shadowTopologyPointCoverageWarnMin = shadowTopologyPointCoverageWarnMin;
        state.shadowTopologyWarnMinFrames = shadowTopologyWarnMinFrames;
        state.shadowTopologyWarnCooldownFrames = shadowTopologyWarnCooldownFrames;
        state.shadowTopologyPromotionReadyMinFrames = shadowTopologyPromotionReadyMinFrames;
        state.shadowPhaseAPromotionReadyMinFrames = shadowPhaseAPromotionReadyMinFrames;
        state.shadowPhaseDPromotionReadyMinFrames = shadowPhaseDPromotionReadyMinFrames;

        VulkanShadowTelemetryDefaults.apply(state, backendOptions, tier);

        shadowCadenceWarnDeferredRatioMax = state.shadowCadenceWarnDeferredRatioMax;
        shadowCadenceWarnMinFrames = state.shadowCadenceWarnMinFrames;
        shadowCadenceWarnCooldownFrames = state.shadowCadenceWarnCooldownFrames;
        shadowCadencePromotionReadyMinFrames = state.shadowCadencePromotionReadyMinFrames;
        shadowPointFaceBudgetWarnSaturationMin = state.shadowPointFaceBudgetWarnSaturationMin;
        shadowPointFaceBudgetWarnMinFrames = state.shadowPointFaceBudgetWarnMinFrames;
        shadowPointFaceBudgetWarnCooldownFrames = state.shadowPointFaceBudgetWarnCooldownFrames;
        shadowPointFaceBudgetPromotionReadyMinFrames = state.shadowPointFaceBudgetPromotionReadyMinFrames;
        shadowCacheChurnWarnMax = state.shadowCacheChurnWarnMax;
        shadowCacheMissWarnMax = state.shadowCacheMissWarnMax;
        shadowCacheWarnMinFrames = state.shadowCacheWarnMinFrames;
        shadowCacheWarnCooldownFrames = state.shadowCacheWarnCooldownFrames;
        shadowRtDenoiseWarnMin = state.shadowRtDenoiseWarnMin;
        shadowRtSampleWarnMin = state.shadowRtSampleWarnMin;
        shadowRtPerfMaxGpuMsLow = state.shadowRtPerfMaxGpuMsLow;
        shadowRtPerfMaxGpuMsMedium = state.shadowRtPerfMaxGpuMsMedium;
        shadowRtPerfMaxGpuMsHigh = state.shadowRtPerfMaxGpuMsHigh;
        shadowRtPerfMaxGpuMsUltra = state.shadowRtPerfMaxGpuMsUltra;
        shadowRtWarnMinFrames = state.shadowRtWarnMinFrames;
        shadowRtWarnCooldownFrames = state.shadowRtWarnCooldownFrames;
        shadowHybridRtShareWarnMin = state.shadowHybridRtShareWarnMin;
        shadowHybridContactShareWarnMin = state.shadowHybridContactShareWarnMin;
        shadowHybridWarnMinFrames = state.shadowHybridWarnMinFrames;
        shadowHybridWarnCooldownFrames = state.shadowHybridWarnCooldownFrames;
        shadowTransparentReceiverCandidateRatioWarnMax = state.shadowTransparentReceiverCandidateRatioWarnMax;
        shadowTransparentReceiverWarnMinFrames = state.shadowTransparentReceiverWarnMinFrames;
        shadowTransparentReceiverWarnCooldownFrames = state.shadowTransparentReceiverWarnCooldownFrames;
        shadowSpotProjectedPromotionReadyMinFrames = state.shadowSpotProjectedPromotionReadyMinFrames;
        shadowTopologyLocalCoverageWarnMin = state.shadowTopologyLocalCoverageWarnMin;
        shadowTopologySpotCoverageWarnMin = state.shadowTopologySpotCoverageWarnMin;
        shadowTopologyPointCoverageWarnMin = state.shadowTopologyPointCoverageWarnMin;
        shadowTopologyWarnMinFrames = state.shadowTopologyWarnMinFrames;
        shadowTopologyWarnCooldownFrames = state.shadowTopologyWarnCooldownFrames;
        shadowTopologyPromotionReadyMinFrames = state.shadowTopologyPromotionReadyMinFrames;
        shadowPhaseAPromotionReadyMinFrames = state.shadowPhaseAPromotionReadyMinFrames;
        shadowPhaseDPromotionReadyMinFrames = state.shadowPhaseDPromotionReadyMinFrames;
    }

}
