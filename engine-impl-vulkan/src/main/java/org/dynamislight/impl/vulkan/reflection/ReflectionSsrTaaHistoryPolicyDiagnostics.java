package org.dynamislight.impl.vulkan.reflection;

public record ReflectionSsrTaaHistoryPolicyDiagnostics(
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
