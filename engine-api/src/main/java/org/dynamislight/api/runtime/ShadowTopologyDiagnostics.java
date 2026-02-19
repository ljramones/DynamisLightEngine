package org.dynamislight.api.runtime;

/**
 * Backend-agnostic shadow topology coverage diagnostics snapshot.
 */
public record ShadowTopologyDiagnostics(
        boolean available,
        int selectedLocalShadowLights,
        int renderedLocalShadowLights,
        int candidateSpotShadowLights,
        int renderedSpotShadowLights,
        int candidatePointShadowLights,
        int renderedPointShadowCubemaps,
        double localCoverageRatio,
        double spotCoverageRatio,
        double pointCoverageRatio,
        double localCoverageWarnMin,
        double spotCoverageWarnMin,
        double pointCoverageWarnMin,
        int warnMinFrames,
        int warnCooldownFrames,
        int highStreak,
        int warnCooldownRemaining,
        boolean envelopeBreachedLastFrame
) {
    public static ShadowTopologyDiagnostics unavailable() {
        return new ShadowTopologyDiagnostics(
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                0,
                0,
                0,
                false
        );
    }
}
