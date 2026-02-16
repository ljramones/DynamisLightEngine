package org.dynamislight.impl.vulkan.profile;

public final class VulkanContextProfileCoordinator {
    private VulkanContextProfileCoordinator() {
    }

    public static SceneReuseStats sceneReuse(SceneReuseRequest in) {
        return VulkanProfileFactory.sceneReuseStats(
                in.sceneReuseHitCount(),
                in.sceneReorderReuseCount(),
                in.sceneTextureRebindCount(),
                in.sceneFullRebuildCount(),
                in.meshBufferRebuildCount(),
                in.descriptorPoolBuildCount(),
                in.descriptorPoolRebuildCount()
        );
    }

    public static FrameResourceProfile frameResource(FrameResourceRequest in) {
        return VulkanProfileFactory.frameResourceProfile(
                in.framesInFlight(),
                in.frameDescriptorSetCount(),
                in.uniformStrideBytes(),
                in.uniformFrameSpanBytes(),
                in.globalUniformFrameSpanBytes(),
                in.maxDynamicSceneObjects(),
                in.pendingSceneDirtyCapacity(),
                in.lastFrameGlobalUploadBytes(),
                in.maxFrameGlobalUploadBytes(),
                in.lastFrameUniformUploadBytes(),
                in.maxFrameUniformUploadBytes(),
                in.lastFrameUniformObjectCount(),
                in.maxFrameUniformObjectCount(),
                in.lastFrameUniformUploadRanges(),
                in.maxFrameUniformUploadRanges(),
                in.lastFrameUniformUploadStartObject(),
                in.pendingUploadRangeOverflowCount(),
                in.descriptorRingSetCapacity(),
                in.descriptorRingPeakSetCapacity(),
                in.descriptorRingActiveSetCount(),
                in.descriptorRingWasteSetCount(),
                in.descriptorRingPeakWasteSetCount(),
                in.descriptorRingMaxSetCapacity(),
                in.descriptorRingReuseHitCount(),
                in.descriptorRingGrowthRebuildCount(),
                in.descriptorRingSteadyRebuildCount(),
                in.descriptorRingPoolReuseCount(),
                in.descriptorRingPoolResetFailureCount(),
                in.descriptorRingCapBypassCount(),
                in.dynamicUploadMergeGapObjects(),
                in.dynamicObjectSoftLimit(),
                in.maxObservedDynamicObjects(),
                in.uniformStagingMapped()
        );
    }

    public static ShadowCascadeProfile shadowCascade(ShadowRequest in) {
        return VulkanProfileFactory.shadowCascadeProfile(
                in.shadowEnabled(),
                in.shadowCascadeCount(),
                in.shadowMapResolution(),
                in.shadowPcfRadius(),
                in.shadowBias(),
                in.shadowCascadeSplitNdc()
        );
    }

    public static PostProcessPipelineProfile postProcess(PostRequest in) {
        return VulkanProfileFactory.postProcessPipelineProfile(in.postOffscreenRequested(), in.postOffscreenActive());
    }

    public record SceneReuseRequest(
            long sceneReuseHitCount,
            long sceneReorderReuseCount,
            long sceneTextureRebindCount,
            long sceneFullRebuildCount,
            long meshBufferRebuildCount,
            long descriptorPoolBuildCount,
            long descriptorPoolRebuildCount
    ) {
    }

    public record FrameResourceRequest(
            int framesInFlight,
            int frameDescriptorSetCount,
            int uniformStrideBytes,
            int uniformFrameSpanBytes,
            int globalUniformFrameSpanBytes,
            int maxDynamicSceneObjects,
            int pendingSceneDirtyCapacity,
            int lastFrameGlobalUploadBytes,
            int maxFrameGlobalUploadBytes,
            int lastFrameUniformUploadBytes,
            int maxFrameUniformUploadBytes,
            int lastFrameUniformObjectCount,
            int maxFrameUniformObjectCount,
            int lastFrameUniformUploadRanges,
            int maxFrameUniformUploadRanges,
            int lastFrameUniformUploadStartObject,
            long pendingUploadRangeOverflowCount,
            int descriptorRingSetCapacity,
            int descriptorRingPeakSetCapacity,
            int descriptorRingActiveSetCount,
            int descriptorRingWasteSetCount,
            int descriptorRingPeakWasteSetCount,
            int descriptorRingMaxSetCapacity,
            long descriptorRingReuseHitCount,
            long descriptorRingGrowthRebuildCount,
            long descriptorRingSteadyRebuildCount,
            long descriptorRingPoolReuseCount,
            long descriptorRingPoolResetFailureCount,
            long descriptorRingCapBypassCount,
            int dynamicUploadMergeGapObjects,
            int dynamicObjectSoftLimit,
            int maxObservedDynamicObjects,
            boolean uniformStagingMapped
    ) {
    }

    public record ShadowRequest(
            boolean shadowEnabled,
            int shadowCascadeCount,
            int shadowMapResolution,
            int shadowPcfRadius,
            float shadowBias,
            float[] shadowCascadeSplitNdc
    ) {
    }

    public record PostRequest(
            boolean postOffscreenRequested,
            boolean postOffscreenActive
    ) {
    }
}
