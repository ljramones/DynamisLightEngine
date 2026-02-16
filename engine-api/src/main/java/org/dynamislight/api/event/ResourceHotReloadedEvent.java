package org.dynamislight.api.event;

/**
 * ResourceHotReloadedEvent represents an event triggered when a resource has been reloaded during runtime.
 *
 * This event provides information about the identifier of the resource that was reloaded, enabling systems
 * handling this event to take appropriate actions, such as updating references or refreshing dependent data.
 *
 * @param resourceId the unique identifier of the resource that was reloaded.
 */
public record ResourceHotReloadedEvent(String resourceId) implements EngineEvent {
}
