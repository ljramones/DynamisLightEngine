package org.dynamisengine.light.api.scene;

/**
 * Ready-made full scene configurations for common use cases.
 *
 * Each preset returns a {@link SceneBuilder} so the developer can
 * further customize before calling {@code build()}.
 */
public final class ScenePresets {
    private ScenePresets() {}

    /**
     * Simple showcase: orbit camera, directional sun, default ambient.
     * Add your own meshes/materials/transforms.
     */
    public static SceneBuilder showcase() {
        return SceneBuilder.create("showcase")
                .camera(CameraPresets.orbit())
                .light(LightPresets.directionalSun())
                .environment(EnvironmentPresets.defaultAmbient());
    }

    /**
     * Studio setup: editor camera, key + fill lights, brighter ambient.
     */
    public static SceneBuilder studio() {
        return SceneBuilder.create("studio")
                .camera(CameraPresets.editorPerspective())
                .light(LightPresets.studioKey())
                .light(LightPresets.studioFill())
                .environment(EnvironmentPresets.outdoorBright());
    }

    /**
     * Night scene: orbit camera, moonlight, point accent, dark ambient.
     */
    public static SceneBuilder nightScene() {
        return SceneBuilder.create("night")
                .camera(CameraPresets.orbit(10f, 45f, 20f))
                .light(LightPresets.directionalMoon())
                .light(LightPresets.pointAccent(0, 2, 0))
                .environment(EnvironmentPresets.night());
    }

    /**
     * Debug scene: front camera, bright sun, no extras.
     */
    public static SceneBuilder debug() {
        return SceneBuilder.create("debug")
                .camera(CameraPresets.editorPerspective())
                .light(LightPresets.directionalSun())
                .environment(EnvironmentPresets.outdoorBright());
    }
}
