package org.dynamislight.api;

public record LightDesc(
        String id,
        Vec3 position,
        Vec3 color,
        float intensity,
        float range,
        boolean castsShadows
) {
}
