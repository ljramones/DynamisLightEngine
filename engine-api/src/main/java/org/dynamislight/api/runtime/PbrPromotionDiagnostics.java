package org.dynamislight.api.runtime;

/**
 * Backend-agnostic PBR promotion diagnostics snapshot.
 */
public record PbrPromotionDiagnostics(
        boolean available,
        String mode,
        int activeAdvancedFeatureCount,
        int advancedWarnMinFeatureCount,
        int expectedCinematicFeatureCount,
        int activeCinematicFeatureCount,
        boolean energyConservationValidationEnabled,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame,
        boolean cinematicEnvelopeBreachedLastFrame,
        boolean cinematicPromotionReadyLastFrame,
        int stableStreak,
        int highStreak,
        int cinematicStableStreak,
        int cinematicHighStreak,
        int warnCooldownRemaining,
        int cinematicWarnCooldownRemaining,
        int warnMinFrames,
        int warnCooldownFrames,
        int promotionReadyMinFrames
) {
    public PbrPromotionDiagnostics {
        mode = mode == null ? "" : mode;
        activeAdvancedFeatureCount = Math.max(0, activeAdvancedFeatureCount);
        advancedWarnMinFeatureCount = Math.max(0, advancedWarnMinFeatureCount);
        expectedCinematicFeatureCount = Math.max(0, expectedCinematicFeatureCount);
        activeCinematicFeatureCount = Math.max(0, activeCinematicFeatureCount);
        stableStreak = Math.max(0, stableStreak);
        highStreak = Math.max(0, highStreak);
        cinematicStableStreak = Math.max(0, cinematicStableStreak);
        cinematicHighStreak = Math.max(0, cinematicHighStreak);
        warnCooldownRemaining = Math.max(0, warnCooldownRemaining);
        cinematicWarnCooldownRemaining = Math.max(0, cinematicWarnCooldownRemaining);
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
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                1,
                0,
                1
        );
    }
}
