package org.dynamislight.api.logging;

/**
 * Represents a log message with associated metadata.
 *
 * This record is used to encapsulate details about a logging event,
 * such as its severity level, category, message content, and the
 * timestamp when the event occurred.
 *
 * Components:
 * - level: The severity level of the log message, defined by the {@link LogLevel} enum.
 * - category: A category or identifier to group related log messages.
 * - message: The main content or description of the log message.
 * - epochMillis: The timestamp of the log event, represented as milliseconds since the Unix epoch.
 */
public record LogMessage(
        LogLevel level,
        String category,
        String message,
        long epochMillis
) {
}
