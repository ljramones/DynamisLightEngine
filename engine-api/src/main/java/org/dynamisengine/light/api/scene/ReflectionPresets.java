package org.dynamisengine.light.api.scene;

/**
 * Ready-made reflection configurations.
 */
public final class ReflectionPresets {
    private ReflectionPresets() {}

    /** IBL only (cheapest, no screen-space). */
    public static ReflectionDesc iblOnly() {
        return new ReflectionDesc(true, "IBL_ONLY");
    }

    /** SSR with moderate quality. */
    public static ReflectionDesc ssr() {
        return new ReflectionDesc(true, "SSR", 0.6f, 0.78f, 1.0f, 0.8f, 0f);
    }

    /** SSR + planar reflections (hybrid). */
    public static ReflectionDesc hybrid() {
        return new ReflectionDesc(true, "HYBRID", 0.6f, 0.78f, 1.0f, 0.8f, 0.35f);
    }

    /** Planar reflections only (floors, water). */
    public static ReflectionDesc planar() {
        return new ReflectionDesc(true, "PLANAR", 0f, 0f, 0f, 0f, 1.0f);
    }

    /** Disabled. */
    public static ReflectionDesc disabled() {
        return new ReflectionDesc(false, "IBL_ONLY");
    }
}
