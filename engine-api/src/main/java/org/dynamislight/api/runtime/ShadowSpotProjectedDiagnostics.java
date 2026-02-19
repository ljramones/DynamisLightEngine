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
        boolean contractBreachedLastFrame
) {
    public static ShadowSpotProjectedDiagnostics unavailable() {
        return new ShadowSpotProjectedDiagnostics(
                false,
                false,
                false,
                0,
                "unavailable",
                false
        );
    }
}
