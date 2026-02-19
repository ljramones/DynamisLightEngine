package org.dynamislight.spi.render;

/**
 * Logical phase where a pass contribution participates in frame execution.
 */
public enum RenderPassPhase {
    PRE_MAIN,
    MAIN,
    POST_MAIN,
    AUXILIARY
}
