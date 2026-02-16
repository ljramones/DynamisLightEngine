package org.dynamislight.api.scene;

/**
 * LightDesc API type.
 */
public record LightDesc(
        String id,
        Vec3 position,
        Vec3 color,
        float intensity,
        float range,
        boolean castsShadows
) {
}
