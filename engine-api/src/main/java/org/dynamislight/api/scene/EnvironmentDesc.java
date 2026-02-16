package org.dynamislight.api.scene;

/**
 * EnvironmentDesc API type.
 */
public record EnvironmentDesc(
        Vec3 ambientColor,
        float ambientIntensity,
        String skyboxAssetPath
) {
}
