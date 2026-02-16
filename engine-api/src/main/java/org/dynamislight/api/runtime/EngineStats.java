package org.dynamislight.api.runtime;

/**
 * EngineStats API type.
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
