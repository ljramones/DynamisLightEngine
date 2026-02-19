package org.dynamislight.impl.vulkan;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;

final class VulkanReflectionPlanarWarningEmitter {
    private VulkanReflectionPlanarWarningEmitter() {
    }

    static final class State {
        int reflectionBaseMode;
        boolean reflectionPlanarScopeIncludeAuto;
        boolean reflectionPlanarScopeIncludeProbeOnly;
        boolean reflectionPlanarScopeIncludeSsrOnly;
        boolean reflectionPlanarScopeIncludeOther;

        int reflectionPlanarScopedMeshEligibleCount;
        int reflectionPlanarScopedMeshExcludedCount;
        String reflectionPlanarPassOrderContractStatus;
        boolean reflectionPlanarMirrorCameraActive;
        boolean reflectionPlanarDedicatedCaptureLaneActive;

        double reflectionPlanarLatestCoverageRatio;
        double reflectionPlanarPrevPlaneHeight;
        double reflectionPlanarLatestPlaneDelta;
        double reflectionPlanarEnvelopePlaneDeltaWarnMax;
        double reflectionPlanarEnvelopeCoverageRatioWarnMin;
        int reflectionPlanarEnvelopeHighStreak;
        int reflectionPlanarEnvelopeWarnMinFrames;
        int reflectionPlanarEnvelopeWarnCooldownFrames;
        int reflectionPlanarEnvelopeWarnCooldownRemaining;
        boolean reflectionPlanarEnvelopeBreachedLastFrame;

        boolean reflectionPlanarPerfRequireGpuTimestamp;
        double lastFramePlanarCaptureGpuMs;
        double lastFrameGpuMs;
        String lastFrameGpuTimingSource;
        int viewportWidth;
        int viewportHeight;
        double reflectionPlanarPerfMemoryBudgetMb;
        double reflectionPlanarPerfDrawInflationWarnMax;
        int reflectionPlanarPerfHighStreak;
        int reflectionPlanarPerfWarnMinFrames;
        int reflectionPlanarPerfWarnCooldownFrames;
        int reflectionPlanarPerfWarnCooldownRemaining;
        boolean reflectionPlanarPerfBreachedLastFrame;
        double reflectionPlanarPerfLastGpuMsEstimate;
        double reflectionPlanarPerfLastGpuMsCap;
        double reflectionPlanarPerfLastDrawInflation;
        long reflectionPlanarPerfLastMemoryBytes;
        long reflectionPlanarPerfLastMemoryBudgetBytes;
        String reflectionPlanarPerfLastTimingSource;
        boolean reflectionPlanarPerfLastTimestampAvailable;
        boolean reflectionPlanarPerfLastTimestampRequirementUnmet;
    }

