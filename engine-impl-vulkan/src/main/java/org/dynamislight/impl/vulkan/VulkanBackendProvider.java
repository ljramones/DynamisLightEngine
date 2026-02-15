package org.dynamislight.impl.vulkan;

import org.dynamislight.api.EngineApiVersion;
import org.dynamislight.api.EngineRuntime;
import org.dynamislight.spi.EngineBackendInfo;
import org.dynamislight.spi.EngineBackendProvider;

public final class VulkanBackendProvider implements EngineBackendProvider {
    private static final EngineApiVersion API_VERSION = new EngineApiVersion(1, 0, 0);

    @Override
    public String backendId() {
        return "vulkan";
    }

    @Override
    public EngineApiVersion supportedApiVersion() {
        return API_VERSION;
    }

    @Override
    public EngineBackendInfo info() {
        return new EngineBackendInfo(
                backendId(),
                "Vulkan",
                "0.1.0-SNAPSHOT",
                "Stub Vulkan backend for DynamicLightEngine"
        );
    }

    @Override
    public EngineRuntime createRuntime() {
        return new VulkanEngineRuntime();
    }
}
