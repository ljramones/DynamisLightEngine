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
    /**
     * Retrieves the API version of the engine.
     *
     * The API version specifies the major, minor, and patch levels of the engine's
     * interface. This information can be used to determine compatibility between
     * the runtime engine and the host environment.
     *
     * @return the version of the engine API as an {@code EngineApiVersion} record, which
     *         contains the major, minor, and patch version numbers.
     */
    EngineApiVersion apiVersion();

    /**
     * Initializes the engine runtime with the specified configuration and host callbacks.
     *
     * This method prepares the engine for operation by applying the provided configuration
     * and associating the host callbacks that will handle engine events, logging, and errors.
     * Initialization must be called once before using other methods of the engine runtime.
     * Re-initialization after shutdown is not supported in version 1 of the runtime.
     *
     * @param config the configuration parameters for engine setup; must not be null.
     *               Includes information such as rendering backend, application name,
     *               initial dimensions, target frame rate, DPI scale, and asset root path.
     * @param host   the callback interface implemented by the host application to handle
     *               engine lifecycle events, log messages, and errors; must not be null.
     * @throws EngineException if an error occurs during initialization, such as invalid
     *                         configuration parameters or issues*/
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
