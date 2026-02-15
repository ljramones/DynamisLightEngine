package org.dynamislight.api;

public record PerformanceWarningEvent(String warningCode, String message) implements EngineEvent {
}
