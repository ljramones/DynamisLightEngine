package org.dynamislight.api.runtime;

/**
 * Backend-agnostic core post-pipeline promotion diagnostics.
 */
public record PostCorePromotionDiagnostics(
        boolean available,
        boolean tonemapEnabled,
        float exposure,
        float gamma,
        boolean bloomEnabled,
        float bloomThreshold,
        float bloomStrength,
        boolean ssaoEnabled,
        float ssaoRadius,
        float ssaoBias,
        float ssaoPower,
        boolean sharpeningEnabled,
        float sharpenStrength,
        boolean volumetricFogEnabled,
        float fogDensity,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame,
        int stableStreak,
        int highStreak,
        int cooldownRemaining,
        int warnMinFrames,
        int warnCooldownFrames,
        int promotionReadyMinFrames
) {
    public static PostCorePromotionDiagnostics unavailable() {
        return new PostCorePromotionDiagnostics(
                false,
                false,
                1.0f,
                2.2f,
                false,
                1.0f,
                0.0f,
                false,
                1.0f,
                0.0f,
                1.0f,
                false,
                0.0f,
                false,
                0.0f,
                false,
                false,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }
}
