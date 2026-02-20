package org.dynamislight.api.runtime;

/**
 * Backend-agnostic VFX/particles promotion diagnostics snapshot.
 */
public record VfxPromotionDiagnostics(
        boolean available,
        int expectedFeatureCount,
        int activeFeatureCount,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        int promotionReadyMinFrames,
        int stableStreak,
        int highStreak,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame
) {
    public VfxPromotionDiagnostics {
        expectedFeatureCount = Math.max(0, expectedFeatureCount);
        activeFeatureCount = Math.max(0, activeFeatureCount);
        warnMinFrames = Math.max(1, warnMinFrames);
        warnCooldownFrames = Math.max(0, warnCooldownFrames);
        warnCooldownRemaining = Math.max(0, warnCooldownRemaining);
        promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
        stableStreak = Math.max(0, stableStreak);
        highStreak = Math.max(0, highStreak);
    }

    public static VfxPromotionDiagnostics unavailable() {
        return new VfxPromotionDiagnostics(
                false,
                0,
                0,
                1,
                0,
                0,
                1,
                0,
                0,
                false,
                false
        );
    }
}
