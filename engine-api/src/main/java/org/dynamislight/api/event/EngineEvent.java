package org.dynamislight.api.event;

/**
 * EngineEvent serves as a sealed interface that represents various types of events
 * occurring within the engine's lifecycle or runtime operation.
 *
 * The interface is implemented by specific event types to encapsulate detailed
 * information about a particular event. These events include, but are not limited to:
 *
 * - Scene loading and related success or failure events.
 * - Resource hot reloading to reflect runtime changes.
 * - Device loss, indicating unavailable hardware or backend systems.
 * - Performance warnings to highlight potential runtime inefficiencies.
 */
public sealed interface EngineEvent permits
        SceneLoadedEvent,
        SceneLoadFailedEvent,
        ResourceHotReloadedEvent,
        DeviceLostEvent,
        AaTelemetryEvent,
        PerformanceWarningEvent {
}
