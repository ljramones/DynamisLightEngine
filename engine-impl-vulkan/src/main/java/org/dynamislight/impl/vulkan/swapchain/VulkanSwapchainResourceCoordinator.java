package org.dynamislight.impl.vulkan.swapchain;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.pipeline.VulkanMainPipelineBuilder;
import org.dynamislight.impl.vulkan.pipeline.VulkanPostProcessResources;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;

public final class VulkanSwapchainResourceCoordinator {
    private VulkanSwapchainResourceCoordinator() {
    }

    public static Allocation create(CreateInputs inputs) throws EngineException {
        VulkanSwapchainAllocation.Allocation swapchainAllocation = VulkanSwapchainAllocation.create(
                inputs.physicalDevice(),
                inputs.device(),
                inputs.stack(),
                inputs.surface(),
                inputs.requestedWidth(),
                inputs.requestedHeight()
        );
        long[] swapchainImages = swapchainAllocation.swapchainImages();
        long[] swapchainImageViews = VulkanSwapchainImageViews.create(
                inputs.device(),
                inputs.stack(),
                swapchainImages,
                swapchainAllocation.swapchainImageFormat()
        );

        VulkanFramebufferResources.DepthResources depthResources = VulkanFramebufferResources.createDepthResources(
                inputs.device(),
                inputs.physicalDevice(),
                inputs.stack(),
                swapchainImages.length,
                swapchainAllocation.swapchainWidth(),
                swapchainAllocation.swapchainHeight(),
                inputs.depthFormat()
        );
        VulkanMainPipelineBuilder.Result mainPipeline = VulkanMainPipelineBuilder.create(
                inputs.device(),
                inputs.stack(),
                swapchainAllocation.swapchainImageFormat(),
                inputs.depthFormat(),
                swapchainAllocation.swapchainWidth(),
                swapchainAllocation.swapchainHeight(),
                inputs.vertexStrideBytes(),
                inputs.descriptorSetLayout(),
                inputs.textureDescriptorSetLayout()
        );
        long[] framebuffers = VulkanFramebufferResources.createMainFramebuffers(
                inputs.device(),
                inputs.stack(),
                mainPipeline.renderPass(),
                swapchainImageViews,
                depthResources.depthImageViews(),
                swapchainAllocation.swapchainWidth(),
                swapchainAllocation.swapchainHeight()
        );

        boolean postOffscreenActive = false;
        VulkanPostProcessResources.Allocation postProcessResources = VulkanPostProcessResources.empty();
        if (inputs.postOffscreenRequested()) {
            try {
                postProcessResources = VulkanPostProcessResources.create(
                        inputs.device(),
                        inputs.physicalDevice(),
                        inputs.stack(),
                        swapchainAllocation.swapchainImageFormat(),
                        swapchainAllocation.swapchainWidth(),
                        swapchainAllocation.swapchainHeight(),
                        swapchainImageViews
                );
                postOffscreenActive = true;
            } catch (EngineException ex) {
                VulkanPostProcessResources.destroy(inputs.device(), postProcessResources);
                postProcessResources = VulkanPostProcessResources.empty();
            }
        }
        return new Allocation(
                swapchainAllocation.swapchain(),
                swapchainAllocation.swapchainImageFormat(),
                swapchainAllocation.swapchainWidth(),
                swapchainAllocation.swapchainHeight(),
                swapchainImages,
                swapchainImageViews,
                depthResources.depthImages(),
                depthResources.depthMemories(),
                depthResources.depthImageViews(),
                mainPipeline.renderPass(),
                mainPipeline.pipelineLayout(),
                mainPipeline.graphicsPipeline(),
                framebuffers,
                postProcessResources,
                postOffscreenActive
        );
    }

    public record CreateInputs(
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

    public record Allocation(
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
            VulkanPostProcessResources.Allocation postProcessResources,
            boolean postOffscreenActive
    ) {
    }
}
