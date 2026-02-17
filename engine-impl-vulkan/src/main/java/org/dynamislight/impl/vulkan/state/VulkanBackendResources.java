package org.dynamislight.impl.vulkan.state;

import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public final class VulkanBackendResources {
    public VkInstance instance;
    public VkPhysicalDevice physicalDevice;
    public VkDevice device;
    public VkQueue graphicsQueue;
    public int graphicsQueueFamilyIndex = -1;
    public long window = VK_NULL_HANDLE;
    public long surface = VK_NULL_HANDLE;

    public long swapchain = VK_NULL_HANDLE;
    public int swapchainImageFormat = VK_FORMAT_B8G8R8A8_SRGB;
    public int swapchainWidth = 1;
    public int swapchainHeight = 1;
    public long[] swapchainImages = new long[0];
    public long[] swapchainImageViews = new long[0];
    public int depthFormat = VK_FORMAT_D32_SFLOAT;
    public long[] depthImages = new long[0];
    public long[] depthMemories = new long[0];
    public long[] depthImageViews = new long[0];
    public long velocityImage = VK_NULL_HANDLE;
    public long velocityMemory = VK_NULL_HANDLE;
    public long velocityImageView = VK_NULL_HANDLE;

    public long shadowDepthImage = VK_NULL_HANDLE;
    public long shadowDepthMemory = VK_NULL_HANDLE;
    public long shadowDepthImageView = VK_NULL_HANDLE;
    public long[] shadowDepthLayerImageViews = new long[0];
    public long shadowSampler = VK_NULL_HANDLE;
    public long shadowRenderPass = VK_NULL_HANDLE;
    public long shadowPipelineLayout = VK_NULL_HANDLE;
    public long shadowPipeline = VK_NULL_HANDLE;
    public long[] shadowFramebuffers = new long[0];
    public long shadowMomentImage = VK_NULL_HANDLE;
    public long shadowMomentMemory = VK_NULL_HANDLE;
    public long shadowMomentImageView = VK_NULL_HANDLE;
    public long[] shadowMomentLayerImageViews = new long[0];
    public long shadowMomentSampler = VK_NULL_HANDLE;
    public int shadowMomentFormat = 0;
    public int shadowMomentMipLevels = 1;

    public long renderPass = VK_NULL_HANDLE;
    public long pipelineLayout = VK_NULL_HANDLE;
    public long graphicsPipeline = VK_NULL_HANDLE;
    public long[] framebuffers = new long[0];

    public long commandPool = VK_NULL_HANDLE;
    public VkCommandBuffer[] commandBuffers = new VkCommandBuffer[0];
    public long[] imageAvailableSemaphores = new long[0];
    public long[] renderFinishedSemaphores = new long[0];
    public long[] renderFences = new long[0];
    public int currentFrame;

    public long offscreenColorImage = VK_NULL_HANDLE;
    public long offscreenColorMemory = VK_NULL_HANDLE;
    public long offscreenColorImageView = VK_NULL_HANDLE;
    public long offscreenColorSampler = VK_NULL_HANDLE;
    public long postTaaHistoryImage = VK_NULL_HANDLE;
    public long postTaaHistoryMemory = VK_NULL_HANDLE;
    public long postTaaHistoryImageView = VK_NULL_HANDLE;
    public long postTaaHistorySampler = VK_NULL_HANDLE;
    public long postTaaHistoryVelocityImage = VK_NULL_HANDLE;
    public long postTaaHistoryVelocityMemory = VK_NULL_HANDLE;
    public long postTaaHistoryVelocityImageView = VK_NULL_HANDLE;
    public long postTaaHistoryVelocitySampler = VK_NULL_HANDLE;
    public long postRenderPass = VK_NULL_HANDLE;
    public long postPipelineLayout = VK_NULL_HANDLE;
    public long postGraphicsPipeline = VK_NULL_HANDLE;
    public long postDescriptorSetLayout = VK_NULL_HANDLE;
    public long postDescriptorPool = VK_NULL_HANDLE;
    public long postDescriptorSet = VK_NULL_HANDLE;
    public long[] postFramebuffers = new long[0];
}
