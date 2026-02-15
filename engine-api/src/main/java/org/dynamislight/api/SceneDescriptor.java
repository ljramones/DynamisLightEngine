package org.dynamislight.api;

import java.util.List;

public record SceneDescriptor(
        String sceneName,
        List<CameraDesc> cameras,
        String activeCameraId,
        List<TransformDesc> transforms,
        List<MeshDesc> meshes,
        List<MaterialDesc> materials,
        List<LightDesc> lights,
        EnvironmentDesc environment,
        FogDesc fog,
        List<SmokeEmitterDesc> smokeEmitters
) {
    public SceneDescriptor {
        cameras = cameras == null ? List.of() : List.copyOf(cameras);
        transforms = transforms == null ? List.of() : List.copyOf(transforms);
        meshes = meshes == null ? List.of() : List.copyOf(meshes);
        materials = materials == null ? List.of() : List.copyOf(materials);
        lights = lights == null ? List.of() : List.copyOf(lights);
        smokeEmitters = smokeEmitters == null ? List.of() : List.copyOf(smokeEmitters);
    }
}
