package org.dynamislight.api.event;

/**
 * EngineEvent API type.
 */
public sealed interface EngineEvent permits
        SceneLoadedEvent,
        SceneLoadFailedEvent,
        ResourceHotReloadedEvent,
        DeviceLostEvent,
        PerformanceWarningEvent {
}
