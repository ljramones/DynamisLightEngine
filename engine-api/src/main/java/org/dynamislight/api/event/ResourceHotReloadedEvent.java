package org.dynamislight.api.event;

/**
 * ResourceHotReloadedEvent API type.
 */
public record ResourceHotReloadedEvent(String resourceId) implements EngineEvent {
}
