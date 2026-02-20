package org.dynamislight.api.runtime;

/**
 * Backend-agnostic lighting promotion diagnostics snapshot.
 */
public record LightingPromotionDiagnostics(
        boolean available,
        String mode,
        int baselineStableStreak,
        int baselinePromotionReadyMinFrames,
        boolean baselinePromotionReady,
        int highStreak,
        int stableStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        int promotionReadyMinFrames,
        int physUnitsStableStreak,
        int physUnitsPromotionReadyMinFrames,
        boolean physUnitsPromotionReady,
        int emissiveStableStreak,
        int emissivePromotionReadyMinFrames,
        boolean emissivePromotionReady,
        int advancedStableStreak,
        int advancedPromotionReadyMinFrames,
        boolean advancedPromotionReady,
        boolean phase2PromotionReady,
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
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                0,
                0,
                false,
                0,
                0,
                false,
                false,
                false,
                false
        );
    }
}
