package org.dynamislight.api;

/**
 * SceneLoadFailedEvent API type.
 */
public record SceneLoadFailedEvent(String sceneName, String reason) implements EngineEvent {
}
