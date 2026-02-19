package org.dynamislight.impl.vulkan;

import org.dynamislight.impl.vulkan.runtime.config.*;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.MaterialDesc;

final class VulkanReflectionRuntimeFlow {
    private static final int REFLECTION_MODE_BASE_MASK = 0x7;

    static void processFrameWarnings(
            VulkanEngineRuntime runtime,
            VulkanContext context,
            QualityTier qualityTier,
            List<EngineWarning> warnings
    ) {
        State state = snapshot(runtime);
        if (!state.currentPost.reflectionsEnabled()) {
            resetWhenDisabled(runtime, qualityTier);
            return;
        }
        int reflectionBaseMode = state.currentPost.reflectionsMode() & REFLECTION_MODE_BASE_MASK;
        ReflectionOverrideSummary overrideSummary =
                VulkanReflectionAnalysis.summarizeReflectionOverrides(context.debugGpuMeshReflectionOverrideModes());
        VulkanContext.ReflectionProbeDiagnostics probeDiagnostics = context.debugReflectionProbeDiagnostics();
        ReflectionProbeChurnDiagnostics churnDiagnostics = updateProbeChurnDiagnostics(runtime, probeDiagnostics);
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
                        + ", ssrStrength=" + state.currentPost.reflectionsSsrStrength()
                        + ", planarStrength=" + state.currentPost.reflectionsPlanarStrength()
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

        int planarEligible = countPlanarEligible(state, overrideSummary);
        int planarExcluded = planarScopeExclusionCount(state, overrideSummary);
        refreshRtPathState(runtime, context, reflectionBaseMode);
        state = snapshot(runtime);

        VulkanReflectionWarningCoreFlow.process(
                runtime,
                context,
                qualityTier,
                warnings,
                reflectionBaseMode,
                overrideSummary,
                probeDiagnostics,
                churnDiagnostics,
                state.reflectionRtLaneRequested,
                planarEligible,
                planarExcluded,
                state.currentPost,
                state.lastFrameGpuMs,
                state.plannedVisibleObjects,
                state.reflectionProfile,
                state.mockContext,
                state.currentSceneMaterials,
                state.reflectionTransparencyCandidateReactiveMin,
                planarPerfGpuMsCapForTier(state, qualityTier),
                rtPerfGpuMsCapForTier(state, qualityTier)
        );

        boolean ssrPathActive = isReflectionSsrPathActive(state.currentPost.reflectionsMode());
        if (ssrPathActive && state.currentPost.taaEnabled()) {
            double taaReject = context.taaHistoryRejectRate();
            double taaConfidence = context.taaConfidenceMean();
            long taaDrops = context.taaConfidenceDropEvents();
            ReflectionSsrTaaRiskDiagnostics ssrTaaRisk = updateSsrTaaRiskDiagnostics(runtime, taaReject, taaConfidence, taaDrops);
            boolean adaptiveTrendWarningTriggered = updateAdaptiveTrendWarningGate(runtime);
            ReflectionAdaptiveTrendDiagnostics adaptiveTrend = snapshotAdaptiveTrendDiagnostics(runtime, adaptiveTrendWarningTriggered);
            VulkanEngineRuntime.TrendSloAudit trendSloAudit = evaluateAdaptiveTrendSlo(runtime, adaptiveTrend);
            VulkanReflectionSsrTaaWarningEmitter.State ssrTaaState = new VulkanReflectionSsrTaaWarningEmitter.State();
            VulkanTelemetryStateBinder.copyMatchingFields(runtime, ssrTaaState);
            VulkanReflectionSsrTaaWarningEmitter.emit(
                    warnings,
                    ssrTaaState,
                    reflectionBaseMode,
                    state.currentPost.reflectionsSsrStrength(),
                    state.currentPost.reflectionsSsrMaxRoughness(),
                    state.currentPost.reflectionsSsrStepScale(),
                    state.currentPost.reflectionsTemporalWeight(),
                    state.currentPost.taaBlend(),
                    state.currentPost.taaClipScale(),
                    state.currentPost.taaLumaClipEnabled(),
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
            VulkanTelemetryStateBinder.copyMatchingFields(ssrTaaState, runtime);
        } else {
            resetSsrTaaRiskDiagnostics(runtime);
            resetAdaptiveState(runtime);
            setAdaptiveTrendWarningTriggered(runtime, false);
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
    }

    static void applyAdaptivePostParameters(VulkanEngineRuntime runtime, VulkanContext context) {
        State state = snapshot(runtime);
        float baseTemporalWeight = state.currentPost.reflectionsTemporalWeight();
        float baseSsrStrength = state.currentPost.reflectionsSsrStrength();
        float baseSsrStepScale = state.currentPost.reflectionsSsrStepScale();
        state.reflectionAdaptiveTemporalWeightActive = baseTemporalWeight;
        state.reflectionAdaptiveSsrStrengthActive = baseSsrStrength;
        state.reflectionAdaptiveSsrStepScaleActive = baseSsrStepScale;
        double severity = 0.0;

        if (state.reflectionSsrTaaAdaptiveEnabled
                && state.currentPost.reflectionsEnabled()
                && state.currentPost.taaEnabled()
                && isReflectionSsrPathActive(state.currentPost.reflectionsMode())) {
            severity = computeAdaptiveSeverity(state);
            state.reflectionAdaptiveTemporalWeightActive = clamp(
                    (float) (baseTemporalWeight + severity * state.reflectionSsrTaaAdaptiveTemporalBoostMax),
                    0.0f,
                    0.98f
            );
            double strengthScale = 1.0 - severity * (1.0 - state.reflectionSsrTaaAdaptiveSsrStrengthScaleMin);
            state.reflectionAdaptiveSsrStrengthActive = clamp(
                    (float) (baseSsrStrength * strengthScale),
                    0.0f,
                    1.0f
            );
            state.reflectionAdaptiveSsrStepScaleActive = clamp(
                    (float) (baseSsrStepScale * (1.0 + severity * state.reflectionSsrTaaAdaptiveStepScaleBoostMax)),
                    0.5f,
                    3.0f
            );
        }

        VulkanReflectionAdaptiveMath.ReflectionSsrTaaHistoryPolicy policy =
                VulkanReflectionAdaptiveMath.computeHistoryPolicy(
                        state.currentPost.reflectionsEnabled(),
                        state.currentPost.taaEnabled(),
                        isReflectionSsrPathActive(state.currentPost.reflectionsMode()),
                        severity,
                        state.reflectionSsrTaaLatestRejectRate,
                        state.reflectionSsrTaaLatestConfidenceMean,
                        state.reflectionSsrTaaLatestDropEvents,
                        state.reflectionSsrTaaDisocclusionRejectDropEventsMin,
                        state.reflectionSsrTaaDisocclusionRejectConfidenceMax,
                        state.reflectionSsrTaaHistoryRejectSeverityMin,
                        state.reflectionSsrTaaHistoryRejectRiskStreakMin,
                        state.reflectionSsrTaaRiskHighStreak,
                        state.reflectionSsrTaaHistoryConfidenceDecaySeverityMin
                );
        state.reflectionSsrTaaHistoryPolicyActive = policy.historyPolicy();
        state.reflectionSsrTaaReprojectionPolicyActive = policy.reprojectionPolicy();
        state.reflectionSsrTaaHistoryRejectBiasActive = policy.historyRejectBias();
        state.reflectionSsrTaaHistoryConfidenceDecayActive = policy.historyConfidenceDecay();

        if (state.currentPost.reflectionsEnabled()) {
            VulkanReflectionAdaptiveTrendEngine.State trend = createTrendState(state);
            VulkanReflectionAdaptiveTrendEngine.recordSample(trend, baseTemporalWeight, baseSsrStrength, baseSsrStepScale, severity);
            applyTrendState(state, trend);
        } else {
            state.reflectionAdaptiveSeverityInstant = 0.0;
        }

        int reflectionBaseMode = state.currentPost.reflectionsMode() & REFLECTION_MODE_BASE_MASK;
        ReflectionOverrideSummary overrideSummary =
                VulkanReflectionAnalysis.summarizeReflectionOverrides(context.debugGpuMeshReflectionOverrideModes());
        int planarEligible = countPlanarEligible(state, overrideSummary);
        state.reflectionTransparentCandidateCount = VulkanReflectionAnalysis.summarizeReflectionTransparencyCandidates(
                state.currentSceneMaterials,
                state.reflectionTransparencyCandidateReactiveMin
        ).totalCount();
        apply(runtime, state);

        refreshRtPathState(runtime, context, reflectionBaseMode);
        state = snapshot(runtime);
        int reflectionModeRuntime = composeReflectionExecutionMode(
                runtime,
                context,
                state.currentPost.reflectionsMode(),
                state.reflectionRtLaneActive,
                planarEligible > 0,
                state.reflectionTransparentCandidateCount > 0
        );

        context.setPostProcessParameters(
                state.currentPost.tonemapEnabled(),
                state.currentPost.exposure(),
                state.currentPost.gamma(),
                state.currentPost.bloomEnabled(),
                state.currentPost.bloomThreshold(),
                state.currentPost.bloomStrength(),
                state.currentPost.ssaoEnabled(),
                state.currentPost.ssaoStrength(),
                state.currentPost.ssaoRadius(),
                state.currentPost.ssaoBias(),
                state.currentPost.ssaoPower(),
                state.currentPost.smaaEnabled(),
                state.currentPost.smaaStrength(),
                state.currentPost.taaEnabled(),
                state.currentPost.taaBlend(),
                state.currentPost.taaClipScale(),
                state.currentPost.taaLumaClipEnabled(),
                state.currentPost.taaSharpenStrength(),
                state.currentPost.taaRenderScale(),
                state.currentPost.reflectionsEnabled(),
                reflectionModeRuntime,
                state.reflectionAdaptiveSsrStrengthActive,
                state.currentPost.reflectionsSsrMaxRoughness(),
                state.reflectionAdaptiveSsrStepScaleActive,
                state.reflectionAdaptiveTemporalWeightActive,
                state.currentPost.reflectionsPlanarStrength(),
                state.currentPost.reflectionsPlanarPlaneHeight(),
                (float) state.reflectionRtDenoiseStrength
        );
    }

    static void resetWhenDisabled(VulkanEngineRuntime runtime, QualityTier qualityTier) {
        resetProbeChurnDiagnostics(runtime);
        resetSsrTaaRiskDiagnostics(runtime);
        resetAdaptiveState(runtime);
        resetAdaptiveTelemetryMetrics(runtime);
        State state = snapshot(runtime);
        VulkanRuntimeWarningResets.resetReflectionWhenDisabled(runtime, rtPerfGpuMsCapForTier(state, qualityTier));
    }

    static ReflectionProbeChurnDiagnostics snapshotProbeChurnDiagnostics(VulkanEngineRuntime runtime, boolean warningTriggered) {
        State state = snapshot(runtime);
        double meanDelta = state.reflectionProbeActiveChurnEvents <= 0
                ? 0.0
                : (double) state.reflectionProbeActiveDeltaAccum / (double) state.reflectionProbeActiveChurnEvents;
        return new ReflectionProbeChurnDiagnostics(
                state.lastActiveReflectionProbeCount,
                state.reflectionProbeLastDelta,
                state.reflectionProbeActiveChurnEvents,
                meanDelta,
                state.reflectionProbeChurnHighStreak,
                state.reflectionProbeChurnWarnCooldownRemaining,
                warningTriggered
        );
    }

    static ReflectionAdaptiveTrendDiagnostics snapshotAdaptiveTrendDiagnostics(
            VulkanEngineRuntime runtime,
            boolean warningTriggered
    ) {
        return VulkanReflectionAdaptiveTrendEngine.snapshotTrend(createTrendState(snapshot(runtime)), warningTriggered);
    }

    static VulkanEngineRuntime.TrendSloAudit evaluateAdaptiveTrendSlo(
            VulkanEngineRuntime runtime,
            ReflectionAdaptiveTrendDiagnostics trend
    ) {
        return VulkanReflectionAdaptiveTrendEngine.evaluateSlo(createTrendState(snapshot(runtime)), trend);
    }

    static void resetAdaptiveState(VulkanEngineRuntime runtime) {
        State state = snapshot(runtime);
        state.reflectionAdaptiveTemporalWeightActive = state.currentPost == null ? 0.80f : state.currentPost.reflectionsTemporalWeight();
        state.reflectionAdaptiveSsrStrengthActive = state.currentPost == null ? 0.6f : state.currentPost.reflectionsSsrStrength();
        state.reflectionAdaptiveSsrStepScaleActive = state.currentPost == null ? 1.0f : state.currentPost.reflectionsSsrStepScale();
        state.reflectionSsrTaaHistoryPolicyActive = "inactive";
        state.reflectionSsrTaaReprojectionPolicyActive = "surface_motion_vectors";
        state.reflectionSsrTaaHistoryRejectBiasActive = 0.0;
        state.reflectionSsrTaaHistoryConfidenceDecayActive = 0.0;
        apply(runtime, state);
    }

    static void resetAdaptiveTelemetryMetrics(VulkanEngineRuntime runtime) {
        State state = snapshot(runtime);
        VulkanReflectionAdaptiveTrendEngine.State trend = createTrendState(state);
        VulkanReflectionAdaptiveTrendEngine.resetTelemetry(trend);
        applyTrendState(state, trend);
        apply(runtime, state);
    }

    static ReflectionProbeChurnDiagnostics updateProbeChurnDiagnostics(
            VulkanEngineRuntime runtime,
            VulkanContext.ReflectionProbeDiagnostics diagnostics
    ) {
        VulkanReflectionAdaptiveDiagnostics.ProbeChurnState state = new VulkanReflectionAdaptiveDiagnostics.ProbeChurnState();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, state);
        VulkanReflectionAdaptiveDiagnostics.ProbeChurnUpdateResult result =
                VulkanReflectionAdaptiveDiagnostics.updateProbeChurn(state, diagnostics.activeProbeCount());
        VulkanTelemetryStateBinder.copyMatchingFields(result.state, runtime);
        return result.diagnostics;
    }

    static void resetProbeChurnDiagnostics(VulkanEngineRuntime runtime) {
        VulkanReflectionAdaptiveDiagnostics.ProbeChurnState state = new VulkanReflectionAdaptiveDiagnostics.ProbeChurnState();
        VulkanReflectionAdaptiveDiagnostics.resetProbeChurn(state);
        VulkanTelemetryStateBinder.copyMatchingFields(state, runtime);
    }

    static ReflectionSsrTaaRiskDiagnostics updateSsrTaaRiskDiagnostics(
            VulkanEngineRuntime runtime,
            double taaReject,
            double taaConfidence,
            long taaDrops
    ) {
        VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskState state = new VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskState();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, state);
        VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskUpdateResult result =
                VulkanReflectionAdaptiveDiagnostics.updateSsrTaaRisk(state, taaReject, taaConfidence, taaDrops);
        VulkanTelemetryStateBinder.copyMatchingFields(result.state, runtime);
        return result.diagnostics;
    }

