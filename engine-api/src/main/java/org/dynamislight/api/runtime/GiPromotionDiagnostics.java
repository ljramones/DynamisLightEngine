package org.dynamislight.api.runtime;

/**
 * Backend-agnostic GI promotion diagnostics snapshot.
 */
public record GiPromotionDiagnostics(
        boolean available,
        String giMode,
        boolean giEnabled,
        boolean rtAvailable,
        boolean rtFallbackActive,
        boolean ssgiActive,
        boolean ssgiExpected,
        double ssgiActiveRatio,
        double ssgiWarnMinActiveRatio,
        int ssgiWarnMinFrames,
        int ssgiWarnCooldownFrames,
        int ssgiWarnCooldownRemaining,
        boolean ssgiEnvelopeBreachedLastFrame,
        boolean probeGridActive,
        boolean probeGridExpected,
        double probeGridActiveRatio,
        double probeGridWarnMinActiveRatio,
        int probeGridWarnMinFrames,
        int probeGridWarnCooldownFrames,
        int probeGridWarnCooldownRemaining,
        boolean probeGridEnvelopeBreachedLastFrame,
        boolean rtDetailActive,
        int stableStreak,
        int promotionReadyMinFrames,
        boolean promotionReady,
        int ssgiStableStreak,
        int ssgiPromotionReadyMinFrames,
        boolean ssgiPromotionReady,
        int probeGridStableStreak,
        int probeGridPromotionReadyMinFrames,
        boolean probeGridPromotionReady
) {
    public GiPromotionDiagnostics {
        giMode = giMode == null ? "" : giMode;
        ssgiActiveRatio = clamp01(ssgiActiveRatio);
        ssgiWarnMinActiveRatio = clamp01(ssgiWarnMinActiveRatio);
        ssgiWarnMinFrames = Math.max(1, ssgiWarnMinFrames);
        ssgiWarnCooldownFrames = Math.max(0, ssgiWarnCooldownFrames);
        ssgiWarnCooldownRemaining = Math.max(0, ssgiWarnCooldownRemaining);
        probeGridActiveRatio = clamp01(probeGridActiveRatio);
        probeGridWarnMinActiveRatio = clamp01(probeGridWarnMinActiveRatio);
        probeGridWarnMinFrames = Math.max(1, probeGridWarnMinFrames);
        probeGridWarnCooldownFrames = Math.max(0, probeGridWarnCooldownFrames);
        probeGridWarnCooldownRemaining = Math.max(0, probeGridWarnCooldownRemaining);
        stableStreak = Math.max(0, stableStreak);
        promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
        ssgiStableStreak = Math.max(0, ssgiStableStreak);
        ssgiPromotionReadyMinFrames = Math.max(1, ssgiPromotionReadyMinFrames);
        probeGridStableStreak = Math.max(0, probeGridStableStreak);
        probeGridPromotionReadyMinFrames = Math.max(1, probeGridPromotionReadyMinFrames);
    }

    public static GiPromotionDiagnostics unavailable() {
        return new GiPromotionDiagnostics(
                false,
                "",
                false,
                false,
                false,
                false,
                false,
                0.0,
                1.0,
                1,
                0,
                0,
                false,
                false,
                false,
                0.0,
                1.0,
                1,
                0,
                0,
                false,
                false,
                0,
                1,
                false,
                0,
                1,
                false,
                0,
                1,
                false
        );
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}
