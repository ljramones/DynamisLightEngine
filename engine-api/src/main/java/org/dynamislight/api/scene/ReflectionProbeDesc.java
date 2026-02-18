package org.dynamislight.api.scene;

/**
 * Reflection probe descriptor supplied by hosts as scene data.
 */
public record ReflectionProbeDesc(
        int id,
        Vec3 position,
        Vec3 extentsMin,
        Vec3 extentsMax,
        String cubemapAssetPath,
        int priority,
        float blendDistance,
        float intensity,
        boolean boxProjection
) {
    public ReflectionProbeDesc {
        position = position == null ? new Vec3(0f, 0f, 0f) : position;
        extentsMin = extentsMin == null ? new Vec3(-1f, -1f, -1f) : extentsMin;
        extentsMax = extentsMax == null ? new Vec3(1f, 1f, 1f) : extentsMax;
        cubemapAssetPath = cubemapAssetPath == null ? "" : cubemapAssetPath;
    }
}
