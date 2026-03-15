package org.dynamisengine.light.impl.vulkan.bootstrap;

import org.dynamisengine.light.impl.vulkan.VulkanEngineRuntime;

import org.dynamisengine.light.api.runtime.EngineApiVersion;
import org.dynamisengine.light.api.runtime.EngineRuntime;
import org.dynamisengine.light.spi.EngineBackendInfo;
import org.dynamisengine.light.spi.EngineBackendProvider;

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
