package org.dynamislight.bridge.dynamisfx;

import java.util.ServiceLoader;
import org.dynamislight.api.EngineErrorCode;
import org.dynamislight.api.EngineException;
import org.dynamislight.api.EngineRuntime;
import org.dynamislight.spi.EngineBackendProvider;

public final class DynamisFxEngineBridge {
    public EngineRuntime createRuntime(String backendId) throws EngineException {
        ServiceLoader<EngineBackendProvider> providers = ServiceLoader.load(EngineBackendProvider.class);
        for (EngineBackendProvider provider : providers) {
            if (provider.backendId().equalsIgnoreCase(backendId)) {
                return provider.createRuntime();
            }
        }
        throw new EngineException(
                EngineErrorCode.BACKEND_NOT_FOUND,
                "No backend provider found for id: " + backendId,
                true
        );
    }
}
