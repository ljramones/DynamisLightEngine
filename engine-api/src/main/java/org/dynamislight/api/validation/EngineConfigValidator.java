package org.dynamislight.api.validation;

import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;

/**
 * Validator for host-provided runtime configuration.
 */
public final class EngineConfigValidator {
    private EngineConfigValidator() {
    }

    public static void validate(EngineConfig config) throws EngineException {
        if (config == null) {
            throw invalid("config is required");
        }
        if (isBlank(config.backendId())) {
            throw invalid("backendId is required");
        }
        if (isBlank(config.appName())) {
            throw invalid("appName is required");
        }
        if (config.initialWidthPx() <= 0 || config.initialHeightPx() <= 0) {
            throw invalid("initial dimensions must be > 0");
        }
        if (config.dpiScale() <= 0f) {
            throw invalid("dpiScale must be > 0");
        }
        if (config.targetFps() <= 0) {
            throw invalid("targetFps must be > 0");
        }
        if (config.qualityTier() == null) {
            throw invalid("qualityTier is required");
        }
        if (config.assetRoot() == null) {
            throw invalid("assetRoot is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static EngineException invalid(String message) {
        return new EngineException(EngineErrorCode.INVALID_ARGUMENT, message, true);
    }
}
