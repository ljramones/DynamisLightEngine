package org.dynamisengine.light.api.runtime;

import java.util.List;

/**
 * Backend-agnostic cinematic post promotion diagnostics.
 */
public record PostCinematicPromotionDiagnostics(
        boolean available,
        int expectedCapabilityCount,
        int activeCapabilityCount,
        double activeRatio,
        double warnMinActiveRatio,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame,
        int stableStreak,
        int highStreak,
        int cooldownRemaining,
        int warnMinFrames,
        int warnCooldownFrames,
        int promotionReadyMinFrames,
        List<String> expectedCapabilities,
        List<String> activeCapabilities
) {
    public PostCinematicPromotionDiagnostics {
        expectedCapabilities = expectedCapabilities == null ? List.of() : List.copyOf(expectedCapabilities);
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
    }

    public static PostCinematicPromotionDiagnostics unavailable() {
        return new PostCinematicPromotionDiagnostics(
                false,
                0,
                0,
                1.0,
                1.0,
                false,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of()
        );
    }
}
