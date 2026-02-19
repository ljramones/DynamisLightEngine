package org.dynamislight.impl.vulkan.reflection;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;

final class VulkanReflectionSsrTaaWarningEmitter {
    private VulkanReflectionSsrTaaWarningEmitter() {
    }

    static final class State {
        boolean reflectionSsrTaaAdaptiveEnabled;
        double reflectionSsrTaaInstabilityRejectMin;
        double reflectionSsrTaaInstabilityConfidenceMax;
        long reflectionSsrTaaInstabilityDropEventsMin;
        int reflectionSsrTaaInstabilityWarnMinFrames;
        int reflectionSsrTaaInstabilityWarnCooldownFrames;
        double reflectionSsrTaaRiskEmaAlpha;

        String reflectionSsrTaaHistoryPolicyActive;
        String reflectionSsrTaaReprojectionPolicyActive;
        double reflectionAdaptiveSeverityInstant;
        int reflectionSsrTaaRiskHighStreak;
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

        float reflectionAdaptiveTemporalWeightActive;
        float reflectionAdaptiveSsrStrengthActive;
        float reflectionAdaptiveSsrStepScaleActive;

        double reflectionSsrEnvelopeRejectWarnMax;
        double reflectionSsrEnvelopeConfidenceWarnMin;
        long reflectionSsrEnvelopeDropWarnMin;
        int reflectionSsrEnvelopeWarnMinFrames;
        int reflectionSsrEnvelopeWarnCooldownFrames;
        int reflectionSsrEnvelopeHighStreak;
        int reflectionSsrEnvelopeWarnCooldownRemaining;
        boolean reflectionSsrEnvelopeBreachedLastFrame;

        int reflectionSsrTaaAdaptiveTrendWindowFrames;
        double reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax;
        double reflectionSsrTaaAdaptiveTrendSloHighRatioMax;
        int reflectionSsrTaaAdaptiveTrendSloMinSamples;
        double reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
        int reflectionSsrTaaAdaptiveTrendWarnMinSamples;
        int reflectionSsrTaaAdaptiveTrendWarnHighStreak;
    }

