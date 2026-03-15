package org.dynamisengine.light.impl.vulkan.vfx;

/**
 * Enforces per-frame VFX ordering:
 * simulate -> opaque complete -> recordDraws -> post.
 */
public final class VfxRenderPhaseTracker {
    private VfxRenderPhase phase = VfxRenderPhase.NOT_STARTED;

    public void beginFrame() {
        require(VfxRenderPhase.NOT_STARTED, "beginFrame() requires NOT_STARTED");
    }

    public void markComputeComplete() {
        require(VfxRenderPhase.NOT_STARTED, "simulate() must run first");
        phase = VfxRenderPhase.COMPUTE_COMPLETE;
    }

    public void markOpaqueComplete() {
        require(VfxRenderPhase.COMPUTE_COMPLETE, "opaque pass requires compute completion");
        phase = VfxRenderPhase.OPAQUE_COMPLETE;
    }

    public void markVfxComplete() {
        require(VfxRenderPhase.OPAQUE_COMPLETE, "recordDraws() requires opaque completion");
        phase = VfxRenderPhase.VFX_COMPLETE;
    }

    public void markPostProcess() {
        require(VfxRenderPhase.VFX_COMPLETE, "post process requires VFX completion");
        phase = VfxRenderPhase.POST_PROCESS;
    }

    public VfxRenderPhase phase() {
        return phase;
    }

    private void require(VfxRenderPhase expected, String message) {
        if (phase != expected) {
            throw new AssertionError(message + " (actual=" + phase + ")");
        }
    }
}
