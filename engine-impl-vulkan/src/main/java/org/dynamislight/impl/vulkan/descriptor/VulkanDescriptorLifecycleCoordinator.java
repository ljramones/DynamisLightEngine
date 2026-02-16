package org.dynamislight.impl.vulkan.descriptor;

import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public final class VulkanDescriptorLifecycleCoordinator {
    private VulkanDescriptorLifecycleCoordinator() {
    }

    public static ResetState destroyAndReset(DestroyRequest request) {
        VulkanDescriptorResources.destroy(request.device(), request.allocation());
        return new ResetState(
                VK_NULL_HANDLE, VK_NULL_HANDLE, VK_NULL_HANDLE, VK_NULL_HANDLE, 0L,
                VK_NULL_HANDLE, VK_NULL_HANDLE, VK_NULL_HANDLE, VK_NULL_HANDLE, 0L,
                request.objectUniformBytes(),
                request.objectUniformBytes(),
                request.globalSceneUniformBytes(),
                0L,
                new long[0],
                VK_NULL_HANDLE,
                0, 0, 0, 0, 0,
                0L, 0L, 0L,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                0, 0, 0, 0, 0, 0, 0, 0, 0
        );
    }

    public record DestroyRequest(
            VkDevice device,
            VulkanDescriptorResources.Allocation allocation,
            int objectUniformBytes,
            int globalSceneUniformBytes
    ) {
    }

    public record ResetState(
            long objectUniformBuffer,
            long objectUniformMemory,
            long objectUniformStagingBuffer,
            long objectUniformStagingMemory,
            long objectUniformStagingMappedAddress,
            long sceneGlobalUniformBuffer,
            long sceneGlobalUniformMemory,
            long sceneGlobalUniformStagingBuffer,
            long sceneGlobalUniformStagingMemory,
            long sceneGlobalUniformStagingMappedAddress,
            int uniformStrideBytes,
            int uniformFrameSpanBytes,
            int globalUniformFrameSpanBytes,
            long estimatedGpuMemoryBytes,
            long[] frameDescriptorSets,
            long descriptorSet,
            int descriptorRingSetCapacity,
            int descriptorRingPeakSetCapacity,
            int descriptorRingActiveSetCount,
            int descriptorRingWasteSetCount,
            int descriptorRingPeakWasteSetCount,
            long descriptorRingCapBypassCount,
            long descriptorRingPoolReuseCount,
            long descriptorRingPoolResetFailureCount,
            long descriptorPool,
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            int lastFrameUniformUploadBytes,
            int maxFrameUniformUploadBytes,
            int lastFrameGlobalUploadBytes,
            int maxFrameGlobalUploadBytes,
            int lastFrameUniformObjectCount,
            int maxFrameUniformObjectCount,
            int lastFrameUniformUploadRanges,
            int maxFrameUniformUploadRanges,
            int lastFrameUniformUploadStartObject
    ) {
    }
}
