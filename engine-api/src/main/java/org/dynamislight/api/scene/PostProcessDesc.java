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
        float ssaoPower,
        boolean smaaEnabled,
        float smaaStrength,
        boolean taaEnabled,
        float taaBlend,
        boolean taaLumaClipEnabled
) {
    public PostProcessDesc(
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
            float ssaoPower,
            boolean smaaEnabled,
            float smaaStrength,
            boolean taaEnabled,
            float taaBlend
    ) {
        this(
                enabled,
                tonemapEnabled,
                exposure,
                gamma,
                bloomEnabled,
                bloomThreshold,
                bloomStrength,
                ssaoEnabled,
                ssaoStrength,
                ssaoRadius,
                ssaoBias,
                ssaoPower,
                smaaEnabled,
                smaaStrength,
                taaEnabled,
                taaBlend,
                false
        );
    }

    public PostProcessDesc(
            boolean enabled,
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength
    ) {
        this(enabled, tonemapEnabled, exposure, gamma, bloomEnabled, bloomThreshold, bloomStrength, false, 0f, 1.0f, 0.02f, 1.0f, false, 0f, false, 0f, false);
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
        this(enabled, tonemapEnabled, exposure, gamma, bloomEnabled, bloomThreshold, bloomStrength, ssaoEnabled, ssaoStrength, 1.0f, 0.02f, 1.0f, false, 0f, false, 0f, false);
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
            float ssaoStrength,
            float ssaoRadius,
            float ssaoBias,
            float ssaoPower
    ) {
        this(enabled, tonemapEnabled, exposure, gamma, bloomEnabled, bloomThreshold, bloomStrength, ssaoEnabled, ssaoStrength, ssaoRadius, ssaoBias, ssaoPower, false, 0f, false, 0f, false);
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
            float ssaoStrength,
            float ssaoRadius,
            float ssaoBias,
            float ssaoPower,
            boolean smaaEnabled,
            float smaaStrength
    ) {
        this(
                enabled, tonemapEnabled, exposure, gamma, bloomEnabled, bloomThreshold, bloomStrength,
                ssaoEnabled, ssaoStrength, ssaoRadius, ssaoBias, ssaoPower,
                smaaEnabled, smaaStrength, false, 0f, false
        );
    }
}
