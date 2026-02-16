package org.dynamislight.impl.vulkan.model;

import java.nio.ByteBuffer;

public record VulkanTexturePixelData(ByteBuffer data, int width, int height) {
}
