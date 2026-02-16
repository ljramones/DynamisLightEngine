package org.dynamislight.api.scene;

/**
 * TransformDesc API type.
 */
public record TransformDesc(
        String id,
        Vec3 position,
        Vec3 rotationEulerDeg,
        Vec3 scale
) {
}
