package org.dynamislight.api.validation;

import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;

/**
 * Utility class for validating {@link EngineConfig} instances to ensure they meet the
 * necessary criteria for proper engine initialization.
 *
 * This class provides a single static method for validation and performs various checks
 * on the configuration object's fields. It ensures that mandatory fields are not null
 * or empty and that numeric values fall within acceptable ranges.
 *
 * Validation Rules:
 * - The configuration object itself must not be null.
 * - The backend ID must not be null or blank.
 * - The application name must not be null or blank.
 * - The initial dimensions (width and height) must be greater than 0.
 * - The DPI scale must be greater than 0.
 * - The target FPS must be greater than 0.
 * - The quality tier must not be null.
 * - The asset root path must not be null.
 *
 * If any validation check fails, an {@link EngineException} is thrown with the appropriate error message.
 *
 * This class is designed as a utility and cannot be instantiated.
 * All methods provided are static and thread-safe.
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
