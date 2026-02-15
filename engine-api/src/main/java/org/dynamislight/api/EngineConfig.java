package org.dynamislight.api;

import java.nio.file.Path;
import java.util.Map;

public record EngineConfig(
        String backendId,
        String appName,
        int initialWidthPx,
        int initialHeightPx,
        float dpiScale,
        boolean vsyncEnabled,
        int targetFps,
        QualityTier qualityTier,
        Path assetRoot,
        Map<String, String> backendOptions
) {
    public EngineConfig {
        backendOptions = backendOptions == null ? Map.of() : Map.copyOf(backendOptions);
    }
}
