package org.dynamislight.api.event;

/**
 * EngineWarning is a record representing a warning related to the engine's operation.
 *
 * This record encapsulates details of an engine warning identified by a specific
 * warning code and an associated message providing further context or description
 * of the issue.
 *
 * It is intended to provide lightweight and immutable representations of warnings
 * that can be used for logging, diagnostics, or runtime handling of warning
 * conditions in the engine.
 *
 * @param code a unique identifier for the warning.
 * @param message a descriptive message providing details about the warning.
 */
public record EngineWarning(String code, String message) {
}
