package org.dynamisengine.light.api.runtime;

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
        int expectedSurfaceGeometryFeatureCount,
        int activeSurfaceGeometryFeatureCount,
        int expectedCharacterSurfaceFeatureCount,
        int activeCharacterSurfaceFeatureCount,
        boolean energyConservationValidationEnabled,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame,
        boolean cinematicEnvelopeBreachedLastFrame,
        boolean cinematicPromotionReadyLastFrame,
        boolean surfaceOpticsEnvelopeBreachedLastFrame,
        boolean surfaceOpticsPromotionReadyLastFrame,
        boolean surfaceGeometryEnvelopeBreachedLastFrame,
        boolean surfaceGeometryPromotionReadyLastFrame,
        boolean characterSurfaceEnvelopeBreachedLastFrame,
        boolean characterSurfacePromotionReadyLastFrame,
        int stableStreak,
        int highStreak,
        int cinematicStableStreak,
        int cinematicHighStreak,
        int surfaceOpticsStableStreak,
        int surfaceOpticsHighStreak,
        int surfaceGeometryStableStreak,
        int surfaceGeometryHighStreak,
        int characterSurfaceStableStreak,
        int characterSurfaceHighStreak,
        int warnCooldownRemaining,
        int cinematicWarnCooldownRemaining,
        int surfaceOpticsWarnCooldownRemaining,
        int surfaceGeometryWarnCooldownRemaining,
        int characterSurfaceWarnCooldownRemaining,
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
        expectedSurfaceGeometryFeatureCount = Math.max(0, expectedSurfaceGeometryFeatureCount);
        activeSurfaceGeometryFeatureCount = Math.max(0, activeSurfaceGeometryFeatureCount);
        expectedCharacterSurfaceFeatureCount = Math.max(0, expectedCharacterSurfaceFeatureCount);
        activeCharacterSurfaceFeatureCount = Math.max(0, activeCharacterSurfaceFeatureCount);
        stableStreak = Math.max(0, stableStreak);
        highStreak = Math.max(0, highStreak);
        cinematicStableStreak = Math.max(0, cinematicStableStreak);
        cinematicHighStreak = Math.max(0, cinematicHighStreak);
        surfaceOpticsStableStreak = Math.max(0, surfaceOpticsStableStreak);
        surfaceOpticsHighStreak = Math.max(0, surfaceOpticsHighStreak);
        surfaceGeometryStableStreak = Math.max(0, surfaceGeometryStableStreak);
        surfaceGeometryHighStreak = Math.max(0, surfaceGeometryHighStreak);
        characterSurfaceStableStreak = Math.max(0, characterSurfaceStableStreak);
        characterSurfaceHighStreak = Math.max(0, characterSurfaceHighStreak);
        warnCooldownRemaining = Math.max(0, warnCooldownRemaining);
        cinematicWarnCooldownRemaining = Math.max(0, cinematicWarnCooldownRemaining);
        surfaceOpticsWarnCooldownRemaining = Math.max(0, surfaceOpticsWarnCooldownRemaining);
        surfaceGeometryWarnCooldownRemaining = Math.max(0, surfaceGeometryWarnCooldownRemaining);
        characterSurfaceWarnCooldownRemaining = Math.max(0, characterSurfaceWarnCooldownRemaining);
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
