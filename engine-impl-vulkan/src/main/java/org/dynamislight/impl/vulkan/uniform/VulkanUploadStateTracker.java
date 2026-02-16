package org.dynamislight.impl.vulkan.uniform;

import java.util.Arrays;

import org.dynamislight.impl.vulkan.scene.VulkanDirtyRangeTrackerOps;

public final class VulkanUploadStateTracker {
    private final int maxPendingUploadRangesHardCap;
    private int maxObservedDynamicObjects;
    private long globalStateRevision = 1;
    private long sceneStateRevision = 1;
    private long[] frameGlobalRevisionApplied;
    private long[] frameSceneRevisionApplied;
    private int[] pendingSceneDirtyStarts;
    private int[] pendingSceneDirtyEnds;
    private int pendingSceneDirtyRangeCount;
    private long pendingUploadSrcOffset = -1L;
    private long pendingUploadDstOffset = -1L;
    private int pendingUploadByteCount;
    private int pendingUploadObjectCount;
    private int pendingUploadStartObject;
    private long[] pendingUploadSrcOffsets;
    private long[] pendingUploadDstOffsets;
    private int[] pendingUploadByteCounts;
    private int pendingUploadRangeCount;
    private long pendingUploadRangeOverflowCount;
    private long pendingGlobalUploadSrcOffset = -1L;
    private long pendingGlobalUploadDstOffset = -1L;
    private int pendingGlobalUploadByteCount;

    public VulkanUploadStateTracker(int framesInFlight, int maxPendingUploadRanges, int maxPendingUploadRangesHardCap) {
        this.maxPendingUploadRangesHardCap = maxPendingUploadRangesHardCap;
        reallocateFrameTracking(framesInFlight);
        reallocateUploadRangeTracking(maxPendingUploadRanges);
    }

    public void reallocateFrameTracking(int framesInFlight) {
        frameGlobalRevisionApplied = new long[framesInFlight];
        frameSceneRevisionApplied = new long[framesInFlight];
    }

    public void reallocateUploadRangeTracking(int maxPendingUploadRanges) {
        int capacity = Math.max(8, maxPendingUploadRanges);
        pendingSceneDirtyStarts = new int[capacity];
        pendingSceneDirtyEnds = new int[capacity];
        pendingUploadSrcOffsets = new long[capacity];
        pendingUploadDstOffsets = new long[capacity];
        pendingUploadByteCounts = new int[capacity];
    }

    public void reset() {
        maxObservedDynamicObjects = 0;
        pendingUploadSrcOffset = -1L;
        pendingUploadDstOffset = -1L;
        pendingUploadByteCount = 0;
        pendingUploadObjectCount = 0;
        pendingUploadStartObject = 0;
        pendingGlobalUploadSrcOffset = -1L;
        pendingGlobalUploadDstOffset = -1L;
        pendingGlobalUploadByteCount = 0;
        pendingSceneDirtyRangeCount = 0;
        globalStateRevision = 1;
        sceneStateRevision = 1;
        Arrays.fill(frameGlobalRevisionApplied, 0L);
        Arrays.fill(frameSceneRevisionApplied, 0L);
    }

    public void markGlobalStateDirty() {
        globalStateRevision++;
    }

    public void markSceneStateDirty(int dirtyStart, int dirtyEnd, int dynamicUploadMergeGapObjects) {
        if (dirtyEnd < dirtyStart) {
            return;
        }
        sceneStateRevision++;
        addPendingSceneDirtyRange(Math.max(0, dirtyStart), Math.max(0, dirtyEnd), dynamicUploadMergeGapObjects);
    }

