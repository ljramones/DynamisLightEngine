package org.dynamisengine.light.api.scene;

/**
 * Ready-made environment configurations.
 */
public final class EnvironmentPresets {
    private EnvironmentPresets() {}

    /** Default ambient: soft neutral white. */
    public static EnvironmentDesc defaultAmbient() {
        return new EnvironmentDesc(new Vec3(0.15f, 0.12f, 0.18f), 1.0f, null);
    }

    /** Bright outdoor ambient. */
    public static EnvironmentDesc outdoorBright() {
        return new EnvironmentDesc(new Vec3(0.3f, 0.35f, 0.4f), 1.0f, null);
    }

    /** Dark indoor ambient. */
    public static EnvironmentDesc indoorDim() {
        return new EnvironmentDesc(new Vec3(0.08f, 0.07f, 0.09f), 1.0f, null);
    }

    /** Night scene ambient. */
    public static EnvironmentDesc night() {
        return new EnvironmentDesc(new Vec3(0.03f, 0.04f, 0.08f), 1.0f, null);
    }
}
