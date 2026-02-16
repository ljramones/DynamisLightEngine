package org.dynamislight.impl.vulkan.profile;

public final class VulkanProfileFactory {
    private VulkanProfileFactory() {
    }

    public static SceneReuseStats sceneReuseStats(
            long sceneReuseHitCount,
            long sceneReorderReuseCount,
            long sceneTextureRebindCount,
            long sceneFullRebuildCount,
            long meshBufferRebuildCount,
            long descriptorPoolBuildCount,
            long descriptorPoolRebuildCount
    ) {
        return new SceneReuseStats(
                sceneReuseHitCount,
                sceneReorderReuseCount,
                sceneTextureRebindCount,
                sceneFullRebuildCount,
                meshBufferRebuildCount,
                descriptorPoolBuildCount,
                descriptorPoolRebuildCount
        );
    }

    public static FrameResourceProfile frameResourceProfile(
            int framesInFlight,
            int frameDescriptorSetCount,
            int uniformStrideBytes,
            int uniformFrameSpanBytes,
            int globalUniformFrameSpanBytes,
            int maxDynamicSceneObjects,
            int pendingRangeCapacity,
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
            boolean stagingMapped
    ) {
        return new FrameResourceProfile(
                framesInFlight,
                frameDescriptorSetCount,
                uniformStrideBytes,
                uniformFrameSpanBytes,
                globalUniformFrameSpanBytes,
                maxDynamicSceneObjects,
                pendingRangeCapacity,
                lastFrameGlobalUploadBytes,
                maxFrameGlobalUploadBytes,
                lastFrameUniformUploadBytes,
                maxFrameUniformUploadBytes,
                lastFrameUniformObjectCount,
                maxFrameUniformObjectCount,
                lastFrameUniformUploadRanges,
                maxFrameUniformUploadRanges,
                lastFrameUniformUploadStartObject,
                pendingUploadRangeOverflowCount,
                descriptorRingSetCapacity,
                descriptorRingPeakSetCapacity,
                descriptorRingActiveSetCount,
                descriptorRingWasteSetCount,
                descriptorRingPeakWasteSetCount,
                descriptorRingMaxSetCapacity,
                descriptorRingReuseHitCount,
                descriptorRingGrowthRebuildCount,
                descriptorRingSteadyRebuildCount,
                descriptorRingPoolReuseCount,
                descriptorRingPoolResetFailureCount,
                descriptorRingCapBypassCount,
                dynamicUploadMergeGapObjects,
                dynamicObjectSoftLimit,
                maxObservedDynamicObjects,
                stagingMapped
        );
    }

    public static ShadowCascadeProfile shadowCascadeProfile(
            boolean shadowEnabled,
            int shadowCascadeCount,
            int shadowMapResolution,
            int shadowPcfRadius,
            float shadowBias,
            float[] shadowCascadeSplitNdc
    ) {
        return new ShadowCascadeProfile(
                shadowEnabled,
                shadowCascadeCount,
                shadowMapResolution,
                shadowPcfRadius,
                shadowBias,
                shadowCascadeSplitNdc[0],
                shadowCascadeSplitNdc[1],
                shadowCascadeSplitNdc[2]
        );
    }

    public static PostProcessPipelineProfile postProcessPipelineProfile(
            boolean postOffscreenRequested,
            boolean postOffscreenActive
    ) {
        return new PostProcessPipelineProfile(
                postOffscreenRequested,
                postOffscreenActive,
                postOffscreenActive ? "offscreen" : "shader-fallback"
        );
    }
}
