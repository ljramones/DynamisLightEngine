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
        String normalTexturePath
) {
}
