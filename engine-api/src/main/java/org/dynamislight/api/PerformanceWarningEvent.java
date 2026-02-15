package org.dynamislight.api;

/**
 * PerformanceWarningEvent API type.
 */
public record PerformanceWarningEvent(String warningCode, String message) implements EngineEvent {
}
