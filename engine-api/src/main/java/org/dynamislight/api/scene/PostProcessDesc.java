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
        float ssaoStrength,
        float ssaoRadius,
        float ssaoBias,
        float ssaoPower
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
        this(enabled, tonemapEnabled, exposure, gamma, bloomEnabled, bloomThreshold, bloomStrength, false, 0f, 1.0f, 0.02f, 1.0f);
    }

    public PostProcessDesc(
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
        this(enabled, tonemapEnabled, exposure, gamma, bloomEnabled, bloomThreshold, bloomStrength, ssaoEnabled, ssaoStrength, 1.0f, 0.02f, 1.0f);
    }
}
