package org.dynamislight.api;

/**
 * CameraDesc API type.
 */
public record CameraDesc(
        String id,
        Vec3 position,
        Vec3 rotationEulerDeg,
        float fovDegrees,
        float nearPlane,
        float farPlane
) {
}
