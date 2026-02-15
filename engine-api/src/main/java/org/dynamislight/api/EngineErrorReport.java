package org.dynamislight.api;

public record EngineErrorReport(
        EngineErrorCode code,
        String message,
        boolean recoverable,
        Throwable cause
) {
}
