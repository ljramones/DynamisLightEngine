package org.dynamislight.api;

public record SceneLoadedEvent(String sceneName) implements EngineEvent {
}
