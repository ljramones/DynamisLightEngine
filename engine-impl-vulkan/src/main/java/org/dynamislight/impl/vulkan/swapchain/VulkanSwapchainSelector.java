package org.dynamislight.impl.vulkan.swapchain;

import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;

public final class VulkanSwapchainSelector {
    private VulkanSwapchainSelector() {
    }

    public static VkSurfaceFormatKHR chooseSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        return formats.get(0);
    }

    public static int choosePresentMode(IntBuffer presentModes, int count) {
        for (int i = 0; i < count; i++) {
            if (presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    public static VkExtent2D chooseExtent(VkSurfaceCapabilitiesKHR capabilities, int width, int height, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return VkExtent2D.calloc(stack).set(capabilities.currentExtent());
        }
        int clampedWidth = Math.max(capabilities.minImageExtent().width(), Math.min(capabilities.maxImageExtent().width(), width));
        int clampedHeight = Math.max(capabilities.minImageExtent().height(), Math.min(capabilities.maxImageExtent().height(), height));
        return VkExtent2D.calloc(stack).set(clampedWidth, clampedHeight);
    }
}
