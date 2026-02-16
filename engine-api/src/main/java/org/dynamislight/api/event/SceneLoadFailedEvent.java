package org.dynamislight.api.event;

/**
 * SceneLoadFailedEvent API type.
 */
public record SceneLoadFailedEvent(String sceneName, String reason) implements EngineEvent {
}
