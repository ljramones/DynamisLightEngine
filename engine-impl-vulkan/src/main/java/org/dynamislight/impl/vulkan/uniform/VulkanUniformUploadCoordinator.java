package org.dynamislight.impl.vulkan.uniform;

import java.util.List;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamislight.impl.vulkan.state.VulkanFrameUploadStats;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

public final class VulkanUniformUploadCoordinator {
    private VulkanUniformUploadCoordinator() {
    }

    public static void prepareFrameUniforms(PrepareInputs inputs) throws EngineException {
        VulkanFrameUniformCoordinator.Result result = VulkanUniformFrameCoordinator.prepare(
                new VulkanUniformFrameCoordinator.PrepareRequest(
                        inputs.frameIdx(),
                        inputs.gpuMeshes().size(),
                        inputs.uploadState().maxObservedDynamicObjects(),
                        inputs.maxDynamicSceneObjects(),
                        inputs.framesInFlight(),
                        inputs.descriptorResources().uniformFrameSpanBytes,
                        inputs.descriptorResources().globalUniformFrameSpanBytes,
                        inputs.descriptorResources().uniformStrideBytes,
                        inputs.objectUniformBytes(),
                        inputs.globalSceneUniformBytes(),
                        inputs.device(),
                        inputs.descriptorResources().objectUniformStagingMemory,
                        inputs.descriptorResources().sceneGlobalUniformStagingMemory,
                        inputs.descriptorResources().objectUniformStagingMappedAddress,
                        inputs.descriptorResources().sceneGlobalUniformStagingMappedAddress,
                        inputs.uploadState().globalStateRevision(),
                        inputs.uploadState().sceneStateRevision(),
                        inputs.uploadState().frameGlobalRevisionApplied(),
                        inputs.uploadState().frameSceneRevisionApplied(),
                        inputs.uploadState().pendingSceneDirtyRangeCount(),
                        inputs.uploadState().pendingSceneDirtyStarts(),
                        inputs.uploadState().pendingSceneDirtyEnds(),
                        inputs.uploadState().pendingUploadSrcOffsets(),
                        inputs.uploadState().pendingUploadDstOffsets(),
                        inputs.uploadState().pendingUploadByteCounts(),
                        idx -> inputs.gpuMeshes().isEmpty() ? null : inputs.gpuMeshes().get(idx),
                        inputs.globalSceneUniformInput(),
                        inputs.vkFailure()
                )
        );
        inputs.uploadState().applyPrepareResult(result);
    }

    public static void uploadFrameUniforms(UploadInputs inputs) {
        VulkanUniformUploadRecorder.UploadStats stats = VulkanUniformFrameCoordinator.upload(
                new VulkanUniformFrameCoordinator.UploadRequest(
                        inputs.commandBuffer(),
                        inputs.descriptorResources().sceneGlobalUniformStagingBuffer,
                        inputs.descriptorResources().sceneGlobalUniformBuffer,
                        inputs.descriptorResources().objectUniformStagingBuffer,
                        inputs.descriptorResources().objectUniformBuffer,
                        inputs.uploadState().pendingGlobalUploadSrcOffset(),
                        inputs.uploadState().pendingGlobalUploadDstOffset(),
                        inputs.uploadState().pendingGlobalUploadByteCount(),
                        inputs.uploadState().pendingUploadObjectCount(),
                        inputs.uploadState().pendingUploadStartObject(),
                        inputs.uploadState().pendingUploadSrcOffsets(),
                        inputs.uploadState().pendingUploadDstOffsets(),
                        inputs.uploadState().pendingUploadByteCounts(),
                        inputs.uploadState().pendingUploadRangeCount()
                )
        );
        inputs.frameUploadStats().apply(stats);
        inputs.uploadState().clearPendingUploads();
    }

    public record PrepareInputs(
            int frameIdx,
            List<VulkanGpuMesh> gpuMeshes,
            int maxDynamicSceneObjects,
            int framesInFlight,
            int objectUniformBytes,
            int globalSceneUniformBytes,
            VkDevice device,
            VulkanDescriptorResourceState descriptorResources,
            VulkanUploadStateTracker uploadState,
            VulkanUniformWriters.GlobalSceneUniformInput globalSceneUniformInput,
            VulkanFrameUniformCoordinator.FailureFactory vkFailure
    ) {
    }

    public record UploadInputs(
            VkCommandBuffer commandBuffer,
            VulkanDescriptorResourceState descriptorResources,
            VulkanUploadStateTracker uploadState,
            VulkanFrameUploadStats frameUploadStats
    ) {
    }
}
