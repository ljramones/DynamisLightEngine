package org.dynamislight.impl.vulkan.shader;

public final class VulkanMainShaderSources {
    private VulkanMainShaderSources() {
    }

    public static String mainVertex() {
        return VulkanMainVertexShaderSource.mainVertex();
    }

    public static String mainFragment() {
        return VulkanMainFragmentShaderSource.mainFragment();
    }
}
