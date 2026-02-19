package org.dynamislight.impl.vulkan;

record ReflectionProbeDiagnostics(
        int configuredProbeCount,
        int activeProbeCount,
        int slotCount,
        int metadataCapacity,
        int frustumVisibleCount,
        int deferredProbeCount,
        int visibleUniquePathCount,
        int missingSlotPathCount,
        int lodTier0Count,
        int lodTier1Count,
        int lodTier2Count,
        int lodTier3Count
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
        int frustumVisibleCount,
        int deferredProbeCount,
        int visibleUniquePathCount,
        int missingSlotPathCount,
        double missingSlotRatio,
        double deferredRatio,
        double lodSkewRatio,
        double memoryBudgetMb,
        double memoryEstimateMb,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean budgetPressure,
        boolean breachedLastFrame
) {
}

record ReflectionProbeQualityDiagnostics(
        int configuredProbeCount,
        int boxProjectedCount,
        double boxProjectionRatio,
        int invalidBlendDistanceCount,
        int invalidExtentCount,
        int overlapPairs,
        double meanOverlapCoverage,
        int bleedRiskPairs,
        int transitionPairs,
        int maxPriorityDelta,
        boolean envelopeBreached,
        String breachReason
) {
    static ReflectionProbeQualityDiagnostics zero() {
        return new ReflectionProbeQualityDiagnostics(0, 0, 0.0, 0, 0, 0, 0.0, 0, 0, 0, false, "none");
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
        double probeOnlyRatio,
        double ssrOnlyRatio,
        double probeOnlyRatioWarnMax,
        double ssrOnlyRatioWarnMax,
        int otherWarnMax,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame,
        String planarSelectiveExcludes
) {
}

record ReflectionContactHardeningDiagnostics(
        boolean activeLastFrame,
        double estimatedStrengthLastFrame,
        double minSsrStrength,
        double minSsrMaxRoughness,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}

record ReflectionRtPathDiagnostics(
        boolean laneRequested,
        boolean laneActive,
        boolean singleBounceEnabled,
        boolean multiBounceEnabled,
        boolean requireActive,
        boolean requireActiveUnmetLastFrame,
        boolean requireMultiBounce,
        boolean requireMultiBounceUnmetLastFrame,
        boolean requireDedicatedPipeline,
        boolean requireDedicatedPipelineUnmetLastFrame,
        boolean dedicatedPipelineEnabled,
        boolean traversalSupported,
        boolean dedicatedCapabilitySupported,
        boolean dedicatedHardwarePipelineActive,
        boolean dedicatedDenoisePipelineEnabled,
        double denoiseStrength,
        String fallbackChain
) {
}

record ReflectionRtPerfDiagnostics(
        double gpuMsEstimate,
        double gpuMsCap,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}

record ReflectionRtPipelineDiagnostics(
        String blasLifecycleState,
        String tlasLifecycleState,
        String sbtLifecycleState,
        int blasObjectCount,
        int tlasInstanceCount,
        int sbtRecordCount
) {
}

record ReflectionRtHybridDiagnostics(
        double rtShare,
        double ssrShare,
        double probeShare,
        double probeShareWarnMax,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}

record ReflectionRtDenoiseDiagnostics(
        double spatialVariance,
        double spatialVarianceWarnMax,
        double temporalLag,
        double temporalLagWarnMax,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}

record ReflectionRtAsBudgetDiagnostics(
        double buildGpuMsEstimate,
        double buildGpuMsWarnMax,
        double memoryMbEstimate,
        double memoryMbBudget,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}

record ReflectionRtPromotionDiagnostics(
        boolean readyLastFrame,
        int highStreak,
        int minFrames,
        boolean dedicatedActive,
        boolean perfBreach,
        boolean hybridBreach,
        boolean denoiseBreach,
        boolean asBudgetBreach,
        String transparencyStageGateStatus
) {
}

record ReflectionTransparencyDiagnostics(
        int transparentCandidateCount,
        int alphaTestedCandidateCount,
        int reactiveCandidateCount,
        int probeOnlyCandidateCount,
        String stageGateStatus,
        String fallbackPath,
        boolean rtLaneActive,
        double candidateReactiveMin,
        double probeOnlyRatioWarnMax,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
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

record ReflectionOverrideSummary(int autoCount, int probeOnlyCount, int ssrOnlyCount, int otherCount) {
    int totalCount() {
        return Math.max(0, autoCount + probeOnlyCount + ssrOnlyCount + otherCount);
    }
}

record TransparencyCandidateSummary(
        int totalCount,
        int alphaTestedCount,
        int reactiveCandidateCount,
        int probeOnlyOverrideCount
) {
    static TransparencyCandidateSummary zero() {
        return new TransparencyCandidateSummary(0, 0, 0, 0);
    }
}

record ReflectionAdaptiveWindowSample(
        double severity,
        double temporalDelta,
        double ssrStrengthDelta,
        double ssrStepScaleDelta
) {
}
