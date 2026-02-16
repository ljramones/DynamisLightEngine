package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

public final class VulkanCommandSubmitter {
    private VulkanCommandSubmitter() {
    }

    @FunctionalInterface
    public interface FrameRecorder {
        void record(int imageIndex) throws EngineException;
    }

    public static int acquireRecordSubmitPresent(
            MemoryStack stack,
            VkDevice device,
            VkQueue graphicsQueue,
            long swapchain,
            VkCommandBuffer commandBuffer,
            long imageAvailableSemaphore,
            long renderFinishedSemaphore,
            long renderFence,
            FrameRecorder recorder
    ) throws EngineException {
        var pImageIndex = stack.ints(0);
        int acquireResult = vkAcquireNextImageKHR(device, swapchain, Long.MAX_VALUE, imageAvailableSemaphore, VK10.VK_NULL_HANDLE, pImageIndex);
        if (acquireResult == VK_SUCCESS || acquireResult == VK_SUBOPTIMAL_KHR) {
            int imageIndex = pImageIndex.get(0);
            int waitResult = vkWaitForFences(device, stack.longs(renderFence), true, Long.MAX_VALUE);
            if (waitResult != VK_SUCCESS) {
                throw vkFailure("vkWaitForFences", waitResult);
            }
            int resetFenceResult = vkResetFences(device, stack.longs(renderFence));
            if (resetFenceResult != VK_SUCCESS) {
                throw vkFailure("vkResetFences", resetFenceResult);
            }
            int resetCmdResult = vkResetCommandBuffer(commandBuffer, 0);
            if (resetCmdResult != VK_SUCCESS) {
                throw vkFailure("vkResetCommandBuffer", resetCmdResult);
            }

            recorder.record(imageIndex);
            return submitAndPresent(
                    stack,
                    graphicsQueue,
                    swapchain,
                    commandBuffer,
                    imageIndex,
                    imageAvailableSemaphore,
                    renderFinishedSemaphore,
                    renderFence
            );
        }
        if (acquireResult != VK_ERROR_OUT_OF_DATE_KHR) {
            throw vkFailure("vkAcquireNextImageKHR", acquireResult);
        }
        return acquireResult;
    }

    public static int submitAndPresent(
            MemoryStack stack,
            VkQueue graphicsQueue,
            long swapchain,
            VkCommandBuffer commandBuffer,
            int imageIndex,
            long imageAvailableSemaphore,
            long renderFinishedSemaphore,
            long renderFence
    ) throws EngineException {
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                .sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pWaitSemaphores(stack.longs(imageAvailableSemaphore))
                .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                .pCommandBuffers(stack.pointers(commandBuffer.address()))
                .pSignalSemaphores(stack.longs(renderFinishedSemaphore));
        int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, renderFence);
        if (submitResult != VK_SUCCESS) {
            throw vkFailure("vkQueueSubmit", submitResult);
        }

        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                .pWaitSemaphores(stack.longs(renderFinishedSemaphore))
                .swapchainCount(1)
                .pSwapchains(stack.longs(swapchain))
                .pImageIndices(stack.ints(imageIndex));
        int presentResult = vkQueuePresentKHR(graphicsQueue, presentInfo);
        glfwPollEvents();
        if (presentResult != VK_SUCCESS && presentResult != VK_SUBOPTIMAL_KHR && presentResult != VK_ERROR_OUT_OF_DATE_KHR) {
            throw vkFailure("vkQueuePresentKHR", presentResult);
        }
        return presentResult;
    }

    private static EngineException vkFailure(String operation, int result) {
        EngineErrorCode code = result == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST
                ? EngineErrorCode.DEVICE_LOST
                : EngineErrorCode.BACKEND_INIT_FAILED;
        return new EngineException(code, operation + " failed: " + result, false);
    }
}
