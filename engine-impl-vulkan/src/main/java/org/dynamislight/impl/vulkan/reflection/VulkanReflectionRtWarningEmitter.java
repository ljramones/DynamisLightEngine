package org.dynamislight.impl.vulkan.reflection;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;

final class VulkanReflectionRtWarningEmitter {
    private VulkanReflectionRtWarningEmitter() {
    }

    static final class State {
        boolean reflectionRtLaneActive;
        boolean reflectionRtMultiBounceEnabled;
        boolean reflectionRtSingleBounceEnabled;
        boolean reflectionRtRequireActive;
        boolean reflectionRtRequireMultiBounce;
        boolean reflectionRtRequireDedicatedPipeline;
        boolean reflectionRtDedicatedPipelineEnabled;
        boolean reflectionRtTraversalSupported;
        boolean reflectionRtDedicatedCapabilitySupported;
        boolean reflectionRtDedicatedDenoisePipelineEnabled;
        double reflectionRtDenoiseStrength;
        String reflectionRtFallbackChainActive;
        boolean reflectionRtRequireActiveUnmetLastFrame;
        boolean reflectionRtRequireMultiBounceUnmetLastFrame;
        boolean reflectionRtRequireDedicatedPipelineUnmetLastFrame;
        boolean reflectionRtDedicatedHardwarePipelineActive;
        String reflectionRtBlasLifecycleState;
        String reflectionRtTlasLifecycleState;
        String reflectionRtSbtLifecycleState;
        int reflectionRtBlasObjectCount;
        int reflectionRtTlasInstanceCount;
        int reflectionRtSbtRecordCount;
        boolean mockContext;

        int reflectionRtPerfHighStreak;
        int reflectionRtPerfWarnCooldownRemaining;
        int reflectionRtPerfWarnMinFrames;
        int reflectionRtPerfWarnCooldownFrames;
        boolean reflectionRtPerfBreachedLastFrame;
        double reflectionRtPerfLastGpuMsEstimate;
        double reflectionRtPerfLastGpuMsCap;

        double reflectionRtHybridRtShare;
        double reflectionRtHybridSsrShare;
        double reflectionRtHybridProbeShare;
        double reflectionRtHybridProbeShareWarnMax;
        int reflectionRtHybridHighStreak;
        int reflectionRtHybridWarnCooldownRemaining;
        int reflectionRtHybridWarnMinFrames;
        int reflectionRtHybridWarnCooldownFrames;
        boolean reflectionRtHybridBreachedLastFrame;

        double reflectionRtDenoiseSpatialVariance;
        double reflectionRtDenoiseTemporalLag;
        double reflectionRtDenoiseSpatialVarianceWarnMax;
        double reflectionRtDenoiseTemporalLagWarnMax;
        int reflectionRtDenoiseHighStreak;
        int reflectionRtDenoiseWarnCooldownRemaining;
        int reflectionRtDenoiseWarnMinFrames;
        int reflectionRtDenoiseWarnCooldownFrames;
        boolean reflectionRtDenoiseBreachedLastFrame;

        double reflectionRtAsBuildGpuMsEstimate;
        double reflectionRtAsMemoryMbEstimate;
        double reflectionRtAsBuildGpuMsWarnMax;
        double reflectionRtAsMemoryBudgetMb;
        int reflectionRtAsBudgetHighStreak;
        int reflectionRtAsBudgetWarnCooldownRemaining;
        boolean reflectionRtAsBudgetBreachedLastFrame;

        boolean reflectionRtPromotionReadyLastFrame;
        int reflectionRtPromotionReadyHighStreak;
    }

