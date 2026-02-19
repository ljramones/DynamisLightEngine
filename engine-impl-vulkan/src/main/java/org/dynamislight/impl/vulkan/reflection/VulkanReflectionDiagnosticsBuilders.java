package org.dynamislight.impl.vulkan.reflection;

import org.dynamislight.impl.vulkan.VulkanContext;
import org.dynamislight.impl.vulkan.runtime.model.*;

import org.dynamislight.impl.vulkan.state.VulkanTelemetryStateBinder;

public final class VulkanReflectionDiagnosticsBuilders {
    static final class State {
        int reflectionProbeMaxVisible;
        int reflectionProbeUpdateCadenceFrames;
        double reflectionProbeLodDepthScale;
        double reflectionProbeStreamingMemoryBudgetMb;
        int reflectionProbeStreamingHighStreak;
        int reflectionProbeStreamingWarnMinFrames;
        int reflectionProbeStreamingWarnCooldownFrames;
        int reflectionProbeStreamingWarnCooldownRemaining;
        boolean reflectionProbeStreamingBreachedLastFrame;
        ReflectionProbeQualityDiagnostics reflectionProbeQualityDiagnostics;
        int reflectionSsrTaaRiskHighStreak;
        int reflectionSsrTaaRiskWarnCooldownRemaining;
        double reflectionSsrTaaEmaReject;
        double reflectionSsrTaaEmaConfidence;
        String reflectionPlanarPassOrderContractStatus;
        int reflectionPlanarScopedMeshEligibleCount;
        int reflectionPlanarScopedMeshExcludedCount;
        boolean reflectionPlanarMirrorCameraActive;
        boolean reflectionPlanarDedicatedCaptureLaneActive;
        double reflectionPlanarLatestPlaneDelta;
        double reflectionPlanarLatestCoverageRatio;
        double reflectionPlanarEnvelopePlaneDeltaWarnMax;
        double reflectionPlanarEnvelopeCoverageRatioWarnMin;
        int reflectionPlanarEnvelopeHighStreak;
        int reflectionPlanarEnvelopeWarnMinFrames;
        int reflectionPlanarEnvelopeWarnCooldownFrames;
        int reflectionPlanarEnvelopeWarnCooldownRemaining;
        boolean reflectionPlanarEnvelopeBreachedLastFrame;
        double reflectionPlanarPerfLastGpuMsEstimate;
        double reflectionPlanarPerfLastGpuMsCap;
        String reflectionPlanarPerfLastTimingSource;
        boolean reflectionPlanarPerfLastTimestampAvailable;
        boolean reflectionPlanarPerfRequireGpuTimestamp;
        boolean reflectionPlanarPerfLastTimestampRequirementUnmet;
        double reflectionPlanarPerfLastDrawInflation;
        double reflectionPlanarPerfDrawInflationWarnMax;
        long reflectionPlanarPerfLastMemoryBytes;
        long reflectionPlanarPerfLastMemoryBudgetBytes;
        int reflectionPlanarPerfHighStreak;
        int reflectionPlanarPerfWarnMinFrames;
        int reflectionPlanarPerfWarnCooldownFrames;
        int reflectionPlanarPerfWarnCooldownRemaining;
        boolean reflectionPlanarPerfBreachedLastFrame;
        double reflectionOverrideProbeOnlyRatioWarnMax;
        double reflectionOverrideSsrOnlyRatioWarnMax;
        int reflectionOverrideOtherWarnMax;
        int reflectionOverrideHighStreak;
        int reflectionOverrideWarnMinFrames;
        int reflectionOverrideWarnCooldownFrames;
        int reflectionOverrideWarnCooldownRemaining;
        boolean reflectionOverrideBreachedLastFrame;
        boolean reflectionContactHardeningActiveLastFrame;
        double reflectionContactHardeningEstimatedStrengthLastFrame;
        double reflectionContactHardeningMinSsrStrength;
        double reflectionContactHardeningMinSsrMaxRoughness;
        int reflectionContactHardeningHighStreak;
        int reflectionContactHardeningWarnMinFrames;
        int reflectionContactHardeningWarnCooldownFrames;
        int reflectionContactHardeningWarnCooldownRemaining;
        boolean reflectionContactHardeningBreachedLastFrame;
        boolean reflectionRtLaneRequested;
        boolean reflectionRtLaneActive;
        boolean reflectionRtSingleBounceEnabled;
        boolean reflectionRtMultiBounceEnabled;
        boolean reflectionRtRequireActive;
        boolean reflectionRtRequireActiveUnmetLastFrame;
        boolean reflectionRtRequireMultiBounce;
        boolean reflectionRtRequireMultiBounceUnmetLastFrame;
        boolean reflectionRtRequireDedicatedPipeline;
        boolean reflectionRtRequireDedicatedPipelineUnmetLastFrame;
        boolean reflectionRtDedicatedPipelineEnabled;
        boolean reflectionRtTraversalSupported;
        boolean reflectionRtDedicatedCapabilitySupported;
        boolean reflectionRtDedicatedHardwarePipelineActive;
        boolean reflectionRtDedicatedDenoisePipelineEnabled;
        double reflectionRtDenoiseStrength;
        String reflectionRtFallbackChainActive;
        double reflectionRtPerfLastGpuMsEstimate;
        double reflectionRtPerfLastGpuMsCap;
        int reflectionRtPerfHighStreak;
        int reflectionRtPerfWarnMinFrames;
        int reflectionRtPerfWarnCooldownFrames;
        int reflectionRtPerfWarnCooldownRemaining;
        boolean reflectionRtPerfBreachedLastFrame;
        String reflectionRtBlasLifecycleState;
        String reflectionRtTlasLifecycleState;
        String reflectionRtSbtLifecycleState;
        int reflectionRtBlasObjectCount;
        int reflectionRtTlasInstanceCount;
        int reflectionRtSbtRecordCount;
        double reflectionRtHybridRtShare;
        double reflectionRtHybridSsrShare;
        double reflectionRtHybridProbeShare;
        double reflectionRtHybridProbeShareWarnMax;
        int reflectionRtHybridHighStreak;
        int reflectionRtHybridWarnMinFrames;
        int reflectionRtHybridWarnCooldownFrames;
        int reflectionRtHybridWarnCooldownRemaining;
        boolean reflectionRtHybridBreachedLastFrame;
        double reflectionRtDenoiseSpatialVariance;
        double reflectionRtDenoiseSpatialVarianceWarnMax;
        double reflectionRtDenoiseTemporalLag;
        double reflectionRtDenoiseTemporalLagWarnMax;
        int reflectionRtDenoiseHighStreak;
        int reflectionRtDenoiseWarnMinFrames;
        int reflectionRtDenoiseWarnCooldownFrames;
        int reflectionRtDenoiseWarnCooldownRemaining;
        boolean reflectionRtDenoiseBreachedLastFrame;
        double reflectionRtAsBuildGpuMsEstimate;
        double reflectionRtAsBuildGpuMsWarnMax;
        double reflectionRtAsMemoryMbEstimate;
        double reflectionRtAsMemoryBudgetMb;
        int reflectionRtAsBudgetHighStreak;
        int reflectionRtAsBudgetWarnCooldownRemaining;
        boolean reflectionRtAsBudgetBreachedLastFrame;
        boolean reflectionRtPromotionReadyLastFrame;
        int reflectionRtPromotionReadyHighStreak;
        int reflectionRtPromotionReadyMinFrames;
        String reflectionTransparencyStageGateStatus;
        int reflectionTransparentCandidateCount;
        int reflectionTransparencyAlphaTestedCandidateCount;
        int reflectionTransparencyReactiveCandidateCount;
        int reflectionTransparencyProbeOnlyCandidateCount;
        String reflectionTransparencyFallbackPath;
        double reflectionTransparencyCandidateReactiveMin;
        double reflectionTransparencyProbeOnlyRatioWarnMax;
        int reflectionTransparencyHighStreak;
        int reflectionTransparencyWarnMinFrames;
        int reflectionTransparencyWarnCooldownFrames;
        int reflectionTransparencyWarnCooldownRemaining;
        boolean reflectionTransparencyBreachedLastFrame;
        boolean reflectionSsrTaaAdaptiveEnabled;
        PostProcessRenderConfig currentPost;
        float reflectionAdaptiveTemporalWeightActive;
        float reflectionAdaptiveSsrStrengthActive;
        float reflectionAdaptiveSsrStepScaleActive;
        double reflectionSsrTaaAdaptiveTemporalBoostMax;
        double reflectionSsrTaaAdaptiveSsrStrengthScaleMin;
        double reflectionSsrTaaAdaptiveStepScaleBoostMax;
        String reflectionSsrTaaHistoryPolicyActive;
        String reflectionSsrTaaReprojectionPolicyActive;
        double reflectionAdaptiveSeverityInstant;
        double reflectionSsrTaaLatestRejectRate;
        double reflectionSsrTaaLatestConfidenceMean;
        long reflectionSsrTaaLatestDropEvents;
        double reflectionSsrTaaHistoryRejectBiasActive;
        double reflectionSsrTaaHistoryConfidenceDecayActive;
        double reflectionSsrTaaHistoryRejectSeverityMin;
        double reflectionSsrTaaHistoryConfidenceDecaySeverityMin;
        int reflectionSsrTaaHistoryRejectRiskStreakMin;
        long reflectionSsrTaaDisocclusionRejectDropEventsMin;
        double reflectionSsrTaaDisocclusionRejectConfidenceMax;
    }

