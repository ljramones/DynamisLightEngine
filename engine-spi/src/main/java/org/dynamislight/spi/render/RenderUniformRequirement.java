package org.dynamislight.spi.render;

/**
 * Uniform field requirement metadata.
 *
 * @param targetUbo target UBO identity (for example global_scene)
 * @param fieldName symbolic field name
 * @param byteOffset byte offset inside target UBO, or 0 when unspecified
 * @param byteSize field byte size, or 0 when unspecified
 */
public record RenderUniformRequirement(
        String targetUbo,
        String fieldName,
        int byteOffset,
        int byteSize
) {
    public RenderUniformRequirement {
        targetUbo = targetUbo == null ? "" : targetUbo.trim();
        fieldName = fieldName == null ? "" : fieldName.trim();
        byteOffset = Math.max(0, byteOffset);
        byteSize = Math.max(0, byteSize);
    }
}
