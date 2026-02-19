package org.dynamislight.spi.render;

/**
 * Resource binding update cadence/frequency declaration.
 */
public enum RenderBindingFrequency {
    PER_FRAME,
    PER_PASS,
    PER_MATERIAL,
    PER_DRAW
}
