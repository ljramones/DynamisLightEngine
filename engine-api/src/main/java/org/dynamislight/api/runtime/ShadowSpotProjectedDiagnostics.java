package org.dynamislight.api.runtime;

/**
 * Backend-agnostic spot-projected shadow diagnostics snapshot.
 */
public record ShadowSpotProjectedDiagnostics(
        boolean available,
        boolean requested,
        boolean active,
        int renderedSpotShadowLights,
        String contractStatus,
        boolean contractBreachedLastFrame,
        int stableStreak,
        int promotionReadyMinFrames,
        boolean promotionReadyLastFrame
) {
    public static ShadowSpotProjectedDiagnostics unavailable() {
        return new ShadowSpotProjectedDiagnostics(
                false,
                false,
                false,
                0,
                "unavailable",
                false,
                0,
                0,
                false
        );
    }
}
