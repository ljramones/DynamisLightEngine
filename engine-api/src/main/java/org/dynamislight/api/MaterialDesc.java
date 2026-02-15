package org.dynamislight.api;

public record MaterialDesc(
        String id,
        Vec3 albedo,
        float metallic,
        float roughness,
        String albedoTexturePath,
        String normalTexturePath
) {
}
