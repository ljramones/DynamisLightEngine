package org.dynamislight.api;

/**
 * ResourceHotReloadedEvent API type.
 */
public record ResourceHotReloadedEvent(String resourceId) implements EngineEvent {
}
