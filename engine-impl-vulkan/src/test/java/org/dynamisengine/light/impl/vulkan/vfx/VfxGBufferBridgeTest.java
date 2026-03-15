package org.dynamisengine.light.impl.vulkan.vfx;

import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.impl.vulkan.state.VulkanBackendResources;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

class VfxGBufferBridgeTest {

    @Test
    void normalTransitionBarrierIsRecorded() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var barrier = VulkanVfxGBufferBridge.normalBarrier(
                    stack,
                    77L,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            );
            assertEquals(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, barrier.get(0).oldLayout());
            assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, barrier.get(0).newLayout());
            assertEquals(77L, barrier.get(0).image());
        }
    }

    @Test
    void normalDescriptorWritesToCorrectBinding() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var write = VulkanVfxGBufferBridge.normalDescriptorWrite(stack, 5L, 6L, 7L);
            assertEquals(5L, write.get(0).dstSet());
            assertEquals(6, write.get(0).dstBinding());
            assertEquals(1, write.get(0).descriptorCount());
        }
    }

    @Test
    void hasActiveDecalsReturnsFalseWhenNone() throws EngineException {
        VulkanVfxIntegration integration = VulkanVfxIntegration.create(null, new VulkanBackendResources());
        assertFalse(integration.hasActiveDecals());
    }

    @Test
    void transitionSkippedWhenNoDecals() {
        assertDoesNotThrow(() -> VulkanVfxGBufferBridge.transitionNormalForVfxRead(null, 0L));
        assertDoesNotThrow(() -> VulkanVfxGBufferBridge.transitionNormalAfterVfxRead(null, 0L));
    }
}
