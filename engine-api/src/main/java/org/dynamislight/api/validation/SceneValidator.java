package org.dynamislight.api.validation;

import java.util.HashSet;
import java.util.Set;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.AntiAliasingDesc;

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
        for (MaterialDesc material : scene.materials()) {
            if (material == null) {
                continue;
            }
            if (material.reactiveStrength() < 0f || material.reactiveStrength() > 1f) {
                throw invalid("material " + material.id() + " reactiveStrength must be in [0,1]");
            }
            if (material.reactiveBoost() < 0f || material.reactiveBoost() > 2f) {
                throw invalid("material " + material.id() + " reactiveBoost must be in [0,2]");
            }
            if (material.taaHistoryClamp() < 0f || material.taaHistoryClamp() > 1f) {
                throw invalid("material " + material.id() + " taaHistoryClamp must be in [0,1]");
            }
            if (material.emissiveReactiveBoost() < 0f || material.emissiveReactiveBoost() > 3f) {
                throw invalid("material " + material.id() + " emissiveReactiveBoost must be in [0,3]");
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
                LightType type = light.type() == null ? LightType.DIRECTIONAL : light.type();
                if (type == LightType.SPOT) {
                    if (light.innerConeDegrees() < 0f || light.outerConeDegrees() < 0f) {
                        throw invalid("light " + light.id() + " spot cone angles must be >= 0");
                    }
                    if (light.outerConeDegrees() < light.innerConeDegrees()) {
                        throw invalid("light " + light.id() + " outerConeDegrees must be >= innerConeDegrees");
                    }
                }
                if (type != LightType.DIRECTIONAL && light.range() <= 0f) {
                    throw invalid("light " + light.id() + " non-directional lights must have range > 0");
                }
            }
        }
        if (scene.postProcess() != null) {
            AntiAliasingDesc aa = scene.postProcess().antiAliasing();
            if (aa != null) {
                if (aa.blend() < 0f || aa.blend() > 0.95f) {
                    throw invalid("postProcess.antiAliasing blend must be in [0,0.95]");
                }
                if (aa.clipScale() < 0.5f || aa.clipScale() > 1.6f) {
                    throw invalid("postProcess.antiAliasing clipScale must be in [0.5,1.6]");
                }
                if (aa.sharpenStrength() < 0f || aa.sharpenStrength() > 0.35f) {
                    throw invalid("postProcess.antiAliasing sharpenStrength must be in [0,0.35]");
                }
                if (aa.renderScale() < 0.5f || aa.renderScale() > 1.0f) {
                    throw invalid("postProcess.antiAliasing renderScale must be in [0.5,1.0]");
                }
                if (aa.debugView() < 0 || aa.debugView() > 5) {
                    throw invalid("postProcess.antiAliasing debugView must be in [0,5]");
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
