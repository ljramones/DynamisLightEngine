package org.dynamisengine.light.impl.opengl;

import org.dynamisengine.light.api.runtime.EngineApiVersion;
import org.dynamisengine.light.api.runtime.EngineRuntime;
import org.dynamisengine.light.spi.EngineBackendInfo;
import org.dynamisengine.light.spi.EngineBackendProvider;

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
