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
        int expectedSurfaceOpticsFeatureCount,
        int activeSurfaceOpticsFeatureCount,
        boolean energyConservationValidationEnabled,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame,
        boolean cinematicEnvelopeBreachedLastFrame,
        boolean cinematicPromotionReadyLastFrame,
        boolean surfaceOpticsEnvelopeBreachedLastFrame,
        boolean surfaceOpticsPromotionReadyLastFrame,
        int stableStreak,
        int highStreak,
        int cinematicStableStreak,
        int cinematicHighStreak,
        int surfaceOpticsStableStreak,
        int surfaceOpticsHighStreak,
        int warnCooldownRemaining,
        int cinematicWarnCooldownRemaining,
        int surfaceOpticsWarnCooldownRemaining,
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
        expectedSurfaceOpticsFeatureCount = Math.max(0, expectedSurfaceOpticsFeatureCount);
        activeSurfaceOpticsFeatureCount = Math.max(0, activeSurfaceOpticsFeatureCount);
        stableStreak = Math.max(0, stableStreak);
        highStreak = Math.max(0, highStreak);
        cinematicStableStreak = Math.max(0, cinematicStableStreak);
        cinematicHighStreak = Math.max(0, cinematicHighStreak);
        surfaceOpticsStableStreak = Math.max(0, surfaceOpticsStableStreak);
        surfaceOpticsHighStreak = Math.max(0, surfaceOpticsHighStreak);
        warnCooldownRemaining = Math.max(0, warnCooldownRemaining);
        cinematicWarnCooldownRemaining = Math.max(0, cinematicWarnCooldownRemaining);
        surfaceOpticsWarnCooldownRemaining = Math.max(0, surfaceOpticsWarnCooldownRemaining);
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
                0,
                0,
                false,
                false,
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
                0,
                0,
                0,
                1,
                0,
                1
        );
    }
}
