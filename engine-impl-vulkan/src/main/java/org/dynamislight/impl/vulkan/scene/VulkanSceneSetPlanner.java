package org.dynamislight.impl.vulkan.scene;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;

import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;

public final class VulkanSceneSetPlanner {
    private VulkanSceneSetPlanner() {
    }

    public static Plan plan(
            List<VulkanGpuMesh> gpuMeshes,
            List<VulkanSceneMeshData> sceneMeshes,
            BiFunction<Path, Boolean, String> textureKeyer
    ) {
        List<VulkanSceneMeshData> safe = (sceneMeshes == null || sceneMeshes.isEmpty())
                ? List.of(VulkanSceneMeshData.defaultTriangle())
                : List.copyOf(sceneMeshes);
        if (VulkanSceneReusePolicy.canReuseGpuMeshes(gpuMeshes, safe, textureKeyer)) {
            return new Plan(safe, Action.REUSE_DYNAMIC_ONLY);
        }
        if (VulkanSceneReusePolicy.canReuseGeometryBuffers(gpuMeshes, safe)) {
            return new Plan(safe, Action.REUSE_GEOMETRY_REBIND_TEXTURES);
        }
        return new Plan(safe, Action.FULL_REBUILD);
    }

    public enum Action {
        REUSE_DYNAMIC_ONLY,
        REUSE_GEOMETRY_REBIND_TEXTURES,
        FULL_REBUILD
    }

    public record Plan(List<VulkanSceneMeshData> sceneMeshes, Action action) {
    }
}
