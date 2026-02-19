package org.dynamislight.api.runtime;

/**
 * Backend-agnostic transparent shadow receiver diagnostics snapshot.
 */
public record ShadowTransparentReceiverDiagnostics(
        boolean available,
        boolean requested,
        boolean supported,
        String activePolicy,
        int candidateMaterialCount,
        double candidateRatio,
        double candidateRatioWarnMax,
        int warnMinFrames,
        int warnCooldownFrames,
        int highStreak,
        int warnCooldownRemaining,
        boolean envelopeBreachedLastFrame
) {
    public static ShadowTransparentReceiverDiagnostics unavailable() {
        return new ShadowTransparentReceiverDiagnostics(
                false,
                false,
                false,
                "unavailable",
                0,
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
