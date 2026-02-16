package org.dynamislight.impl.vulkan.swapchain;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public final class VulkanSwapchainAllocation {
    private VulkanSwapchainAllocation() {
    }

    public static Allocation create(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            MemoryStack stack,
            long surface,
            int requestedWidth,
            int requestedHeight
    ) throws EngineException {
        VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
        int capsResult = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);
        if (capsResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR failed: " + capsResult, false);
        }

        var formatCount = stack.ints(0);
        int formatResult = vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null);
        if (formatResult != VK_SUCCESS || formatCount.get(0) == 0) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "No Vulkan surface formats", false);
        }
        VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(formatCount.get(0), stack);
        vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, formats);
        VkSurfaceFormatKHR chosenFormat = VulkanSwapchainSelector.chooseSurfaceFormat(formats);

        var presentModeCount = stack.ints(0);
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null);
        var presentModes = stack.mallocInt(Math.max(1, presentModeCount.get(0)));
        if (presentModeCount.get(0) > 0) {
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, presentModes);
        }
        int presentMode = VulkanSwapchainSelector.choosePresentMode(presentModes, presentModeCount.get(0));

        VkExtent2D extent = VulkanSwapchainSelector.chooseExtent(capabilities, requestedWidth, requestedHeight, stack);
        int imageCount = capabilities.minImageCount() + 1;
        if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
            imageCount = capabilities.maxImageCount();
        }

        VkSwapchainCreateInfoKHR swapchainInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(imageCount)
                .imageFormat(chosenFormat.format())
                .imageColorSpace(chosenFormat.colorSpace())
                .imageExtent(extent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .preTransform((capabilities.supportedTransforms() & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR) != 0
                        ? VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR
                        : capabilities.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(presentMode)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE);

        var pSwapchain = stack.longs(VK_NULL_HANDLE);
        int swapchainResult = vkCreateSwapchainKHR(device, swapchainInfo, null, pSwapchain);
        if (swapchainResult != VK_SUCCESS || pSwapchain.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSwapchainKHR failed: " + swapchainResult, false);
        }
        long swapchain = pSwapchain.get(0);

        var imageCountBuf = stack.ints(0);
        vkGetSwapchainImagesKHR(device, swapchain, imageCountBuf, null);
        var imageHandles = stack.mallocLong(imageCountBuf.get(0));
        vkGetSwapchainImagesKHR(device, swapchain, imageCountBuf, imageHandles);
        long[] swapchainImages = new long[imageHandles.capacity()];
        for (int i = 0; i < swapchainImages.length; i++) {
            swapchainImages[i] = imageHandles.get(i);
        }

        return new Allocation(swapchain, chosenFormat.format(), extent.width(), extent.height(), swapchainImages);
    }

    public record Allocation(
            long swapchain,
            int swapchainImageFormat,
            int swapchainWidth,
            int swapchainHeight,
            long[] swapchainImages
    ) {
    }
}
