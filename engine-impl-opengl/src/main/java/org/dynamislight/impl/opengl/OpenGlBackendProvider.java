package org.dynamislight.impl.opengl;

import org.dynamislight.api.EngineApiVersion;
import org.dynamislight.api.EngineRuntime;
import org.dynamislight.spi.EngineBackendInfo;
import org.dynamislight.spi.EngineBackendProvider;

public final class OpenGlBackendProvider implements EngineBackendProvider {
    private static final EngineApiVersion API_VERSION = new EngineApiVersion(1, 0, 0);

    @Override
    public String backendId() {
        return "opengl";
    }

    @Override
    public EngineApiVersion supportedApiVersion() {
        return API_VERSION;
    }

    @Override
    public EngineBackendInfo info() {
        return new EngineBackendInfo(
                backendId(),
                "OpenGL",
                "0.1.0-SNAPSHOT",
                "Reference OpenGL backend for DynamicLightEngine"
        );
    }

    @Override
    public EngineRuntime createRuntime() {
        return new OpenGlEngineRuntime();
    }
}
