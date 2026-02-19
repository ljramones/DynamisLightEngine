package org.dynamislight.spi.render;

/**
 * Host-pass injection point for shader contributions.
 */
public enum RenderShaderInjectionPoint {
    LIGHTING_EVAL,
    POST_RESOLVE,
    AUXILIARY
}
