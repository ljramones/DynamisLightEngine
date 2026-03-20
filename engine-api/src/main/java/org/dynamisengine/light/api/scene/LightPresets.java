package org.dynamisengine.light.api.scene;

/**
 * Ready-made light configurations for common use cases.
 */
public final class LightPresets {
    private LightPresets() {}

    /** Warm directional sunlight from upper-right. */
    public static LightDesc directionalSun() {
        return new LightDesc("sun", new Vec3(0, 10, 0),
                new Vec3(1.0f, 0.95f, 0.85f), 1.0f, 0, false, null,
                LightType.DIRECTIONAL, new Vec3(0.4f, -0.8f, 0.3f), 0, 0);
    }

    /** Cool moonlight from above. */
    public static LightDesc directionalMoon() {
        return new LightDesc("moon", new Vec3(0, 10, 0),
                new Vec3(0.6f, 0.7f, 0.9f), 0.4f, 0, false, null,
                LightType.DIRECTIONAL, new Vec3(-0.3f, -0.9f, -0.2f), 0, 0);
    }

    /** Point light accent at a given position. */
    public static LightDesc pointAccent(float x, float y, float z) {
        return new LightDesc("point-accent", new Vec3(x, y, z),
                new Vec3(0.9f, 0.7f, 1.0f), 2.0f, 8f, false, null,
                LightType.POINT, new Vec3(0, -1, 0), 0, 0);
    }

    /** Point light with custom color. */
    public static LightDesc point(String id, float x, float y, float z,
                                   float r, float g, float b, float intensity, float range) {
        return new LightDesc(id, new Vec3(x, y, z), new Vec3(r, g, b),
                intensity, range, false, null, LightType.POINT, new Vec3(0, -1, 0), 0, 0);
    }

    /** Studio key + fill two-light setup. Returns the key light. */
    public static LightDesc studioKey() {
        return new LightDesc("studio-key", new Vec3(5, 5, 5),
                new Vec3(1.0f, 0.98f, 0.92f), 1.2f, 0, false, null,
                LightType.DIRECTIONAL, new Vec3(-0.5f, -0.7f, -0.5f), 0, 0);
    }

    /** Studio fill light (softer, complementary direction). */
    public static LightDesc studioFill() {
        return new LightDesc("studio-fill", new Vec3(-3, 3, -3),
                new Vec3(0.7f, 0.8f, 1.0f), 0.5f, 0, false, null,
                LightType.DIRECTIONAL, new Vec3(0.4f, -0.5f, 0.6f), 0, 0);
    }
}
