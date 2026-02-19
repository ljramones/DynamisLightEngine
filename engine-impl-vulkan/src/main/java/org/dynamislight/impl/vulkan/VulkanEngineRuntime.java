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
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityMode;
import org.dynamislight.impl.vulkan.warning.aa.VulkanAaPostWarningEmitter;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.profile.FrameResourceProfile;
import org.dynamislight.impl.vulkan.profile.PostProcessPipelineProfile;
import org.dynamislight.impl.vulkan.profile.SceneReuseStats;
import org.dynamislight.impl.vulkan.profile.ShadowCascadeProfile;
import org.dynamislight.impl.vulkan.profile.VulkanFrameMetrics;
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
        return VulkanShadowBackendDiagnosticsBridge.capability(this);
    }

    @Override
    protected ShadowCadenceDiagnostics backendShadowCadenceDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.cadence(this);
    }

    @Override
    protected ShadowPointBudgetDiagnostics backendShadowPointBudgetDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.pointBudget(this);
    }

    @Override
    protected ShadowSpotProjectedDiagnostics backendShadowSpotProjectedDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.spotProjected(this);
    }

    @Override
    protected ShadowCacheDiagnostics backendShadowCacheDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.cache(this);
    }

    @Override
    protected ShadowRtDiagnostics backendShadowRtDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.rt(this);
    }

    @Override
    protected ShadowHybridDiagnostics backendShadowHybridDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.hybrid(this);
    }

    @Override
    protected ShadowTransparentReceiverDiagnostics backendShadowTransparentReceiverDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.transparentReceivers(this);
    }

    @Override
    protected ShadowExtendedModeDiagnostics backendShadowExtendedModeDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.extendedModes(this);
    }

    @Override
    protected ShadowTopologyDiagnostics backendShadowTopologyDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.topology(this);
    }

    @Override
    protected ShadowPhaseAPromotionDiagnostics backendShadowPhaseAPromotionDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.phaseA(this);
    }

    @Override
    protected ShadowPhaseDPromotionDiagnostics backendShadowPhaseDPromotionDiagnostics() {
        return VulkanShadowBackendDiagnosticsBridge.phaseD(this);
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
            int planarEligible = countPlanarEligibleFromOverrideSummary(overrideSummary);
            int planarExcluded = planarScopeExclusionCountFromOverrideSummary(overrideSummary);
            refreshReflectionRtPathState(reflectionBaseMode);
            VulkanReflectionWarningCoreFlow.process(
                    this,
                    context,
                    qualityTier,
                    warnings,
                    reflectionBaseMode,
                    overrideSummary,
                    probeDiagnostics,
                    churnDiagnostics,
                    reflectionRtLaneRequested,
                    planarEligible,
                    planarExcluded,
                    currentPost,
                    lastFrameGpuMs,
                    plannedVisibleObjects,
                    reflectionProfile,
                    mockContext,
                    currentSceneMaterials,
                    reflectionTransparencyCandidateReactiveMin,
                    planarPerfGpuMsCapForTier(qualityTier),
                    rtPerfGpuMsCapForTier(qualityTier)
            );
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
                VulkanTelemetryStateBinder.copyMatchingFields(this, ssrTaaState);
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
        VulkanShadowFrameWarningFlow.process(this, context, qualityTier, warnings);
        return warnings;
    }

    private void resetReflectionWarningStateWhenDisabled() {
        resetReflectionProbeChurnDiagnostics();
        resetReflectionSsrTaaRiskDiagnostics();
        resetReflectionAdaptiveState();
        resetReflectionAdaptiveTelemetryMetrics();
        VulkanRuntimeWarningResets.resetReflectionWhenDisabled(this, rtPerfGpuMsCapForTier(qualityTier));
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
        VulkanReflectionAdaptiveDiagnostics.ProbeChurnState state = new VulkanReflectionAdaptiveDiagnostics.ProbeChurnState();
        VulkanTelemetryStateBinder.copyMatchingFields(this, state);
        VulkanReflectionAdaptiveDiagnostics.ProbeChurnUpdateResult result =
                VulkanReflectionAdaptiveDiagnostics.updateProbeChurn(state, diagnostics.activeProbeCount());
        VulkanTelemetryStateBinder.copyMatchingFields(result.state, this);
        return result.diagnostics;
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
        VulkanReflectionAdaptiveDiagnostics.ProbeChurnState state = new VulkanReflectionAdaptiveDiagnostics.ProbeChurnState();
        VulkanReflectionAdaptiveDiagnostics.resetProbeChurn(state);
        VulkanTelemetryStateBinder.copyMatchingFields(state, this);
    }

    private ReflectionSsrTaaRiskDiagnostics updateReflectionSsrTaaRiskDiagnostics(
            double taaReject,
            double taaConfidence,
            long taaDrops
    ) {
        VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskState state = new VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskState();
        VulkanTelemetryStateBinder.copyMatchingFields(this, state);
        VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskUpdateResult result =
                VulkanReflectionAdaptiveDiagnostics.updateSsrTaaRisk(state, taaReject, taaConfidence, taaDrops);
        VulkanTelemetryStateBinder.copyMatchingFields(result.state, this);
        return result.diagnostics;
    }

    private void resetReflectionSsrTaaRiskDiagnostics() {
        VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskState state = new VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskState();
        VulkanReflectionAdaptiveDiagnostics.resetSsrTaaRisk(state);
        VulkanTelemetryStateBinder.copyMatchingFields(state, this);
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
        VulkanReflectionRtStateMachine.State state = new VulkanReflectionRtStateMachine.State();
        VulkanTelemetryStateBinder.copyMatchingFields(this, state);
        VulkanReflectionRtStateMachine.refreshRtPathState(state, reflectionBaseMode, context);
        VulkanTelemetryStateBinder.copyMatchingFields(state, this);
    }

    private int composeReflectionExecutionMode(
            int configuredMode,
            boolean rtLaneActive,
            boolean planarSelectiveEligible,
            boolean transparencyCandidatesPresent
    ) {
        VulkanReflectionRtStateMachine.State state = new VulkanReflectionRtStateMachine.State();
        VulkanTelemetryStateBinder.copyMatchingFields(this, state);
        return VulkanReflectionRtStateMachine.composeExecutionMode(
                state,
                configuredMode,
                rtLaneActive,
                planarSelectiveEligible,
                transparencyCandidatesPresent
        );
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
        VulkanReflectionAdaptiveTrendEngine.State state = createReflectionAdaptiveTrendState();
        VulkanReflectionAdaptiveTrendEngine.recordSample(state, baseTemporalWeight, baseSsrStrength, baseSsrStepScale, severity);
        applyReflectionAdaptiveTrendState(state);
    }

    private void resetReflectionAdaptiveTelemetryMetrics() {
        VulkanReflectionAdaptiveTrendEngine.State state = createReflectionAdaptiveTrendState();
        VulkanReflectionAdaptiveTrendEngine.resetTelemetry(state);
        applyReflectionAdaptiveTrendState(state);
    }

    private ReflectionAdaptiveTrendDiagnostics snapshotReflectionAdaptiveTrendDiagnostics(boolean warningTriggered) {
        return VulkanReflectionAdaptiveTrendEngine.snapshotTrend(createReflectionAdaptiveTrendState(), warningTriggered);
    }

    private boolean updateReflectionAdaptiveTrendWarningGate() {
        VulkanReflectionAdaptiveTrendEngine.State state = createReflectionAdaptiveTrendState();
        boolean warningTriggered = VulkanReflectionAdaptiveTrendEngine.updateWarningGate(state);
        applyReflectionAdaptiveTrendState(state);
        return warningTriggered;
    }

    private TrendSloAudit evaluateReflectionAdaptiveTrendSlo(ReflectionAdaptiveTrendDiagnostics trend) {
        return VulkanReflectionAdaptiveTrendEngine.evaluateSlo(createReflectionAdaptiveTrendState(), trend);
    }

    private VulkanReflectionAdaptiveTrendEngine.State createReflectionAdaptiveTrendState() {
        VulkanReflectionAdaptiveTrendEngine.State state = new VulkanReflectionAdaptiveTrendEngine.State();
        state.reflectionAdaptiveSeverityInstant = reflectionAdaptiveSeverityInstant;
        state.reflectionAdaptiveSeverityPeak = reflectionAdaptiveSeverityPeak;
        state.reflectionAdaptiveSeverityAccum = reflectionAdaptiveSeverityAccum;
        state.reflectionAdaptiveTemporalDeltaAccum = reflectionAdaptiveTemporalDeltaAccum;
        state.reflectionAdaptiveSsrStrengthDeltaAccum = reflectionAdaptiveSsrStrengthDeltaAccum;
        state.reflectionAdaptiveSsrStepScaleDeltaAccum = reflectionAdaptiveSsrStepScaleDeltaAccum;
        state.reflectionAdaptiveTelemetrySamples = reflectionAdaptiveTelemetrySamples;
        state.reflectionSsrTaaAdaptiveTrendWarnHighStreak = reflectionSsrTaaAdaptiveTrendWarnHighStreak;
        state.reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining = reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining;
        state.reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame = reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame;
        state.reflectionSsrTaaAdaptiveTrendWindowFrames = reflectionSsrTaaAdaptiveTrendWindowFrames;
        state.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin = reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
        state.reflectionSsrTaaAdaptiveTrendWarnMinFrames = reflectionSsrTaaAdaptiveTrendWarnMinFrames;
        state.reflectionSsrTaaAdaptiveTrendWarnCooldownFrames = reflectionSsrTaaAdaptiveTrendWarnCooldownFrames;
        state.reflectionSsrTaaAdaptiveTrendWarnMinSamples = reflectionSsrTaaAdaptiveTrendWarnMinSamples;
        state.reflectionSsrTaaAdaptiveTrendSloMinSamples = reflectionSsrTaaAdaptiveTrendSloMinSamples;
        state.reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax = reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax;
        state.reflectionSsrTaaAdaptiveTrendSloHighRatioMax = reflectionSsrTaaAdaptiveTrendSloHighRatioMax;
        state.reflectionAdaptiveTemporalWeightActive = reflectionAdaptiveTemporalWeightActive;
        state.reflectionAdaptiveSsrStrengthActive = reflectionAdaptiveSsrStrengthActive;
        state.reflectionAdaptiveSsrStepScaleActive = reflectionAdaptiveSsrStepScaleActive;
        state.reflectionAdaptiveTrendSamples = reflectionAdaptiveTrendSamples;
        return state;
    }

    private void applyReflectionAdaptiveTrendState(VulkanReflectionAdaptiveTrendEngine.State state) {
        reflectionAdaptiveSeverityInstant = state.reflectionAdaptiveSeverityInstant;
        reflectionAdaptiveSeverityPeak = state.reflectionAdaptiveSeverityPeak;
        reflectionAdaptiveSeverityAccum = state.reflectionAdaptiveSeverityAccum;
        reflectionAdaptiveTemporalDeltaAccum = state.reflectionAdaptiveTemporalDeltaAccum;
        reflectionAdaptiveSsrStrengthDeltaAccum = state.reflectionAdaptiveSsrStrengthDeltaAccum;
        reflectionAdaptiveSsrStepScaleDeltaAccum = state.reflectionAdaptiveSsrStepScaleDeltaAccum;
        reflectionAdaptiveTelemetrySamples = state.reflectionAdaptiveTelemetrySamples;
        reflectionSsrTaaAdaptiveTrendWarnHighStreak = state.reflectionSsrTaaAdaptiveTrendWarnHighStreak;
        reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining = state.reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining;
        reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame = state.reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame;
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
        return VulkanReflectionDiagnosticsBuilders.probeStreaming(this, context);
    }

    ReflectionProbeChurnDiagnostics debugReflectionProbeChurnDiagnostics() {
        return snapshotReflectionProbeChurnDiagnostics(false);
    }

    ReflectionProbeQualityDiagnostics debugReflectionProbeQualityDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.probeQuality(this);
    }

    ReflectionSsrTaaRiskDiagnostics debugReflectionSsrTaaRiskDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.ssrTaaRisk(this);
    }

    ReflectionPlanarContractDiagnostics debugReflectionPlanarContractDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.planarContract(this);
    }

    ReflectionPlanarStabilityDiagnostics debugReflectionPlanarStabilityDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.planarStability(this);
    }

    ReflectionPlanarPerfDiagnostics debugReflectionPlanarPerfDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.planarPerf(this);
    }

    ReflectionOverridePolicyDiagnostics debugReflectionOverridePolicyDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.overridePolicy(this, context);
    }

    ReflectionContactHardeningDiagnostics debugReflectionContactHardeningDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.contactHardening(this);
    }

    ReflectionRtPathDiagnostics debugReflectionRtPathDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.rtPath(this);
    }

    ReflectionRtPerfDiagnostics debugReflectionRtPerfDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.rtPerf(this);
    }

    ReflectionRtPipelineDiagnostics debugReflectionRtPipelineDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.rtPipeline(this);
    }

    ReflectionRtHybridDiagnostics debugReflectionRtHybridDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.rtHybrid(this);
    }

    ReflectionRtDenoiseDiagnostics debugReflectionRtDenoiseDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.rtDenoise(this);
    }

    ReflectionRtAsBudgetDiagnostics debugReflectionRtAsBudgetDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.rtAsBudget(this);
    }

    ReflectionRtPromotionDiagnostics debugReflectionRtPromotionDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.rtPromotion(this);
    }

    ReflectionTransparencyDiagnostics debugReflectionTransparencyDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.transparency(this);
    }

    ReflectionAdaptivePolicyDiagnostics debugReflectionAdaptivePolicyDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.adaptivePolicy(this);
    }

    ReflectionSsrTaaHistoryPolicyDiagnostics debugReflectionSsrTaaHistoryPolicyDiagnostics() {
        return VulkanReflectionDiagnosticsBuilders.historyPolicy(this);
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

    record TrendSloAudit(
            String status,
            String reason,
            boolean failed
    ) {
    }

    private PostProcessRenderConfig applyExternalUpscalerDecision(PostProcessRenderConfig base) {
        VulkanExternalUpscalerDecider.Result result = VulkanExternalUpscalerDecider.decide(
                base,
                externalUpscaler,
                aaMode,
                upscalerMode,
                upscalerQuality,
                qualityTier,
                tsrControls
        );
        nativeUpscalerActive = result.nativeUpscalerActive;
        nativeUpscalerProvider = result.nativeUpscalerProvider;
        nativeUpscalerDetail = result.nativeUpscalerDetail;
        return result.config;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void applyReflectionProfileTelemetryDefaults(Map<String, String> backendOptions) {
        VulkanReflectionTelemetryDefaults.State state = new VulkanReflectionTelemetryDefaults.State();
        VulkanTelemetryStateBinder.copyMatchingFields(this, state);

        VulkanReflectionTelemetryDefaults.apply(state, reflectionProfile, backendOptions);

        VulkanTelemetryStateBinder.copyMatchingFields(state, this);
    }

    private void applyShadowTelemetryProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        VulkanShadowTelemetryDefaults.State state = new VulkanShadowTelemetryDefaults.State();
        VulkanTelemetryStateBinder.copyMatchingFields(this, state);

        VulkanShadowTelemetryDefaults.apply(state, backendOptions, tier);

        VulkanTelemetryStateBinder.copyMatchingFields(state, this);
    }

}
