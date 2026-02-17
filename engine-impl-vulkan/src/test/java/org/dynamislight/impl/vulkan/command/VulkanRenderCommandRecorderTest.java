package org.dynamislight.impl.vulkan.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VulkanRenderCommandRecorderTest {
    @Test
    void shadowPassCountUsesConfiguredCascadeCountForLocalLayeredPath() {
        VulkanRenderCommandRecorder.RenderPassInputs inputs = new VulkanRenderCommandRecorder.RenderPassInputs(
                1,
                1280,
                720,
                1024,
                true,
                false,
                12,
                12,
                12,
                6,
                1L,
                1L,
                1L,
                1L,
                1L,
                1L,
                1L,
                1L,
                new long[12]
        );
        assertEquals(12, VulkanRenderCommandRecorder.shadowPassCount(inputs));
    }

    @Test
    void shadowPassCountKeepsPointModeAtLeastSingleCubemap() {
        VulkanRenderCommandRecorder.RenderPassInputs inputs = new VulkanRenderCommandRecorder.RenderPassInputs(
                1,
                1280,
                720,
                1024,
                true,
                true,
                1,
                12,
                12,
                6,
                1L,
                1L,
                1L,
                1L,
                1L,
                1L,
                1L,
                1L,
                new long[12]
        );
        assertEquals(6, VulkanRenderCommandRecorder.shadowPassCount(inputs));
    }
}
