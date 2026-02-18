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
import org.dynamislight.api.scene.ReflectionAdvancedDesc;
import org.dynamislight.api.scene.ReflectionDesc;

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
            ReflectionDesc reflections = scene.postProcess().reflections();
            if (reflections != null) {
                String mode = reflections.mode() == null ? "" : reflections.mode().trim().toLowerCase();
                if (!mode.isEmpty()
                        && !mode.equals("ibl_only")
                        && !mode.equals("ssr")
                        && !mode.equals("planar")
                        && !mode.equals("hybrid")
                        && !mode.equals("rt_hybrid")) {
                    throw invalid("postProcess.reflections mode must be one of ibl_only|ssr|planar|hybrid|rt_hybrid");
                }
                if (reflections.ssrStrength() < 0f || reflections.ssrStrength() > 1f) {
                    throw invalid("postProcess.reflections ssrStrength must be in [0,1]");
                }
                if (reflections.ssrMaxRoughness() < 0f || reflections.ssrMaxRoughness() > 1f) {
                    throw invalid("postProcess.reflections ssrMaxRoughness must be in [0,1]");
                }
                if (reflections.ssrStepScale() < 0.5f || reflections.ssrStepScale() > 3f) {
                    throw invalid("postProcess.reflections ssrStepScale must be in [0.5,3]");
                }
                if (reflections.temporalWeight() < 0f || reflections.temporalWeight() > 0.98f) {
                    throw invalid("postProcess.reflections temporalWeight must be in [0,0.98]");
                }
                if (reflections.planarStrength() < 0f || reflections.planarStrength() > 1f) {
                    throw invalid("postProcess.reflections planarStrength must be in [0,1]");
                }
            }
            ReflectionAdvancedDesc reflectionAdvanced = scene.postProcess().reflectionAdvanced();
            if (reflectionAdvanced != null) {
                if (reflectionAdvanced.hiZMipCount() < 1 || reflectionAdvanced.hiZMipCount() > 12) {
                    throw invalid("postProcess.reflectionAdvanced hiZMipCount must be in [1,12]");
                }
                if (reflectionAdvanced.denoisePasses() < 0 || reflectionAdvanced.denoisePasses() > 6) {
                    throw invalid("postProcess.reflectionAdvanced denoisePasses must be in [0,6]");
                }
                if (reflectionAdvanced.planarFadeStart() < 0f || reflectionAdvanced.planarFadeStart() > 1000f) {
                    throw invalid("postProcess.reflectionAdvanced planarFadeStart must be in [0,1000]");
                }
                if (reflectionAdvanced.planarFadeEnd() < reflectionAdvanced.planarFadeStart()
                        || reflectionAdvanced.planarFadeEnd() > 2000f) {
                    throw invalid("postProcess.reflectionAdvanced planarFadeEnd must be >= planarFadeStart and <= 2000");
                }
                if (reflectionAdvanced.probeBlendDistance() < 0f || reflectionAdvanced.probeBlendDistance() > 100f) {
                    throw invalid("postProcess.reflectionAdvanced probeBlendDistance must be in [0,100]");
                }
                if (reflectionAdvanced.rtMaxRoughness() < 0f || reflectionAdvanced.rtMaxRoughness() > 1f) {
                    throw invalid("postProcess.reflectionAdvanced rtMaxRoughness must be in [0,1]");
                }
                for (var probe : reflectionAdvanced.probes()) {
                    if (probe.blendDistance() < 0f || probe.blendDistance() > 1000f) {
                        throw invalid("postProcess.reflectionAdvanced.probes blendDistance must be in [0,1000]");
                    }
                    if (probe.intensity() < 0f || probe.intensity() > 16f) {
                        throw invalid("postProcess.reflectionAdvanced.probes intensity must be in [0,16]");
                    }
                    if (probe.cubemapAssetPath() == null || probe.cubemapAssetPath().isBlank()) {
                        throw invalid("postProcess.reflectionAdvanced.probes cubemapAssetPath is required");
                    }
                    if (probe.extentsMin().x() > probe.extentsMax().x()
                            || probe.extentsMin().y() > probe.extentsMax().y()
                            || probe.extentsMin().z() > probe.extentsMax().z()) {
                        throw invalid("postProcess.reflectionAdvanced.probes extentsMin must be <= extentsMax on all axes");
                    }
                }
                String rtFallbackMode = reflectionAdvanced.rtFallbackMode() == null
                        ? ""
                        : reflectionAdvanced.rtFallbackMode().trim().toLowerCase();
                if (!rtFallbackMode.isEmpty()
                        && !rtFallbackMode.equals("ibl_only")
                        && !rtFallbackMode.equals("ssr")
                        && !rtFallbackMode.equals("planar")
                        && !rtFallbackMode.equals("hybrid")
                        && !rtFallbackMode.equals("rt_hybrid")) {
                    throw invalid("postProcess.reflectionAdvanced rtFallbackMode must be one of ibl_only|ssr|planar|hybrid|rt_hybrid");
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
