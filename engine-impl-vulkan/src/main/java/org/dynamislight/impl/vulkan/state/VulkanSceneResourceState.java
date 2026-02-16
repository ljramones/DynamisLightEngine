package org.dynamislight.impl.vulkan.state;

import java.util.ArrayList;
import java.util.List;

import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;

public final class VulkanSceneResourceState {
    public final List<VulkanGpuMesh> gpuMeshes = new ArrayList<>();
    public List<VulkanSceneMeshData> pendingSceneMeshes = List.of(VulkanSceneMeshData.defaultTriangle());
    public long sceneReuseHitCount;
    public long sceneReorderReuseCount;
    public long sceneTextureRebindCount;
    public long sceneFullRebuildCount;
    public long meshBufferRebuildCount;
}
