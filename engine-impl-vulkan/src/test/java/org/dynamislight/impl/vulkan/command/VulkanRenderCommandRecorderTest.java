package org.dynamislight.impl.vulkan.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VulkanRenderCommandRecorderTest {
    @Test
    void barrierTraceHookInstallsAndClears() {
        VulkanRuntimeBarrierTrace trace = new VulkanRuntimeBarrierTrace();
        VulkanRenderCommandRecorder.installBarrierTrace(trace);
        assertEquals(true, VulkanRenderCommandRecorder.barrierTraceInstalled());
        VulkanRenderCommandRecorder.clearBarrierTrace();
        assertEquals(false, VulkanRenderCommandRecorder.barrierTraceInstalled());
    }

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
                new long[12],
                0L,
                1,
                false,
                false,
                0,
                0L,
                -1,
                -1,
                false,
                0L,
                0L,
                0f
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
                new long[12],
                0L,
                1,
                false,
                false,
                0,
                0L,
                -1,
                -1,
                false,
                0L,
                0L,
                0f
        );
        assertEquals(6, VulkanRenderCommandRecorder.shadowPassCount(inputs));
    }

    @Test
    void shadowPassCountUsesExpandedLocalLayeredPointBudget() {
        VulkanRenderCommandRecorder.RenderPassInputs inputs = new VulkanRenderCommandRecorder.RenderPassInputs(
                1,
                1280,
                720,
                1024,
                true,
                false,
                18,
                24,
                24,
                6,
                1L,
                1L,
                1L,
                1L,
                1L,
                1L,
                1L,
                1L,
                new long[24],
                0L,
                1,
                false,
                false,
                0,
                0L,
                -1,
                -1,
                false,
                0L,
                0L,
                0f
        );
        assertEquals(18, VulkanRenderCommandRecorder.shadowPassCount(inputs));
    }
}
