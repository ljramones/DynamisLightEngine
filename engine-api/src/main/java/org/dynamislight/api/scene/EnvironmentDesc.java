package org.dynamislight.api.scene;

/**
 * EnvironmentDesc API type.
 */
public record EnvironmentDesc(
        Vec3 ambientColor,
        float ambientIntensity,
        String skyboxAssetPath,
        String iblIrradiancePath,
        String iblRadiancePath,
        String iblBrdfLutPath
) {
    public EnvironmentDesc(Vec3 ambientColor, float ambientIntensity, String skyboxAssetPath) {
        this(ambientColor, ambientIntensity, skyboxAssetPath, null, null, null);
    }
}
