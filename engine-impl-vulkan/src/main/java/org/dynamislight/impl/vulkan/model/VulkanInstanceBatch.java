package org.dynamislight.impl.vulkan.model;

public final class VulkanInstanceBatch {
    public final int batchHandle;
    public final int meshHandle;
    public final VulkanInstanceBatchBuffer buffer;
    public int instanceCount;

    public VulkanInstanceBatch(int batchHandle, int meshHandle, VulkanInstanceBatchBuffer buffer, int instanceCount) {
        this.batchHandle = batchHandle;
        this.meshHandle = meshHandle;
        this.buffer = buffer;
        this.instanceCount = Math.max(0, instanceCount);
    }
}
