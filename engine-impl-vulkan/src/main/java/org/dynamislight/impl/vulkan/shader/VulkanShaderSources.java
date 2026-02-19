package org.dynamislight.impl.vulkan.shader;

public final class VulkanShaderSources {
    private VulkanShaderSources() {
    }

    public static String shadowVertex() {
        return VulkanShadowShaderSources.shadowVertex();
    }

    public static String shadowFragment() {
        return VulkanShadowShaderSources.shadowFragment();
    }

    public static String shadowFragmentMoments() {
        return VulkanShadowShaderSources.shadowFragmentMoments();
    }

    public static String mainVertex() {
        return VulkanMainShaderSources.mainVertex();
    }

    public static String mainFragment() {
        return VulkanMainShaderSources.mainFragment();
    }

    public static String postVertex() {
        return VulkanPostShaderSources.postVertex();
    }

    public static String postFragment() {
        return VulkanPostShaderSources.postFragment();
    }
}
