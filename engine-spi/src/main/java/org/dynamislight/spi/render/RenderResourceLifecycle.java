package org.dynamislight.spi.render;

/**
 * Resource lifetime category used by capability contracts.
 */
public enum RenderResourceLifecycle {
    TRANSIENT,
    PERSISTENT,
    PERSISTENT_PARTIAL_UPDATE,
    CROSS_FRAME_TEMPORAL
}
