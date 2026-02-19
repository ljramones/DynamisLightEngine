package org.dynamislight.api.runtime;

/**
 * Backend-agnostic shadow hybrid composition diagnostics snapshot.
 */
public record ShadowHybridDiagnostics(
        boolean available,
        boolean hybridModeActive,
        double cascadeShare,
        double contactShare,
        double rtShare,
        double rtShareWarnMin,
        double contactShareWarnMin,
        int warnMinFrames,
        int warnCooldownFrames,
        int highStreak,
        int warnCooldownRemaining,
        boolean envelopeBreachedLastFrame
) {
    public static ShadowHybridDiagnostics unavailable() {
        return new ShadowHybridDiagnostics(
                false,
                false,
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
