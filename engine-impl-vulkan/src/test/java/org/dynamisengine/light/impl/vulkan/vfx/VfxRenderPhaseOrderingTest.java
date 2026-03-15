package org.dynamisengine.light.impl.vulkan.vfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VfxRenderPhaseOrderingTest {

    @Test
    void assertionErrorIfRecordDrawsBeforeSimulate() {
        VfxRenderPhaseTracker tracker = new VfxRenderPhaseTracker();
        tracker.beginFrame();
        assertThrows(AssertionError.class, tracker::markVfxComplete);
    }

    @Test
    void assertionErrorIfSimulateTwiceInFrame() {
        VfxRenderPhaseTracker tracker = new VfxRenderPhaseTracker();
        tracker.beginFrame();
        tracker.markComputeComplete();
        assertThrows(AssertionError.class, tracker::markComputeComplete);
    }

    @Test
    void correctOrderProducesNoError() {
        VfxRenderPhaseTracker tracker = new VfxRenderPhaseTracker();
        assertDoesNotThrow(() -> {
            tracker.beginFrame();
            tracker.markComputeComplete();
            tracker.markOpaqueComplete();
            tracker.markVfxComplete();
            tracker.markPostProcess();
        });
    }
}
