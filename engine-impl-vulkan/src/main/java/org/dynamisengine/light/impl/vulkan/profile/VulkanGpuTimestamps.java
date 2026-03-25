package org.dynamisengine.light.impl.vulkan.profile;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Manages Vulkan timestamp queries for GPU pass timing.
 *
 * <p>Double-buffered: queries written in frame N are read in frame N+1.
 * No same-frame reads. No CPU waits. Reset only after results consumed.
 *
 * <p>Coarse passes instrumented:
 * <ul>
 *   <li>frame (total)</li>
 *   <li>shadow_passes</li>
 *   <li>main_geometry</li>
 *   <li>post_composite</li>
 *   <li>ui (debug/UI composition)</li>
 * </ul>
 */
public final class VulkanGpuTimestamps {

    private static final Logger LOG = Logger.getLogger(VulkanGpuTimestamps.class.getName());

    // Query indices: each pass has START and END
    private static final int FRAME_START = 0;
    private static final int FRAME_END = 1;
    private static final int SHADOW_START = 2;
    private static final int SHADOW_END = 3;
    private static final int GEOMETRY_START = 4;
    private static final int GEOMETRY_END = 5;
    private static final int POST_START = 6;
    private static final int POST_END = 7;
    private static final int UI_START = 8;
    private static final int UI_END = 9;
    private static final int QUERY_COUNT = 10;

    private long[] queryPools; // one per frame-in-flight
    private int framesInFlight;
    private float timestampPeriodNs; // nanoseconds per timestamp tick
    private boolean available;
    private int currentFrame;

    // Results from the previous frame
    private double frameTimeMs;
    private double shadowPassMs;
    private double geometryPassMs;
    private double postProcessMs;
    private double uiPassMs;
    private boolean resultsValid;

    /**
     * Initialize query pools. Call after device and physical device are ready.
     */
    public void initialize(VkDevice device, int framesInFlight, float timestampPeriod) {
        this.framesInFlight = framesInFlight;
        this.timestampPeriodNs = timestampPeriod;

        if (timestampPeriod <= 0) {
            LOG.warning("GPU timestamps not supported (timestampPeriod=0)");
            available = false;
            return;
        }

        queryPools = new long[framesInFlight];
        try (var stack = MemoryStack.stackPush()) {
            var createInfo = org.lwjgl.vulkan.VkQueryPoolCreateInfo.calloc(stack)
                .sType$Default()
                .queryType(VK_QUERY_TYPE_TIMESTAMP)
                .queryCount(QUERY_COUNT);

            for (int i = 0; i < framesInFlight; i++) {
                var pPool = stack.mallocLong(1);
                int result = vkCreateQueryPool(device, createInfo, null, pPool);
                if (result != VK_SUCCESS) {
                    LOG.warning("Failed to create timestamp query pool: " + result);
                    available = false;
                    return;
                }
                queryPools[i] = pPool.get(0);
            }
        }

        available = true;
        LOG.info("GPU timestamps initialized: " + framesInFlight + " pools x " + QUERY_COUNT + " queries");
    }

    /**
     * Reset queries for the current frame. Call at beginning of command recording.
     */
    public void resetForFrame(VkCommandBuffer cmd, int frameIndex) {
        if (!available) return;
        currentFrame = frameIndex % framesInFlight;
        vkCmdResetQueryPool(cmd, queryPools[currentFrame], 0, QUERY_COUNT);
    }

