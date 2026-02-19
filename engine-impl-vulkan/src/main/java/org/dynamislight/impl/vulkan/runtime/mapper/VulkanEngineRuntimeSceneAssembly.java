package org.dynamislight.impl.vulkan.runtime.mapper;

import org.dynamislight.impl.vulkan.runtime.math.VulkanEngineRuntimeCameraMath;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.ReactivePreset;
import org.dynamislight.api.scene.ReflectionOverrideMode;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.impl.vulkan.asset.VulkanGltfMeshParser;
import org.dynamislight.impl.vulkan.asset.VulkanMeshAssetLoader;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;

public final class VulkanEngineRuntimeSceneAssembly {
    private VulkanEngineRuntimeSceneAssembly() {
    }

    public static List<VulkanSceneMeshData> buildSceneMeshes(
            SceneDescriptor scene,
            VulkanMeshAssetLoader meshLoader,
            Path assetRoot
    ) {
        if (scene == null || scene.meshes() == null || scene.meshes().isEmpty()) {
            return List.of(VulkanSceneMeshData.defaultTriangle());
        }
        Map<String, MaterialDesc> materials = scene.materials() == null ? Map.of() : scene.materials().stream()
                .filter(m -> m != null && m.id() != null)
                .collect(java.util.stream.Collectors.toMap(MaterialDesc::id, m -> m, (a, b) -> a));
        Map<String, TransformDesc> transforms = scene.transforms() == null ? Map.of() : scene.transforms().stream()
                .filter(t -> t != null && t.id() != null)
                .collect(java.util.stream.Collectors.toMap(TransformDesc::id, t -> t, (a, b) -> a));

        List<VulkanSceneMeshData> out = new java.util.ArrayList<>(scene.meshes().size());
        for (int i = 0; i < scene.meshes().size(); i++) {
            MeshDesc mesh = scene.meshes().get(i);
            if (mesh == null) {
                continue;
            }
            VulkanGltfMeshParser.MeshGeometry geometry = meshLoader.loadMeshGeometry(mesh, i);
            MaterialDesc material = materials.get(mesh.materialId());
            float[] color = materialToColor(material);
            float metallic = material == null ? 0.0f : clamp01(material.metallic());
            float roughness = material == null ? 0.6f : clamp01(material.roughness());
            float reactiveStrength = material == null ? 0f : clamp01(material.reactiveStrength());
            boolean alphaTested = material != null && material.alphaTested();
            boolean foliage = material != null && material.foliage();
            int reflectionOverrideMode = material == null ? 0 : toReflectionOverrideMode(material.reflectionOverride());
            float reactiveBoost = material == null ? 1.0f : clamp(material.reactiveBoost(), 0.0f, 2.0f);
            float taaHistoryClamp = material == null ? 1.0f : clamp01(material.taaHistoryClamp());
            float emissiveReactiveBoost = material == null ? 1.0f : clamp(material.emissiveReactiveBoost(), 0.0f, 3.0f);
            float reactivePreset = material == null ? 0f : toReactivePresetValue(material.reactivePreset());
            float[] model = VulkanEngineRuntimeCameraMath.modelMatrixOf(transforms.get(mesh.transformId()), i);
            String stableMeshId = (mesh.id() == null || mesh.id().isBlank()) ? ("mesh-index-" + i) : mesh.id();
            VulkanSceneMeshData meshData = new VulkanSceneMeshData(
                    stableMeshId,
                    geometry.vertices(),
                    geometry.indices(),
                    model,
                    color,
                    metallic,
                    roughness,
                    reactiveStrength,
                    alphaTested,
                    foliage,
                    reflectionOverrideMode,
                    reactiveBoost,
                    taaHistoryClamp,
                    emissiveReactiveBoost,
                    reactivePreset,
                    resolveTexturePath(material == null ? null : material.albedoTexturePath(), assetRoot),
                    resolveTexturePath(material == null ? null : material.normalTexturePath(), assetRoot),
                    resolveTexturePath(material == null ? null : material.metallicRoughnessTexturePath(), assetRoot),
                    resolveTexturePath(material == null ? null : material.occlusionTexturePath(), assetRoot)
            );
            out.add(meshData);
        }
        return out.isEmpty() ? List.of(VulkanSceneMeshData.defaultTriangle()) : List.copyOf(out);
    }

    private static float[] materialToColor(MaterialDesc material) {
        Vec3 albedo = material == null ? null : material.albedo();
        if (albedo == null) {
            return new float[]{1f, 1f, 1f, 1f};
        }
        return new float[]{
                clamp01(albedo.x()),
                clamp01(albedo.y()),
                clamp01(albedo.z()),
                1f
        };
    }

    private static Path resolveTexturePath(String texturePath, Path assetRoot) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        Path path = Path.of(texturePath);
        return path.isAbsolute() ? path.normalize() : assetRoot.resolve(path).normalize();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float toReactivePresetValue(ReactivePreset preset) {
        if (preset == null) {
            return 0f;
        }
        return switch (preset) {
            case AUTO -> 0f;
            case STABLE -> 1f;
            case BALANCED -> 2f;
            case AGGRESSIVE -> 3f;
        };
    }

    private static int toReflectionOverrideMode(ReflectionOverrideMode mode) {
        if (mode == null) {
            return 0;
        }
        return switch (mode) {
            case AUTO -> 0;
            case PROBE_ONLY -> 1;
            case SSR_ONLY -> 2;
        };
    }
}