    private static State stateFrom(Object runtime) {
        State state = new State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, state);
        return state;
    }

    public static ReflectionProbeStreamingDiagnostics probeStreaming(Object runtime, VulkanContext context) {
        State state = stateFrom(runtime);
        VulkanContext.ReflectionProbeDiagnostics diagnostics = context.debugReflectionProbeDiagnostics();
        int effectiveStreamingBudget = Math.max(1, Math.min(state.reflectionProbeMaxVisible, diagnostics.metadataCapacity()));
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
                state.reflectionProbeMaxVisible,
                effectiveStreamingBudget,
                state.reflectionProbeUpdateCadenceFrames,
                state.reflectionProbeLodDepthScale,
                diagnostics.frustumVisibleCount(),
                diagnostics.deferredProbeCount(),
                diagnostics.visibleUniquePathCount(),
                diagnostics.missingSlotPathCount(),
                missingSlotRatio,
                deferredRatio,
                lodSkewRatio,
                state.reflectionProbeStreamingMemoryBudgetMb,
                memoryEstimateMb,
                state.reflectionProbeStreamingHighStreak,
                state.reflectionProbeStreamingWarnMinFrames,
                state.reflectionProbeStreamingWarnCooldownFrames,
                state.reflectionProbeStreamingWarnCooldownRemaining,
                budgetPressure,
                state.reflectionProbeStreamingBreachedLastFrame
        );
    }

    public static ReflectionProbeQualityDiagnostics probeQuality(Object runtime) {
        return stateFrom(runtime).reflectionProbeQualityDiagnostics;
    }

    public static ReflectionSsrTaaRiskDiagnostics ssrTaaRisk(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionSsrTaaRiskDiagnostics(
                false,
                state.reflectionSsrTaaRiskHighStreak,
                state.reflectionSsrTaaRiskWarnCooldownRemaining,
                state.reflectionSsrTaaEmaReject < 0.0 ? 0.0 : state.reflectionSsrTaaEmaReject,
                state.reflectionSsrTaaEmaConfidence < 0.0 ? 1.0 : state.reflectionSsrTaaEmaConfidence,
                false
        );
    }

    public static ReflectionPlanarContractDiagnostics planarContract(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionPlanarContractDiagnostics(
                state.reflectionPlanarPassOrderContractStatus,
                state.reflectionPlanarScopedMeshEligibleCount,
                state.reflectionPlanarScopedMeshExcludedCount,
                state.reflectionPlanarMirrorCameraActive,
                state.reflectionPlanarDedicatedCaptureLaneActive
        );
    }

    public static ReflectionPlanarStabilityDiagnostics planarStability(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionPlanarStabilityDiagnostics(
                state.reflectionPlanarLatestPlaneDelta,
                state.reflectionPlanarLatestCoverageRatio,
                state.reflectionPlanarEnvelopePlaneDeltaWarnMax,
                state.reflectionPlanarEnvelopeCoverageRatioWarnMin,
                state.reflectionPlanarEnvelopeHighStreak,
                state.reflectionPlanarEnvelopeWarnMinFrames,
                state.reflectionPlanarEnvelopeWarnCooldownFrames,
                state.reflectionPlanarEnvelopeWarnCooldownRemaining,
                state.reflectionPlanarEnvelopeBreachedLastFrame
        );
    }

    public static ReflectionPlanarPerfDiagnostics planarPerf(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionPlanarPerfDiagnostics(
                state.reflectionPlanarPerfLastGpuMsEstimate,
                state.reflectionPlanarPerfLastGpuMsCap,
                state.reflectionPlanarPerfLastTimingSource,
                state.reflectionPlanarPerfLastTimestampAvailable,
                state.reflectionPlanarPerfRequireGpuTimestamp,
                state.reflectionPlanarPerfLastTimestampRequirementUnmet,
                state.reflectionPlanarPerfLastDrawInflation,
                state.reflectionPlanarPerfDrawInflationWarnMax,
                state.reflectionPlanarPerfLastMemoryBytes,
                state.reflectionPlanarPerfLastMemoryBudgetBytes,
                state.reflectionPlanarPerfHighStreak,
                state.reflectionPlanarPerfWarnMinFrames,
                state.reflectionPlanarPerfWarnCooldownFrames,
                state.reflectionPlanarPerfWarnCooldownRemaining,
                state.reflectionPlanarPerfBreachedLastFrame
        );
    }

    public static ReflectionOverridePolicyDiagnostics overridePolicy(Object runtime, VulkanContext context) {
        State state = stateFrom(runtime);
        ReflectionOverrideSummary summary = VulkanReflectionAnalysis.summarizeReflectionOverrides(context.debugGpuMeshReflectionOverrideModes());
        int total = Math.max(1, summary.totalCount());
        return new ReflectionOverridePolicyDiagnostics(
                summary.autoCount(),
                summary.probeOnlyCount(),
                summary.ssrOnlyCount(),
                summary.otherCount(),
                (double) summary.probeOnlyCount() / (double) total,
                (double) summary.ssrOnlyCount() / (double) total,
                state.reflectionOverrideProbeOnlyRatioWarnMax,
                state.reflectionOverrideSsrOnlyRatioWarnMax,
                state.reflectionOverrideOtherWarnMax,
                state.reflectionOverrideHighStreak,
                state.reflectionOverrideWarnMinFrames,
                state.reflectionOverrideWarnCooldownFrames,
                state.reflectionOverrideWarnCooldownRemaining,
                state.reflectionOverrideBreachedLastFrame,
                "probe_only|ssr_only"
        );
    }

    public static ReflectionContactHardeningDiagnostics contactHardening(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionContactHardeningDiagnostics(
                state.reflectionContactHardeningActiveLastFrame,
                state.reflectionContactHardeningEstimatedStrengthLastFrame,
                state.reflectionContactHardeningMinSsrStrength,
                state.reflectionContactHardeningMinSsrMaxRoughness,
                state.reflectionContactHardeningHighStreak,
                state.reflectionContactHardeningWarnMinFrames,
                state.reflectionContactHardeningWarnCooldownFrames,
                state.reflectionContactHardeningWarnCooldownRemaining,
                state.reflectionContactHardeningBreachedLastFrame
        );
    }

    public static ReflectionRtPathDiagnostics rtPath(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionRtPathDiagnostics(
                state.reflectionRtLaneRequested,
                state.reflectionRtLaneActive,
                state.reflectionRtSingleBounceEnabled,
                state.reflectionRtMultiBounceEnabled,
                state.reflectionRtRequireActive,
                state.reflectionRtRequireActiveUnmetLastFrame,
                state.reflectionRtRequireMultiBounce,
                state.reflectionRtRequireMultiBounceUnmetLastFrame,
                state.reflectionRtRequireDedicatedPipeline,
                state.reflectionRtRequireDedicatedPipelineUnmetLastFrame,
                state.reflectionRtDedicatedPipelineEnabled,
                state.reflectionRtTraversalSupported,
                state.reflectionRtDedicatedCapabilitySupported,
                state.reflectionRtDedicatedHardwarePipelineActive,
                state.reflectionRtDedicatedDenoisePipelineEnabled,
                state.reflectionRtDenoiseStrength,
                state.reflectionRtFallbackChainActive
        );
    }

    public static ReflectionRtPerfDiagnostics rtPerf(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionRtPerfDiagnostics(
                state.reflectionRtPerfLastGpuMsEstimate,
                state.reflectionRtPerfLastGpuMsCap,
                state.reflectionRtPerfHighStreak,
                state.reflectionRtPerfWarnMinFrames,
                state.reflectionRtPerfWarnCooldownFrames,
                state.reflectionRtPerfWarnCooldownRemaining,
                state.reflectionRtPerfBreachedLastFrame
        );
    }

    public static ReflectionRtPipelineDiagnostics rtPipeline(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionRtPipelineDiagnostics(
                state.reflectionRtBlasLifecycleState,
                state.reflectionRtTlasLifecycleState,
                state.reflectionRtSbtLifecycleState,
                state.reflectionRtBlasObjectCount,
                state.reflectionRtTlasInstanceCount,
                state.reflectionRtSbtRecordCount
        );
    }

    public static ReflectionRtHybridDiagnostics rtHybrid(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionRtHybridDiagnostics(
                state.reflectionRtHybridRtShare,
                state.reflectionRtHybridSsrShare,
                state.reflectionRtHybridProbeShare,
                state.reflectionRtHybridProbeShareWarnMax,
                state.reflectionRtHybridHighStreak,
                state.reflectionRtHybridWarnMinFrames,
                state.reflectionRtHybridWarnCooldownFrames,
                state.reflectionRtHybridWarnCooldownRemaining,
                state.reflectionRtHybridBreachedLastFrame
        );
    }

    public static ReflectionRtDenoiseDiagnostics rtDenoise(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionRtDenoiseDiagnostics(
                state.reflectionRtDenoiseSpatialVariance,
                state.reflectionRtDenoiseSpatialVarianceWarnMax,
                state.reflectionRtDenoiseTemporalLag,
                state.reflectionRtDenoiseTemporalLagWarnMax,
                state.reflectionRtDenoiseHighStreak,
                state.reflectionRtDenoiseWarnMinFrames,
                state.reflectionRtDenoiseWarnCooldownFrames,
                state.reflectionRtDenoiseWarnCooldownRemaining,
                state.reflectionRtDenoiseBreachedLastFrame
        );
    }

    public static ReflectionRtAsBudgetDiagnostics rtAsBudget(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionRtAsBudgetDiagnostics(
                state.reflectionRtAsBuildGpuMsEstimate,
                state.reflectionRtAsBuildGpuMsWarnMax,
                state.reflectionRtAsMemoryMbEstimate,
                state.reflectionRtAsMemoryBudgetMb,
                state.reflectionRtAsBudgetHighStreak,
                state.reflectionRtPerfWarnMinFrames,
                state.reflectionRtPerfWarnCooldownFrames,
                state.reflectionRtAsBudgetWarnCooldownRemaining,
                state.reflectionRtAsBudgetBreachedLastFrame
        );
    }

    public static ReflectionRtPromotionDiagnostics rtPromotion(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionRtPromotionDiagnostics(
                state.reflectionRtPromotionReadyLastFrame,
                state.reflectionRtPromotionReadyHighStreak,
                state.reflectionRtPromotionReadyMinFrames,
                state.reflectionRtDedicatedHardwarePipelineActive,
                state.reflectionRtPerfBreachedLastFrame,
                state.reflectionRtHybridBreachedLastFrame,
                state.reflectionRtDenoiseBreachedLastFrame,
                state.reflectionRtAsBudgetBreachedLastFrame,
                state.reflectionTransparencyStageGateStatus
        );
    }

    public static ReflectionTransparencyDiagnostics transparency(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionTransparencyDiagnostics(
                state.reflectionTransparentCandidateCount,
                state.reflectionTransparencyAlphaTestedCandidateCount,
                state.reflectionTransparencyReactiveCandidateCount,
                state.reflectionTransparencyProbeOnlyCandidateCount,
                state.reflectionTransparencyStageGateStatus,
                state.reflectionTransparencyFallbackPath,
                state.reflectionRtLaneActive,
                state.reflectionTransparencyCandidateReactiveMin,
                state.reflectionTransparencyProbeOnlyRatioWarnMax,
                state.reflectionTransparencyHighStreak,
                state.reflectionTransparencyWarnMinFrames,
                state.reflectionTransparencyWarnCooldownFrames,
                state.reflectionTransparencyWarnCooldownRemaining,
                state.reflectionTransparencyBreachedLastFrame
        );
    }

    public static ReflectionAdaptivePolicyDiagnostics adaptivePolicy(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionAdaptivePolicyDiagnostics(
                state.reflectionSsrTaaAdaptiveEnabled,
                state.currentPost.reflectionsTemporalWeight(),
                state.reflectionAdaptiveTemporalWeightActive,
                state.currentPost.reflectionsSsrStrength(),
                state.reflectionAdaptiveSsrStrengthActive,
                state.currentPost.reflectionsSsrStepScale(),
                state.reflectionAdaptiveSsrStepScaleActive,
                state.reflectionSsrTaaAdaptiveTemporalBoostMax,
                state.reflectionSsrTaaAdaptiveSsrStrengthScaleMin,
                state.reflectionSsrTaaAdaptiveStepScaleBoostMax
        );
    }

    public static ReflectionSsrTaaHistoryPolicyDiagnostics historyPolicy(Object runtime) {
        State state = stateFrom(runtime);
        return new ReflectionSsrTaaHistoryPolicyDiagnostics(
                state.reflectionSsrTaaHistoryPolicyActive,
                state.reflectionSsrTaaReprojectionPolicyActive,
                state.reflectionAdaptiveSeverityInstant,
                state.reflectionSsrTaaRiskHighStreak,
                state.reflectionSsrTaaLatestRejectRate,
                state.reflectionSsrTaaLatestConfidenceMean,
                state.reflectionSsrTaaLatestDropEvents,
                state.reflectionSsrTaaHistoryRejectBiasActive,
                state.reflectionSsrTaaHistoryConfidenceDecayActive,
                state.reflectionSsrTaaHistoryRejectSeverityMin,
                state.reflectionSsrTaaHistoryConfidenceDecaySeverityMin,
                state.reflectionSsrTaaHistoryRejectRiskStreakMin,
                state.reflectionSsrTaaDisocclusionRejectDropEventsMin,
                state.reflectionSsrTaaDisocclusionRejectConfidenceMax,
                state.reflectionSsrTaaAdaptiveEnabled
        );
    }

    private VulkanReflectionDiagnosticsBuilders() {
    }
}
