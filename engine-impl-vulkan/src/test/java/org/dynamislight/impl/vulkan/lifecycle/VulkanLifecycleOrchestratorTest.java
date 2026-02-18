package org.dynamislight.impl.vulkan.lifecycle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.dynamislight.impl.vulkan.command.VulkanFrameSyncLifecycleCoordinator;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowLifecycleCoordinator;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamislight.impl.vulkan.state.VulkanRenderState;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainLifecycleCoordinator;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkCommandBuffer;

class VulkanLifecycleOrchestratorTest {
    @Test
    void applySwapchainStateCopiesResourceAndRenderFields() {
        var backend = new VulkanBackendResources();
        var render = new VulkanRenderState();
        long[] swapchainImages = new long[]{101L, 102L};
        long[] framebuffers = new long[]{301L};
        long[] postFramebuffers = new long[]{401L};
        var state = new VulkanSwapchainLifecycleCoordinator.State(
                11L, 44, 1920, 1080,
                swapchainImages, new long[]{201L}, new long[]{202L}, new long[]{203L}, new long[]{204L},
                12L, 13L, 14L,
                25L, 26L, 27L,
                framebuffers, true,
                15L, 16L, 17L, 18L,
                19L, 20L, 21L, 22L,
                28L, 29L, 30L, 31L,
                36L, 37L, 38L, 39L,
                32L, 33L, 34L,
                23L, 24L, 35L,
                postFramebuffers, true, true
        );

        VulkanLifecycleOrchestrator.applySwapchainState(backend, render, state);

        assertEquals(11L, backend.swapchain);
        assertEquals(44, backend.swapchainImageFormat);
        assertEquals(1920, backend.swapchainWidth);
        assertEquals(1080, backend.swapchainHeight);
        assertSame(swapchainImages, backend.swapchainImages);
        assertSame(framebuffers, backend.framebuffers);
        assertSame(postFramebuffers, backend.postFramebuffers);
        assertEquals(36L, backend.postPlanarCaptureImage);
        assertEquals(37L, backend.postPlanarCaptureMemory);
        assertEquals(38L, backend.postPlanarCaptureImageView);
        assertEquals(39L, backend.postPlanarCaptureSampler);
        assertEquals(35L, backend.postGraphicsPipeline);
        assertEquals(true, render.postOffscreenActive);
        assertEquals(true, render.postIntermediateInitialized);
    }

    @Test
    void applyShadowStateCopiesAllShadowHandles() {
        var backend = new VulkanBackendResources();
        long[] layerViews = new long[]{7L, 8L};
        long[] framebuffers = new long[]{9L};
        long[] momentLayerViews = new long[]{15L, 16L};
        var state = new VulkanShadowLifecycleCoordinator.State(
                1L, 2L, 3L, layerViews, 4L, 5L, 6L, 10L, framebuffers,
                11L, 12L, 13L, momentLayerViews, 14L, 97, 6
        );

        VulkanLifecycleOrchestrator.applyShadowState(backend, state);

        assertEquals(1L, backend.shadowDepthImage);
        assertEquals(2L, backend.shadowDepthMemory);
        assertEquals(3L, backend.shadowDepthImageView);
        assertSame(layerViews, backend.shadowDepthLayerImageViews);
        assertEquals(4L, backend.shadowSampler);
        assertEquals(5L, backend.shadowRenderPass);
        assertEquals(6L, backend.shadowPipelineLayout);
        assertEquals(10L, backend.shadowPipeline);
        assertSame(framebuffers, backend.shadowFramebuffers);
        assertEquals(11L, backend.shadowMomentImage);
        assertEquals(12L, backend.shadowMomentMemory);
        assertEquals(13L, backend.shadowMomentImageView);
        assertSame(momentLayerViews, backend.shadowMomentLayerImageViews);
        assertEquals(14L, backend.shadowMomentSampler);
        assertEquals(97, backend.shadowMomentFormat);
        assertEquals(6, backend.shadowMomentMipLevels);
    }

    @Test
    void applyFrameSyncStateCopiesSyncResources() {
        var backend = new VulkanBackendResources();
        VkCommandBuffer[] commandBuffers = new VkCommandBuffer[0];
        long[] imageAvailable = new long[]{1L, 2L};
        long[] renderFinished = new long[]{3L, 4L};
        long[] fences = new long[]{5L, 6L};
        var state = new VulkanFrameSyncLifecycleCoordinator.State(99L, commandBuffers, imageAvailable, renderFinished, fences, 2);

        VulkanLifecycleOrchestrator.applyFrameSyncState(backend, state);

        assertEquals(99L, backend.commandPool);
        assertSame(commandBuffers, backend.commandBuffers);
        assertArrayEquals(imageAvailable, backend.imageAvailableSemaphores);
        assertArrayEquals(renderFinished, backend.renderFinishedSemaphores);
        assertArrayEquals(fences, backend.renderFences);
        assertEquals(2, backend.currentFrame);
    }
}
