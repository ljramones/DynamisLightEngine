package org.dynamislight.api.event;

/**
 * SceneLoadFailedEvent represents an event triggered when a scene fails to load within the engine.
 *
 * This event provides details about the failed scene and the reason for the failure. It can be used
 * by systems or modules to handle error scenarios, log failure reasons, or initiate recovery procedures.
 *
 * @param sceneName the name of the scene that failed to load.
 * @param reason a descriptive message explaining why the scene failed to load.
 */
public record SceneLoadFailedEvent(String sceneName, String reason) implements EngineEvent {
}
