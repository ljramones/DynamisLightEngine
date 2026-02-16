package org.dynamislight.bridge.dynamisfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.bridge.dynamisfx.model.FxSceneSnapshot;
import org.junit.jupiter.api.Test;

class SceneMapperTest {
    @Test
    void mapsSceneCollectionsAndCoreFields() throws Exception {
        SceneMapper mapper = new SceneMapper();
        SceneDescriptor source = validScene();

        SceneDescriptor mapped = mapper.mapScene(source);

        assertEquals(source.sceneName(), mapped.sceneName());
        assertEquals(source.activeCameraId(), mapped.activeCameraId());
        assertEquals(1, mapped.cameras().size());
        assertEquals(1, mapped.transforms().size());
        assertEquals(1, mapped.meshes().size());
        assertEquals(1, mapped.materials().size());
        assertEquals(1, mapped.lights().size());
        assertNotSame(source.cameras(), mapped.cameras());
        assertNotSame(source.transforms(), mapped.transforms());
    }

    @Test
    void mapsInvalidSourceToSceneValidationFailed() {
        SceneMapper mapper = new SceneMapper();
        SceneDescriptor invalid = new SceneDescriptor(
                "invalid",
                List.of(new CameraDesc("cam", new Vec3(0, 0, 5), new Vec3(0, 0, 0), 60f, 0.1f, 100f)),
                "cam",
                List.of(new TransformDesc("x", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "missing-transform", "mat", "mesh.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 1f, null, null)),
                List.of(),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.of()
        );

        EngineException ex = assertThrows(EngineException.class, () -> mapper.mapScene(invalid));

        assertEquals(EngineErrorCode.SCENE_VALIDATION_FAILED, ex.code());
    }

    @Test
    void mapsFxSceneSnapshot() throws Exception {
        SceneMapper mapper = new SceneMapper();
        FxSceneSnapshot snapshot = new FxSceneSnapshot(validScene());

        SceneDescriptor mapped = mapper.mapScene(snapshot);

        assertEquals("sample-scene", mapped.sceneName());
    }

    private static SceneDescriptor validScene() {
        CameraDesc camera = new CameraDesc("cam", new Vec3(0, 2, 5), new Vec3(0, 0, 0), 60f, 0.1f, 1000f);
        TransformDesc transform = new TransformDesc("root", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh-1", "root", "mat-1", "meshes/triangle.glb");
        MaterialDesc material = new MaterialDesc("mat-1", new Vec3(1, 1, 1), 0.1f, 0.7f, null, null);
        LightDesc light = new LightDesc("sun", new Vec3(0, 10, 0), new Vec3(1, 1, 1), 1.0f, 100f, false);
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.12f), 0.25f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "sample-scene",
                List.of(camera),
                camera.id(),
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(light),
                environment,
                fog,
                List.<SmokeEmitterDesc>of()
        );
    }
}
