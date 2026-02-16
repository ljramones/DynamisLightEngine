package org.dynamislight.api.event;

/**
 * PerformanceWarningEvent API type.
 */
public record PerformanceWarningEvent(String warningCode, String message) implements EngineEvent {
}
