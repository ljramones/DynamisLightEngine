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
        boolean probeGridActive,
        boolean rtDetailActive,
        int stableStreak,
        int promotionReadyMinFrames,
        boolean promotionReady
) {
    public GiPromotionDiagnostics {
        giMode = giMode == null ? "" : giMode;
        stableStreak = Math.max(0, stableStreak);
        promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
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
                false,
                0,
                1,
                false
        );
    }
}
