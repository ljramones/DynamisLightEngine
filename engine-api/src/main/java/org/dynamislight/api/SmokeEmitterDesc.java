package org.dynamislight.api;

/**
 * SmokeEmitterDesc API type.
 */
public record SmokeEmitterDesc(
        String id,
        Vec3 position,
        Vec3 boxExtents,
        float emissionRate,
        float density,
        Vec3 albedo,
        float extinction,
        Vec3 velocity,
        float turbulence,
        float lifetimeSeconds,
        boolean enabled
) {
}
