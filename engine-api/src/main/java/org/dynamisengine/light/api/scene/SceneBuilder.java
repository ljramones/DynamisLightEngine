package org.dynamisengine.light.api.scene;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link SceneDescriptor}.
 *
 * Dramatically reduces scene construction boilerplate by providing
 * sensible defaults and a composable API. Developers can mix
 * presets with manual configuration.
 *
 * <pre>{@code
 * SceneDescriptor scene = SceneBuilder.create("my-scene")
 *     .camera(CameraPresets.orbit(5f, 30f, 25f))
 *     .light(LightPresets.directionalSun())
 *     .light(LightPresets.pointAccent(0, 3, 0))
 *     .environment(EnvironmentPresets.defaultAmbient())
 *     .build();
 * }</pre>
 */
public final class SceneBuilder {

    private final String sceneName;
    private final List<CameraDesc> cameras = new ArrayList<>();
    private String activeCameraId;
    private final List<TransformDesc> transforms = new ArrayList<>();
    private final List<MeshDesc> meshes = new ArrayList<>();
    private final List<MaterialDesc> materials = new ArrayList<>();
    private final List<LightDesc> lights = new ArrayList<>();
    private EnvironmentDesc environment;
    private FogDesc fog;
    private final List<SmokeEmitterDesc> smokeEmitters = new ArrayList<>();
    private PostProcessDesc postProcess;

    private SceneBuilder(String sceneName) {
        this.sceneName = sceneName;
    }

    public static SceneBuilder create(String sceneName) {
        return new SceneBuilder(sceneName);
    }

    public SceneBuilder camera(CameraDesc camera) {
        cameras.add(camera);
        if (activeCameraId == null) activeCameraId = camera.id();
        return this;
    }

    public SceneBuilder activeCamera(String cameraId) {
        this.activeCameraId = cameraId;
        return this;
    }

    public SceneBuilder transform(TransformDesc transform) {
        transforms.add(transform);
        return this;
    }

    public SceneBuilder mesh(MeshDesc mesh) {
        meshes.add(mesh);
        return this;
    }

    public SceneBuilder material(MaterialDesc material) {
        materials.add(material);
        return this;
    }

    public SceneBuilder light(LightDesc light) {
        lights.add(light);
        return this;
    }

    public SceneBuilder environment(EnvironmentDesc env) {
        this.environment = env;
        return this;
    }

    public SceneBuilder fog(FogDesc fog) {
        this.fog = fog;
        return this;
    }

    public SceneBuilder smokeEmitter(SmokeEmitterDesc emitter) {
        smokeEmitters.add(emitter);
        return this;
    }

    public SceneBuilder postProcess(PostProcessDesc pp) {
        this.postProcess = pp;
        return this;
    }

    public SceneDescriptor build() {
        return new SceneDescriptor(
                sceneName, cameras, activeCameraId,
                transforms, meshes, materials, lights,
                environment, fog, smokeEmitters, postProcess);
    }
}
