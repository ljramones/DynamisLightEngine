package org.dynamislight.api.runtime;

/**
 * Represents statistics related to the engine's performance during rendering.
 *
 * The record encapsulates key metrics that provide insights into the rendering process and system resource usage.
 */
public record EngineStats(
        double fps,
        double cpuFrameMs,
        double gpuFrameMs,
        long drawCalls,
        long triangles,
        long visibleObjects,
        long gpuMemoryBytes
) {
}
