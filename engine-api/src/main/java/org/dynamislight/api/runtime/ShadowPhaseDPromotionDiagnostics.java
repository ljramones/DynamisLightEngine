package org.dynamislight.api.runtime;

/**
 * Backend-agnostic Phase D shadow promotion diagnostics snapshot.
 */
public record ShadowPhaseDPromotionDiagnostics(
        boolean available,
        boolean cacheStable,
        boolean rtStable,
        boolean hybridStable,
        boolean transparentReceiverStable,
        boolean areaApproxStable,
        boolean distanceFieldStable,
        int promotionReadyMinFrames,
        int stableStreak,
        boolean promotionReadyLastFrame
) {
    public static ShadowPhaseDPromotionDiagnostics unavailable() {
        return new ShadowPhaseDPromotionDiagnostics(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                false
        );
    }
}