    static void emit(
            List<EngineWarning> warnings,
            State state,
            boolean reflectionRtLaneRequested,
            int reflectionBaseMode,
            double lastFrameGpuMs,
            long plannedVisibleObjects,
            double ssrStrength,
            double ssrMaxRoughness,
            double reflectionTemporalWeight,
            double rtGpuMsCap
    ) {
        boolean reflectionRtMultiBounceActive = state.reflectionRtLaneActive && state.reflectionRtMultiBounceEnabled;
        if (reflectionRtLaneRequested || reflectionBaseMode == 4) {
            warnings.add(new EngineWarning(
                    "REFLECTION_RT_PATH_REQUESTED",
                    "RT reflection path requested (singleBounceEnabled=" + state.reflectionRtSingleBounceEnabled
                            + ", multiBounceEnabled=" + state.reflectionRtMultiBounceEnabled
                            + ", multiBounceActive=" + reflectionRtMultiBounceActive
                            + ", requireActive=" + state.reflectionRtRequireActive
                            + ", requireMultiBounce=" + state.reflectionRtRequireMultiBounce
                            + ", requireDedicatedPipeline=" + state.reflectionRtRequireDedicatedPipeline
                            + ", dedicatedPipelineEnabled=" + state.reflectionRtDedicatedPipelineEnabled
                            + ", traversalSupported=" + state.reflectionRtTraversalSupported
                            + ", dedicatedCapabilitySupported=" + state.reflectionRtDedicatedCapabilitySupported
                            + ", dedicatedDenoisePipelineEnabled=" + state.reflectionRtDedicatedDenoisePipelineEnabled
                            + ", denoiseStrength=" + state.reflectionRtDenoiseStrength
                            + ", laneActive=" + state.reflectionRtLaneActive
                            + ", fallbackChain=" + state.reflectionRtFallbackChainActive + ")"
            ));
            if (state.reflectionRtDedicatedHardwarePipelineActive) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_DEDICATED_PIPELINE_ACTIVE",
                        "RT dedicated pipeline active (dedicatedHardwarePipelineActive=true, mockContext="
                                + state.mockContext + ", dedicatedCapabilitySupported="
                                + state.reflectionRtDedicatedCapabilitySupported + ")"
                ));
            } else {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_DEDICATED_PIPELINE_PENDING",
                        "RT dedicated pipeline contract (dedicatedHardwarePipelineActive="
                                + state.reflectionRtDedicatedHardwarePipelineActive
                                + ", dedicatedPipelineEnabled=" + state.reflectionRtDedicatedPipelineEnabled
                                + ", dedicatedCapabilitySupported=" + state.reflectionRtDedicatedCapabilitySupported
                                + ", requireDedicatedPipeline=" + state.reflectionRtRequireDedicatedPipeline + ")"
                ));
            }
            warnings.add(new EngineWarning(
                    "REFLECTION_RT_PIPELINE_LIFECYCLE",
                    "RT pipeline lifecycle (blasState=" + state.reflectionRtBlasLifecycleState
                            + ", tlasState=" + state.reflectionRtTlasLifecycleState
                            + ", sbtState=" + state.reflectionRtSbtLifecycleState
                            + ", blasObjectCount=" + state.reflectionRtBlasObjectCount
                            + ", tlasInstanceCount=" + state.reflectionRtTlasInstanceCount
                            + ", sbtRecordCount=" + state.reflectionRtSbtRecordCount
                            + ", dedicatedActive=" + state.reflectionRtDedicatedHardwarePipelineActive + ")"
            ));
            if (!state.reflectionRtLaneActive) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_PATH_FALLBACK_ACTIVE",
                        "RT reflection lane unavailable; fallback chain active (" + state.reflectionRtFallbackChainActive + ")"
                ));
            }
            if (state.reflectionRtRequireActiveUnmetLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_PATH_REQUIRED_UNAVAILABLE_BREACH",
                        "RT reflection lane required but unavailable (requireActive=true, fallbackChain="
                                + state.reflectionRtFallbackChainActive + ")"
                ));
            }
            if (state.reflectionRtRequireMultiBounceUnmetLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_MULTI_BOUNCE_REQUIRED_UNAVAILABLE_BREACH",
                        "RT multi-bounce required but unavailable (requireMultiBounce=true, laneActive="
                                + state.reflectionRtLaneActive + ", multiBounceEnabled=" + state.reflectionRtMultiBounceEnabled + ")"
                ));
            }
            if (state.reflectionRtRequireDedicatedPipelineUnmetLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_DEDICATED_PIPELINE_REQUIRED_UNAVAILABLE_BREACH",
                        "RT dedicated pipeline required but unavailable (requireDedicatedPipeline=true)"
                ));
            }

            double rtLaneWeight = state.reflectionRtLaneActive ? 0.45 : 0.0;
            double bounceFactor = state.reflectionRtMultiBounceEnabled ? 1.35 : 1.0;
            double denoiseFactor = state.reflectionRtDedicatedDenoisePipelineEnabled ? 1.08 : 1.0;
            double rtGpuMsEstimate = Math.max(0.0, lastFrameGpuMs) * rtLaneWeight * bounceFactor * denoiseFactor;
            boolean rtPerfRisk = rtGpuMsEstimate > rtGpuMsCap;
            if (rtPerfRisk) {
                state.reflectionRtPerfHighStreak++;
            } else {
                state.reflectionRtPerfHighStreak = 0;
            }
            boolean rtPerfTriggered = false;
            if (state.reflectionRtPerfWarnCooldownRemaining > 0) {
                state.reflectionRtPerfWarnCooldownRemaining--;
            }
            if (rtPerfRisk
                    && state.reflectionRtPerfHighStreak >= state.reflectionRtPerfWarnMinFrames
                    && state.reflectionRtPerfWarnCooldownRemaining <= 0) {
                state.reflectionRtPerfWarnCooldownRemaining = state.reflectionRtPerfWarnCooldownFrames;
                rtPerfTriggered = true;
            }
            state.reflectionRtPerfLastGpuMsEstimate = rtGpuMsEstimate;
            state.reflectionRtPerfLastGpuMsCap = rtGpuMsCap;
            state.reflectionRtPerfBreachedLastFrame = rtPerfTriggered;
            warnings.add(new EngineWarning(
                    "REFLECTION_RT_PERF_GATES",
                    "RT perf gates (risk=" + rtPerfRisk
                            + ", laneActive=" + state.reflectionRtLaneActive
                            + ", gpuMsEstimate=" + rtGpuMsEstimate
                            + ", gpuMsCap=" + rtGpuMsCap
                            + ", multiBounceEnabled=" + state.reflectionRtMultiBounceEnabled
                            + ", dedicatedDenoisePipelineEnabled=" + state.reflectionRtDedicatedDenoisePipelineEnabled
                            + ", highStreak=" + state.reflectionRtPerfHighStreak
                            + ", warnMinFrames=" + state.reflectionRtPerfWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionRtPerfWarnCooldownFrames
                            + ", warnCooldownRemaining=" + state.reflectionRtPerfWarnCooldownRemaining
                            + ", breached=" + rtPerfTriggered + ")"
            ));
            if (rtPerfTriggered) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_PERF_GATES_BREACH",
                        "RT perf gates breached (gpuMsEstimate=" + rtGpuMsEstimate
                                + ", gpuMsCap=" + rtGpuMsCap
                                + ", multiBounceEnabled=" + state.reflectionRtMultiBounceEnabled
                                + ", dedicatedDenoisePipelineEnabled=" + state.reflectionRtDedicatedDenoisePipelineEnabled + ")"
                ));
            }

            state.reflectionRtHybridRtShare = state.reflectionRtLaneActive
                    ? Math.max(0.15, Math.min(0.85, 1.0 - ssrMaxRoughness))
                    : 0.0;
            state.reflectionRtHybridSsrShare = Math.max(
                    0.0,
                    Math.min(1.0 - state.reflectionRtHybridRtShare, ssrStrength * (1.0 - state.reflectionRtHybridRtShare))
            );
            state.reflectionRtHybridProbeShare = Math.max(0.0, 1.0 - state.reflectionRtHybridRtShare - state.reflectionRtHybridSsrShare);
            boolean rtHybridRisk = state.reflectionRtLaneActive && state.reflectionRtHybridProbeShare > state.reflectionRtHybridProbeShareWarnMax;
            if (rtHybridRisk) {
                state.reflectionRtHybridHighStreak++;
            } else {
                state.reflectionRtHybridHighStreak = 0;
            }
            boolean rtHybridTriggered = false;
            if (state.reflectionRtHybridWarnCooldownRemaining > 0) {
                state.reflectionRtHybridWarnCooldownRemaining--;
            }
            if (rtHybridRisk
                    && state.reflectionRtHybridHighStreak >= state.reflectionRtHybridWarnMinFrames
                    && state.reflectionRtHybridWarnCooldownRemaining <= 0) {
                state.reflectionRtHybridWarnCooldownRemaining = state.reflectionRtHybridWarnCooldownFrames;
                rtHybridTriggered = true;
            }
            state.reflectionRtHybridBreachedLastFrame = rtHybridTriggered;
            warnings.add(new EngineWarning(
                    "REFLECTION_RT_HYBRID_COMPOSITION",
                    "RT hybrid composition (rtShare=" + state.reflectionRtHybridRtShare
                            + ", ssrShare=" + state.reflectionRtHybridSsrShare
                            + ", probeShare=" + state.reflectionRtHybridProbeShare
                            + ", laneActive=" + state.reflectionRtLaneActive
                            + ", threshold=" + state.reflectionRtHybridProbeShareWarnMax
                            + ", highStreak=" + state.reflectionRtHybridHighStreak
                            + ", warnMinFrames=" + state.reflectionRtHybridWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionRtHybridWarnCooldownFrames
                            + ", warnCooldownRemaining=" + state.reflectionRtHybridWarnCooldownRemaining
                            + ", breached=" + state.reflectionRtHybridBreachedLastFrame + ")"
            ));
            if (state.reflectionRtHybridBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_HYBRID_COMPOSITION_BREACH",
                        "RT hybrid composition breached (probeShare=" + state.reflectionRtHybridProbeShare
                                + ", threshold=" + state.reflectionRtHybridProbeShareWarnMax
                                + ", laneActive=" + state.reflectionRtLaneActive + ")"
                ));
            }

            double denoiseBoost = state.reflectionRtDedicatedDenoisePipelineEnabled ? 1.12 : 1.0;
            double bounceBoost = state.reflectionRtMultiBounceEnabled ? 1.20 : 1.0;
            state.reflectionRtDenoiseSpatialVariance = Math.max(
                    0.0,
                    Math.min(1.0, (1.0 - state.reflectionRtDenoiseStrength) * denoiseBoost * bounceBoost)
            );
            state.reflectionRtDenoiseTemporalLag = Math.max(
                    0.0,
                    Math.min(1.0, reflectionTemporalWeight * 0.60 * denoiseBoost)
            );
            boolean rtDenoiseRisk = state.reflectionRtLaneActive
                    && (state.reflectionRtDenoiseSpatialVariance > state.reflectionRtDenoiseSpatialVarianceWarnMax
                    || state.reflectionRtDenoiseTemporalLag > state.reflectionRtDenoiseTemporalLagWarnMax);
            if (rtDenoiseRisk) {
                state.reflectionRtDenoiseHighStreak++;
            } else {
                state.reflectionRtDenoiseHighStreak = 0;
            }
            boolean rtDenoiseTriggered = false;
            if (state.reflectionRtDenoiseWarnCooldownRemaining > 0) {
                state.reflectionRtDenoiseWarnCooldownRemaining--;
            }
            if (rtDenoiseRisk
                    && state.reflectionRtDenoiseHighStreak >= state.reflectionRtDenoiseWarnMinFrames
                    && state.reflectionRtDenoiseWarnCooldownRemaining <= 0) {
                state.reflectionRtDenoiseWarnCooldownRemaining = state.reflectionRtDenoiseWarnCooldownFrames;
                rtDenoiseTriggered = true;
            }
            state.reflectionRtDenoiseBreachedLastFrame = rtDenoiseTriggered;
            warnings.add(new EngineWarning(
                    "REFLECTION_RT_DENOISE_ENVELOPE",
                    "RT denoise envelope (spatialVariance=" + state.reflectionRtDenoiseSpatialVariance
                            + ", temporalLag=" + state.reflectionRtDenoiseTemporalLag
                            + ", spatialVarianceMax=" + state.reflectionRtDenoiseSpatialVarianceWarnMax
                            + ", temporalLagMax=" + state.reflectionRtDenoiseTemporalLagWarnMax
                            + ", highStreak=" + state.reflectionRtDenoiseHighStreak
                            + ", warnMinFrames=" + state.reflectionRtDenoiseWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionRtDenoiseWarnCooldownFrames
                            + ", warnCooldownRemaining=" + state.reflectionRtDenoiseWarnCooldownRemaining
                            + ", breached=" + state.reflectionRtDenoiseBreachedLastFrame + ")"
            ));
            if (state.reflectionRtDenoiseBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_DENOISE_ENVELOPE_BREACH",
                        "RT denoise envelope breached (spatialVariance=" + state.reflectionRtDenoiseSpatialVariance
                                + ", temporalLag=" + state.reflectionRtDenoiseTemporalLag + ")"
                ));
            }

            double asBuildFactor = state.reflectionRtDedicatedHardwarePipelineActive ? 0.11 : 0.07;
            double asBounceFactor = state.reflectionRtMultiBounceEnabled ? 1.25 : 1.0;
            state.reflectionRtAsBuildGpuMsEstimate = Math.max(0.0, lastFrameGpuMs) * asBuildFactor * asBounceFactor;
            long sceneObjectEstimate = Math.max(0L, plannedVisibleObjects);
            state.reflectionRtAsMemoryMbEstimate = Math.max(0.0, sceneObjectEstimate * 0.11);
            boolean rtAsBudgetRisk = state.reflectionRtLaneActive
                    && (state.reflectionRtAsBuildGpuMsEstimate > state.reflectionRtAsBuildGpuMsWarnMax
                    || state.reflectionRtAsMemoryMbEstimate > state.reflectionRtAsMemoryBudgetMb);
            if (rtAsBudgetRisk) {
                state.reflectionRtAsBudgetHighStreak++;
            } else {
                state.reflectionRtAsBudgetHighStreak = 0;
            }
            boolean rtAsBudgetTriggered = false;
            if (state.reflectionRtAsBudgetWarnCooldownRemaining > 0) {
                state.reflectionRtAsBudgetWarnCooldownRemaining--;
            }
            if (rtAsBudgetRisk
                    && state.reflectionRtAsBudgetHighStreak >= state.reflectionRtPerfWarnMinFrames
                    && state.reflectionRtAsBudgetWarnCooldownRemaining <= 0) {
                state.reflectionRtAsBudgetWarnCooldownRemaining = state.reflectionRtPerfWarnCooldownFrames;
                rtAsBudgetTriggered = true;
            }
            state.reflectionRtAsBudgetBreachedLastFrame = rtAsBudgetTriggered;
            warnings.add(new EngineWarning(
                    "REFLECTION_RT_AS_BUDGET",
                    "RT AS budget (buildGpuMsEstimate=" + state.reflectionRtAsBuildGpuMsEstimate
                            + ", buildGpuMsMax=" + state.reflectionRtAsBuildGpuMsWarnMax
                            + ", memoryMbEstimate=" + state.reflectionRtAsMemoryMbEstimate
                            + ", memoryMbBudget=" + state.reflectionRtAsMemoryBudgetMb
                            + ", highStreak=" + state.reflectionRtAsBudgetHighStreak
                            + ", warnMinFrames=" + state.reflectionRtPerfWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionRtPerfWarnCooldownFrames
                            + ", warnCooldownRemaining=" + state.reflectionRtAsBudgetWarnCooldownRemaining
                            + ", breached=" + state.reflectionRtAsBudgetBreachedLastFrame + ")"
            ));
            if (state.reflectionRtAsBudgetBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_RT_AS_BUDGET_BREACH",
                        "RT AS budget breached (buildGpuMsEstimate=" + state.reflectionRtAsBuildGpuMsEstimate
                                + ", memoryMbEstimate=" + state.reflectionRtAsMemoryMbEstimate + ")"
                ));
            }
            return;
        }

        state.reflectionRtFallbackChainActive = "probe";
        state.reflectionRtRequireActiveUnmetLastFrame = false;
        state.reflectionRtRequireMultiBounceUnmetLastFrame = false;
        state.reflectionRtRequireDedicatedPipelineUnmetLastFrame = false;
        state.reflectionRtTraversalSupported = false;
        state.reflectionRtDedicatedCapabilitySupported = false;
        state.reflectionRtDedicatedHardwarePipelineActive = false;
        state.reflectionRtBlasLifecycleState = "disabled";
        state.reflectionRtTlasLifecycleState = "disabled";
        state.reflectionRtSbtLifecycleState = "disabled";
        state.reflectionRtBlasObjectCount = 0;
        state.reflectionRtTlasInstanceCount = 0;
        state.reflectionRtSbtRecordCount = 0;
        state.reflectionRtHybridRtShare = 0.0;
        state.reflectionRtHybridSsrShare = Math.max(0.0, Math.min(1.0, ssrStrength));
        state.reflectionRtHybridProbeShare = Math.max(0.0, 1.0 - state.reflectionRtHybridSsrShare);
        state.reflectionRtHybridHighStreak = 0;
        state.reflectionRtHybridWarnCooldownRemaining = 0;
        state.reflectionRtHybridBreachedLastFrame = false;
        state.reflectionRtDenoiseSpatialVariance = 0.0;
        state.reflectionRtDenoiseTemporalLag = 0.0;
        state.reflectionRtDenoiseHighStreak = 0;
        state.reflectionRtDenoiseWarnCooldownRemaining = 0;
        state.reflectionRtDenoiseBreachedLastFrame = false;
        state.reflectionRtAsBuildGpuMsEstimate = 0.0;
        state.reflectionRtAsMemoryMbEstimate = 0.0;
        state.reflectionRtAsBudgetHighStreak = 0;
        state.reflectionRtAsBudgetWarnCooldownRemaining = 0;
        state.reflectionRtAsBudgetBreachedLastFrame = false;
        state.reflectionRtPromotionReadyLastFrame = false;
        state.reflectionRtPromotionReadyHighStreak = 0;
        state.reflectionRtPerfHighStreak = 0;
        state.reflectionRtPerfWarnCooldownRemaining = 0;
        state.reflectionRtPerfBreachedLastFrame = false;
        state.reflectionRtPerfLastGpuMsEstimate = 0.0;
        state.reflectionRtPerfLastGpuMsCap = rtGpuMsCap;
    }
}