    private void addPendingSceneDirtyRange(int start, int end, int dynamicUploadMergeGapObjects) {
        int previousCapacity = pendingSceneDirtyStarts.length;
        VulkanDirtyRangeTrackerOps.AddResult result = VulkanDirtyRangeTrackerOps.addRange(
                pendingSceneDirtyStarts,
                pendingSceneDirtyEnds,
                pendingSceneDirtyRangeCount,
                start,
                end,
                maxPendingUploadRangesHardCap
        );
        pendingSceneDirtyStarts = result.starts();
        pendingSceneDirtyEnds = result.ends();
        if (pendingSceneDirtyStarts.length != previousCapacity) {
            pendingUploadSrcOffsets = Arrays.copyOf(pendingUploadSrcOffsets, pendingSceneDirtyStarts.length);
            pendingUploadDstOffsets = Arrays.copyOf(pendingUploadDstOffsets, pendingSceneDirtyStarts.length);
            pendingUploadByteCounts = Arrays.copyOf(pendingUploadByteCounts, pendingSceneDirtyStarts.length);
        }
        pendingSceneDirtyRangeCount = result.count();
        if (result.overflowed()) {
            pendingUploadRangeOverflowCount++;
        }
        pendingSceneDirtyRangeCount = VulkanDirtyRangeTrackerOps.normalizeRanges(
                pendingSceneDirtyStarts,
                pendingSceneDirtyEnds,
                pendingSceneDirtyRangeCount,
                dynamicUploadMergeGapObjects
        );
    }

    public void applyPrepareResult(VulkanFrameUniformCoordinator.Result result) {
        maxObservedDynamicObjects = result.maxObservedDynamicObjects();
        if (result.clearPendingOnly()) {
            clearPendingUploads();
            return;
        }
        pendingGlobalUploadSrcOffset = result.pendingGlobalUploadSrcOffset();
        pendingGlobalUploadDstOffset = result.pendingGlobalUploadDstOffset();
        pendingGlobalUploadByteCount = result.pendingGlobalUploadByteCount();
        pendingUploadRangeCount = result.pendingUploadRangeCount();
        pendingUploadObjectCount = result.pendingUploadObjectCount();
        pendingUploadStartObject = result.pendingUploadStartObject();
        pendingUploadByteCount = result.pendingUploadByteCount();
        pendingUploadSrcOffset = result.pendingUploadSrcOffset();
        pendingUploadDstOffset = result.pendingUploadDstOffset();
        pendingSceneDirtyRangeCount = result.pendingSceneDirtyRangeCount();
    }

    public void clearPendingUploads() {
        pendingUploadSrcOffset = -1L;
        pendingUploadDstOffset = -1L;
        pendingUploadByteCount = 0;
        pendingUploadObjectCount = 0;
        pendingUploadStartObject = 0;
        pendingUploadRangeCount = 0;
        pendingGlobalUploadSrcOffset = -1L;
        pendingGlobalUploadDstOffset = -1L;
        pendingGlobalUploadByteCount = 0;
    }

    public int maxObservedDynamicObjects() { return maxObservedDynamicObjects; }
    public long globalStateRevision() { return globalStateRevision; }
    public long sceneStateRevision() { return sceneStateRevision; }
    public long[] frameGlobalRevisionApplied() { return frameGlobalRevisionApplied; }
    public long[] frameSceneRevisionApplied() { return frameSceneRevisionApplied; }
    public int[] pendingSceneDirtyStarts() { return pendingSceneDirtyStarts; }
    public int[] pendingSceneDirtyEnds() { return pendingSceneDirtyEnds; }
    public int pendingSceneDirtyRangeCount() { return pendingSceneDirtyRangeCount; }
    public long pendingUploadSrcOffset() { return pendingUploadSrcOffset; }
    public long pendingUploadDstOffset() { return pendingUploadDstOffset; }
    public int pendingUploadByteCount() { return pendingUploadByteCount; }
    public int pendingUploadObjectCount() { return pendingUploadObjectCount; }
    public int pendingUploadStartObject() { return pendingUploadStartObject; }
    public long[] pendingUploadSrcOffsets() { return pendingUploadSrcOffsets; }
    public long[] pendingUploadDstOffsets() { return pendingUploadDstOffsets; }
    public int[] pendingUploadByteCounts() { return pendingUploadByteCounts; }
    public int pendingUploadRangeCount() { return pendingUploadRangeCount; }
    public long pendingUploadRangeOverflowCount() { return pendingUploadRangeOverflowCount; }
    public long pendingGlobalUploadSrcOffset() { return pendingGlobalUploadSrcOffset; }
    public long pendingGlobalUploadDstOffset() { return pendingGlobalUploadDstOffset; }
    public int pendingGlobalUploadByteCount() { return pendingGlobalUploadByteCount; }
}
