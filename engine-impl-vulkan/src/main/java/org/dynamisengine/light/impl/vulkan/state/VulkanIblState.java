package org.dynamisengine.light.impl.vulkan.state;

import java.nio.file.Path;

import org.dynamisengine.light.impl.vulkan.model.VulkanGpuTexture;

public final class VulkanIblState {
    public boolean enabled;
    public float diffuseStrength;
    public float specularStrength;
    public float prefilterStrength;

    public Path irradiancePath;
    public Path radiancePath;
    public Path brdfLutPath;

    public VulkanGpuTexture irradianceTexture;
    public VulkanGpuTexture radianceTexture;
    public VulkanGpuTexture brdfLutTexture;
    public VulkanGpuTexture probeRadianceTexture;
}
