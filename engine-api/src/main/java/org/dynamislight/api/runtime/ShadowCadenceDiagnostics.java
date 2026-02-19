package org.dynamislight.api.runtime;

/**
 * Backend-agnostic shadow cadence diagnostics snapshot.
 */
public record ShadowCadenceDiagnostics(
        boolean available,
        int selectedLocalShadowLights,
        int deferredShadowLightCount,
        int staleBypassShadowLightCount,
        double deferredRatio,
        double deferredRatioWarnMax,
        int warnMinFrames,
        int warnCooldownFrames,
        int highStreak,
        int warnCooldownRemaining,
        int stableStreak,
        int promotionReadyMinFrames,
        boolean promotionReadyLastFrame,
        boolean envelopeBreachedLastFrame
) {
    public static ShadowCadenceDiagnostics unavailable() {
        return new ShadowCadenceDiagnostics(
                false,
                0,
                0,
                0,
                0.0,
                0.0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false
        );
    }
}
