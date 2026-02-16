package org.dynamislight.api.event;

/**
 * PerformanceWarningEvent represents an event triggered when a potential performance issue
 * is detected during the engine's runtime.
 *
 * This event is identified by a specific warning code and includes a descriptive message
 * providing details about the performance concern. It is intended to inform systems or
 * developers about runtime inefficiencies or areas that may require optimization.
 *
 * @param warningCode a unique identifier for the performance warning.
 * @param message a descriptive message providing details about the performance issue.
 */
public record PerformanceWarningEvent(String warningCode, String message) implements EngineEvent {
}
