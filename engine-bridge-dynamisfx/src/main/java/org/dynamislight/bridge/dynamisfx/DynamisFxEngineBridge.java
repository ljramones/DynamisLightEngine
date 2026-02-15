package org.dynamislight.bridge.dynamisfx;

import org.dynamislight.api.EngineApiVersion;
import org.dynamislight.api.EngineException;
import org.dynamislight.api.EngineRuntime;
import org.dynamislight.spi.registry.BackendRegistry;

public final class DynamisFxEngineBridge {
    private static final EngineApiVersion HOST_REQUIRED_API = new EngineApiVersion(1, 0, 0);

    public EngineRuntime createRuntime(String backendId) throws EngineException {
        return BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API).createRuntime();
    }
}
