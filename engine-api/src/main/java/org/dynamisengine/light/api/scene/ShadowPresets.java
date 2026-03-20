package org.dynamisengine.light.api.scene;

/**
 * Ready-made shadow quality configurations.
 */
public final class ShadowPresets {
    private ShadowPresets() {}

    /** Performance: 1024px, 2 cascades, small kernel. */
    public static ShadowDesc performance() {
        return new ShadowDesc(1024, 0.005f, 1, 2);
    }

    /** Balanced: 2048px, 3 cascades, 3x3 PCF. */
    public static ShadowDesc balanced() {
        return new ShadowDesc(2048, 0.003f, 3, 3);
    }

    /** Quality: 4096px, 4 cascades, 5x5 PCF. */
    public static ShadowDesc quality() {
        return new ShadowDesc(4096, 0.002f, 5, 4);
    }

    /** Ultra: 8192px, 4 cascades, 7x7 PCF. */
    public static ShadowDesc ultra() {
        return new ShadowDesc(8192, 0.001f, 7, 4);
    }
}
