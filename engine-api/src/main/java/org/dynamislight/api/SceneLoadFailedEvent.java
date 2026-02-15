package org.dynamislight.api;

public record SceneLoadFailedEvent(String sceneName, String reason) implements EngineEvent {
}
