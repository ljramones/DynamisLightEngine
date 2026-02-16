package org.dynamislight.api.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.runtime.EngineApiVersions;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.input.KeyCode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class EngineApiContractTest {
    @Test
    void dtoCollectionsAreDefensivelyCopied() {
        Map<String, String> options = new HashMap<>();
        options.put("profile", "dev");
        EngineConfig config = new EngineConfig("opengl", "app", 1280, 720, 1.0f, true, 60,
                QualityTier.MEDIUM, Path.of("."), options);
        options.put("profile", "prod");
        assertEquals("dev", config.backendOptions().get("profile"));

        Set<KeyCode> keys = new java.util.HashSet<>(Set.of(KeyCode.W));
        EngineInput input = new EngineInput(0, 0, 0, 0, false, false, keys, 0.0);
        keys.add(KeyCode.S);
        assertEquals(1, input.keysDown().size());

        List<CameraDesc> cameras = new ArrayList<>();
        cameras.add(new CameraDesc("cam", new Vec3(0, 0, 1), new Vec3(0, 0, 0), 60f, 0.1f, 100f));
        SceneDescriptor scene = new SceneDescriptor(
                "scene",
                cameras,
                "cam",
                List.of(new TransformDesc("x", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "x", "mat", "mesh.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 1f, null, null)),
                List.of(new LightDesc("light", new Vec3(0, 5, 0), new Vec3(1, 1, 1), 1f, 10f, false, null)),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.<SmokeEmitterDesc>of());
        cameras.add(new CameraDesc("cam2", new Vec3(0, 0, 2), new Vec3(0, 0, 0), 60f, 0.1f, 100f));
        assertEquals(1, scene.cameras().size());
    }

    @Test
    void configValidatorRejectsInvalidConfig() {
        var ex = assertThrows(EngineException.class, () -> EngineConfigValidator.validate(new EngineConfig(
                "",
                "app",
                1280,
                720,
                1.0f,
                true,
                60,
                QualityTier.MEDIUM,
                Path.of("."),
                Map.of())));
        assertEquals(EngineErrorCode.INVALID_ARGUMENT, ex.code());
    }

    @Test
    void sceneValidatorRejectsBrokenReferences() {
        SceneDescriptor broken = new SceneDescriptor(
                "scene",
                List.of(new CameraDesc("cam", new Vec3(0, 0, 1), new Vec3(0, 0, 0), 60f, 0.1f, 100f)),
                "cam",
                List.of(new TransformDesc("x", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "missing", "mat", "mesh.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 1f, null, null)),
                List.of(),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.of());

        var ex = assertThrows(EngineException.class, () -> SceneValidator.validate(broken));
        assertEquals(EngineErrorCode.SCENE_VALIDATION_FAILED, ex.code());
    }

    @Test
    void sceneValidatorRejectsOutOfRangeReactiveStrength() {
        SceneDescriptor broken = new SceneDescriptor(
                "scene",
                List.of(new CameraDesc("cam", new Vec3(0, 0, 1), new Vec3(0, 0, 0), 60f, 0.1f, 100f)),
                "cam",
                List.of(new TransformDesc("x", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1))),
                List.of(new MeshDesc("mesh", "x", "mat", "mesh.glb")),
                List.of(new MaterialDesc("mat", new Vec3(1, 1, 1), 0f, 1f, null, null, null, null, 1.5f, false, false)),
                List.of(),
                new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.1f), 0.2f, null),
                new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0, 0, 0, 0, 0, 0),
                List.of());

        var ex = assertThrows(EngineException.class, () -> SceneValidator.validate(broken));
        assertEquals(EngineErrorCode.SCENE_VALIDATION_FAILED, ex.code());
    }

    @Test
    void apiVersionCompatibilityFollowsMajorMinorRules() {
        assertTrue(EngineApiVersions.isRuntimeCompatible(new EngineApiVersion(1, 0, 0), new EngineApiVersion(1, 0, 0)));
        assertTrue(EngineApiVersions.isRuntimeCompatible(new EngineApiVersion(1, 1, 0), new EngineApiVersion(1, 2, 0)));
        assertFalse(EngineApiVersions.isRuntimeCompatible(new EngineApiVersion(1, 2, 0), new EngineApiVersion(1, 1, 9)));
        assertFalse(EngineApiVersions.isRuntimeCompatible(new EngineApiVersion(1, 0, 0), new EngineApiVersion(2, 0, 0)));
    }
}
