package org.dynamislight.api;

/**
 * LogMessage API type.
 */
public record LogMessage(
        LogLevel level,
        String category,
        String message,
        long epochMillis
) {
}
