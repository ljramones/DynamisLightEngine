package org.dynamisengine.light.impl.vulkan.command;

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
        VulkanRenderCommandRecorder.RenderPassInputs inputs = shadowInputs(12, true, false, 12, 12, 6, new long[12])
                .toRenderPassInputsShadowView();
        assertEquals(12, VulkanRenderCommandRecorder.shadowPassCount(inputs));
    }

    @Test
    void shadowPassCountKeepsPointModeAtLeastSingleCubemap() {
        VulkanRenderCommandRecorder.RenderPassInputs inputs = shadowInputs(1, true, true, 12, 12, 6, new long[12])
                .toRenderPassInputsShadowView();
        assertEquals(6, VulkanRenderCommandRecorder.shadowPassCount(inputs));
    }

    @Test
    void shadowPassCountUsesExpandedLocalLayeredPointBudget() {
        VulkanRenderCommandRecorder.RenderPassInputs inputs = shadowInputs(18, true, false, 18, 24, 6, new long[24])
                .toRenderPassInputsShadowView();
        assertEquals(18, VulkanRenderCommandRecorder.shadowPassCount(inputs));
    }

    private static VulkanRenderCommandRecorder.ShadowPassInputs shadowInputs(
            int shadowCascadeCount,
            boolean shadowEnabled,
            boolean pointShadowEnabled,
            int maxShadowMatrices,
            int maxShadowCascades,
            int pointShadowFaces,
            long[] shadowFramebuffers
    ) {
        return new VulkanRenderCommandRecorder.ShadowPassInputs(
                1,
                1L,
                1L,
                0,
                maxShadowCascades,
                1024,
                shadowEnabled,
                pointShadowEnabled,
                shadowCascadeCount,
                maxShadowMatrices,
                maxShadowCascades,
                pointShadowFaces,
                1L,
                false,
                0L,
                1L,
                1L,
                1L,
                1L,
                1L,
                shadowFramebuffers,
                0L,
                1,
                false,
                false
        );
    }
}
