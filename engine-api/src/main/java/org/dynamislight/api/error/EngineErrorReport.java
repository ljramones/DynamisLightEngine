package org.dynamislight.api.error;

/**
 * Represents a detailed report of an engine-related error, providing information about
 * the nature of the error, its recoverability, and any underlying cause.
 *
 * This record is typically used to capture and communicate errors occurring within the
 * engine's operation, aiding in diagnostics and error handling workflows.
 *
 * Components:
 * - code: The specific error code categorizing the type of engine failure. Refer to {@code EngineErrorCode} for possible values.
 * - message: A descriptive message providing additional detail about the error context.
 * - recoverable: Indicates whether the error condition is recoverable, allowing the engine or application to potentially continue operation.
 * - cause: An optional {@code Throwable} representing the underlying cause for the error, if available.
 */
public record EngineErrorReport(
        EngineErrorCode code,
        String message,
        boolean recoverable,
        Throwable cause
) {
}
