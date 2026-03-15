package org.dynamisengine.light.impl.vulkan.model;

public record VulkanGpuTexture(long image, long memory, long view, long sampler, long bytes) {
}
