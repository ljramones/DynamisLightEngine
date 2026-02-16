package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;

public final class VulkanCommandSubmitter {
    private VulkanCommandSubmitter() {
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
