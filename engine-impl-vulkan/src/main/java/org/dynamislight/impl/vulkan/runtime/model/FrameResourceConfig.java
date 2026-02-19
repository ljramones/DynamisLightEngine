package org.dynamislight.impl.vulkan.runtime.model;

public record FrameResourceConfig(
        int framesInFlight,
        int maxDynamicSceneObjects,
        int maxPendingUploadRanges,
        int maxTextureDescriptorSets,
        int meshGeometryCacheEntries
) {
}
