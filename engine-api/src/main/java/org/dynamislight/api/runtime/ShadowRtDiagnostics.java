package org.dynamislight.api.runtime;

/**
 * Backend-agnostic shadow RT diagnostics snapshot.
 */
public record ShadowRtDiagnostics(
        boolean available,
        String mode,
        boolean active,
        double denoiseStrength,
        double denoiseWarnMin,
        int sampleCount,
        int sampleWarnMin,
        double perfGpuMsEstimate,
        double perfGpuMsWarnMax,
        int warnMinFrames,
        int warnCooldownFrames,
        int highStreak,
        int warnCooldownRemaining,
        boolean envelopeBreachedLastFrame
) {
    public static ShadowRtDiagnostics unavailable() {
        return new ShadowRtDiagnostics(
                false,
                "unavailable",
                false,
                0.0,
                0.0,
                0,
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
