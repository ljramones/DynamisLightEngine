package org.dynamislight.impl.vulkan.swapchain;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.pipeline.VulkanPostProcessResources;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public final class VulkanSwapchainLifecycleCoordinator {
    private VulkanSwapchainLifecycleCoordinator() {
    }

    public static State create(CreateRequest request) throws EngineException {
        VulkanSwapchainResourceCoordinator.Allocation allocation = VulkanSwapchainResourceCoordinator.create(
                new VulkanSwapchainResourceCoordinator.CreateInputs(
                        request.physicalDevice(),
                        request.device(),
                        request.stack(),
                        request.surface(),
                        request.requestedWidth(),
                        request.requestedHeight(),
                        request.depthFormat(),
                        request.vertexStrideBytes(),
                        request.descriptorSetLayout(),
                        request.textureDescriptorSetLayout(),
                        request.postOffscreenRequested()
                )
        );
        VulkanPostProcessResources.Allocation postResources = allocation.postProcessResources();
        return new State(
                allocation.swapchain(),
                allocation.swapchainImageFormat(),
                allocation.swapchainWidth(),
                allocation.swapchainHeight(),
                allocation.swapchainImages(),
                allocation.swapchainImageViews(),
                allocation.depthImages(),
                allocation.depthMemories(),
                allocation.depthImageViews(),
                allocation.renderPass(),
                allocation.pipelineLayout(),
                allocation.graphicsPipeline(),
                allocation.framebuffers(),
                allocation.postOffscreenActive(),
                postResources.offscreenColorImage(),
                postResources.offscreenColorMemory(),
                postResources.offscreenColorImageView(),
                postResources.offscreenColorSampler(),
                postResources.taaHistoryImage(),
                postResources.taaHistoryMemory(),
                postResources.taaHistoryImageView(),
                postResources.taaHistorySampler(),
                postResources.postDescriptorSetLayout(),
                postResources.postDescriptorPool(),
                postResources.postDescriptorSet(),
                postResources.postRenderPass(),
                postResources.postPipelineLayout(),
                postResources.postGraphicsPipeline(),
                postResources.postFramebuffers(),
                false,
                false
        );
    }

    public static State destroy(DestroyRequest request) {
        VulkanSwapchainDestroyCoordinator.Result result = VulkanSwapchainDestroyCoordinator.destroy(
                new VulkanSwapchainDestroyCoordinator.DestroyInputs(
                        request.device(),
                        request.framebuffers(),
                        request.graphicsPipeline(),
                        request.pipelineLayout(),
                        request.renderPass(),
                        request.swapchainImageViews(),
                        request.depthImages(),
                        request.depthMemories(),
                        request.depthImageViews(),
                        request.swapchain(),
                        new VulkanPostProcessResources.Allocation(
                                request.offscreenColorImage(),
                                request.offscreenColorMemory(),
                                request.offscreenColorImageView(),
                                request.offscreenColorSampler(),
                                request.postTaaHistoryImage(),
                                request.postTaaHistoryMemory(),
                                request.postTaaHistoryImageView(),
                                request.postTaaHistorySampler(),
                                request.postDescriptorSetLayout(),
                                request.postDescriptorPool(),
                                request.postDescriptorSet(),
                                request.postRenderPass(),
                                request.postPipelineLayout(),
                                request.postGraphicsPipeline(),
                                request.postFramebuffers()
                        )
                )
        );
        if (result == null) {
            return null;
        }
        return new State(
                result.swapchain(),
                request.swapchainImageFormat(),
                request.swapchainWidth(),
                request.swapchainHeight(),
                result.swapchainImages(),
                result.swapchainImageViews(),
                result.depthImages(),
                result.depthMemories(),
                result.depthImageViews(),
                result.renderPass(),
                result.pipelineLayout(),
                result.graphicsPipeline(),
                result.framebuffers(),
                result.postOffscreenActive(),
                result.offscreenColorImage(),
                result.offscreenColorMemory(),
                result.offscreenColorImageView(),
                result.offscreenColorSampler(),
                result.postTaaHistoryImage(),
                result.postTaaHistoryMemory(),
                result.postTaaHistoryImageView(),
                result.postTaaHistorySampler(),
                result.postDescriptorSetLayout(),
                result.postDescriptorPool(),
                result.postDescriptorSet(),
                result.postRenderPass(),
                result.postPipelineLayout(),
                result.postGraphicsPipeline(),
                result.postFramebuffers(),
                result.postIntermediateInitialized(),
                result.postTaaHistoryInitialized()
        );
    }

    public record CreateRequest(
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            MemoryStack stack,
            long surface,
            int requestedWidth,
            int requestedHeight,
            int depthFormat,
            int vertexStrideBytes,
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            boolean postOffscreenRequested
    ) {
    }

    public record DestroyRequest(
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
            int swapchainImageFormat,
            int swapchainWidth,
            int swapchainHeight,
            long offscreenColorImage,
            long offscreenColorMemory,
            long offscreenColorImageView,
            long offscreenColorSampler,
            long postTaaHistoryImage,
            long postTaaHistoryMemory,
            long postTaaHistoryImageView,
            long postTaaHistorySampler,
            long postDescriptorSetLayout,
            long postDescriptorPool,
            long postDescriptorSet,
            long postRenderPass,
            long postPipelineLayout,
            long postGraphicsPipeline,
            long[] postFramebuffers
    ) {
    }

    public record State(
            long swapchain,
            int swapchainImageFormat,
            int swapchainWidth,
            int swapchainHeight,
            long[] swapchainImages,
            long[] swapchainImageViews,
            long[] depthImages,
            long[] depthMemories,
            long[] depthImageViews,
            long renderPass,
            long pipelineLayout,
            long graphicsPipeline,
            long[] framebuffers,
            boolean postOffscreenActive,
            long offscreenColorImage,
            long offscreenColorMemory,
            long offscreenColorImageView,
            long offscreenColorSampler,
            long postTaaHistoryImage,
            long postTaaHistoryMemory,
            long postTaaHistoryImageView,
            long postTaaHistorySampler,
            long postDescriptorSetLayout,
            long postDescriptorPool,
            long postDescriptorSet,
            long postRenderPass,
            long postPipelineLayout,
            long postGraphicsPipeline,
            long[] postFramebuffers,
            boolean postIntermediateInitialized,
            boolean postTaaHistoryInitialized
    ) {
        public static State empty() {
            return new State(
                    VK_NULL_HANDLE,
                    0,
                    1,
                    1,
                    new long[0],
                    new long[0],
                    new long[0],
                    new long[0],
                    new long[0],
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    new long[0],
                    false,
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
                    new long[0],
                    false,
                    false
            );
        }
    }
}
