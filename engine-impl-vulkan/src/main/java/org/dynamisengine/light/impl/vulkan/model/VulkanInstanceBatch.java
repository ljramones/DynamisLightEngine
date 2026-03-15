package org.dynamisengine.light.impl.vulkan.model;

public final class VulkanInstanceBatch {
    public final int batchHandle;
    public final int meshHandle;
    public final VulkanInstanceBatchBuffer buffer;
    public long bindlessInstanceHandle;
    public int instanceCount;

    public VulkanInstanceBatch(
            int batchHandle,
            int meshHandle,
            VulkanInstanceBatchBuffer buffer,
            long bindlessInstanceHandle,
            int instanceCount
    ) {
        this.batchHandle = batchHandle;
        this.meshHandle = meshHandle;
        this.buffer = buffer;
        this.bindlessInstanceHandle = bindlessInstanceHandle;
        this.instanceCount = Math.max(0, instanceCount);
    }
}
