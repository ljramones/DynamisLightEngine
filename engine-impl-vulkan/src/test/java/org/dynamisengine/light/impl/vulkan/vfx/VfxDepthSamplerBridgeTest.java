package org.dynamisengine.light.impl.vulkan.vfx;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

class VfxDepthSamplerBridgeTest {

    @Test
    void depthTransitionBarrierIsRecorded() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var barrier = VulkanVfxDepthSamplerBridge.depthBarrier(
                    stack,
                    42L,
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            );
            assertEquals(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, barrier.get(0).oldLayout());
            assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, barrier.get(0).newLayout());
            assertEquals(42L, barrier.get(0).image());
        }
    }

    @Test
    void depthDescriptorWritesToCorrectBinding() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var write = VulkanVfxDepthSamplerBridge.depthDescriptorWrite(stack, 11L, 22L, 33L);
            assertEquals(11L, write.get(0).dstSet());
            assertEquals(4, write.get(0).dstBinding());
            assertEquals(1, write.get(0).descriptorCount());
        }
    }

    @Test
    void transitionAfterRestoresLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var barrier = VulkanVfxDepthSamplerBridge.depthBarrier(
                    stack,
                    84L,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
            );
            assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, barrier.get(0).oldLayout());
            assertEquals(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, barrier.get(0).newLayout());
            assertEquals(84L, barrier.get(0).image());
        }
    }
}
