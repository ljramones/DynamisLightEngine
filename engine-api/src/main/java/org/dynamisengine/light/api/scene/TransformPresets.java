package org.dynamisengine.light.api.scene;

/**
 * Convenience factories for {@link TransformDesc}.
 */
public final class TransformPresets {
    private TransformPresets() {}

    public static TransformDesc identity(String id) {
        return new TransformDesc(id, Vec3.zero(), Vec3.zero(), Vec3.one());
    }

    public static TransformDesc translated(String id, float x, float y, float z) {
        return new TransformDesc(id, new Vec3(x, y, z), Vec3.zero(), Vec3.one());
    }

    public static TransformDesc translated(String id, Vec3 position) {
        return new TransformDesc(id, position, Vec3.zero(), Vec3.one());
    }

    public static TransformDesc scaled(String id, float uniform) {
        return new TransformDesc(id, Vec3.zero(), Vec3.zero(), Vec3.all(uniform));
    }

    public static TransformDesc rotated(String id, float yawDeg, float pitchDeg, float rollDeg) {
        return new TransformDesc(id, Vec3.zero(), new Vec3(pitchDeg, yawDeg, rollDeg), Vec3.one());
    }
}
