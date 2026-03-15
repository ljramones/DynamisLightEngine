package org.dynamisengine.light.impl.vulkan.vfx;

public enum VfxRenderPhase {
    NOT_STARTED,
    COMPUTE_COMPLETE,
    OPAQUE_COMPLETE,
    VFX_COMPLETE,
    POST_PROCESS
}
