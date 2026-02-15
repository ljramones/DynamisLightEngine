package org.dynamislight.api;

public sealed interface EngineEvent permits
        SceneLoadedEvent,
        SceneLoadFailedEvent,
        ResourceHotReloadedEvent,
        DeviceLostEvent,
        PerformanceWarningEvent {
}