    static void resetSsrTaaRiskDiagnostics(VulkanEngineRuntime runtime) {
        VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskState state = new VulkanReflectionAdaptiveDiagnostics.SsrTaaRiskState();
        VulkanReflectionAdaptiveDiagnostics.resetSsrTaaRisk(state);
        VulkanTelemetryStateBinder.copyMatchingFields(state, runtime);
    }

    static boolean updateAdaptiveTrendWarningGate(VulkanEngineRuntime runtime) {
        State state = snapshot(runtime);
        VulkanReflectionAdaptiveTrendEngine.State trendState = createTrendState(state);
        boolean warningTriggered = VulkanReflectionAdaptiveTrendEngine.updateWarningGate(trendState);
        applyTrendState(state, trendState);
        apply(runtime, state);
        return warningTriggered;
    }

    static void refreshRtPathState(VulkanEngineRuntime runtime, VulkanContext context, int reflectionBaseMode) {
        VulkanReflectionRtStateMachine.State state = new VulkanReflectionRtStateMachine.State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, state);
        VulkanReflectionRtStateMachine.refreshRtPathState(state, reflectionBaseMode, context);
        VulkanTelemetryStateBinder.copyMatchingFields(state, runtime);
    }

    static int composeReflectionExecutionMode(
            VulkanEngineRuntime runtime,
            VulkanContext context,
            int configuredMode,
            boolean rtLaneActive,
            boolean planarSelectiveEligible,
            boolean transparencyCandidatesPresent
    ) {
        VulkanReflectionRtStateMachine.State state = new VulkanReflectionRtStateMachine.State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, state);
        return VulkanReflectionRtStateMachine.composeExecutionMode(
                state,
                configuredMode,
                rtLaneActive,
                planarSelectiveEligible,
                transparencyCandidatesPresent
        );
    }

    static boolean isReflectionSsrPathActive(int reflectionsMode) {
        int baseMode = reflectionsMode & REFLECTION_MODE_BASE_MASK;
        return baseMode == 1 || baseMode == 3 || baseMode == 4;
    }

    private static double computeAdaptiveSeverity(State state) {
        return VulkanReflectionAdaptiveMath.computeAdaptiveSeverity(
                state.reflectionSsrTaaEmaReject,
                state.reflectionSsrTaaEmaConfidence,
                state.reflectionSsrTaaInstabilityRejectMin,
                state.reflectionSsrTaaInstabilityConfidenceMax,
                state.reflectionSsrTaaRiskHighStreak,
                state.reflectionSsrTaaInstabilityWarnMinFrames
        );
    }

    private static int countPlanarEligible(State state, ReflectionOverrideSummary summary) {
        int eligible = 0;
        if (state.reflectionPlanarScopeIncludeAuto) {
            eligible += summary.autoCount();
        }
        if (state.reflectionPlanarScopeIncludeProbeOnly) {
            eligible += summary.probeOnlyCount();
        }
        if (state.reflectionPlanarScopeIncludeSsrOnly) {
            eligible += summary.ssrOnlyCount();
        }
        if (state.reflectionPlanarScopeIncludeOther) {
            eligible += summary.otherCount();
        }
        return Math.max(0, eligible);
    }

    private static int planarScopeExclusionCount(State state, ReflectionOverrideSummary summary) {
        int excluded = 0;
        if (!state.reflectionPlanarScopeIncludeAuto) {
            excluded += summary.autoCount();
        }
        if (!state.reflectionPlanarScopeIncludeProbeOnly) {
            excluded += summary.probeOnlyCount();
        }
        if (!state.reflectionPlanarScopeIncludeSsrOnly) {
            excluded += summary.ssrOnlyCount();
        }
        if (!state.reflectionPlanarScopeIncludeOther) {
            excluded += summary.otherCount();
        }
        return Math.max(0, excluded);
    }

    private static double planarPerfGpuMsCapForTier(State state, QualityTier tier) {
        return switch (tier) {
            case LOW -> state.reflectionPlanarPerfMaxGpuMsLow;
            case MEDIUM -> state.reflectionPlanarPerfMaxGpuMsMedium;
            case HIGH -> state.reflectionPlanarPerfMaxGpuMsHigh;
            case ULTRA -> state.reflectionPlanarPerfMaxGpuMsUltra;
        };
    }

    private static double rtPerfGpuMsCapForTier(State state, QualityTier tier) {
        return switch (tier) {
            case LOW -> state.reflectionRtPerfMaxGpuMsLow;
            case MEDIUM -> state.reflectionRtPerfMaxGpuMsMedium;
            case HIGH -> state.reflectionRtPerfMaxGpuMsHigh;
            case ULTRA -> state.reflectionRtPerfMaxGpuMsUltra;
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static VulkanReflectionAdaptiveTrendEngine.State createTrendState(State state) {
        VulkanReflectionAdaptiveTrendEngine.State trendState = new VulkanReflectionAdaptiveTrendEngine.State();
        VulkanTelemetryStateBinder.copyMatchingFields(state, trendState);
        return trendState;
    }

    private static void applyTrendState(State state, VulkanReflectionAdaptiveTrendEngine.State trendState) {
        VulkanTelemetryStateBinder.copyMatchingFields(trendState, state);
    }

    private static void setAdaptiveTrendWarningTriggered(VulkanEngineRuntime runtime, boolean value) {
        State state = snapshot(runtime);
        state.reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame = value;
        apply(runtime, state);
    }

    private static State snapshot(VulkanEngineRuntime runtime) {
        State state = new State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, state);
        return state;
    }

    private static void apply(VulkanEngineRuntime runtime, State state) {
        VulkanTelemetryStateBinder.copyMatchingFields(state, runtime);
    }

    static final class State {
        PostProcessRenderConfig currentPost;
        List<MaterialDesc> currentSceneMaterials;
        ReflectionProfile reflectionProfile;
        boolean mockContext;
        boolean reflectionRtLaneRequested;
        boolean reflectionRtLaneActive;
        double reflectionTransparencyCandidateReactiveMin;
        long plannedVisibleObjects;
        double lastFrameGpuMs;

        int lastActiveReflectionProbeCount;
        int reflectionProbeLastDelta;
        int reflectionProbeActiveChurnEvents;
        long reflectionProbeActiveDeltaAccum;
        int reflectionProbeChurnHighStreak;
        int reflectionProbeChurnWarnCooldownRemaining;

        boolean reflectionPlanarScopeIncludeAuto;
        boolean reflectionPlanarScopeIncludeProbeOnly;
        boolean reflectionPlanarScopeIncludeSsrOnly;
        boolean reflectionPlanarScopeIncludeOther;
        double reflectionPlanarPerfMaxGpuMsLow;
        double reflectionPlanarPerfMaxGpuMsMedium;
        double reflectionPlanarPerfMaxGpuMsHigh;
        double reflectionPlanarPerfMaxGpuMsUltra;
        double reflectionRtPerfMaxGpuMsLow;
        double reflectionRtPerfMaxGpuMsMedium;
        double reflectionRtPerfMaxGpuMsHigh;
        double reflectionRtPerfMaxGpuMsUltra;

        boolean reflectionSsrTaaAdaptiveEnabled;
        double reflectionSsrTaaAdaptiveTemporalBoostMax;
        double reflectionSsrTaaAdaptiveSsrStrengthScaleMin;
        double reflectionSsrTaaAdaptiveStepScaleBoostMax;
        double reflectionSsrTaaEmaReject;
        double reflectionSsrTaaEmaConfidence;
        double reflectionSsrTaaInstabilityRejectMin;
        double reflectionSsrTaaInstabilityConfidenceMax;
        int reflectionSsrTaaRiskHighStreak;
        int reflectionSsrTaaInstabilityWarnMinFrames;
        double reflectionSsrTaaLatestRejectRate;
        double reflectionSsrTaaLatestConfidenceMean;
        long reflectionSsrTaaLatestDropEvents;
        long reflectionSsrTaaDisocclusionRejectDropEventsMin;
        double reflectionSsrTaaDisocclusionRejectConfidenceMax;
        double reflectionSsrTaaHistoryRejectSeverityMin;
        int reflectionSsrTaaHistoryRejectRiskStreakMin;
        double reflectionSsrTaaHistoryConfidenceDecaySeverityMin;

        float reflectionAdaptiveTemporalWeightActive;
        float reflectionAdaptiveSsrStrengthActive;
        float reflectionAdaptiveSsrStepScaleActive;
        double reflectionAdaptiveSeverityInstant;
        String reflectionSsrTaaHistoryPolicyActive;
        String reflectionSsrTaaReprojectionPolicyActive;
        double reflectionSsrTaaHistoryRejectBiasActive;
        double reflectionSsrTaaHistoryConfidenceDecayActive;
        double reflectionRtDenoiseStrength;

        int reflectionTransparentCandidateCount;
        boolean reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame;
    }

    private VulkanReflectionRuntimeFlow() {
    }
}
