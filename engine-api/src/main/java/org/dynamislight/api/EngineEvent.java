package org.dynamislight.api;

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
