package org.dynamislight.impl.vulkan.shader;

public final class VulkanMainFragmentShaderSource {
    private VulkanMainFragmentShaderSource() {
    }

    public static String mainFragment() {
        return new StringBuilder()
                .append(VulkanMainFragmentShaderPart0.TEXT)
                .append(VulkanMainFragmentShaderPart1.TEXT)
                .append(VulkanMainFragmentShaderPart2.TEXT)
                .append(VulkanMainFragmentShaderPart3.TEXT)
                .toString();
    }
}
