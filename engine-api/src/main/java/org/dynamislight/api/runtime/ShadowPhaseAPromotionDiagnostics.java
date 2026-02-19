package org.dynamislight.api.runtime;

/**
 * Backend-agnostic Phase A shadow promotion diagnostics snapshot.
 */
public record ShadowPhaseAPromotionDiagnostics(
        boolean available,
        boolean cadencePromotionReady,
        boolean pointFaceBudgetPromotionReady,
        boolean spotProjectedPromotionReady,
        int promotionReadyMinFrames,
        int stableStreak,
        boolean promotionReadyLastFrame
) {
    public static ShadowPhaseAPromotionDiagnostics unavailable() {
        return new ShadowPhaseAPromotionDiagnostics(
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
