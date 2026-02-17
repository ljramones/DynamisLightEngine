package org.dynamislight.api.scene;

/**
 * Convenience descriptor for authored spot lights.
 */
public record SpotLightDesc(
        String id,
        Vec3 position,
        Vec3 direction,
        Vec3 color,
        float intensity,
        float range,
        float innerConeDegrees,
        float outerConeDegrees,
        boolean castsShadows,
        ShadowDesc shadow
) {
    public SpotLightDesc {
        direction = direction == null ? new Vec3(0f, -1f, 0f) : direction;
        innerConeDegrees = Math.max(0f, innerConeDegrees);
        outerConeDegrees = Math.max(innerConeDegrees, outerConeDegrees);
        range = Math.max(0f, range);
        intensity = Math.max(0f, intensity);
    }

    public LightDesc toLightDesc() {
        return new LightDesc(
                id,
                position,
                color,
                intensity,
                range,
                castsShadows,
                shadow,
                LightType.SPOT,
                direction,
                innerConeDegrees,
                outerConeDegrees
        );
    }
}
