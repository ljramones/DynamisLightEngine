package org.dynamislight.impl.vulkan;

import org.dynamislight.impl.vulkan.shadow.VulkanShadowFrameWarningFlow;
import org.dynamislight.impl.vulkan.runtime.warning.VulkanRuntimeWarningResets;
import org.dynamislight.impl.vulkan.runtime.warning.VulkanRuntimeWarningPolicy;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowBackendDiagnosticsBridge;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowContextBindings;
import org.dynamislight.impl.vulkan.runtime.upscale.VulkanExternalUpscalerDecider;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowRuntimeTuning;
import org.dynamislight.impl.vulkan.runtime.model.*;
import org.dynamislight.impl.vulkan.state.VulkanTelemetryStateBinder;
import org.dynamislight.impl.vulkan.runtime.config.AaMode;
import org.dynamislight.impl.vulkan.runtime.config.AaPreset;
import org.dynamislight.impl.vulkan.runtime.config.GiMode;
import org.dynamislight.impl.vulkan.runtime.config.ReflectionProfile;
import org.dynamislight.impl.vulkan.runtime.config.TsrControls;
import org.dynamislight.impl.vulkan.runtime.config.UpscalerMode;
import org.dynamislight.impl.vulkan.runtime.config.UpscalerQuality;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptions;
import org.dynamislight.impl.vulkan.reflection.*;
import org.dynamislight.impl.vulkan.reflection.VulkanReflectionTelemetryDefaults;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowTelemetryDefaults;
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
import org.dynamislight.api.runtime.AaTemporalPromotionDiagnostics;
import org.dynamislight.api.runtime.AaUpscalePromotionDiagnostics;
import org.dynamislight.api.runtime.AaMsaaPromotionDiagnostics;
import org.dynamislight.api.runtime.AaQualityPromotionDiagnostics;
import org.dynamislight.api.runtime.GiCapabilityDiagnostics;
import org.dynamislight.api.runtime.LightingBudgetDiagnostics;
import org.dynamislight.api.runtime.LightingCapabilityDiagnostics;
import org.dynamislight.api.runtime.LightingPromotionDiagnostics;
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
import org.dynamislight.impl.vulkan.warning.aa.VulkanAaTemporalMaterialWarningEmitter;
import org.dynamislight.impl.vulkan.warning.aa.VulkanAaMsaaWarningEmitter;
import org.dynamislight.impl.vulkan.warning.aa.VulkanAaQualityWarningEmitter;
import org.dynamislight.impl.vulkan.warning.aa.VulkanAaTemporalRuntimeState;
import org.dynamislight.impl.vulkan.warning.aa.VulkanAaTemporalWarningEmitter;
import org.dynamislight.impl.vulkan.warning.aa.VulkanAaUpscaleWarningEmitter;
import org.dynamislight.impl.vulkan.warning.gi.VulkanGiWarningEmitter;
import org.dynamislight.impl.vulkan.lighting.VulkanLightingCapabilityRuntimeState;
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
    private final VulkanAaTemporalRuntimeState aaTemporalState = new VulkanAaTemporalRuntimeState();
    private GiMode giMode = GiMode.SSGI;
    private boolean giEnabled;
    private String giModeLastFrame = "ssgi";
    private boolean giRtAvailableLastFrame;
    private List<String> giActiveCapabilitiesLastFrame = List.of();
    private List<String> giPrunedCapabilitiesLastFrame = List.of();
    private final VulkanLightingCapabilityRuntimeState lightingCapabilityState = new VulkanLightingCapabilityRuntimeState();
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
        VulkanTelemetryStateBinder.copyMatchingFields(options, this);
        shadowMaxLocalLayers = options.shadowMaxLocalLayers();
        shadowMaxFacesPerFrame = options.shadowMaxFacesPerFrame();
        shadowTransparentReceiversRequested = options.shadowTransparentReceiversEnabled();
        shadowAreaApproxRequested = options.shadowAreaApproxEnabled();
        shadowDistanceFieldRequested = options.shadowDistanceFieldSoftEnabled();
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
        shadowSchedulerFrameTick = 0L;
        currentSceneLights = List.of();
        shadowSchedulerLastRenderedTicks.clear();
        shadowLayerAllocatorAssignments.clear();
        shadowAllocatorAssignedLights = 0;
        shadowAllocatorReusedAssignments = 0;
        shadowAllocatorEvictions = 0;
        VulkanReflectionRuntimeFlow.resetProbeChurnDiagnostics(this);
        VulkanReflectionRuntimeFlow.resetSsrTaaRiskDiagnostics(this);
        VulkanReflectionRuntimeFlow.resetAdaptiveState(this);
        VulkanReflectionRuntimeFlow.resetAdaptiveTelemetryMetrics(this);
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
        aaTemporalState.resetFrameState();
        giModeLastFrame = giMode.name().toLowerCase(java.util.Locale.ROOT);
        giRtAvailableLastFrame = false;
        giActiveCapabilitiesLastFrame = List.of();
        giPrunedCapabilitiesLastFrame = List.of();
        lightingCapabilityState.reset();
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
        giMode = VulkanRuntimeOptionParsing.parseGiMode(safeBackendOptions.get("vulkan.gi.mode"));
        giEnabled = Boolean.parseBoolean(safeBackendOptions.getOrDefault("vulkan.gi.enabled", "false"));
        lightingCapabilityState.applyBackendOptions(safeBackendOptions);
        aaTemporalState.applyBackendOptions(safeBackendOptions);
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
        QualityTier resolvedQualityTier = config.qualityTier() == null ? QualityTier.MEDIUM : config.qualityTier();
        applyShadowTelemetryProfileDefaults(safeBackendOptions, resolvedQualityTier);
        applyReflectionProfileTelemetryDefaults(safeBackendOptions);
        tsrControls = VulkanRuntimeOptionParsing.parseTsrControls(safeBackendOptions, "vulkan.");
        externalUpscaler = ExternalUpscalerIntegration.create("vulkan", "vulkan.", safeBackendOptions);
        nativeUpscalerActive = false;
        nativeUpscalerProvider = externalUpscaler.providerId();
        nativeUpscalerDetail = externalUpscaler.statusDetail();
        assetRoot = config.assetRoot() == null ? Path.of(".") : config.assetRoot();
        meshLoader = new VulkanMeshAssetLoader(assetRoot, meshGeometryCacheMaxEntries);
        qualityTier = resolvedQualityTier;
        context.setPipelineProfileTier(qualityTier);
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
        VulkanReflectionRuntimeFlow.resetAdaptiveState(this);
        VulkanReflectionRuntimeFlow.resetAdaptiveTelemetryMetrics(this);
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
        VulkanReflectionRuntimeFlow.applyAdaptivePostParameters(this, context);
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
    protected AaTemporalPromotionDiagnostics backendAaTemporalPromotionDiagnostics() {
        return aaTemporalState.diagnostics(aaPostAaModeLastFrame);
    }

    @Override
    protected AaUpscalePromotionDiagnostics backendAaUpscalePromotionDiagnostics() {
        return aaTemporalState.upscaleDiagnostics();
    }

    @Override
    protected AaMsaaPromotionDiagnostics backendAaMsaaPromotionDiagnostics() {
        return aaTemporalState.msaaDiagnostics();
    }

    @Override
    protected AaQualityPromotionDiagnostics backendAaQualityPromotionDiagnostics() {
        return aaTemporalState.qualityDiagnostics();
    }

    @Override
    protected GiCapabilityDiagnostics backendGiCapabilityDiagnostics() {
        return new GiCapabilityDiagnostics(
                !giModeLastFrame.isBlank(),
                giModeLastFrame,
                giEnabled,
                giRtAvailableLastFrame,
                giActiveCapabilitiesLastFrame,
                giPrunedCapabilitiesLastFrame
        );
    }

    @Override
    protected LightingCapabilityDiagnostics backendLightingCapabilityDiagnostics() { return lightingCapabilityState.diagnostics(); }

    @Override
    protected LightingBudgetDiagnostics backendLightingBudgetDiagnostics() { return lightingCapabilityState.budgetDiagnostics(); }

    @Override
    protected LightingPromotionDiagnostics backendLightingPromotionDiagnostics() { return lightingCapabilityState.promotionDiagnostics(); }

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
        VulkanAaTemporalWarningEmitter.Result aaTemporalEmission = VulkanAaTemporalWarningEmitter.emit(
                new VulkanAaTemporalWarningEmitter.Input(
                        aaMode,
                        currentPost.taaEnabled(),
                        context.taaHistoryRejectRate(),
                        context.taaConfidenceMean(),
                        context.taaConfidenceDropEvents(),
                        aaTemporalState.temporalRejectWarnMax,
                        aaTemporalState.temporalConfidenceWarnMin,
                        aaTemporalState.temporalDropWarnMin,
                        aaTemporalState.temporalWarnMinFrames,
                        aaTemporalState.temporalWarnCooldownFrames,
                        aaTemporalState.temporalPromotionReadyMinFrames,
                        aaTemporalState.temporalStableStreak,
                        aaTemporalState.temporalHighStreak,
                        aaTemporalState.temporalWarnCooldownRemaining
                )
        );
        warnings.addAll(aaTemporalEmission.warnings());
        aaTemporalState.applyTemporalEmission(aaTemporalEmission);
        VulkanAaUpscaleWarningEmitter.Result aaUpscaleEmission = aaTemporalState.emitUpscale(
                aaMode,
                currentPost.taaRenderScale(),
                upscalerMode,
                nativeUpscalerActive,
                nativeUpscalerProvider,
                aaTemporalEmission.temporalPathActive()
        );
        warnings.addAll(aaUpscaleEmission.warnings());
        aaTemporalState.applyUpscaleEmission(aaUpscaleEmission);
        VulkanAaMsaaWarningEmitter.Result aaMsaaEmission = aaTemporalState.emitMsaa(
                aaMode,
                currentSceneMaterials,
                currentPost.smaaEnabled(),
                aaTemporalEmission.temporalPathActive()
        );
        warnings.addAll(aaMsaaEmission.warnings());
        aaTemporalState.applyMsaaEmission(aaMsaaEmission);
        VulkanAaQualityWarningEmitter.Result aaQualityEmission = aaTemporalState.emitQuality(
                aaMode,
                currentSceneMaterials,
                aaTemporalEmission.temporalPathActive(),
                currentPost.taaBlend(),
                currentPost.taaRenderScale(),
                currentPost.taaClipScale()
        );
        warnings.addAll(aaQualityEmission.warnings());
        aaTemporalState.applyQualityEmission(aaQualityEmission);
        VulkanAaTemporalMaterialWarningEmitter.Result aaMaterialEmission = VulkanAaTemporalMaterialWarningEmitter.emit(
                new VulkanAaTemporalMaterialWarningEmitter.Input(
                        currentSceneMaterials,
                        aaTemporalEmission.temporalPathActive(),
                        aaTemporalState.reactiveMaskWarnMinCoverage,
                        aaTemporalState.reactiveMaskWarnMinFrames,
                        aaTemporalState.reactiveMaskWarnCooldownFrames,
                        aaTemporalState.historyClampWarnMinCustomizedRatio,
                        aaTemporalState.historyClampWarnMinFrames,
                        aaTemporalState.historyClampWarnCooldownFrames,
                        aaTemporalState.reactiveMaskHighStreak,
                        aaTemporalState.reactiveMaskWarnCooldownRemaining,
                        aaTemporalState.historyClampHighStreak,
                        aaTemporalState.historyClampWarnCooldownRemaining
                )
        );
        warnings.addAll(aaMaterialEmission.warnings());
        aaTemporalState.applyMaterialEmission(aaMaterialEmission);
        aaTemporalState.updateCorePromotion(aaTemporalEmission, aaMaterialEmission, warnings, aaPostAaModeLastFrame);
        VulkanGiWarningEmitter.Result giEmission = VulkanGiWarningEmitter.emit(
                qualityTier,
                giMode,
                giEnabled,
                shadowRtTraversalSupported
        );
        giModeLastFrame = giEmission.plan().giModeId();
        giRtAvailableLastFrame = giEmission.plan().rtAvailable();
        giActiveCapabilitiesLastFrame = giEmission.plan().activeCapabilities();
        giPrunedCapabilitiesLastFrame = giEmission.plan().prunedCapabilities();
        warnings.add(giEmission.warning());
        lightingCapabilityState.emitFrameWarning(qualityTier, currentSceneLights, warnings);
        VulkanReflectionRuntimeFlow.processFrameWarnings(this, context, qualityTier, warnings);
        VulkanShadowFrameWarningFlow.process(this, context, qualityTier, warnings);
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
        return VulkanReflectionRuntimeFlow.snapshotProbeChurnDiagnostics(this, false);
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
        return VulkanReflectionRuntimeFlow.snapshotAdaptiveTrendDiagnostics(this, reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame);
    }

    ReflectionAdaptiveTrendSloDiagnostics debugReflectionAdaptiveTrendSloDiagnostics() {
        ReflectionAdaptiveTrendDiagnostics trend = VulkanReflectionRuntimeFlow.snapshotAdaptiveTrendDiagnostics(
                this,
                reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame
        );
        TrendSloAudit audit = VulkanReflectionRuntimeFlow.evaluateAdaptiveTrendSlo(this, trend);
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

    public record TrendSloAudit(
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
        nativeUpscalerActive = result.nativeUpscalerActive();
        nativeUpscalerProvider = result.nativeUpscalerProvider();
        nativeUpscalerDetail = result.nativeUpscalerDetail();
        return result.config();
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
