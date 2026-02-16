package org.dynamislight.api.event;

/**
 * SceneLoadedEvent represents an event triggered when a scene is successfully loaded within the engine.
 *
 * This event encapsulates the name of the loaded scene, which can be used by systems or modules
 * for post-loading operations, logging, or triggering dependent workflows.
 *
 * @param sceneName the name of the scene that was successfully loaded.
 */
public record SceneLoadedEvent(String sceneName) implements EngineEvent {
}
