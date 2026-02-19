package org.dynamislight.impl.vulkan;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorLifecycleCoordinator;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorResources;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorRingStats;
import org.dynamislight.impl.vulkan.state.VulkanFrameUploadStats;
import org.dynamislight.impl.vulkan.uniform.VulkanUploadStateTracker;
import org.lwjgl.system.MemoryStack;

final class VulkanContextDescriptorRuntimeHelper {
    private VulkanContextDescriptorRuntimeHelper() {
    }

    static long createDescriptorResources(
            MemoryStack stack,
            VulkanBackendResources backendResources,
            VulkanDescriptorResourceState descriptorResources,
            int framesInFlight,
            int maxDynamicSceneObjects,
            int maxReflectionProbes,
            int objectUniformBytes,
            int globalSceneUniformBytes
    ) throws EngineException {
        VulkanDescriptorResources.Allocation allocation = VulkanDescriptorResources.create(
                backendResources.device,
                backendResources.physicalDevice,
                stack,
                framesInFlight,
                maxDynamicSceneObjects,
                maxReflectionProbes,
                objectUniformBytes,
                globalSceneUniformBytes
        );
        descriptorResources.descriptorSetLayout = allocation.descriptorSetLayout();
        descriptorResources.textureDescriptorSetLayout = allocation.textureDescriptorSetLayout();
        descriptorResources.descriptorPool = allocation.descriptorPool();
        descriptorResources.frameDescriptorSets = allocation.frameDescriptorSets();
        descriptorResources.descriptorSet = descriptorResources.frameDescriptorSets[0];
        descriptorResources.objectUniformBuffer = allocation.objectUniformBuffer();
        descriptorResources.objectUniformMemory = allocation.objectUniformMemory();
        descriptorResources.objectUniformStagingBuffer = allocation.objectUniformStagingBuffer();
        descriptorResources.objectUniformStagingMemory = allocation.objectUniformStagingMemory();
        descriptorResources.objectUniformStagingMappedAddress = allocation.objectUniformStagingMappedAddress();
        descriptorResources.sceneGlobalUniformBuffer = allocation.sceneGlobalUniformBuffer();
        descriptorResources.sceneGlobalUniformMemory = allocation.sceneGlobalUniformMemory();
        descriptorResources.sceneGlobalUniformStagingBuffer = allocation.sceneGlobalUniformStagingBuffer();
        descriptorResources.sceneGlobalUniformStagingMemory = allocation.sceneGlobalUniformStagingMemory();
        descriptorResources.sceneGlobalUniformStagingMappedAddress = allocation.sceneGlobalUniformStagingMappedAddress();
        descriptorResources.reflectionProbeMetadataBuffer = allocation.reflectionProbeMetadataBuffer();
        descriptorResources.reflectionProbeMetadataMemory = allocation.reflectionProbeMetadataMemory();
        descriptorResources.reflectionProbeMetadataMappedAddress = allocation.reflectionProbeMetadataMappedAddress();
        descriptorResources.reflectionProbeMetadataMaxCount = allocation.reflectionProbeMetadataMaxCount();
        descriptorResources.reflectionProbeMetadataStrideBytes = allocation.reflectionProbeMetadataStrideBytes();
        descriptorResources.reflectionProbeMetadataBufferBytes = allocation.reflectionProbeMetadataBufferBytes();
        descriptorResources.reflectionProbeMetadataActiveCount = 0;
        descriptorResources.uniformStrideBytes = allocation.uniformStrideBytes();
        descriptorResources.uniformFrameSpanBytes = allocation.uniformFrameSpanBytes();
        descriptorResources.globalUniformFrameSpanBytes = allocation.globalUniformFrameSpanBytes();
        return allocation.estimatedGpuMemoryBytes();
    }

