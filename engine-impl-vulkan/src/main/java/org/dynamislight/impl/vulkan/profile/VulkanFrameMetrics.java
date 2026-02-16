package org.dynamislight.impl.vulkan.profile;

public record VulkanFrameMetrics(
        double cpuFrameMs,
        double gpuFrameMs,
        long drawCalls,
        long triangles,
        long visibleObjects,
        long gpuMemoryBytes
) {
}
