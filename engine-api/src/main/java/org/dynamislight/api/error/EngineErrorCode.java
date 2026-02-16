package org.dynamislight.api.error;

/**
 * Represents a set of predefined error codes used to classify engine-related errors.
 * This enum is part of the error-handling mechanism in the engine's API and is referenced
 * by classes such as {@code EngineException} and {@code EngineErrorReport}.
 *
 * Each constant identifies a specific category of errors or failure scenarios that
 * may occur during the engine's operation.
 *
 * Enum Constants:
 * - INVALID_STATE: Indicates that an operation was attempted in an invalid or inconsistent state.
 * - INVALID_ARGUMENT: Signals that an invalid parameter was provided to a function or operation.
 * - BACKEND_NOT_FOUND: Indicates that the specified backend could not be located or loaded.
 * - BACKEND_INIT_FAILED: Represents a failure during the initialization of the backend.
 * - SHADER_COMPILATION_FAILED: Indicates that shader compilation was unsuccessful.
 * - RESOURCE_CREATION_FAILED: Signals that a resource (e.g., a texture or buffer) could not be created.
 * - SCENE_VALIDATION_FAILED: Represents an error in validating the structure or configuration of a scene.
 * - OUT_OF_MEMORY: Indicates a failure due to insufficient memory being available.
 * - DEVICE_LOST: Represents a hardware or driver-related issue resulting in a lost device context.
 * - INTERNAL_ERROR: A generic error representing an unexpected issue within the engine.
 */
public enum EngineErrorCode {
    INVALID_STATE,
    INVALID_ARGUMENT,
    BACKEND_NOT_FOUND,
    BACKEND_INIT_FAILED,
    SHADER_COMPILATION_FAILED,
    RESOURCE_CREATION_FAILED,
    SCENE_VALIDATION_FAILED,
    OUT_OF_MEMORY,
    DEVICE_LOST,
    INTERNAL_ERROR
}
