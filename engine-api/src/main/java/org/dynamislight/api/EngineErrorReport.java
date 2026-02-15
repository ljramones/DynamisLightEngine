package org.dynamislight.api;

/**
 * EngineErrorReport API type.
 */
public record EngineErrorReport(
        EngineErrorCode code,
        String message,
        boolean recoverable,
        Throwable cause
) {
}
