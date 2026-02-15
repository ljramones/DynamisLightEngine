package org.dynamislight.api;

/**
 * Host callback sink for runtime events, logs, and errors.
 *
 * <p>Callbacks may run on the engine thread unless a backend documents a
 * different dispatch model. Implementations must be non-blocking and must not
 * synchronously call runtime methods (reentrancy is forbidden).</p>
 */
public interface EngineHostCallbacks {
    void onEvent(EngineEvent event);

    void onLog(LogMessage message);

    void onError(EngineErrorReport error);

    default void onFrameReady(FrameHandle frame) {
        // Optional callback for embedding/preview workflows.
    }
}
