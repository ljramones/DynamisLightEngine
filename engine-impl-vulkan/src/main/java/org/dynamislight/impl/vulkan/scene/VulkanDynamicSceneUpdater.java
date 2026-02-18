package org.dynamislight.impl.vulkan.scene;

import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VulkanDynamicSceneUpdater {
    private VulkanDynamicSceneUpdater() {
    }

    public static Result update(List<VulkanGpuMesh> gpuMeshes, List<VulkanSceneMeshData> sceneMeshes) {
        Map<String, VulkanGpuMesh> byId = new HashMap<>();
        for (VulkanGpuMesh mesh : gpuMeshes) {
            byId.put(mesh.meshId, mesh);
        }
        List<VulkanGpuMesh> ordered = new ArrayList<>(sceneMeshes.size());
        boolean reordered = false;
        int dirtyStart = Integer.MAX_VALUE;
        int dirtyEnd = -1;
        for (int i = 0; i < sceneMeshes.size(); i++) {
            VulkanSceneMeshData sceneMesh = sceneMeshes.get(i);
            VulkanGpuMesh mesh = byId.get(sceneMesh.meshId());
            if (mesh == null) {
                continue;
            }
            if (i < gpuMeshes.size() && gpuMeshes.get(i) != mesh) {
                reordered = true;
            }
            boolean changed = mesh.updateDynamicState(
                    sceneMesh.modelMatrix().clone(),
                    sceneMesh.color()[0],
                    sceneMesh.color()[1],
                    sceneMesh.color()[2],
                    sceneMesh.metallic(),
                    sceneMesh.roughness(),
                    sceneMesh.reactiveStrength(),
                    sceneMesh.alphaTested(),
                    sceneMesh.foliage(),
                    sceneMesh.reflectionProbeOnly(),
                    sceneMesh.reactiveBoost(),
                    sceneMesh.taaHistoryClamp(),
                    sceneMesh.emissiveReactiveBoost(),
                    sceneMesh.reactivePreset()
            );
            if (changed) {
                dirtyStart = Math.min(dirtyStart, i);
                dirtyEnd = Math.max(dirtyEnd, i);
            }
            ordered.add(mesh);
        }
        if (ordered.size() == gpuMeshes.size()) {
            gpuMeshes.clear();
            gpuMeshes.addAll(ordered);
        }
        if (reordered) {
            dirtyStart = 0;
            dirtyEnd = Math.max(0, gpuMeshes.size() - 1);
        }
        return new Result(reordered, dirtyStart, dirtyEnd);
    }

    public record Result(boolean reordered, int dirtyStart, int dirtyEnd) {
    }
}
