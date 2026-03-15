package org.dynamisengine.light.bridge.dynamisfx;

import org.dynamisengine.light.api.runtime.EngineApiVersion;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.api.runtime.EngineRuntime;
import org.dynamisengine.light.spi.registry.BackendRegistry;

public final class DynamisFxEngineBridge {
    private static final EngineApiVersion HOST_REQUIRED_API = new EngineApiVersion(1, 0, 0);

    public EngineRuntime createRuntime(String backendId) throws EngineException {
        return BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API).createRuntime();
    }
}
