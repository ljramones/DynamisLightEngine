package org.dynamislight.api.runtime;

/**
 * Backend-agnostic lighting promotion diagnostics snapshot.
 */
public record LightingPromotionDiagnostics(
        boolean available,
        String mode,
        int highStreak,
        int stableStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        int promotionReadyMinFrames,
        boolean envelopeBreached,
        boolean promotionReady
) {
    public LightingPromotionDiagnostics {
        mode = mode == null ? "" : mode;
    }

    public static LightingPromotionDiagnostics unavailable() {
        return new LightingPromotionDiagnostics(
                false,
                "",
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
