package org.dynamislight.impl.vulkan.swapchain;

import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

public final class VulkanSwapchainRecreateCoordinator {
    private VulkanSwapchainRecreateCoordinator() {
    }

    public static void recreateFromWindow(
            long window,
            RecreateFromSizeAction recreateFromSize
    ) throws EngineException {
        if (window == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var pW = stack.ints(1);
            var pH = stack.ints(1);
            glfwGetFramebufferSize(window, pW, pH);
            recreateFromSize.run(Math.max(1, pW.get(0)), Math.max(1, pH.get(0)));
        }
    }

    public static void recreate(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            long surface,
            int width,
            int height,
            DestroySwapchainAction destroy,
            CreateSwapchainAction create
    ) throws EngineException {
        if (device == null || physicalDevice == null || surface == VK_NULL_HANDLE) {
            return;
        }
        vkDeviceWaitIdle(device);
        destroy.run();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            create.run(stack, width, height);
        }
    }

    @FunctionalInterface
    public interface RecreateFromSizeAction {
        void run(int width, int height) throws EngineException;
    }

    @FunctionalInterface
    public interface DestroySwapchainAction {
        void run() throws EngineException;
    }

    @FunctionalInterface
    public interface CreateSwapchainAction {
        void run(MemoryStack stack, int width, int height) throws EngineException;
    }
}