    static long destroyDescriptorResources(
            VulkanBackendResources backendResources,
            VulkanDescriptorResourceState descriptorResources,
            VulkanDescriptorRingStats descriptorRingStats,
            VulkanFrameUploadStats frameUploadStats,
            VulkanUploadStateTracker uploadState,
            long estimatedGpuMemoryBytes,
            int objectUniformBytes,
            int globalSceneUniformBytes
    ) {
        if (backendResources.device == null) {
            return estimatedGpuMemoryBytes;
        }
        VulkanDescriptorLifecycleCoordinator.ResetState state = VulkanDescriptorLifecycleCoordinator.destroyAndReset(
                new VulkanDescriptorLifecycleCoordinator.DestroyRequest(
                        backendResources.device,
                        new VulkanDescriptorResources.Allocation(
                                descriptorResources.descriptorSetLayout,
                                descriptorResources.textureDescriptorSetLayout,
                                descriptorResources.descriptorPool,
                                descriptorResources.frameDescriptorSets,
                                descriptorResources.objectUniformBuffer,
                                descriptorResources.objectUniformMemory,
                                descriptorResources.objectUniformStagingBuffer,
                                descriptorResources.objectUniformStagingMemory,
                                descriptorResources.objectUniformStagingMappedAddress,
                                descriptorResources.sceneGlobalUniformBuffer,
                                descriptorResources.sceneGlobalUniformMemory,
                                descriptorResources.sceneGlobalUniformStagingBuffer,
                                descriptorResources.sceneGlobalUniformStagingMemory,
                                descriptorResources.sceneGlobalUniformStagingMappedAddress,
                                descriptorResources.reflectionProbeMetadataBuffer,
                                descriptorResources.reflectionProbeMetadataMemory,
                                descriptorResources.reflectionProbeMetadataMappedAddress,
                                descriptorResources.reflectionProbeMetadataMaxCount,
                                descriptorResources.reflectionProbeMetadataStrideBytes,
                                descriptorResources.reflectionProbeMetadataBufferBytes,
                                descriptorResources.uniformStrideBytes,
                                descriptorResources.uniformFrameSpanBytes,
                                descriptorResources.globalUniformFrameSpanBytes,
                                estimatedGpuMemoryBytes
                        ),
                        objectUniformBytes,
                        globalSceneUniformBytes
                )
        );
        descriptorResources.objectUniformBuffer = state.objectUniformBuffer();
        descriptorResources.objectUniformMemory = state.objectUniformMemory();
        descriptorResources.objectUniformStagingBuffer = state.objectUniformStagingBuffer();
        descriptorResources.objectUniformStagingMemory = state.objectUniformStagingMemory();
        descriptorResources.objectUniformStagingMappedAddress = state.objectUniformStagingMappedAddress();
        descriptorResources.sceneGlobalUniformBuffer = state.sceneGlobalUniformBuffer();
        descriptorResources.sceneGlobalUniformMemory = state.sceneGlobalUniformMemory();
        descriptorResources.sceneGlobalUniformStagingBuffer = state.sceneGlobalUniformStagingBuffer();
        descriptorResources.sceneGlobalUniformStagingMemory = state.sceneGlobalUniformStagingMemory();
        descriptorResources.sceneGlobalUniformStagingMappedAddress = state.sceneGlobalUniformStagingMappedAddress();
        descriptorResources.reflectionProbeMetadataBuffer = state.reflectionProbeMetadataBuffer();
        descriptorResources.reflectionProbeMetadataMemory = state.reflectionProbeMetadataMemory();
        descriptorResources.reflectionProbeMetadataMappedAddress = state.reflectionProbeMetadataMappedAddress();
        descriptorResources.reflectionProbeMetadataMaxCount = state.reflectionProbeMetadataMaxCount();
        descriptorResources.reflectionProbeMetadataStrideBytes = state.reflectionProbeMetadataStrideBytes();
        descriptorResources.reflectionProbeMetadataBufferBytes = state.reflectionProbeMetadataBufferBytes();
        descriptorResources.reflectionProbeMetadataActiveCount = state.reflectionProbeMetadataActiveCount();
        descriptorResources.uniformStrideBytes = state.uniformStrideBytes();
        descriptorResources.uniformFrameSpanBytes = state.uniformFrameSpanBytes();
        descriptorResources.globalUniformFrameSpanBytes = state.globalUniformFrameSpanBytes();
        descriptorResources.frameDescriptorSets = state.frameDescriptorSets();
        descriptorResources.descriptorSet = state.descriptorSet();
        descriptorRingStats.descriptorRingSetCapacity = state.descriptorRingSetCapacity();
        descriptorRingStats.descriptorRingPeakSetCapacity = state.descriptorRingPeakSetCapacity();
        descriptorRingStats.descriptorRingActiveSetCount = state.descriptorRingActiveSetCount();
        descriptorRingStats.descriptorRingWasteSetCount = state.descriptorRingWasteSetCount();
        descriptorRingStats.descriptorRingPeakWasteSetCount = state.descriptorRingPeakWasteSetCount();
        descriptorRingStats.descriptorRingCapBypassCount = state.descriptorRingCapBypassCount();
        descriptorRingStats.descriptorRingPoolReuseCount = state.descriptorRingPoolReuseCount();
        descriptorRingStats.descriptorRingPoolResetFailureCount = state.descriptorRingPoolResetFailureCount();
        descriptorResources.descriptorPool = state.descriptorPool();
        descriptorResources.descriptorSetLayout = state.descriptorSetLayout();
        descriptorResources.textureDescriptorSetLayout = state.textureDescriptorSetLayout();
        frameUploadStats.lastUniformUploadBytes = state.lastFrameUniformUploadBytes();
        frameUploadStats.maxUniformUploadBytes = state.maxFrameUniformUploadBytes();
        frameUploadStats.lastGlobalUploadBytes = state.lastFrameGlobalUploadBytes();
        frameUploadStats.maxGlobalUploadBytes = state.maxFrameGlobalUploadBytes();
        frameUploadStats.lastUniformObjectCount = state.lastFrameUniformObjectCount();
        frameUploadStats.maxUniformObjectCount = state.maxFrameUniformObjectCount();
        frameUploadStats.lastUniformUploadRanges = state.lastFrameUniformUploadRanges();
        frameUploadStats.maxUniformUploadRanges = state.maxFrameUniformUploadRanges();
        frameUploadStats.lastUniformUploadStartObject = state.lastFrameUniformUploadStartObject();
        uploadState.reset();
        return state.estimatedGpuMemoryBytes();
    }
}
