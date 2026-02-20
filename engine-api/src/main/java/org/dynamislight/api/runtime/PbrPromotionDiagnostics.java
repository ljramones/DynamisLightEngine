package org.dynamislight.api.runtime;

/**
 * Backend-agnostic PBR promotion diagnostics snapshot.
 */
public record PbrPromotionDiagnostics(
        boolean available,
        String mode,
        int activeAdvancedFeatureCount,
        int advancedWarnMinFeatureCount,
        boolean energyConservationValidationEnabled,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame,
        int stableStreak,
        int highStreak,
        int warnCooldownRemaining,
        int warnMinFrames,
        int warnCooldownFrames,
        int promotionReadyMinFrames
) {
    public PbrPromotionDiagnostics {
        mode = mode == null ? "" : mode;
        activeAdvancedFeatureCount = Math.max(0, activeAdvancedFeatureCount);
        advancedWarnMinFeatureCount = Math.max(0, advancedWarnMinFeatureCount);
        stableStreak = Math.max(0, stableStreak);
        highStreak = Math.max(0, highStreak);
        warnCooldownRemaining = Math.max(0, warnCooldownRemaining);
        warnMinFrames = Math.max(1, warnMinFrames);
        warnCooldownFrames = Math.max(0, warnCooldownFrames);
        promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
    }

    public static PbrPromotionDiagnostics unavailable() {
        return new PbrPromotionDiagnostics(
                false,
                "",
                0,
                0,
                false,
                false,
                false,
                0,
                0,
                0,
                1,
                0,
                1
        );
    }
}
