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
        boolean foliage
) {
    public MaterialDesc(
            String id,
            Vec3 albedo,
            float metallic,
            float roughness,
            String albedoTexturePath,
            String normalTexturePath
    ) {
        this(id, albedo, metallic, roughness, albedoTexturePath, normalTexturePath, null, null, 0f, false, false);
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
                false
        );
    }
}
