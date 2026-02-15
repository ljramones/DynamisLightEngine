package org.dynamislight.api;

public record EnvironmentDesc(
        Vec3 ambientColor,
        float ambientIntensity,
        String skyboxAssetPath
) {
}
