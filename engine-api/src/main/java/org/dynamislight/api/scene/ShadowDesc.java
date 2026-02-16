package org.dynamislight.api.scene;

/**
 * Optional shadow tuning for a light source.
 */
public record ShadowDesc(
        int mapResolution,
        float depthBias,
        int pcfKernelSize,
        int cascadeCount
) {
}
