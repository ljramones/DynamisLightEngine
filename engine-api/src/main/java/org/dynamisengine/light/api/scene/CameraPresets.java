package org.dynamisengine.light.api.scene;

/**
 * Ready-made camera configurations for common use cases.
 */
public final class CameraPresets {
    private CameraPresets() {}

    /** Orbit camera at given distance, yaw, and pitch from origin. */
    public static CameraDesc orbit(float distance, float yawDeg, float pitchDeg) {
        double yr = Math.toRadians(yawDeg), pr = Math.toRadians(pitchDeg);
        float x = distance * (float)(Math.cos(pr) * Math.cos(yr));
        float y = distance * (float)(Math.sin(pr));
        float z = distance * (float)(Math.cos(pr) * Math.sin(yr));
        return new CameraDesc("orbit", new Vec3(x, y, z),
                new Vec3(-pitchDeg, -yawDeg, 0), 60f, 0.1f, 100f);
    }

    /** Default orbit: 8 units, 30 deg yaw, 25 deg pitch. */
    public static CameraDesc orbit() {
        return orbit(8f, 30f, 25f);
    }

    /** Front-facing editor perspective. */
    public static CameraDesc editorPerspective() {
        return new CameraDesc("editor", new Vec3(0, 3, 8),
                new Vec3(-15, 0, 0), 60f, 0.1f, 200f);
    }

    /** Close-up showcase camera. */
    public static CameraDesc showcase() {
        return new CameraDesc("showcase", new Vec3(3, 2, 5),
                new Vec3(-15, -25, 0), 45f, 0.1f, 50f);
    }

    /** Top-down orthographic-like view. */
    public static CameraDesc topDown(float height) {
        return new CameraDesc("topdown", new Vec3(0, height, 0.01f),
                new Vec3(-89, 0, 0), 60f, 0.1f, height * 2);
    }
}
