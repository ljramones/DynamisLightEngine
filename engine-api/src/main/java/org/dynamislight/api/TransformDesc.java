package org.dynamislight.api;

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
