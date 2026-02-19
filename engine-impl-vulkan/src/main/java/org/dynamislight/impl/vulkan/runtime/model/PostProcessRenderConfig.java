package org.dynamislight.impl.vulkan.runtime.model;

public record PostProcessRenderConfig(
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
        float taaClipScale,
        boolean taaLumaClipEnabled,
        float taaSharpenStrength,
        float taaRenderScale,
        boolean reflectionsEnabled,
        int reflectionsMode,
        float reflectionsSsrStrength,
        float reflectionsSsrMaxRoughness,
        float reflectionsSsrStepScale,
        float reflectionsTemporalWeight,
        float reflectionsPlanarStrength,
        float reflectionsPlanarPlaneHeight
) {
}
