package org.dynamislight.impl.vulkan.shadow;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.memory.VulkanMemoryOps;
import org.dynamislight.impl.vulkan.model.VulkanImageAlloc;
import org.dynamislight.impl.vulkan.pipeline.VulkanShadowPipelineBuilder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;

public final class VulkanShadowResources {
    private VulkanShadowResources() {
    }

    public static Allocation create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack,
            int depthFormat,
            int shadowMapResolution,
            int maxShadowMatrices,
            int vertexStrideBytes,
            long descriptorSetLayout
    ) throws EngineException {
        VulkanImageAlloc shadowDepth = VulkanMemoryOps.createImage(
                device,
                physicalDevice,
                stack,
                shadowMapResolution,
                shadowMapResolution,
                depthFormat,
                VK10.VK_IMAGE_TILING_OPTIMAL,
                VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                maxShadowMatrices
        );
        long shadowDepthImage = shadowDepth.image();
        long shadowDepthMemory = shadowDepth.memory();
        long shadowDepthImageView = createImageView(
                device,
                stack,
                shadowDepthImage,
                depthFormat,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK10.VK_IMAGE_VIEW_TYPE_2D_ARRAY,
                0,
                maxShadowMatrices
        );
        long[] shadowDepthLayerImageViews = new long[maxShadowMatrices];
        for (int i = 0; i < maxShadowMatrices; i++) {
            shadowDepthLayerImageViews[i] = createImageView(
                    device,
                    stack,
                    shadowDepthImage,
                    depthFormat,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_IMAGE_VIEW_TYPE_2D,
                    i,
                    1
            );
        }
        long shadowSampler = createShadowSampler(device, stack);
        VulkanShadowPipelineBuilder.Result shadowPipeline = VulkanShadowPipelineBuilder.create(
                device,
                stack,
                depthFormat,
                shadowMapResolution,
                vertexStrideBytes,
                descriptorSetLayout
        );
        long[] shadowFramebuffers = createShadowFramebuffers(
                device,
                stack,
                shadowPipeline.renderPass(),
                shadowDepthLayerImageViews,
                shadowMapResolution
        );
        return new Allocation(
                shadowDepthImage,
                shadowDepthMemory,
                shadowDepthImageView,
                shadowDepthLayerImageViews,
                shadowSampler,
                shadowPipeline.renderPass(),
                shadowPipeline.pipelineLayout(),
                shadowPipeline.graphicsPipeline(),
                shadowFramebuffers
        );
    }

    public static void destroy(VkDevice device, Allocation resources) {
        if (device == null || resources == null) {
            return;
        }
        for (long framebuffer : resources.shadowFramebuffers()) {
            if (framebuffer != VK_NULL_HANDLE) {
                vkDestroyFramebuffer(device, framebuffer, null);
            }
        }
        if (resources.shadowPipeline() != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, resources.shadowPipeline(), null);
        }
        if (resources.shadowPipelineLayout() != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, resources.shadowPipelineLayout(), null);
        }
        if (resources.shadowRenderPass() != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, resources.shadowRenderPass(), null);
        }
        if (resources.shadowSampler() != VK_NULL_HANDLE) {
            VK10.vkDestroySampler(device, resources.shadowSampler(), null);
        }
        if (resources.shadowDepthImageView() != VK_NULL_HANDLE) {
            vkDestroyImageView(device, resources.shadowDepthImageView(), null);
        }
        for (long layerView : resources.shadowDepthLayerImageViews()) {
            if (layerView != VK_NULL_HANDLE) {
                vkDestroyImageView(device, layerView, null);
            }
        }
        if (resources.shadowDepthImage() != VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, resources.shadowDepthImage(), null);
        }
        if (resources.shadowDepthMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.shadowDepthMemory(), null);
        }
    }

    private static long[] createShadowFramebuffers(
            VkDevice device,
            MemoryStack stack,
            long shadowRenderPass,
            long[] shadowDepthLayerImageViews,
            int shadowMapResolution
    ) throws EngineException {
        long[] framebuffers = new long[shadowDepthLayerImageViews.length];
        for (int i = 0; i < shadowDepthLayerImageViews.length; i++) {
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(shadowRenderPass)
                    .pAttachments(stack.longs(shadowDepthLayerImageViews[i]))
                    .width(shadowMapResolution)
                    .height(shadowMapResolution)
                    .layers(1);
            var pFramebuffer = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer);
            if (result != VK_SUCCESS || pFramebuffer.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateFramebuffer(shadow) failed: " + result, false);
            }
            framebuffers[i] = pFramebuffer.get(0);
        }
        return framebuffers;
    }

    private static long createImageView(
            VkDevice device,
            MemoryStack stack,
            long image,
            int format,
            int aspectMask,
            int viewType,
            int baseArrayLayer,
            int layerCount
    ) throws EngineException {
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(viewType)
                .format(format);
        viewInfo.subresourceRange()
                .aspectMask(aspectMask)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(baseArrayLayer)
                .layerCount(layerCount);
        var pView = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateImageView(device, viewInfo, null, pView);
        if (result != VK_SUCCESS || pView.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateImageView(shadow) failed: " + result, false);
        }
        return pView.get(0);
    }

    private static long createShadowSampler(VkDevice device, MemoryStack stack) throws EngineException {
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(VK10.VK_FILTER_LINEAR)
                .minFilter(VK10.VK_FILTER_LINEAR)
                .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .anisotropyEnable(false)
                .maxAnisotropy(1.0f)
                .borderColor(VK10.VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)
                .unnormalizedCoordinates(false)
                .compareEnable(true)
                .compareOp(VK10.VK_COMPARE_OP_LESS_OR_EQUAL)
                .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .mipLodBias(0.0f)
                .minLod(0.0f)
                .maxLod(0.0f);
        var pSampler = stack.longs(VK_NULL_HANDLE);
        int result = VK10.vkCreateSampler(device, samplerInfo, null, pSampler);
        if (result != VK_SUCCESS || pSampler.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSampler(shadow) failed: " + result, false);
        }
        return pSampler.get(0);
    }

    public record Allocation(
            long shadowDepthImage,
            long shadowDepthMemory,
            long shadowDepthImageView,
            long[] shadowDepthLayerImageViews,
            long shadowSampler,
            long shadowRenderPass,
            long shadowPipelineLayout,
            long shadowPipeline,
            long[] shadowFramebuffers
    ) {
    }
}
