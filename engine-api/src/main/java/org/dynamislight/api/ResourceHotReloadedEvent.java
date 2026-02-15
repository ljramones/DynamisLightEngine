package org.dynamislight.api;

public record ResourceHotReloadedEvent(String resourceId) implements EngineEvent {
}
