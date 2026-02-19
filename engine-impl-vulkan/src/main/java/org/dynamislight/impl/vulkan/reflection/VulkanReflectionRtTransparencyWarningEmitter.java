package org.dynamislight.impl.vulkan.reflection;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;

final class VulkanReflectionRtTransparencyWarningEmitter {
    private VulkanReflectionRtTransparencyWarningEmitter() {
    }

    static final class State {
        boolean reflectionRtLaneActive;
        boolean reflectionRtDedicatedHardwarePipelineActive;
        boolean mockContext;
        boolean reflectionRtDedicatedPipelineEnabled;
        boolean reflectionRtRequireActiveUnmetLastFrame;
        boolean reflectionRtRequireMultiBounceUnmetLastFrame;
        boolean reflectionRtRequireDedicatedPipelineUnmetLastFrame;
        boolean reflectionRtPerfBreachedLastFrame;
        boolean reflectionRtHybridBreachedLastFrame;
        boolean reflectionRtDenoiseBreachedLastFrame;
        boolean reflectionRtAsBudgetBreachedLastFrame;
        int reflectionRtPromotionReadyHighStreak;
        int reflectionRtPromotionReadyMinFrames;
        boolean reflectionRtPromotionReadyLastFrame;

        double reflectionTransparencyCandidateReactiveMin;
        double reflectionTransparencyProbeOnlyRatioWarnMax;
        int reflectionTransparencyWarnMinFrames;
        int reflectionTransparencyWarnCooldownFrames;
        int reflectionTransparencyWarnCooldownRemaining;
        int reflectionTransparencyHighStreak;
        boolean reflectionTransparencyBreachedLastFrame;
        String reflectionTransparencyStageGateStatus;
        String reflectionTransparencyFallbackPath;
        int reflectionTransparentCandidateCount;
        int reflectionTransparencyAlphaTestedCandidateCount;
        int reflectionTransparencyReactiveCandidateCount;
        int reflectionTransparencyProbeOnlyCandidateCount;
    }

