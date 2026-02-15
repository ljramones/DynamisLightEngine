package org.dynamislight.api;

public record FogDesc(
        boolean enabled,
        FogMode mode,
        Vec3 color,
        float density,
        float heightFalloff,
        float maxOpacity,
        float noiseAmount,
        float noiseScale,
        float noiseSpeed
) {
}
