package org.dynamislight.api.logging;

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
