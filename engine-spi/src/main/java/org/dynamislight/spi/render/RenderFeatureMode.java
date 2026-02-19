package org.dynamislight.spi.render;

/**
 * Stable feature mode identifier.
 *
 * @param id stable mode ID
 */
public record RenderFeatureMode(String id) {
    public RenderFeatureMode {
        id = id == null ? "" : id.trim();
    }
}
