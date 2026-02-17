package org.dynamislight.api.scene;

/**
 * MaterialDesc API type.
 */
public record MaterialDesc(
        String id,
        Vec3 albedo,
        float metallic,
        float roughness,
        String albedoTexturePath,
        String normalTexturePath,
        String metallicRoughnessTexturePath,
        String occlusionTexturePath,
        float reactiveStrength,
        boolean alphaTested,
        boolean foliage,
        float reactiveBoost,
        float taaHistoryClamp,
        float emissiveReactiveBoost,
        ReactivePreset reactivePreset
) {
    public MaterialDesc(
            String id,
            Vec3 albedo,
            float metallic,
            float roughness,
            String albedoTexturePath,
            String normalTexturePath,
            String metallicRoughnessTexturePath,
            String occlusionTexturePath,
            float reactiveStrength,
            boolean alphaTested,
            boolean foliage
    ) {
        this(
                id,
                albedo,
                metallic,
                roughness,
                albedoTexturePath,
                normalTexturePath,
                metallicRoughnessTexturePath,
                occlusionTexturePath,
                reactiveStrength,
                alphaTested,
                foliage,
                1.0f,
                1.0f,
                1.0f,
                ReactivePreset.AUTO
        );
    }

    public MaterialDesc(
            String id,
            Vec3 albedo,
            float metallic,
            float roughness,
            String albedoTexturePath,
            String normalTexturePath
    ) {
        this(id, albedo, metallic, roughness, albedoTexturePath, normalTexturePath, null, null, 0f, false, false, 1.0f, 1.0f);
    }

    public MaterialDesc(
            String id,
            Vec3 albedo,
            float metallic,
            float roughness,
            String albedoTexturePath,
            String normalTexturePath,
            String metallicRoughnessTexturePath,
            String occlusionTexturePath
    ) {
        this(
                id,
                albedo,
                metallic,
                roughness,
                albedoTexturePath,
                normalTexturePath,
                metallicRoughnessTexturePath,
                occlusionTexturePath,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                ReactivePreset.AUTO
        );
    }

    public MaterialDesc(
            String id,
            Vec3 albedo,
            float metallic,
            float roughness,
            String albedoTexturePath,
            String normalTexturePath,
            String metallicRoughnessTexturePath,
            String occlusionTexturePath,
            float reactiveStrength,
            boolean alphaTested,
            boolean foliage,
            float reactiveBoost,
            float taaHistoryClamp
    ) {
        this(
                id,
                albedo,
                metallic,
                roughness,
                albedoTexturePath,
                normalTexturePath,
                metallicRoughnessTexturePath,
                occlusionTexturePath,
                reactiveStrength,
                alphaTested,
                foliage,
                reactiveBoost,
                taaHistoryClamp,
                1.0f,
                ReactivePreset.AUTO
        );
    }
}
