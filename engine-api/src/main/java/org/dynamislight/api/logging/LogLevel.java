package org.dynamislight.api.logging;

/**
 * Defines the log levels used for categorizing and filtering log messages.
 *
 * Log levels represent the severity or importance of a log entry and can be
 * used to determine the type of information conveyed. Common usage includes
 * filtering messages based on the selected log level or reporting critical
 * runtime events.
 *
 * Available log levels:
 * - TRACE: The most detailed logging level, typically used for diagnostic purposes.
 * - DEBUG: Logs fine-grained informational events used for debugging.
 * - INFO: Logs informational messages representing standard application behavior.
 * - WARN: Logs potentially harmful situations or warnings that require attention.
 * - ERROR: Logs error events that might allow the application to continue running.
 */
public enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}
