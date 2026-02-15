package org.dynamislight.api;

public record DeviceLostEvent(String backendId) implements EngineEvent {
}
