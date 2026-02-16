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
        String occlusionTexturePath
) {
    public MaterialDesc(
            String id,
            Vec3 albedo,
            float metallic,
            float roughness,
            String albedoTexturePath,
            String normalTexturePath
    ) {
        this(id, albedo, metallic, roughness, albedoTexturePath, normalTexturePath, null, null);
    }
}
