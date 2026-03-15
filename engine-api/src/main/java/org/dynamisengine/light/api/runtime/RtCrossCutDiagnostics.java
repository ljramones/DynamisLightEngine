package org.dynamisengine.light.api.runtime;

/**
 * Backend-agnostic RT cross-cut diagnostics snapshot.
 */
public record RtCrossCutDiagnostics(
        boolean available,
        boolean shadowRtExpected,
        boolean shadowRtActive,
        boolean reflectionRtExpected,
        boolean reflectionRtActive,
        boolean reflectionRtFallbackActive,
        boolean reflectionRtPromotionReady,
        boolean giRtExpected,
        boolean giRtActive,
        boolean giRtFallbackActive,
        boolean giRtPromotionReady,
        boolean allExpectedRtDomainsActive,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame,
        int stableStreak,
        int highStreak,
        int warnCooldownRemaining,
        int warnMinFrames,
        int warnCooldownFrames,
        int promotionReadyMinFrames
) {
    public RtCrossCutDiagnostics {
        stableStreak = Math.max(0, stableStreak);
        highStreak = Math.max(0, highStreak);
        warnCooldownRemaining = Math.max(0, warnCooldownRemaining);
        warnMinFrames = Math.max(1, warnMinFrames);
        warnCooldownFrames = Math.max(0, warnCooldownFrames);
        promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
    }

    public static RtCrossCutDiagnostics unavailable() {
        return new RtCrossCutDiagnostics(
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
