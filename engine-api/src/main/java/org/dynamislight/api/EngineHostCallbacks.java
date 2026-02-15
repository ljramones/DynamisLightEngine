package org.dynamislight.api;

public interface EngineHostCallbacks {
    void onEvent(EngineEvent event);

    void onLog(LogMessage message);

    void onError(EngineErrorReport error);

    default void onFrameReady(FrameHandle frame) {
        // Optional callback for embedding/preview workflows.
    }
}