    static void emit(
            List<EngineWarning> warnings,
            State state,
            int reflectionBaseMode,
            double ssrStrength,
            double ssrMaxRoughness,
            double ssrStepScale,
            double reflectionTemporalWeight,
            double taaBlend,
            double taaClipScale,
            boolean taaLumaClipEnabled,
            double taaReject,
            double taaConfidence,
            long taaDrops,
            ReflectionSsrTaaRiskDiagnostics ssrTaaRisk,
            ReflectionAdaptiveTrendDiagnostics adaptiveTrend,
            boolean adaptiveTrendWarningTriggered,
            String trendSloStatus,
            String trendSloReason,
            boolean trendSloFailed
    ) {
        double activeTemporalWeight = Math.max((double) state.reflectionAdaptiveTemporalWeightActive, reflectionTemporalWeight);
        double activeSsrStrength = Math.min((double) state.reflectionAdaptiveSsrStrengthActive, ssrStrength);
        double activeSsrStepScale = Math.max((double) state.reflectionAdaptiveSsrStepScaleActive, ssrStepScale);

        warnings.add(new EngineWarning(
                "REFLECTION_SSR_TAA_DIAGNOSTICS",
                "SSR/TAA diagnostics (mode="
                        + switch (reflectionBaseMode) {
                    case 1 -> "ssr";
                    case 3 -> "hybrid";
                    case 4 -> "rt_hybrid_fallback";
                    default -> "unknown";
                }
                        + ", ssrStrength=" + ssrStrength
                        + ", ssrMaxRoughness=" + ssrMaxRoughness
                        + ", ssrStepScale=" + ssrStepScale
                        + ", reflectionTemporalWeight=" + reflectionTemporalWeight
                        + ", taaBlend=" + taaBlend
                        + ", taaClipScale=" + taaClipScale
                        + ", taaLumaClip=" + taaLumaClipEnabled
                        + ", historyRejectRate=" + taaReject
                        + ", confidenceMean=" + taaConfidence
                        + ", confidenceDropEvents=" + taaDrops
                        + ", instabilityRejectMin=" + state.reflectionSsrTaaInstabilityRejectMin
                        + ", instabilityConfidenceMax=" + state.reflectionSsrTaaInstabilityConfidenceMax
                        + ", instabilityDropEventsMin=" + state.reflectionSsrTaaInstabilityDropEventsMin
                        + ", instabilityWarnMinFrames=" + state.reflectionSsrTaaInstabilityWarnMinFrames
                        + ", instabilityWarnCooldownFrames=" + state.reflectionSsrTaaInstabilityWarnCooldownFrames
                        + ", instabilityRiskEmaAlpha=" + state.reflectionSsrTaaRiskEmaAlpha
                        + ", instabilityRiskHighStreak=" + ssrTaaRisk.highStreak()
                        + ", instabilityRiskCooldownRemaining=" + ssrTaaRisk.warnCooldownRemaining()
                        + ", instabilityRiskEmaReject=" + ssrTaaRisk.emaReject()
                        + ", instabilityRiskEmaConfidence=" + ssrTaaRisk.emaConfidence()
                        + ", instabilityRiskInstant=" + ssrTaaRisk.instantRisk()
                        + ", adaptiveTemporalWeightActive=" + activeTemporalWeight
                        + ", adaptiveSsrStrengthActive=" + activeSsrStrength
                        + ", adaptiveSsrStepScaleActive=" + activeSsrStepScale
                        + ")"
        ));
        warnings.add(new EngineWarning(
                "REFLECTION_SSR_TAA_ADAPTIVE_POLICY_ACTIVE",
                "SSR/TAA adaptive policy (enabled=" + state.reflectionSsrTaaAdaptiveEnabled
                        + ", baseTemporalWeight=" + reflectionTemporalWeight
                        + ", activeTemporalWeight=" + activeTemporalWeight
                        + ", baseSsrStrength=" + ssrStrength
                        + ", activeSsrStrength=" + activeSsrStrength
                        + ", baseSsrStepScale=" + ssrStepScale
                        + ", activeSsrStepScale=" + activeSsrStepScale
                        + ", riskHighStreak=" + ssrTaaRisk.highStreak()
                        + ", riskWarnCooldownRemaining=" + ssrTaaRisk.warnCooldownRemaining()
                        + ")"
        ));
        warnings.add(new EngineWarning(
                "REFLECTION_SSR_TAA_HISTORY_POLICY",
                "SSR/TAA history policy (policy=" + state.reflectionSsrTaaHistoryPolicyActive
                        + ", reprojectionPolicy=" + state.reflectionSsrTaaReprojectionPolicyActive
                        + ", severityInstant=" + state.reflectionAdaptiveSeverityInstant
                        + ", riskHighStreak=" + state.reflectionSsrTaaRiskHighStreak
                        + ", latestRejectRate=" + state.reflectionSsrTaaLatestRejectRate
                        + ", latestConfidenceMean=" + state.reflectionSsrTaaLatestConfidenceMean
                        + ", latestDropEvents=" + state.reflectionSsrTaaLatestDropEvents
                        + ", rejectBias=" + state.reflectionSsrTaaHistoryRejectBiasActive
                        + ", confidenceDecay=" + state.reflectionSsrTaaHistoryConfidenceDecayActive
                        + ", rejectSeverityMin=" + state.reflectionSsrTaaHistoryRejectSeverityMin
                        + ", decaySeverityMin=" + state.reflectionSsrTaaHistoryConfidenceDecaySeverityMin
                        + ", rejectRiskStreakMin=" + state.reflectionSsrTaaHistoryRejectRiskStreakMin
                        + ", disocclusionRejectDropEventsMin=" + state.reflectionSsrTaaDisocclusionRejectDropEventsMin
                        + ", disocclusionRejectConfidenceMax=" + state.reflectionSsrTaaDisocclusionRejectConfidenceMax
                        + ")"
        ));

        boolean envelopeRisk = taaReject >= state.reflectionSsrEnvelopeRejectWarnMax
                || taaConfidence <= state.reflectionSsrEnvelopeConfidenceWarnMin
                || taaDrops >= state.reflectionSsrEnvelopeDropWarnMin;
        if (envelopeRisk) {
            state.reflectionSsrEnvelopeHighStreak++;
        } else {
            state.reflectionSsrEnvelopeHighStreak = 0;
        }
        boolean envelopeTriggered = false;
        if (state.reflectionSsrEnvelopeHighStreak >= state.reflectionSsrEnvelopeWarnMinFrames
                && state.reflectionSsrEnvelopeWarnCooldownRemaining <= 0) {
            envelopeTriggered = true;
            state.reflectionSsrEnvelopeWarnCooldownRemaining = state.reflectionSsrEnvelopeWarnCooldownFrames;
        }
        if (state.reflectionSsrEnvelopeWarnCooldownRemaining > 0) {
            state.reflectionSsrEnvelopeWarnCooldownRemaining--;
        }
        state.reflectionSsrEnvelopeBreachedLastFrame = envelopeTriggered;
        warnings.add(new EngineWarning(
                "REFLECTION_SSR_REPROJECTION_ENVELOPE",
                "SSR reprojection envelope (risk=" + envelopeRisk
                        + ", rejectRate=" + taaReject
                        + ", confidenceMean=" + taaConfidence
                        + ", dropEvents=" + taaDrops
                        + ", rejectWarnMax=" + state.reflectionSsrEnvelopeRejectWarnMax
                        + ", confidenceWarnMin=" + state.reflectionSsrEnvelopeConfidenceWarnMin
                        + ", dropWarnMin=" + state.reflectionSsrEnvelopeDropWarnMin
                        + ", warnMinFrames=" + state.reflectionSsrEnvelopeWarnMinFrames
                        + ", warnCooldownFrames=" + state.reflectionSsrEnvelopeWarnCooldownFrames
                        + ", highStreak=" + state.reflectionSsrEnvelopeHighStreak
                        + ", cooldownRemaining=" + state.reflectionSsrEnvelopeWarnCooldownRemaining
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
        warnings.add(new EngineWarning(
                "REFLECTION_SSR_TAA_ADAPTIVE_TREND_REPORT",
                "SSR/TAA adaptive trend report (windowFrames=" + state.reflectionSsrTaaAdaptiveTrendWindowFrames
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
        warnings.add(new EngineWarning(
                "REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_AUDIT",
                "SSR/TAA adaptive trend SLO audit (status=" + trendSloStatus
                        + ", reason=" + trendSloReason
                        + ", meanSeverity=" + adaptiveTrend.meanSeverity()
                        + ", highRatio=" + adaptiveTrend.highRatio()
                        + ", windowSamples=" + adaptiveTrend.windowSamples()
                        + ", sloMeanSeverityMax=" + state.reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax
                        + ", sloHighRatioMax=" + state.reflectionSsrTaaAdaptiveTrendSloHighRatioMax
                        + ", sloMinSamples=" + state.reflectionSsrTaaAdaptiveTrendSloMinSamples
                        + ")"
        ));
        if (trendSloFailed) {
            warnings.add(new EngineWarning(
                    "REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_FAILED",
                    "SSR/TAA adaptive trend SLO failed (meanSeverity=" + adaptiveTrend.meanSeverity()
                            + ", highRatio=" + adaptiveTrend.highRatio()
                            + ", windowSamples=" + adaptiveTrend.windowSamples()
                            + ", reason=" + trendSloReason
                            + ")"
            ));
        }
        if (adaptiveTrendWarningTriggered) {
            warnings.add(new EngineWarning(
                    "REFLECTION_SSR_TAA_ADAPTIVE_TREND_HIGH_RISK",
                    "SSR/TAA adaptive trend high-risk gate triggered (highRatio=" + adaptiveTrend.highRatio()
                            + ", highRatioWarnMin=" + state.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin
                            + ", windowSamples=" + adaptiveTrend.windowSamples()
                            + ", warnMinSamples=" + state.reflectionSsrTaaAdaptiveTrendWarnMinSamples
                            + ", warnHighStreak=" + state.reflectionSsrTaaAdaptiveTrendWarnHighStreak
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
    }
}
