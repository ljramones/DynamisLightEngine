package org.dynamislight.impl.vulkan.uniform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VulkanUploadStateTrackerTest {
    @Test
    void markGlobalStateDirtyIncrementsRevision() {
        var tracker = new VulkanUploadStateTracker(3, 8, 32);
        long before = tracker.globalStateRevision();
        tracker.markGlobalStateDirty();
        assertEquals(before + 1, tracker.globalStateRevision());
    }

    @Test
    void markSceneStateDirtyMergesAdjacentRanges() {
        var tracker = new VulkanUploadStateTracker(3, 8, 32);
        tracker.markSceneStateDirty(0, 2, 0);
        tracker.markSceneStateDirty(3, 5, 0);

        assertEquals(1, tracker.pendingSceneDirtyRangeCount());
        assertEquals(0, tracker.pendingSceneDirtyStarts()[0]);
        assertEquals(5, tracker.pendingSceneDirtyEnds()[0]);
    }

    @Test
    void markSceneStateDirtyTracksOverflowWhenHardCapReached() {
        var tracker = new VulkanUploadStateTracker(2, 8, 8);
        for (int i = 0; i < 9; i++) {
            int start = i * 10;
            tracker.markSceneStateDirty(start, start, 0);
        }

        assertEquals(1, tracker.pendingUploadRangeOverflowCount());
        assertEquals(1, tracker.pendingSceneDirtyRangeCount());
    }

    @Test
    void applyPrepareResultNoopClearsPendingUploads() {
        var tracker = new VulkanUploadStateTracker(2, 4, 16);
        tracker.applyPrepareResult(new VulkanFrameUniformCoordinator.Result(
                false,
                3,
                10L,
                20L,
                64,
                1,
                2,
                0,
                128,
                10L,
                20L,
                1
        ));
        tracker.applyPrepareResult(VulkanFrameUniformCoordinator.Result.noop(5));

        assertEquals(5, tracker.maxObservedDynamicObjects());
        assertEquals(-1L, tracker.pendingGlobalUploadSrcOffset());
        assertEquals(-1L, tracker.pendingGlobalUploadDstOffset());
        assertEquals(0, tracker.pendingGlobalUploadByteCount());
        assertEquals(0, tracker.pendingUploadRangeCount());
    }
}
