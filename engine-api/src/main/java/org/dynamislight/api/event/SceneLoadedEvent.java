package org.dynamislight.api.event;

/**
 * SceneLoadedEvent API type.
 */
public record SceneLoadedEvent(String sceneName) implements EngineEvent {
}
