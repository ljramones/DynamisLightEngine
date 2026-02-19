package org.dynamislight.impl.vulkan.reflection;

import java.util.Deque;

final class VulkanReflectionAdaptiveMath {
    private VulkanReflectionAdaptiveMath() {
    }

    static double computeAdaptiveSeverity(
            double emaReject,
            double emaConfidence,
            double instabilityRejectMin,
            double instabilityConfidenceMax,
            int highRiskStreak,
            int instabilityWarnMinFrames
    ) {
        if (emaReject < 0.0 || emaConfidence < 0.0) {
            return 0.0;
        }
        double rejectRange = Math.max(1e-6, 1.0 - instabilityRejectMin);
        double rejectSeverity = clamp01((emaReject - instabilityRejectMin) / rejectRange);
        double confidenceRange = Math.max(1e-6, instabilityConfidenceMax);
        double confidenceSeverity = clamp01((instabilityConfidenceMax - emaConfidence) / confidenceRange);
        double streakDenominator = Math.max(1, instabilityWarnMinFrames);
        double streakSeverity = clamp01(highRiskStreak / streakDenominator);
        return Math.max(streakSeverity, Math.max(rejectSeverity, confidenceSeverity));
    }

    static ReflectionAdaptiveTrendDiagnostics snapshotTrendDiagnostics(
            Deque<ReflectionAdaptiveWindowSample> trendSamples,
            double highRatioWarnMin,
            int warnMinFrames,
            int warnCooldownFrames,
            int warnMinSamples,
            int warnHighStreak,
            int warnCooldownRemaining,
            boolean warningTriggered
    ) {
        int windowSamples = trendSamples.size();
        if (windowSamples <= 0) {
            return new ReflectionAdaptiveTrendDiagnostics(
                    0,
                    0.0,
                    0.0,
                    0,
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    highRatioWarnMin,
                    warnMinFrames,
                    warnCooldownFrames,
                    warnMinSamples,
                    warnHighStreak,
                    warnCooldownRemaining,
                    warningTriggered
            );
        }
        double severityAccum = 0.0;
        double severityPeak = 0.0;
        double temporalDeltaAccum = 0.0;
        double ssrStrengthDeltaAccum = 0.0;
        double ssrStepScaleDeltaAccum = 0.0;
        int lowCount = 0;
        int mediumCount = 0;
        int highCount = 0;
        for (ReflectionAdaptiveWindowSample sample : trendSamples) {
            double severity = sample.severity();
            severityAccum += severity;
            severityPeak = Math.max(severityPeak, severity);
            temporalDeltaAccum += sample.temporalDelta();
            ssrStrengthDeltaAccum += sample.ssrStrengthDelta();
            ssrStepScaleDeltaAccum += sample.ssrStepScaleDelta();
            if (severity < 0.25) {
                lowCount++;
            } else if (severity < 0.60) {
                mediumCount++;
            } else {
                highCount++;
            }
        }
        double denom = (double) windowSamples;
        return new ReflectionAdaptiveTrendDiagnostics(
                windowSamples,
                severityAccum / denom,
                severityPeak,
                lowCount,
                mediumCount,
                highCount,
                lowCount / denom,
                mediumCount / denom,
                highCount / denom,
                temporalDeltaAccum / denom,
                ssrStrengthDeltaAccum / denom,
                ssrStepScaleDeltaAccum / denom,
                highRatioWarnMin,
                warnMinFrames,
                warnCooldownFrames,
                warnMinSamples,
                warnHighStreak,
                warnCooldownRemaining,
                warningTriggered
        );
    }

    static ReflectionSsrTaaHistoryPolicy computeHistoryPolicy(
            boolean reflectionsEnabled,
            boolean taaEnabled,
            boolean ssrPathActive,
            double severity,
            double taaReject,
            double taaConfidence,
            long taaDrops,
            long disocclusionRejectDropEventsMin,
            double disocclusionRejectConfidenceMax,
            double historyRejectSeverityMin,
            int historyRejectRiskStreakMin,
            int riskHighStreak,
            double historyConfidenceDecaySeverityMin
    ) {
        if (!(reflectionsEnabled && taaEnabled && ssrPathActive)) {
            return new ReflectionSsrTaaHistoryPolicy(
                    "inactive",
                    "surface_motion_vectors",
                    0.0,
                    0.0
            );
        }

        double clampedSeverity = clamp01(severity);
        boolean disocclusionReject = taaDrops >= disocclusionRejectDropEventsMin
                && taaConfidence <= disocclusionRejectConfidenceMax;
        boolean rejectMode = clampedSeverity >= historyRejectSeverityMin
                || riskHighStreak >= historyRejectRiskStreakMin;
        boolean decayMode = clampedSeverity >= historyConfidenceDecaySeverityMin;

        if (disocclusionReject) {
            return new ReflectionSsrTaaHistoryPolicy(
                    "reflection_disocclusion_reject",
                    "reflection_space_reject",
                    clamp01(0.50 + clampedSeverity * 0.50),
                    clamp01(0.60 + clampedSeverity * 0.40)
            );
        }
        if (rejectMode) {
            return new ReflectionSsrTaaHistoryPolicy(
                    "reflection_region_reject",
                    "reflection_space_reject",
                    clamp01(0.35 + clampedSeverity * 0.65),
                    clamp01(0.45 + clampedSeverity * 0.55)
            );
        }
        if (decayMode) {
            return new ReflectionSsrTaaHistoryPolicy(
                    "reflection_region_decay",
                    taaReject >= 0.25 ? "reflection_space_bias" : "surface_motion_vectors",
                    clamp01(0.15 + clampedSeverity * 0.45),
                    clamp01(0.25 + clampedSeverity * 0.50)
            );
        }
        return new ReflectionSsrTaaHistoryPolicy(
                "surface_motion_vectors",
                "surface_motion_vectors",
                clamp01(clampedSeverity * 0.20),
                clamp01(clampedSeverity * 0.30)
        );
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    record ReflectionSsrTaaHistoryPolicy(
            String historyPolicy,
            String reprojectionPolicy,
            double historyRejectBias,
            double historyConfidenceDecay
    ) {
    }
}
