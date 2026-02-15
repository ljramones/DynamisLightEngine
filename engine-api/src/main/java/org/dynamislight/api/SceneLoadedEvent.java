package org.dynamislight.api;

/**
 * SceneLoadedEvent API type.
 */
public record SceneLoadedEvent(String sceneName) implements EngineEvent {
}
