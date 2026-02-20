package org.dynamislight.api.runtime;

/**
 * Backend-agnostic lighting budget diagnostics snapshot.
 */
public record LightingBudgetDiagnostics(
        boolean available,
        int localLightCount,
        int localLightBudget,
        double loadRatio,
        double warnRatioThreshold,
        boolean envelopeBreached
) {
    public static LightingBudgetDiagnostics unavailable() {
        return new LightingBudgetDiagnostics(false, 0, 0, 0.0, 1.0, false);
    }
}
