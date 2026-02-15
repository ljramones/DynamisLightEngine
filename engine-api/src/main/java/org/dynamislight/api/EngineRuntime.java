package org.dynamislight.api;

public interface EngineRuntime extends AutoCloseable {
    EngineApiVersion apiVersion();

    void initialize(EngineConfig config, EngineHostCallbacks host) throws EngineException;

    void loadScene(SceneDescriptor scene) throws EngineException;

    EngineFrameResult update(double dtSeconds, EngineInput input) throws EngineException;

    EngineFrameResult render() throws EngineException;

    void resize(int widthPx, int heightPx, float dpiScale) throws EngineException;

    EngineStats getStats();

    EngineCapabilities getCapabilities();

    void shutdown();

    @Override
    default void close() {
        shutdown();
    }
}
