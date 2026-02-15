package org.dynamislight.api;

public record TransformDesc(
        String id,
        Vec3 position,
        Vec3 rotationEulerDeg,
        Vec3 scale
) {
}
