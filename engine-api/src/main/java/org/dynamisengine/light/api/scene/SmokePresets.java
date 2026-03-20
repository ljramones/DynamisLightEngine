package org.dynamisengine.light.api.scene;

/**
 * Ready-made smoke emitter configurations.
 */
public final class SmokePresets {
    private SmokePresets() {}

    /** Gentle campfire smoke. */
    public static SmokeEmitterDesc campfire(String id, float x, float y, float z) {
        return new SmokeEmitterDesc(id, new Vec3(x, y, z), new Vec3(0.3f, 0.5f, 0.3f),
                8f, 0.4f, new Vec3(0.3f, 0.3f, 0.3f), 0.8f,
                new Vec3(0, 1.5f, 0), 0.3f, 4f, true);
    }

    /** Industrial stack exhaust. */
    public static SmokeEmitterDesc exhaust(String id, float x, float y, float z) {
        return new SmokeEmitterDesc(id, new Vec3(x, y, z), new Vec3(0.5f, 1f, 0.5f),
                20f, 0.6f, new Vec3(0.4f, 0.4f, 0.45f), 1.2f,
                new Vec3(0, 3f, 0), 0.5f, 6f, true);
    }

    /** Explosion burst. */
    public static SmokeEmitterDesc explosion(String id, float x, float y, float z) {
        return new SmokeEmitterDesc(id, new Vec3(x, y, z), new Vec3(2f, 2f, 2f),
                50f, 0.8f, new Vec3(0.2f, 0.15f, 0.1f), 2f,
                new Vec3(0, 5f, 0), 1.5f, 3f, true);
    }

    /** Gentle steam vent. */
    public static SmokeEmitterDesc steam(String id, float x, float y, float z) {
        return new SmokeEmitterDesc(id, new Vec3(x, y, z), new Vec3(0.2f, 0.3f, 0.2f),
                12f, 0.2f, new Vec3(0.8f, 0.85f, 0.9f), 0.4f,
                new Vec3(0, 2f, 0), 0.2f, 2.5f, true);
    }
}
