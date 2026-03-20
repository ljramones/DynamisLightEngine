package org.dynamisengine.light.api.scene;

/**
 * Ready-made fog configurations.
 */
public final class FogPresets {
    private FogPresets() {}

    /** No fog. */
    public static FogDesc none() {
        return new FogDesc(false, FogMode.NONE, Vec3.zero(), 0, 0, 0, 0, 0, 0);
    }

    /** Light atmospheric haze. */
    public static FogDesc lightHaze() {
        return new FogDesc(true, FogMode.EXPONENTIAL,
                new Vec3(0.7f, 0.75f, 0.8f), 0.01f, 0, 0.6f, 0, 1f, 0);
    }

    /** Dense ground fog with height falloff. */
    public static FogDesc groundFog() {
        return new FogDesc(true, FogMode.HEIGHT_EXPONENTIAL,
                new Vec3(0.6f, 0.65f, 0.7f), 0.04f, 0.5f, 0.9f, 0.1f, 2f, 0.3f);
    }

    /** Thick pea-soup fog. */
    public static FogDesc dense() {
        return new FogDesc(true, FogMode.EXPONENTIAL,
                new Vec3(0.5f, 0.5f, 0.5f), 0.08f, 0, 1.0f, 0, 1f, 0);
    }

    /** Moody night fog with noise. */
    public static FogDesc nightMist() {
        return new FogDesc(true, FogMode.HEIGHT_EXPONENTIAL,
                new Vec3(0.15f, 0.18f, 0.25f), 0.03f, 0.3f, 0.8f, 0.15f, 3f, 0.5f);
    }
}
