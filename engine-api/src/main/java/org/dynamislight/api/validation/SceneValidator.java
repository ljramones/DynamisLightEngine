package org.dynamislight.api.validation;

import java.util.HashSet;
import java.util.Set;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.TransformDesc;

/**
 * Validator for runtime scene descriptors.
 */
public final class SceneValidator {
    private SceneValidator() {
    }

    public static void validate(SceneDescriptor scene) throws EngineException {
        if (scene == null) {
            throw invalid("scene is required");
        }
        if (isBlank(scene.sceneName())) {
            throw invalid("sceneName is required");
        }

        Set<String> cameraIds = requireUniqueIds(scene.cameras(), CameraDesc::id, "camera");
        Set<String> transformIds = requireUniqueIds(scene.transforms(), TransformDesc::id, "transform");
        Set<String> materialIds = requireUniqueIds(scene.materials(), MaterialDesc::id, "material");
        requireUniqueIds(scene.meshes(), MeshDesc::id, "mesh");

        if (!isBlank(scene.activeCameraId()) && !cameraIds.contains(scene.activeCameraId())) {
            throw invalid("activeCameraId must reference an existing camera id");
        }

        for (MeshDesc mesh : scene.meshes()) {
            if (isBlank(mesh.transformId()) || !transformIds.contains(mesh.transformId())) {
                throw invalid("mesh " + mesh.id() + " has unknown transformId");
            }
            if (isBlank(mesh.materialId()) || !materialIds.contains(mesh.materialId())) {
                throw invalid("mesh " + mesh.id() + " has unknown materialId");
            }
        }

        if (scene.lights() != null) {
            for (LightDesc light : scene.lights()) {
                if (light == null) {
                    continue;
                }
                if (light.castsShadows()) {
                    ShadowDesc shadow = light.shadow();
                    if (shadow != null) {
                        if (shadow.mapResolution() <= 0) {
                            throw invalid("light " + light.id() + " shadow mapResolution must be > 0");
                        }
                        if (shadow.pcfKernelSize() < 1) {
                            throw invalid("light " + light.id() + " shadow pcfKernelSize must be >= 1");
                        }
                        if (shadow.cascadeCount() < 1) {
                            throw invalid("light " + light.id() + " shadow cascadeCount must be >= 1");
                        }
                    }
                }
            }
        }
    }

    private interface IdExtractor<T> {
        String id(T value);
    }

    private static <T> Set<String> requireUniqueIds(Iterable<T> values, IdExtractor<T> idExtractor, String type)
            throws EngineException {
        Set<String> ids = new HashSet<>();
        for (T value : values) {
            String id = idExtractor.id(value);
            if (isBlank(id)) {
                throw invalid(type + " id is required");
            }
            if (!ids.add(id)) {
                throw invalid("duplicate " + type + " id: " + id);
            }
        }
        return ids;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static EngineException invalid(String message) {
        return new EngineException(EngineErrorCode.SCENE_VALIDATION_FAILED, message, true);
    }
}
