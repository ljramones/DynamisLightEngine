package org.dynamislight.impl.vulkan.uniform;

import org.dynamislight.api.error.EngineException;
import org.lwjgl.vulkan.VkCommandBuffer;

public final class VulkanUniformFrameCoordinator {
    private VulkanUniformFrameCoordinator() {
    }

    public static VulkanFrameUniformCoordinator.Result prepare(PrepareRequest request) throws EngineException {
        return VulkanFrameUniformCoordinator.prepare(
                new VulkanFrameUniformCoordinator.Inputs(
                        request.frameIdx(),
                        request.meshCount(),
                        request.maxObservedDynamicObjects(),
                        request.maxDynamicSceneObjects(),
                        request.framesInFlight(),
                        request.uniformFrameSpanBytes(),
                        request.globalUniformFrameSpanBytes(),
                        request.uniformStrideBytes(),
                        request.objectUniformBytes(),
                        request.globalSceneUniformBytes(),
                        request.device(),
                        request.objectUniformStagingMemory(),
                        request.sceneGlobalUniformStagingMemory(),
                        request.objectUniformStagingMappedAddress(),
                        request.sceneGlobalUniformStagingMappedAddress(),
                        request.globalStateRevision(),
                        request.sceneStateRevision(),
                        request.frameGlobalRevisionApplied(),
                        request.frameSceneRevisionApplied(),
                        request.pendingSceneDirtyRangeCount(),
                        request.pendingSceneDirtyStarts(),
                        request.pendingSceneDirtyEnds(),
                        request.pendingUploadSrcOffsets(),
                        request.pendingUploadDstOffsets(),
                        request.pendingUploadByteCounts(),
                        request.meshProvider(),
                        request.globalSceneUniformInput(),
                        request.vkFailure()
                )
        );
    }

    public static VulkanUniformUploadRecorder.UploadStats upload(UploadRequest request) {
        return VulkanUniformUploadRecorder.recordUploads(
                request.commandBuffer(),
                new VulkanUniformUploadRecorder.UploadInputs(
                        request.sceneGlobalUniformStagingBuffer(),
                        request.sceneGlobalUniformBuffer(),
                        request.objectUniformStagingBuffer(),
                        request.objectUniformBuffer(),
                        request.pendingGlobalUploadSrcOffset(),
                        request.pendingGlobalUploadDstOffset(),
                        request.pendingGlobalUploadByteCount(),
                        request.pendingUploadObjectCount(),
                        request.pendingUploadStartObject(),
                        request.pendingUploadSrcOffsets(),
                        request.pendingUploadDstOffsets(),
                        request.pendingUploadByteCounts(),
                        request.pendingUploadRangeCount()
                )
        );
    }

    public static int dynamicUniformOffset(int uniformStrideBytes, int meshIndex) {
        return meshIndex * uniformStrideBytes;
    }

    public static long descriptorSetForFrame(long[] frameDescriptorSets, long descriptorSet, int frameIdx) {
        if (frameDescriptorSets.length == 0) {
            return descriptorSet;
        }
        int normalizedFrame = Math.floorMod(frameIdx, frameDescriptorSets.length);
        return frameDescriptorSets[normalizedFrame];
    }

    public record PrepareRequest(
            int frameIdx,
            int meshCount,
            int maxObservedDynamicObjects,
            int maxDynamicSceneObjects,
            int framesInFlight,
            int uniformFrameSpanBytes,
            int globalUniformFrameSpanBytes,
            int uniformStrideBytes,
            int objectUniformBytes,
            int globalSceneUniformBytes,
            org.lwjgl.vulkan.VkDevice device,
            long objectUniformStagingMemory,
            long sceneGlobalUniformStagingMemory,
            long objectUniformStagingMappedAddress,
            long sceneGlobalUniformStagingMappedAddress,
            long globalStateRevision,
            long sceneStateRevision,
            long[] frameGlobalRevisionApplied,
            long[] frameSceneRevisionApplied,
            int pendingSceneDirtyRangeCount,
            int[] pendingSceneDirtyStarts,
            int[] pendingSceneDirtyEnds,
            long[] pendingUploadSrcOffsets,
            long[] pendingUploadDstOffsets,
            int[] pendingUploadByteCounts,
            VulkanFrameUniformCoordinator.MeshProvider meshProvider,
            VulkanUniformWriters.GlobalSceneUniformInput globalSceneUniformInput,
            VulkanFrameUniformCoordinator.FailureFactory vkFailure
    ) {
    }

    public record UploadRequest(
            VkCommandBuffer commandBuffer,
            long sceneGlobalUniformStagingBuffer,
            long sceneGlobalUniformBuffer,
            long objectUniformStagingBuffer,
            long objectUniformBuffer,
            long pendingGlobalUploadSrcOffset,
            long pendingGlobalUploadDstOffset,
            int pendingGlobalUploadByteCount,
            int pendingUploadObjectCount,
            int pendingUploadStartObject,
            long[] pendingUploadSrcOffsets,
            long[] pendingUploadDstOffsets,
            int[] pendingUploadByteCounts,
            int pendingUploadRangeCount
    ) {
    }
}
