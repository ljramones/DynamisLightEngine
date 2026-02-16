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
        float bloomStrength
) {
}