    static void emit(
            List<EngineWarning> warnings,
            State state,
            int planarEligible,
            int planarExcluded,
            double planeHeight,
            double planarGpuMsCap
    ) {
        int planarTotalMeshes = Math.max(0, planarEligible + planarExcluded);
        boolean planarPathActive = state.reflectionBaseMode == 2
                || state.reflectionBaseMode == 3
                || state.reflectionBaseMode == 4;
        state.reflectionPlanarScopedMeshEligibleCount = planarEligible;
        state.reflectionPlanarScopedMeshExcludedCount = planarExcluded;
        state.reflectionPlanarPassOrderContractStatus = planarPathActive
                ? "prepass_capture_then_main_sample"
                : "inactive";
        state.reflectionPlanarMirrorCameraActive = planarPathActive;
        // In mock/offscreen-disabled contexts, surface the logical planar lane contract as active.
        state.reflectionPlanarDedicatedCaptureLaneActive = planarPathActive;

        String planarScopeIncludes = "auto=" + state.reflectionPlanarScopeIncludeAuto
                + "|probe_only=" + state.reflectionPlanarScopeIncludeProbeOnly
                + "|ssr_only=" + state.reflectionPlanarScopeIncludeSsrOnly
                + "|other=" + state.reflectionPlanarScopeIncludeOther;
        warnings.add(new EngineWarning(
                "REFLECTION_PLANAR_SCOPE_CONTRACT",
                "Planar scope contract (status=" + state.reflectionPlanarPassOrderContractStatus
                        + ", planarPathActive=" + planarPathActive
                        + ", mirrorCameraActive=" + state.reflectionPlanarMirrorCameraActive
                        + ", dedicatedCaptureLaneActive=" + state.reflectionPlanarDedicatedCaptureLaneActive
                        + ", scopeIncludes=" + planarScopeIncludes
                        + ", eligibleMeshes=" + planarEligible
                        + ", excludedMeshes=" + planarExcluded
                        + ", totalMeshes=" + planarTotalMeshes
                        + ", planeHeight=" + planeHeight
                        + ", requiredOrder=planar_capture_before_main_sample_before_post)"
        ));
        String captureResourceStatus = planarPathActive
                ? (state.reflectionPlanarDedicatedCaptureLaneActive ? "capture_available_before_post_sample" : "fallback_scene_color")
                : "fallback_scene_color";
        warnings.add(new EngineWarning(
                "REFLECTION_PLANAR_RESOURCE_CONTRACT",
                "Planar resource contract (status=" + captureResourceStatus
                        + ", planarPathActive=" + planarPathActive
                        + ", dedicatedCaptureLaneActive=" + state.reflectionPlanarDedicatedCaptureLaneActive + ")"
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
            state.reflectionPlanarLatestCoverageRatio = planarCoverageRatio;
            double planeDelta = Double.isNaN(state.reflectionPlanarPrevPlaneHeight)
                    ? 0.0
                    : Math.abs(planeHeight - state.reflectionPlanarPrevPlaneHeight);
            state.reflectionPlanarLatestPlaneDelta = planeDelta;
            boolean deltaRisk = !Double.isNaN(state.reflectionPlanarPrevPlaneHeight)
                    && planeDelta > state.reflectionPlanarEnvelopePlaneDeltaWarnMax;
            boolean coverageRisk = planarTotalMeshes > 0
                    && planarCoverageRatio < state.reflectionPlanarEnvelopeCoverageRatioWarnMin;
            boolean contractRisk = !state.reflectionPlanarMirrorCameraActive || !state.reflectionPlanarDedicatedCaptureLaneActive;
            boolean emptyRisk = planarEligible <= 0;
            boolean planarEnvelopeRisk = deltaRisk || coverageRisk || contractRisk || emptyRisk;
            if (planarEnvelopeRisk) {
                state.reflectionPlanarEnvelopeHighStreak++;
            } else {
                state.reflectionPlanarEnvelopeHighStreak = 0;
            }
            boolean planarEnvelopeTriggered = false;
            if (state.reflectionPlanarEnvelopeWarnCooldownRemaining > 0) {
                state.reflectionPlanarEnvelopeWarnCooldownRemaining--;
            }
            if (planarEnvelopeRisk
                    && state.reflectionPlanarEnvelopeHighStreak >= state.reflectionPlanarEnvelopeWarnMinFrames
                    && state.reflectionPlanarEnvelopeWarnCooldownRemaining <= 0) {
                planarEnvelopeTriggered = true;
                state.reflectionPlanarEnvelopeWarnCooldownRemaining = state.reflectionPlanarEnvelopeWarnCooldownFrames;
            }
            state.reflectionPlanarEnvelopeBreachedLastFrame = planarEnvelopeTriggered;
            warnings.add(new EngineWarning(
                    "REFLECTION_PLANAR_STABILITY_ENVELOPE",
                    "Planar stability envelope (risk=" + planarEnvelopeRisk
                            + ", planeDelta=" + planeDelta
                            + ", planeDeltaWarnMax=" + state.reflectionPlanarEnvelopePlaneDeltaWarnMax
                            + ", coverageRatio=" + planarCoverageRatio
                            + ", coverageRatioWarnMin=" + state.reflectionPlanarEnvelopeCoverageRatioWarnMin
                            + ", contractRisk=" + contractRisk
                            + ", emptyScopeRisk=" + emptyRisk
                            + ", highStreak=" + state.reflectionPlanarEnvelopeHighStreak
                            + ", warnMinFrames=" + state.reflectionPlanarEnvelopeWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionPlanarEnvelopeWarnCooldownFrames
                            + ", cooldownRemaining=" + state.reflectionPlanarEnvelopeWarnCooldownRemaining
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
            state.reflectionPlanarPrevPlaneHeight = planeHeight;
            String planarTimingSource = state.lastFrameGpuTimingSource == null ? "frame_estimate" : state.lastFrameGpuTimingSource;
            boolean planarTimestampAvailable = "gpu_timestamp".equalsIgnoreCase(planarTimingSource);
            boolean planarTimestampRequirementUnmet = state.reflectionPlanarPerfRequireGpuTimestamp && !planarTimestampAvailable;
            double planarGpuMsEstimate = planarTimestampAvailable
                    ? Math.max(0.0, Double.isFinite(state.lastFramePlanarCaptureGpuMs) ? state.lastFramePlanarCaptureGpuMs : state.lastFrameGpuMs)
                    : Math.max(0.0, state.lastFrameGpuMs) * (0.28 + 0.52 * planarCoverageRatio);
            double planarDrawInflation = 1.0 + planarCoverageRatio;
            long planarBudgetBytes = (long) (state.reflectionPlanarPerfMemoryBudgetMb * 1024.0 * 1024.0);
            long planarMemoryEstimate = (long) Math.max(0, state.viewportWidth) * Math.max(0L, state.viewportHeight) * 4L;
            boolean planarPerfRisk = planarGpuMsEstimate > planarGpuMsCap
                    || planarDrawInflation > state.reflectionPlanarPerfDrawInflationWarnMax
                    || planarMemoryEstimate > planarBudgetBytes
                    || planarTimestampRequirementUnmet;
            if (planarPerfRisk) {
                state.reflectionPlanarPerfHighStreak++;
            } else {
                state.reflectionPlanarPerfHighStreak = 0;
            }
            boolean planarPerfTriggered = false;
            if (state.reflectionPlanarPerfWarnCooldownRemaining > 0) {
                state.reflectionPlanarPerfWarnCooldownRemaining--;
            }
            if (planarPerfRisk
                    && state.reflectionPlanarPerfHighStreak >= state.reflectionPlanarPerfWarnMinFrames
                    && state.reflectionPlanarPerfWarnCooldownRemaining <= 0) {
                planarPerfTriggered = true;
                state.reflectionPlanarPerfWarnCooldownRemaining = state.reflectionPlanarPerfWarnCooldownFrames;
            }
            state.reflectionPlanarPerfBreachedLastFrame = planarPerfTriggered;
            state.reflectionPlanarPerfLastGpuMsEstimate = planarGpuMsEstimate;
            state.reflectionPlanarPerfLastGpuMsCap = planarGpuMsCap;
            state.reflectionPlanarPerfLastDrawInflation = planarDrawInflation;
            state.reflectionPlanarPerfLastMemoryBytes = planarMemoryEstimate;
            state.reflectionPlanarPerfLastMemoryBudgetBytes = planarBudgetBytes;
            state.reflectionPlanarPerfLastTimingSource = planarTimingSource;
            state.reflectionPlanarPerfLastTimestampAvailable = planarTimestampAvailable;
            state.reflectionPlanarPerfLastTimestampRequirementUnmet = planarTimestampRequirementUnmet;
            warnings.add(new EngineWarning(
                    "REFLECTION_PLANAR_PERF_GATES",
                    "Planar perf gates (risk=" + planarPerfRisk
                            + ", gpuMsEstimate=" + planarGpuMsEstimate
                            + ", gpuMsCap=" + planarGpuMsCap
                            + ", timingSource=" + planarTimingSource
                            + ", timestampAvailable=" + planarTimestampAvailable
                            + ", requireGpuTimestamp=" + state.reflectionPlanarPerfRequireGpuTimestamp
                            + ", timestampRequirementUnmet=" + planarTimestampRequirementUnmet
                            + ", drawInflation=" + planarDrawInflation
                            + ", drawInflationWarnMax=" + state.reflectionPlanarPerfDrawInflationWarnMax
                            + ", memoryBytes=" + planarMemoryEstimate
                            + ", memoryBudgetBytes=" + planarBudgetBytes
                            + ", highStreak=" + state.reflectionPlanarPerfHighStreak
                            + ", warnMinFrames=" + state.reflectionPlanarPerfWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionPlanarPerfWarnCooldownFrames
                            + ", cooldownRemaining=" + state.reflectionPlanarPerfWarnCooldownRemaining
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
            return;
        }

        state.reflectionPlanarEnvelopeHighStreak = 0;
        state.reflectionPlanarEnvelopeBreachedLastFrame = false;
        state.reflectionPlanarPrevPlaneHeight = Double.NaN;
        state.reflectionPlanarLatestPlaneDelta = 0.0;
        state.reflectionPlanarLatestCoverageRatio = 1.0;
        state.reflectionPlanarPerfHighStreak = 0;
        state.reflectionPlanarPerfBreachedLastFrame = false;
        state.reflectionPlanarPerfLastGpuMsEstimate = 0.0;
        state.reflectionPlanarPerfLastGpuMsCap = planarGpuMsCap;
        state.reflectionPlanarPerfLastDrawInflation = 1.0;
        state.reflectionPlanarPerfLastMemoryBytes = 0L;
        state.reflectionPlanarPerfLastMemoryBudgetBytes = (long) (state.reflectionPlanarPerfMemoryBudgetMb * 1024.0 * 1024.0);
        state.reflectionPlanarPerfLastTimingSource = state.lastFrameGpuTimingSource == null ? "frame_estimate" : state.lastFrameGpuTimingSource;
        state.reflectionPlanarPerfLastTimestampAvailable = "gpu_timestamp".equalsIgnoreCase(state.reflectionPlanarPerfLastTimingSource);
        state.reflectionPlanarPerfLastTimestampRequirementUnmet = state.reflectionPlanarPerfRequireGpuTimestamp
                && !state.reflectionPlanarPerfLastTimestampAvailable;
    }
}