    static void emit(List<EngineWarning> warnings, State state, TransparencyCandidateSummary summary) {
        state.reflectionTransparentCandidateCount = summary.totalCount();
        state.reflectionTransparencyAlphaTestedCandidateCount = summary.alphaTestedCount();
        state.reflectionTransparencyReactiveCandidateCount = summary.reactiveCandidateCount();
        state.reflectionTransparencyProbeOnlyCandidateCount = summary.probeOnlyOverrideCount();

        if (state.reflectionTransparentCandidateCount > 0) {
            if (state.reflectionRtLaneActive) {
                state.reflectionTransparencyStageGateStatus = "active_rt_or_probe";
                state.reflectionTransparencyFallbackPath = "rt_or_probe";
            } else {
                state.reflectionTransparencyStageGateStatus = "active_probe_fallback";
                state.reflectionTransparencyFallbackPath = "probe_only";
            }
            double probeOnlyRatio = (double) state.reflectionTransparencyProbeOnlyCandidateCount
                    / (double) Math.max(1, state.reflectionTransparentCandidateCount);
            boolean transparencyRisk = probeOnlyRatio > state.reflectionTransparencyProbeOnlyRatioWarnMax;
            if (transparencyRisk) {
                state.reflectionTransparencyHighStreak++;
            } else {
                state.reflectionTransparencyHighStreak = 0;
            }
            if (state.reflectionTransparencyWarnCooldownRemaining > 0) {
                state.reflectionTransparencyWarnCooldownRemaining--;
            }
            boolean transparencyTriggered = false;
            if (transparencyRisk
                    && state.reflectionTransparencyHighStreak >= state.reflectionTransparencyWarnMinFrames
                    && state.reflectionTransparencyWarnCooldownRemaining <= 0) {
                state.reflectionTransparencyWarnCooldownRemaining = state.reflectionTransparencyWarnCooldownFrames;
                transparencyTriggered = true;
            }
            state.reflectionTransparencyBreachedLastFrame = transparencyTriggered;
            warnings.add(new EngineWarning(
                    "REFLECTION_TRANSPARENCY_STAGE_GATE",
                    "Transparency/refraction stage gate (status=" + state.reflectionTransparencyStageGateStatus
                            + ", transparentCandidates=" + state.reflectionTransparentCandidateCount
                            + ", alphaTestedCandidates=" + state.reflectionTransparencyAlphaTestedCandidateCount
                            + ", reactiveCandidates=" + state.reflectionTransparencyReactiveCandidateCount
                            + ", fallbackPath=" + state.reflectionTransparencyFallbackPath + ")"
            ));
            warnings.add(new EngineWarning(
                    "REFLECTION_TRANSPARENCY_POLICY",
                    "Transparency policy (candidateReactiveMin=" + state.reflectionTransparencyCandidateReactiveMin
                            + ", probeOnlyCandidates=" + state.reflectionTransparencyProbeOnlyCandidateCount
                            + ", probeOnlyRatio=" + probeOnlyRatio
                            + ", probeOnlyRatioWarnMax=" + state.reflectionTransparencyProbeOnlyRatioWarnMax
                            + ", highStreak=" + state.reflectionTransparencyHighStreak
                            + ", warnMinFrames=" + state.reflectionTransparencyWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionTransparencyWarnCooldownFrames
                            + ", warnCooldownRemaining=" + state.reflectionTransparencyWarnCooldownRemaining
                            + ", breached=" + state.reflectionTransparencyBreachedLastFrame + ")"
            ));
            if (state.reflectionTransparencyBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_TRANSPARENCY_ENVELOPE_BREACH",
                        "Transparency envelope breached (probeOnlyRatio=" + probeOnlyRatio
                                + ", probeOnlyRatioWarnMax=" + state.reflectionTransparencyProbeOnlyRatioWarnMax + ")"
                ));
            }
        } else {
            state.reflectionTransparencyStageGateStatus = "not_required";
            state.reflectionTransparencyFallbackPath = "none";
            state.reflectionTransparencyHighStreak = 0;
            state.reflectionTransparencyWarnCooldownRemaining = 0;
            state.reflectionTransparencyBreachedLastFrame = false;
            state.reflectionTransparencyAlphaTestedCandidateCount = 0;
            state.reflectionTransparencyReactiveCandidateCount = 0;
            state.reflectionTransparencyProbeOnlyCandidateCount = 0;
        }

        boolean rtTransparencyReady = state.reflectionTransparentCandidateCount <= 0
                || state.reflectionTransparencyStageGateStatus.startsWith("active_");
        boolean rtPromotionDedicatedReady = state.reflectionRtDedicatedHardwarePipelineActive
                || (state.mockContext && state.reflectionRtLaneActive && state.reflectionRtDedicatedPipelineEnabled);
        boolean rtPromotionCandidate = state.reflectionRtLaneActive
                && rtPromotionDedicatedReady
                && !state.reflectionRtRequireActiveUnmetLastFrame
                && !state.reflectionRtRequireMultiBounceUnmetLastFrame
                && !state.reflectionRtRequireDedicatedPipelineUnmetLastFrame
                && !state.reflectionRtPerfBreachedLastFrame
                && !state.reflectionRtHybridBreachedLastFrame
                && !state.reflectionRtDenoiseBreachedLastFrame
                && !state.reflectionRtAsBudgetBreachedLastFrame
                && rtTransparencyReady;
        if (rtPromotionCandidate) {
            state.reflectionRtPromotionReadyHighStreak++;
        } else {
            state.reflectionRtPromotionReadyHighStreak = 0;
        }
        state.reflectionRtPromotionReadyLastFrame = rtPromotionCandidate
                && state.reflectionRtPromotionReadyHighStreak >= state.reflectionRtPromotionReadyMinFrames;
        warnings.add(new EngineWarning(
                "REFLECTION_RT_PROMOTION_STATUS",
                "RT promotion status (candidate=" + rtPromotionCandidate
                        + ", ready=" + state.reflectionRtPromotionReadyLastFrame
                        + ", highStreak=" + state.reflectionRtPromotionReadyHighStreak
                        + ", minFrames=" + state.reflectionRtPromotionReadyMinFrames
                        + ", dedicatedReady=" + rtPromotionDedicatedReady
                        + ", perfBreach=" + state.reflectionRtPerfBreachedLastFrame
                        + ", hybridBreach=" + state.reflectionRtHybridBreachedLastFrame
                        + ", denoiseBreach=" + state.reflectionRtDenoiseBreachedLastFrame
                        + ", asBudgetBreach=" + state.reflectionRtAsBudgetBreachedLastFrame
                        + ", transparencyReady=" + rtTransparencyReady + ")"
        ));
        if (state.reflectionRtPromotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "REFLECTION_RT_PROMOTION_READY",
                    "RT reflection promotion-ready envelope satisfied (vulkan path)"
            ));
        }
    }
}
