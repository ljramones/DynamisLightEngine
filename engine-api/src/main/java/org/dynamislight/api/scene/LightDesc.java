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
        boolean castsShadows,
        ShadowDesc shadow,
        LightType type,
        Vec3 direction,
        float innerConeDegrees,
        float outerConeDegrees
) {
    public LightDesc {
        type = type == null ? LightType.DIRECTIONAL : type;
        direction = direction == null ? new Vec3(0f, -1f, 0f) : direction;
        innerConeDegrees = Math.max(0f, innerConeDegrees);
        outerConeDegrees = Math.max(innerConeDegrees, outerConeDegrees);
    }

    public LightDesc(
            String id,
            Vec3 position,
            Vec3 color,
            float intensity,
            float range,
            boolean castsShadows,
            ShadowDesc shadow
    ) {
        this(
                id,
                position,
                color,
                intensity,
                range,
                castsShadows,
                shadow,
                LightType.DIRECTIONAL,
                new Vec3(0f, -1f, 0f),
                15f,
                30f
        );
    }

    public LightDesc(
            String id,
            Vec3 position,
            Vec3 color,
            float intensity,
            float range,
            boolean castsShadows,
            ShadowDesc shadow,
            LightType type
    ) {
        this(
                id,
                position,
                color,
                intensity,
                range,
                castsShadows,
                shadow,
                type,
                new Vec3(0f, -1f, 0f),
                15f,
                30f
        );
    }
}
