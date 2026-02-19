package org.dynamislight.impl.vulkan.reflection;

public record ReflectionProbeQualityDiagnostics(
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
    public static ReflectionProbeQualityDiagnostics zero() {
        return new ReflectionProbeQualityDiagnostics(0, 0, 0.0, 0, 0, 0, 0.0, 0, 0, 0, false, "none");
    }
}
