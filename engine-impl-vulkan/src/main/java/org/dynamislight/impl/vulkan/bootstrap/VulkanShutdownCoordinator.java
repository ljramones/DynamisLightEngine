package org.dynamislight.impl.vulkan.bootstrap;

import org.dynamislight.impl.vulkan.command.VulkanFrameSyncResources;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

public final class VulkanShutdownCoordinator {
    private VulkanShutdownCoordinator() {
    }

    public static Result shutdown(Inputs in) {
        if (in.device() != null) {
            vkDeviceWaitIdle(in.device());
            VulkanFrameSyncResources.destroy(
                    in.device(),
                    new VulkanFrameSyncResources.Allocation(
                            in.commandPool(),
                            in.commandBuffers(),
                            in.imageAvailableSemaphores(),
                            in.renderFinishedSemaphores(),
                            in.renderFences()
                    )
            );
        }

        in.destroySceneMeshes().run();
        in.destroyShadowResources().run();
        in.destroySwapchainResources().run();
        in.destroyDescriptorResources().run();

        if (in.device() != null) {
            vkDestroyDevice(in.device(), null);
        }
        if (in.surface() != VK_NULL_HANDLE && in.instance() != null) {
            vkDestroySurfaceKHR(in.instance(), in.surface(), null);
        }
        if (in.instance() != null) {
            vkDestroyInstance(in.instance(), null);
        }
        if (in.window() != VK_NULL_HANDLE) {
            glfwDestroyWindow(in.window());
        }
        glfwTerminate();
        GLFWErrorCallback callback = org.lwjgl.glfw.GLFW.glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }

        return new Result(
                null,
                null,
                null,
                null,
                -1,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                new VkCommandBuffer[0],
                new long[0],
                new long[0],
                new long[0]
        );
    }

    public record Inputs(
            VkInstance instance,
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            VkQueue graphicsQueue,
            int graphicsQueueFamilyIndex,
            long window,
            long surface,
            long commandPool,
            VkCommandBuffer[] commandBuffers,
            long[] imageAvailableSemaphores,
            long[] renderFinishedSemaphores,
            long[] renderFences,
            Runnable destroySceneMeshes,
            Runnable destroyShadowResources,
            Runnable destroySwapchainResources,
            Runnable destroyDescriptorResources
    ) {
    }

    public record Result(
            VkInstance instance,
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            VkQueue graphicsQueue,
            int graphicsQueueFamilyIndex,
            long window,
            long surface,
            long commandPool,
            VkCommandBuffer[] commandBuffers,
            long[] imageAvailableSemaphores,
            long[] renderFinishedSemaphores,
            long[] renderFences
    ) {
    }
}
