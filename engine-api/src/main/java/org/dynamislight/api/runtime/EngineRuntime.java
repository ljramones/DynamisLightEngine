package org.dynamislight.api.runtime;

import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.resource.EngineResourceService;
import org.dynamislight.api.scene.SceneDescriptor;

/**
 * Core runtime contract used by hosts to drive the engine as a black box.
 *
 * <p>Lifecycle order is strict: {@code initialize()} once, then any number of
 * {@code loadScene()/update()/render()/resize()} calls, then {@code shutdown()}.
 * Re-initialization after shutdown is not supported in v1.</p>
 *
 * <p>Threading contract (v1): all methods are called from one designated engine
 * thread. Calls are not reentrant. Callback handlers must not synchronously call
 * back into this runtime.</p>
 */
public interface EngineRuntime extends AutoCloseable {
    EngineApiVersion apiVersion();

    void initialize(EngineConfig config, EngineHostCallbacks host) throws EngineException;

    void loadScene(SceneDescriptor scene) throws EngineException;

    EngineFrameResult update(double dtSeconds, EngineInput input) throws EngineException;

    EngineFrameResult render() throws EngineException;

    void resize(int widthPx, int heightPx, float dpiScale) throws EngineException;

    EngineStats getStats();

    EngineCapabilities getCapabilities();

    /**
     * Returns runtime resource service for cache/ownership/hot-reload workflows.
     */
    default EngineResourceService resources() {
        throw new UnsupportedOperationException("Resource service is not available for this runtime");
    }

    void shutdown();

    @Override
    default void close() {
        shutdown();
    }
}
