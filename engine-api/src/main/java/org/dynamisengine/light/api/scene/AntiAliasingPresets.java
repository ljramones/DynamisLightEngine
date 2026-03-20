package org.dynamisengine.light.api.scene;

/**
 * Ready-made anti-aliasing configurations.
 */
public final class AntiAliasingPresets {
    private AntiAliasingPresets() {}

    /** TAA with stable defaults. */
    public static AntiAliasingDesc taaStable() {
        return new AntiAliasingDesc("TAA", true, 0.1f, 1.0f, true, 0.0f, 1.0f);
    }

    /** TAA responsive (lower blend, less ghosting). */
    public static AntiAliasingDesc taaResponsive() {
        return new AntiAliasingDesc("TAA", true, 0.05f, 0.8f, true, 0.1f, 1.0f);
    }

    /** FXAA low (fast, minimal quality). */
    public static AntiAliasingDesc fxaaLow() {
        return new AntiAliasingDesc("FXAA_LOW", true, 0.5f, 1.0f, false, 0.0f, 1.0f);
    }

    /** DLAA (deep learning AA). */
    public static AntiAliasingDesc dlaa() {
        return new AntiAliasingDesc("DLAA", true, 0.1f, 1.0f, true, 0.0f, 1.0f);
    }

    /** Disabled. */
    public static AntiAliasingDesc disabled() {
        return new AntiAliasingDesc("NONE", false, 0, 0, false, 0, 1.0f);
    }
}
