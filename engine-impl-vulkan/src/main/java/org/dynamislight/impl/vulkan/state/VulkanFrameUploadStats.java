package org.dynamislight.impl.vulkan.state;

import org.dynamislight.impl.vulkan.uniform.VulkanUniformUploadRecorder;

public final class VulkanFrameUploadStats {
    public int lastUniformUploadBytes;
    public int maxUniformUploadBytes;
    public int lastGlobalUploadBytes;
    public int maxGlobalUploadBytes;
    public int lastUniformObjectCount;
    public int maxUniformObjectCount;
    public int lastUniformUploadRanges;
    public int maxUniformUploadRanges;
    public int lastUniformUploadStartObject;

    public void apply(VulkanUniformUploadRecorder.UploadStats stats) {
        lastGlobalUploadBytes = stats.globalUploadBytes();
        maxGlobalUploadBytes = Math.max(maxGlobalUploadBytes, stats.globalUploadBytes());
        lastUniformUploadBytes = stats.uniformUploadBytes();
        maxUniformUploadBytes = Math.max(maxUniformUploadBytes, stats.uniformUploadBytes());
        lastUniformObjectCount = stats.uniformObjectCount();
        maxUniformObjectCount = Math.max(maxUniformObjectCount, stats.uniformObjectCount());
        lastUniformUploadRanges = stats.uniformUploadRanges();
        maxUniformUploadRanges = Math.max(maxUniformUploadRanges, stats.uniformUploadRanges());
        lastUniformUploadStartObject = stats.uniformUploadStartObject();
    }
}
