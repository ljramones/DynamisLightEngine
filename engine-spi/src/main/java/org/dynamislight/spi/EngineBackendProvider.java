package org.dynamislight.spi;

import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.runtime.EngineRuntime;

/**
 * Represents a contract for providing an engine backend implementation.
 * Implementations of this interface supply metadata about the backend,
 * version compatibility, and runtime initialization capabilities.
 */
public interface EngineBackendProvider {
    /**
     * Returns the unique identifier of the backend implementation.
     * The backend ID is used to distinguish different engine backend providers
     * and facilitate lookup and selection within the registry.
     *
     * @return the unique identifier of the backend, typically a lowercase string.
     */
    String backendId();

    /**
     * Returns the engine API version supported by the backend implementation.
     * The version indicates the major, minor, and patch levels of API compatibility.
     *
     * @return the supported {@link EngineApiVersion} of this backend.
     */
    EngineApiVersion supportedApiVersion();

    /**
     * Provides metadata information about the engine backend implementation.
     * The returned information includes details such as the backend ID, display name,
     * version, and a description of the backend.
     *
     * @return an {@link EngineBackendInfo} instance containing the metadata for the backend.
     */
    EngineBackendInfo info();

    /**
     * Creates and initializes an instance of the engine runtime for this backend implementation.
     * The returned runtime serves as the main interface between the host and the engine,
     * enabling lifecycle control, scene management, rendering, and other core operations.
     *
     * @return a new instance of {@link EngineRuntime} that adheres to the lifecycle and threading
     *         contracts defined by the engine framework.
     */
    EngineRuntime createRuntime();
}
