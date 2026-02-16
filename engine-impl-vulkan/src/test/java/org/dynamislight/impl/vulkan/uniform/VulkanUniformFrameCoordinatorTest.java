package org.dynamislight.impl.vulkan.uniform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VulkanUniformFrameCoordinatorTest {
    @Test
    void dynamicUniformOffsetScalesByStride() {
        assertEquals(0, VulkanUniformFrameCoordinator.dynamicUniformOffset(96, 0));
        assertEquals(96, VulkanUniformFrameCoordinator.dynamicUniformOffset(96, 1));
        assertEquals(384, VulkanUniformFrameCoordinator.dynamicUniformOffset(96, 4));
    }

    @Test
    void descriptorSetForFrameUsesFallbackWhenRingEmpty() {
        assertEquals(77L, VulkanUniformFrameCoordinator.descriptorSetForFrame(new long[0], 77L, 0));
        assertEquals(77L, VulkanUniformFrameCoordinator.descriptorSetForFrame(new long[0], 77L, 15));
    }

    @Test
    void descriptorSetForFrameWrapsBothPositiveAndNegativeFrameIndices() {
        long[] ring = new long[]{10L, 20L, 30L};
        assertEquals(10L, VulkanUniformFrameCoordinator.descriptorSetForFrame(ring, 99L, 0));
        assertEquals(30L, VulkanUniformFrameCoordinator.descriptorSetForFrame(ring, 99L, 2));
        assertEquals(10L, VulkanUniformFrameCoordinator.descriptorSetForFrame(ring, 99L, 3));
        assertEquals(30L, VulkanUniformFrameCoordinator.descriptorSetForFrame(ring, 99L, -1));
    }
}
