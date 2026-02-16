package org.dynamislight.bridge.dynamisfx;

import java.util.List;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.validation.SceneValidator;
import org.dynamislight.bridge.dynamisfx.model.FxSceneSnapshot;

public final class SceneMapper {
    public SceneDescriptor mapScene(FxSceneSnapshot snapshot) throws EngineException {
        if (snapshot == null) {
            throw new EngineException(EngineErrorCode.SCENE_VALIDATION_FAILED, "scene snapshot is required", true);
        }
        return mapScene(snapshot.descriptor());
    }

    public SceneDescriptor mapScene(SceneDescriptor source) throws EngineException {
        if (source == null) {
            throw new EngineException(EngineErrorCode.SCENE_VALIDATION_FAILED, "source scene is required", true);
        }

        try {
            SceneDescriptor mapped = new SceneDescriptor(
                    source.sceneName(),
                    mapCameras(source.cameras()),
                    source.activeCameraId(),
                    mapTransforms(source.transforms()),
                    mapMeshes(source.meshes()),
                    mapMaterials(source.materials()),
                    mapLights(source.lights()),
                    mapEnvironment(source.environment()),
                    mapFog(source.fog()),
                    mapSmokeEmitters(source.smokeEmitters())
            );

            SceneValidator.validate(mapped);
            return mapped;
        } catch (EngineException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EngineException(EngineErrorCode.SCENE_VALIDATION_FAILED, "Failed to map scene: " + e.getMessage(), true);
        }
    }

    private static List<CameraDesc> mapCameras(List<CameraDesc> cameras) {
        return cameras.stream()
                .map(c -> new CameraDesc(c.id(), c.position(), c.rotationEulerDeg(), c.fovDegrees(), c.nearPlane(), c.farPlane()))
                .toList();
    }

    private static List<TransformDesc> mapTransforms(List<TransformDesc> transforms) {
        return transforms.stream()
                .map(t -> new TransformDesc(t.id(), t.position(), t.rotationEulerDeg(), t.scale()))
                .toList();
    }

    private static List<MeshDesc> mapMeshes(List<MeshDesc> meshes) {
        return meshes.stream()
                .map(m -> new MeshDesc(m.id(), m.transformId(), m.materialId(), m.meshAssetPath()))
                .toList();
    }

    private static List<MaterialDesc> mapMaterials(List<MaterialDesc> materials) {
        return materials.stream()
                .map(m -> new MaterialDesc(m.id(), m.albedo(), m.metallic(), m.roughness(), m.albedoTexturePath(), m.normalTexturePath()))
                .toList();
    }

    private static List<LightDesc> mapLights(List<LightDesc> lights) {
        return lights.stream()
                .map(l -> new LightDesc(l.id(), l.position(), l.color(), l.intensity(), l.range(), l.castsShadows()))
                .toList();
    }

    private static EnvironmentDesc mapEnvironment(EnvironmentDesc environment) {
        if (environment == null) {
            return null;
        }
        return new EnvironmentDesc(environment.ambientColor(), environment.ambientIntensity(), environment.skyboxAssetPath());
    }

    private static FogDesc mapFog(FogDesc fog) {
        if (fog == null) {
            return null;
        }
        return new FogDesc(
                fog.enabled(),
                fog.mode(),
                fog.color(),
                fog.density(),
                fog.heightFalloff(),
                fog.maxOpacity(),
                fog.noiseAmount(),
                fog.noiseScale(),
                fog.noiseSpeed()
        );
    }

    private static List<SmokeEmitterDesc> mapSmokeEmitters(List<SmokeEmitterDesc> emitters) {
        return emitters.stream()
                .map(e -> new SmokeEmitterDesc(
                        e.id(),
                        e.position(),
                        e.boxExtents(),
                        e.emissionRate(),
                        e.density(),
                        e.albedo(),
                        e.extinction(),
                        e.velocity(),
                        e.turbulence(),
                        e.lifetimeSeconds(),
                        e.enabled()
                ))
                .toList();
    }
}
