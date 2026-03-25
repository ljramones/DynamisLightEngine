package org.dynamisengine.light.api.runtime;

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
        long gpuMemoryBytes,
        double taaHistoryRejectRate,
        double taaConfidenceMean,
        long taaConfidenceDropEvents,
        // Per-pass draw call counts
        long shadowDrawCalls,
        long geometryDrawCalls,
        long postDrawCalls,
        // Pipeline/shader switch count (variant transitions in sorted draw list)
        long pipelineSwitches,
        // Submitted objects before culling vs visible after
        long submittedObjects,
        // Per-variant draw counts
        long staticDraws,
        long morphDraws,
        long skinnedDraws,
        long instancedDraws
) {
}
