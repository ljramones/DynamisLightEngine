package org.dynamislight.impl.vulkan.scene;

import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class VulkanSceneReusePolicy {
    private VulkanSceneReusePolicy() {
    }

    public static boolean canReuseGpuMeshes(
            List<VulkanGpuMesh> gpuMeshes,
            List<VulkanSceneMeshData> sceneMeshes,
            BiFunction<java.nio.file.Path, Boolean, String> textureCacheKey
    ) {
        if (gpuMeshes.isEmpty() || sceneMeshes.size() != gpuMeshes.size()) {
            return false;
        }
        Map<String, VulkanGpuMesh> byId = new HashMap<>();
        for (VulkanGpuMesh gpuMesh : gpuMeshes) {
            if (byId.put(gpuMesh.meshId, gpuMesh) != null) {
                return false;
            }
        }
        for (VulkanSceneMeshData sceneMesh : sceneMeshes) {
            VulkanGpuMesh gpuMesh = byId.get(sceneMesh.meshId());
            if (gpuMesh == null) {
                return false;
            }
            long vertexBytes = (long) sceneMesh.vertices().length * Float.BYTES;
            long indexBytes = (long) sceneMesh.indices().length * Integer.BYTES;
            if (gpuMesh.vertexBytes != vertexBytes || gpuMesh.indexBytes != indexBytes || gpuMesh.indexCount != sceneMesh.indices().length) {
                return false;
            }
            if (gpuMesh.vertexHash != Arrays.hashCode(sceneMesh.vertices()) || gpuMesh.indexHash != Arrays.hashCode(sceneMesh.indices())) {
                return false;
            }
            String albedoKey = textureCacheKey.apply(sceneMesh.albedoTexturePath(), false);
            String normalKey = textureCacheKey.apply(sceneMesh.normalTexturePath(), true);
            String metallicRoughnessKey = textureCacheKey.apply(sceneMesh.metallicRoughnessTexturePath(), false);
            String occlusionKey = textureCacheKey.apply(sceneMesh.occlusionTexturePath(), false);
            if (!gpuMesh.albedoKey.equals(albedoKey)
                    || !gpuMesh.normalKey.equals(normalKey)
                    || !gpuMesh.metallicRoughnessKey.equals(metallicRoughnessKey)
                    || !gpuMesh.occlusionKey.equals(occlusionKey)) {
                return false;
            }
        }
        return true;
    }

    public static boolean canReuseGeometryBuffers(List<VulkanGpuMesh> gpuMeshes, List<VulkanSceneMeshData> sceneMeshes) {
        if (gpuMeshes.isEmpty() || sceneMeshes.size() != gpuMeshes.size()) {
            return false;
        }
        Map<String, VulkanGpuMesh> byId = new HashMap<>();
        for (VulkanGpuMesh gpuMesh : gpuMeshes) {
            if (byId.put(gpuMesh.meshId, gpuMesh) != null) {
                return false;
            }
        }
        for (VulkanSceneMeshData sceneMesh : sceneMeshes) {
            VulkanGpuMesh gpuMesh = byId.get(sceneMesh.meshId());
            if (gpuMesh == null) {
                return false;
            }
            long vertexBytes = (long) sceneMesh.vertices().length * Float.BYTES;
            long indexBytes = (long) sceneMesh.indices().length * Integer.BYTES;
            if (gpuMesh.vertexBytes != vertexBytes || gpuMesh.indexBytes != indexBytes || gpuMesh.indexCount != sceneMesh.indices().length) {
                return false;
            }
            if (gpuMesh.vertexHash != Arrays.hashCode(sceneMesh.vertices()) || gpuMesh.indexHash != Arrays.hashCode(sceneMesh.indices())) {
                return false;
            }
        }
        return true;
    }
}
