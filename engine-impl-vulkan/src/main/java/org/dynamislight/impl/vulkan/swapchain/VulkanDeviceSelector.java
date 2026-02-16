package org.dynamislight.impl.vulkan.swapchain;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkEnumerateDeviceExtensionProperties;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;

public final class VulkanDeviceSelector {
    private VulkanDeviceSelector() {
    }

    public static Selection select(VkInstance instance, long surface, MemoryStack stack) throws EngineException {
        var pCount = stack.ints(0);
        int enumResult = vkEnumeratePhysicalDevices(instance, pCount, null);
        if (enumResult != VK_SUCCESS || pCount.get(0) == 0) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "No Vulkan physical devices available", false);
        }
        PointerBuffer devices = stack.mallocPointer(pCount.get(0));
        vkEnumeratePhysicalDevices(instance, pCount, devices);

        VkPhysicalDevice chosen = null;
        int chosenQueueFamily = -1;
        for (int i = 0; i < devices.capacity(); i++) {
            VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
            int queueFamily = findGraphicsPresentQueueFamily(candidate, surface, stack);
            if (queueFamily >= 0 && supportsSwapchainExtension(candidate, stack)) {
                chosen = candidate;
                chosenQueueFamily = queueFamily;
                break;
            }
        }
        if (chosen == null || chosenQueueFamily < 0) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "No Vulkan device with graphics+present+swapchain support found",
                    false
            );
        }
        return new Selection(chosen, chosenQueueFamily);
    }

    private static int findGraphicsPresentQueueFamily(VkPhysicalDevice candidate, long surface, MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, null);
        if (count.get(0) <= 0) {
            return -1;
        }
        VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, families);
        for (int i = 0; i < families.capacity(); i++) {
            VkQueueFamilyProperties family = families.get(i);
            if ((family.queueFlags() & VK_QUEUE_GRAPHICS_BIT) == 0 || family.queueCount() <= 0) {
                continue;
            }
            IntBuffer pSupported = stack.ints(VK10.VK_FALSE);
            int presentResult = KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, pSupported);
            if (presentResult == VK_SUCCESS && pSupported.get(0) == VK10.VK_TRUE) {
                return i;
            }
        }
        return -1;
    }

    private static boolean supportsSwapchainExtension(VkPhysicalDevice candidate, MemoryStack stack) {
        IntBuffer extCount = stack.ints(0);
        int extResult = vkEnumerateDeviceExtensionProperties(candidate, (String) null, extCount, null);
        if (extResult != VK_SUCCESS || extCount.get(0) == 0) {
            return false;
        }
        VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(extCount.get(0), stack);
        vkEnumerateDeviceExtensionProperties(candidate, (String) null, extCount, extensions);
        for (int i = 0; i < extensions.capacity(); i++) {
            String extName = extensions.get(i).extensionNameString();
            if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(extName)) {
                return true;
            }
        }
        return false;
    }

    public record Selection(VkPhysicalDevice physicalDevice, int graphicsQueueFamilyIndex) {
    }
}
