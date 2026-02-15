package org.dynamislight.api;

public record LogMessage(
        LogLevel level,
        String category,
        String message,
        long epochMillis
) {
}
