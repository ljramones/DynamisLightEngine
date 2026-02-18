package org.dynamislight.api.runtime;

/**
 * Backend-agnostic snapshot of reflection adaptive-trend SLO diagnostics.
 *
 * @param status SLO status ({@code pass}, {@code pending}, {@code fail}, or {@code unavailable})
 * @param reason backend-provided status reason
 * @param failed whether the SLO is currently failed
 * @param windowSamples number of samples in the active trend window
 * @param meanSeverity mean adaptive severity in the active trend window
 * @param highRatio high-severity sample ratio in the active trend window
 * @param sloMeanSeverityMax configured maximum mean severity threshold
 * @param sloHighRatioMax configured maximum high-severity ratio threshold
 * @param sloMinSamples configured minimum samples required for SLO evaluation
 */
public record ReflectionAdaptiveTrendSloDiagnostics(
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
    public static ReflectionAdaptiveTrendSloDiagnostics unavailable() {
        return new ReflectionAdaptiveTrendSloDiagnostics(
                "unavailable",
                "backend_does_not_publish_reflection_slo_diagnostics",
                false,
                0,
                0.0,
                0.0,
                0.0,
                0.0,
                0
        );
    }
}