    /** Write frame-start timestamp. */
    public void writeFrameStart(VkCommandBuffer cmd) {
        if (!available) return;
        vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, queryPools[currentFrame], FRAME_START);
    }

    /** Write frame-end timestamp. */
    public void writeFrameEnd(VkCommandBuffer cmd) {
        if (!available) return;
        vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPools[currentFrame], FRAME_END);
    }

    /** Write pass-start timestamp for a named pass. */
    public void writePassStart(VkCommandBuffer cmd, String passId) {
        if (!available) return;
        int idx = passStartIndex(passId);
        if (idx >= 0) vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, queryPools[currentFrame], idx);
    }

    /** Write pass-end timestamp for a named pass. */
    public void writePassEnd(VkCommandBuffer cmd, String passId) {
        if (!available) return;
        int idx = passEndIndex(passId);
        if (idx >= 0) vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPools[currentFrame], idx);
    }

    /**
     * Read results from the previous frame. Call at beginning of new frame.
     * Returns false if results are not yet available.
     */
    public boolean readPreviousFrame(VkDevice device, int frameIndex) {
        if (!available) { resultsValid = false; return false; }

        int prevFrame = ((frameIndex % framesInFlight) - 1 + framesInFlight) % framesInFlight;
        long[] timestamps = new long[QUERY_COUNT];

        try (var stack = MemoryStack.stackPush()) {
            var pResults = stack.mallocLong(QUERY_COUNT);
            int result = vkGetQueryPoolResults(device, queryPools[prevFrame], 0, QUERY_COUNT,
                pResults, 8, VK_QUERY_RESULT_64_BIT);

            if (result == VK_NOT_READY) {
                resultsValid = false;
                return false;
            }
            if (result != VK_SUCCESS) {
                resultsValid = false;
                return false;
            }

            for (int i = 0; i < QUERY_COUNT; i++) {
                timestamps[i] = pResults.get(i);
            }
        }

        // Convert to milliseconds
        double nsToMs = timestampPeriodNs / 1_000_000.0;
        frameTimeMs = (timestamps[FRAME_END] - timestamps[FRAME_START]) * nsToMs;
        shadowPassMs = (timestamps[SHADOW_END] - timestamps[SHADOW_START]) * nsToMs;
        geometryPassMs = (timestamps[GEOMETRY_END] - timestamps[GEOMETRY_START]) * nsToMs;
        postProcessMs = (timestamps[POST_END] - timestamps[POST_START]) * nsToMs;
        uiPassMs = (timestamps[UI_END] - timestamps[UI_START]) * nsToMs;

        // Clamp negatives (can happen if timestamps wrap or pass not executed)
        if (frameTimeMs < 0) frameTimeMs = 0;
        if (shadowPassMs < 0) shadowPassMs = 0;
        if (geometryPassMs < 0) geometryPassMs = 0;
        if (postProcessMs < 0) postProcessMs = 0;
        if (uiPassMs < 0) uiPassMs = 0;

        resultsValid = true;
        return true;
    }

    /** Get timing results as a map suitable for DebugSnapshot metrics. */
    public Map<String, Double> getMetrics() {
        var metrics = new LinkedHashMap<String, Double>();
        metrics.put("gpu.frameTimeMs", frameTimeMs);
        metrics.put("gpu.shadowPassMs", shadowPassMs);
        metrics.put("gpu.geometryPassMs", geometryPassMs);
        metrics.put("gpu.postProcessMs", postProcessMs);
        metrics.put("gpu.uiPassMs", uiPassMs);
        metrics.put("gpu.timingAvailable", resultsValid ? 1.0 : 0.0);
        return metrics;
    }

    public boolean isAvailable() { return available; }
    public boolean hasValidResults() { return resultsValid; }
    public double frameTimeMs() { return frameTimeMs; }

    public void destroy(VkDevice device) {
        if (queryPools != null) {
            for (long pool : queryPools) {
                if (pool != VK_NULL_HANDLE) vkDestroyQueryPool(device, pool, null);
            }
            queryPools = null;
        }
        available = false;
    }

    // --- Pass ID mapping ---

    private static int passStartIndex(String passId) {
        return switch (passId) {
            case "shadow_passes" -> SHADOW_START;
            case "main_geometry" -> GEOMETRY_START;
            case "post_composite" -> POST_START;
            case "ui" -> UI_START;
            default -> -1;
        };
    }

    private static int passEndIndex(String passId) {
        return switch (passId) {
            case "shadow_passes" -> SHADOW_END;
            case "main_geometry" -> GEOMETRY_END;
            case "post_composite" -> POST_END;
            case "ui" -> UI_END;
            default -> -1;
        };
    }
}
