package org.dynamislight.api;

import java.util.Set;

public record EngineCapabilities(
        Set<String> backends,
        boolean volumetricFog,
        boolean volumetricSmoke,
        boolean shadowedVolumetrics,
        boolean temporalReprojection,
        int maxRenderWidth,
        int maxRenderHeight,
        Set<QualityTier> supportedQualityTiers
) {
    public EngineCapabilities {
        backends = backends == null ? Set.of() : Set.copyOf(backends);
        supportedQualityTiers = supportedQualityTiers == null ? Set.of() : Set.copyOf(supportedQualityTiers);
    }
}
