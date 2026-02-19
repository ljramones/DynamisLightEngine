package org.dynamislight.api.runtime;

/**
 * Backend-agnostic point-shadow face-budget diagnostics snapshot.
 */
public record ShadowPointBudgetDiagnostics(
        boolean available,
        int configuredMaxShadowFacesPerFrame,
        int renderedPointShadowCubemaps,
        int renderedPointFaces,
        int deferredShadowLightCount,
        double saturationRatio,
        double saturationWarnMin,
        int warnMinFrames,
        int warnCooldownFrames,
        int highStreak,
        int warnCooldownRemaining,
        boolean envelopeBreachedLastFrame
) {
    public static ShadowPointBudgetDiagnostics unavailable() {
        return new ShadowPointBudgetDiagnostics(
                false,
                0,
                0,
                0,
                0,
                0.0,
                0.0,
                0,
                0,
                0,
                0,
                false
        );
    }
}
