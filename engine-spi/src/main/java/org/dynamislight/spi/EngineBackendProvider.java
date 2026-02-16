package org.dynamislight.spi;

import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.runtime.EngineRuntime;

public interface EngineBackendProvider {
    String backendId();

    EngineApiVersion supportedApiVersion();

    EngineBackendInfo info();

    EngineRuntime createRuntime();
}
