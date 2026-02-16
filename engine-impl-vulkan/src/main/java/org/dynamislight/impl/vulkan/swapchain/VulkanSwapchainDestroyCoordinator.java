package org.dynamislight.impl.vulkan.swapchain;

import org.dynamislight.impl.vulkan.pipeline.VulkanPostProcessResources;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;

public final class VulkanSwapchainDestroyCoordinator {
    private VulkanSwapchainDestroyCoordinator() {
    }

    public static Result destroy(DestroyInputs inputs) {
        if (inputs.device() == null) {
            return Result.empty();
        }
        VulkanPostProcessResources.destroy(inputs.device(), inputs.postProcessResources());
        VulkanFramebufferResources.destroyFramebuffers(inputs.device(), inputs.framebuffers());
        if (inputs.graphicsPipeline() != VK_NULL_HANDLE) {
            vkDestroyPipeline(inputs.device(), inputs.graphicsPipeline(), null);
        }
        if (inputs.pipelineLayout() != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(inputs.device(), inputs.pipelineLayout(), null);
        }
        if (inputs.renderPass() != VK_NULL_HANDLE) {
            vkDestroyRenderPass(inputs.device(), inputs.renderPass(), null);
        }
        VulkanSwapchainImageViews.destroy(inputs.device(), inputs.swapchainImageViews());
        VulkanFramebufferResources.destroyDepthResources(
                inputs.device(),
                new VulkanFramebufferResources.DepthResources(
                        inputs.depthImages(),
                        inputs.depthMemories(),
                        inputs.depthImageViews()
                )
        );
        if (inputs.swapchain() != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(inputs.device(), inputs.swapchain(), null);
        }
        return Result.empty();
    }

    public record DestroyInputs(
            VkDevice device,
            long[] framebuffers,
            long graphicsPipeline,
            long pipelineLayout,
            long renderPass,
            long[] swapchainImageViews,
            long[] depthImages,
            long[] depthMemories,
            long[] depthImageViews,
            long swapchain,
            VulkanPostProcessResources.Allocation postProcessResources
    ) {
    }

    public record Result(
            long[] framebuffers,
            long graphicsPipeline,
            long pipelineLayout,
            long renderPass,
            long[] swapchainImageViews,
            long[] depthImages,
            long[] depthMemories,
            long[] depthImageViews,
            long[] swapchainImages,
            long swapchain,
            long[] postFramebuffers,
            long postGraphicsPipeline,
            long postPipelineLayout,
            long postRenderPass,
            long postDescriptorPool,
            long postDescriptorSetLayout,
            long postDescriptorSet,
            long offscreenColorSampler,
            long offscreenColorImageView,
            long offscreenColorImage,
            long offscreenColorMemory,
            long postTaaHistorySampler,
            long postTaaHistoryImageView,
            long postTaaHistoryImage,
            long postTaaHistoryMemory,
            boolean postIntermediateInitialized,
            boolean postTaaHistoryInitialized,
            boolean postOffscreenActive
    ) {
        public static Result empty() {
            return new Result(
                    new long[0],
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    new long[0],
                    new long[0],
                    new long[0],
                    new long[0],
                    new long[0],
                    VK_NULL_HANDLE,
                    new long[0],
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    false,
                    false,
                    false
            );
        }
    }
}
