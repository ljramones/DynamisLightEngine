package org.dynamislight.impl.vulkan.command;

public record BindlessHeapStats(
        int jointUsed,
        int jointCapacity,
        int morphDeltaUsed,
        int morphDeltaCapacity,
        int morphWeightUsed,
        int morphWeightCapacity,
        int instanceUsed,
        int instanceCapacity,
        long allocations,
        long freesQueued,
        long freesRetired,
        long staleHandleRejects,
        int drawMetaCount,
        int invalidIndexWrites
) {
}
