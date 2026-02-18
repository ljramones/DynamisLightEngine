package org.dynamislight.impl.vulkan.state;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public final class VulkanDescriptorResourceState {
    public long descriptorSetLayout = VK_NULL_HANDLE;
    public long textureDescriptorSetLayout = VK_NULL_HANDLE;
    public long descriptorPool = VK_NULL_HANDLE;
    public long descriptorSet = VK_NULL_HANDLE;
    public long[] frameDescriptorSets = new long[0];
    public long textureDescriptorPool = VK_NULL_HANDLE;

    public long sceneGlobalUniformBuffer = VK_NULL_HANDLE;
    public long sceneGlobalUniformMemory = VK_NULL_HANDLE;
    public long sceneGlobalUniformStagingBuffer = VK_NULL_HANDLE;
    public long sceneGlobalUniformStagingMemory = VK_NULL_HANDLE;
    public long sceneGlobalUniformStagingMappedAddress;

    public long objectUniformBuffer = VK_NULL_HANDLE;
    public long objectUniformMemory = VK_NULL_HANDLE;
    public long objectUniformStagingBuffer = VK_NULL_HANDLE;
    public long objectUniformStagingMemory = VK_NULL_HANDLE;
    public long objectUniformStagingMappedAddress;

    public long reflectionProbeMetadataBuffer = VK_NULL_HANDLE;
    public long reflectionProbeMetadataMemory = VK_NULL_HANDLE;
    public long reflectionProbeMetadataMappedAddress;
    public int reflectionProbeMetadataMaxCount;
    public int reflectionProbeMetadataStrideBytes;
    public int reflectionProbeMetadataBufferBytes;
    public int reflectionProbeMetadataActiveCount;

    public int uniformStrideBytes = 96;
    public int uniformFrameSpanBytes = 96;
    public int globalUniformFrameSpanBytes = 784;
}
