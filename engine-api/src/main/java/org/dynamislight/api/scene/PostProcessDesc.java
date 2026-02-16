package org.dynamislight.api.scene;

/**
 * PostProcessDesc API type.
 */
public record PostProcessDesc(
        boolean enabled,
        boolean tonemapEnabled,
        float exposure,
        float gamma,
        boolean bloomEnabled,
        float bloomThreshold,
        float bloomStrength,
        boolean ssaoEnabled,
        float ssaoStrength
) {
    public PostProcessDesc(
            boolean enabled,
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength
    ) {
        this(enabled, tonemapEnabled, exposure, gamma, bloomEnabled, bloomThreshold, bloomStrength, false, 0f);
    }
}
