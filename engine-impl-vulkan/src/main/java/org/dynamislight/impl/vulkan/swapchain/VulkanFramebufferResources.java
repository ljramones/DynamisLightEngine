package org.dynamislight.impl.vulkan.swapchain;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.memory.VulkanMemoryOps;
import org.dynamislight.impl.vulkan.model.VulkanImageAlloc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;

public final class VulkanFramebufferResources {
    private VulkanFramebufferResources() {
    }

    public static DepthResources createDepthResources(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack,
            int imageCount,
            int width,
            int height,
            int depthFormat
    ) throws EngineException {
        long[] depthImages = new long[imageCount];
        long[] depthMemories = new long[imageCount];
        long[] depthImageViews = new long[imageCount];
        for (int i = 0; i < imageCount; i++) {
            VulkanImageAlloc depth = VulkanMemoryOps.createImage(
                    device,
                    physicalDevice,
                    stack,
                    width,
                    height,
                    depthFormat,
                    VK10.VK_IMAGE_TILING_OPTIMAL,
                    VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    1
            );
            depthImages[i] = depth.image();
            depthMemories[i] = depth.memory();
            depthImageViews[i] = createDepthImageView(device, stack, depth.image(), depthFormat);
        }
        return new DepthResources(depthImages, depthMemories, depthImageViews);
    }

    public static long[] createMainFramebuffers(
            VkDevice device,
            MemoryStack stack,
            long renderPass,
            long[] swapchainImageViews,
            long[] depthImageViews,
            int width,
            int height
    ) throws EngineException {
        long[] framebuffers = new long[swapchainImageViews.length];
        for (int i = 0; i < swapchainImageViews.length; i++) {
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPass)
                    .pAttachments(stack.longs(swapchainImageViews[i], depthImageViews[i]))
                    .width(width)
                    .height(height)
                    .layers(1);
            var pFramebuffer = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer);
            if (result != VK_SUCCESS || pFramebuffer.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateFramebuffer failed: " + result, false);
            }
            framebuffers[i] = pFramebuffer.get(0);
        }
        return framebuffers;
    }

    public static void destroyDepthResources(VkDevice device, DepthResources resources) {
        if (device == null || resources == null) {
            return;
        }
        for (long view : resources.depthImageViews()) {
            if (view != VK_NULL_HANDLE) {
                vkDestroyImageView(device, view, null);
            }
        }
        for (long image : resources.depthImages()) {
            if (image != VK_NULL_HANDLE) {
                VK10.vkDestroyImage(device, image, null);
            }
        }
        for (long memory : resources.depthMemories()) {
            if (memory != VK_NULL_HANDLE) {
                vkFreeMemory(device, memory, null);
            }
        }
    }

    public static void destroyFramebuffers(VkDevice device, long[] framebuffers) {
        if (device == null || framebuffers == null) {
            return;
        }
        for (long fb : framebuffers) {
            if (fb != VK_NULL_HANDLE) {
                vkDestroyFramebuffer(device, fb, null);
            }
        }
    }

    private static long createDepthImageView(VkDevice device, MemoryStack stack, long image, int depthFormat) throws EngineException {
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                .format(depthFormat);
        viewInfo.subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        var pView = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateImageView(device, viewInfo, null, pView);
        if (result != VK_SUCCESS || pView.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateImageView(depth) failed: " + result, false);
        }
        return pView.get(0);
    }

    public record DepthResources(long[] depthImages, long[] depthMemories, long[] depthImageViews) {
    }
}
