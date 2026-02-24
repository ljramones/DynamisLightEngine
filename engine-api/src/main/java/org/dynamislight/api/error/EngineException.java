package org.dynamislight.api.error;

import org.dynamisgpu.api.error.GpuErrorCode;
import org.dynamisgpu.api.error.GpuException;

/**
 * Represents an exception specific to engine-related errors, providing additional
 * context about the error type and its recoverability.
 *
 * This exception is typically thrown when an operation within the engine encounters
 * a failure condition that matches a predefined error code.
 *
 * Components:
 * - code: An {@link EngineErrorCode} instance categorizing the specific type of failure.
 * - recoverable: A flag indicating whether the error is recoverable, which can inform
 *                the application or engine mechanism whether recovery steps are possible.
 *
 * Constructors:
 * - EngineException(EngineErrorCode code, String message, boolean recoverable):
 *   Creates an instance of {@code EngineException} with the specified error code,
 *   descriptive message, and recoverability flag.
 *
 * Methods:
 * - {@link #code()}: Returns the error code associated with this exception.
 * - {@link #recoverable()}: Indicates whether this error is recoverable.
 */
public final class EngineException extends GpuException {
    private final EngineErrorCode code;
    private final boolean recoverable;

    public EngineException(EngineErrorCode code, String message, boolean recoverable) {
        super(mapGpuCode(code), message, recoverable);
        this.code = code;
        this.recoverable = recoverable;
    }

    private static GpuErrorCode mapGpuCode(EngineErrorCode code) {
        if (code == null) {
            return GpuErrorCode.INVALID_ARGUMENT;
        }
        return switch (code) {
            case BACKEND_INIT_FAILED -> GpuErrorCode.BACKEND_INIT_FAILED;
            case INVALID_ARGUMENT -> GpuErrorCode.INVALID_ARGUMENT;
            default -> GpuErrorCode.INVALID_ARGUMENT;
        };
    }

    public EngineErrorCode code() {
        return code;
    }

    public boolean recoverable() {
        return recoverable;
    }
}
