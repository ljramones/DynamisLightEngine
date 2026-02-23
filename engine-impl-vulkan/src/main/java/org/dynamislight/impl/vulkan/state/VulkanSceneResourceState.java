package org.dynamislight.impl.vulkan.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dynamislight.impl.vulkan.model.VulkanInstanceBatch;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;

public final class VulkanSceneResourceState {
    public final List<VulkanGpuMesh> gpuMeshes = new ArrayList<>();
    public final Map<Integer, VulkanInstanceBatch> instanceBatches = new LinkedHashMap<>();
    public int nextInstanceBatchHandle;
    public List<VulkanSceneMeshData> pendingSceneMeshes = List.of(VulkanSceneMeshData.defaultTriangle());
    public long sceneReuseHitCount;
    public long sceneReorderReuseCount;
    public long sceneTextureRebindCount;
    public long sceneFullRebuildCount;
    public long meshBufferRebuildCount;
}
