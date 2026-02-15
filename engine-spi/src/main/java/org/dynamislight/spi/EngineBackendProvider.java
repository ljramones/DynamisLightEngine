package org.dynamislight.spi;

import org.dynamislight.api.EngineApiVersion;
import org.dynamislight.api.EngineRuntime;

public interface EngineBackendProvider {
    String backendId();

    EngineApiVersion supportedApiVersion();

    EngineBackendInfo info();

    EngineRuntime createRuntime();
}
