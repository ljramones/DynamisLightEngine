package org.dynamislight.impl.vulkan.runtime.model;

import java.nio.file.Path;

public record IblRenderConfig(
        boolean enabled,
        float diffuseStrength,
        float specularStrength,
        boolean textureDriven,
        boolean skyboxDerived,
        boolean ktxContainerRequested,
        boolean ktxSkyboxFallback,
        int ktxDecodeUnavailableCount,
        int ktxTranscodeRequiredCount,
        int ktxUnsupportedVariantCount,
        float prefilterStrength,
        boolean degraded,
        int missingAssetCount,
        Path irradiancePath,
        Path radiancePath,
        Path brdfLutPath
) {
}
