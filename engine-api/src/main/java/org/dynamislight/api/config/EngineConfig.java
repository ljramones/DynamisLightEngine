package org.dynamislight.api.config;

import java.nio.file.Path;
import java.util.Map;

/**
 * Configuration for the engine initialization and runtime behavior.
 * This record encapsulates various parameters required to configure the engine's operation.
 *
 * @param backendId          Identifier for the rendering backend to be used.
 * @param appName            Name of the application.
 * @param initialWidthPx     The initial width of the application window in pixels.
 * @param initialHeightPx    The initial height of the application window in pixels.
 * @param dpiScale           The scaling factor for rendering, typically used for high DPI displays.
 * @param vsyncEnabled       Determines whether vertical synchronization (VSync) is enabled.
 * @param targetFps          Target frames per second the engine should aim to achieve.
 * @param qualityTier        Specifies the rendering quality level.
 * @param assetRoot          Root directory for asset loading.
 * @param backendOptions     Optional configuration options specific to the rendering backend.
 */
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
