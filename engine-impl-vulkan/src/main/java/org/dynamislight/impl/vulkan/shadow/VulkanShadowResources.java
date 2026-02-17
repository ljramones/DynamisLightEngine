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
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16B16A16_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16_SFLOAT;
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

/**
 * Utility class for managing Vulkan resources required for shadow rendering.
 * Provides methods to create and destroy shadow-related Vulkan resources.
 * This class operates in the context of Vulkan's API and manages objects
 * such as images, image views, framebuffers, and samplers used for shadow mapping.
 *
 * Note: This class is not instantiable.
 */
public final class VulkanShadowResources {
    private VulkanShadowResources() {
    }

    /**
     * Creates a Vulkan shadow allocation, including depth images, samplers, framebuffers,
     * and shadow pipeline resources, for rendering shadow maps in a Vulkan graphics pipeline.
     *
     * This method sets up the necessary Vulkan resources required to handle shadow mapping
     * with multiple shadow matrices and specified resolution.
     *
     * @param device                The Vulkan logical device used for resource creation.
     * @param physicalDevice        The Vulkan physical device (graphics adapter) used for memory allocation.
     * @param stack                 The memory stack for Vulkan function calls, enabling efficient memory allocation.
     * @param depthFormat           The format of the depth image used for shadow mapping.
     * @param shadowMapResolution   The resolution (width and height) of the shadow map texture.
     * @param maxShadowMatrices     The maximum number of shadow matrices supported for rendering.
     * @param vertexStrideBytes     The size, in bytes, of a single vertex in the vertex buffer.
     * @param descriptorSetLayout   The descriptor set layout associated with the shadow pipeline.
     * @return An {@link Allocation} instance containing the created Vulkan resources for shadow mapping.
     * @throws EngineException      If any Vulkan resource creation or operation fails.
     */
    public static Allocation create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack,
            int depthFormat,
            int shadowMapResolution,
            int maxShadowMatrices,
            int vertexStrideBytes,
            long descriptorSetLayout,
            boolean momentPipelineRequested,
            int momentMode
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
        long shadowMomentImage = VK_NULL_HANDLE;
        long shadowMomentMemory = VK_NULL_HANDLE;
        long shadowMomentImageView = VK_NULL_HANDLE;
        long shadowMomentSampler = VK_NULL_HANDLE;
        int shadowMomentFormat = 0;
        if (momentPipelineRequested && momentMode > 0) {
            shadowMomentFormat = switch (momentMode) {
                case 2 -> VK_FORMAT_R16G16B16A16_SFLOAT;
                default -> VK_FORMAT_R16G16_SFLOAT;
            };
            VulkanImageAlloc shadowMoment = VulkanMemoryOps.createImage(
                    device,
                    physicalDevice,
                    stack,
                    shadowMapResolution,
                    shadowMapResolution,
                    shadowMomentFormat,
                    VK10.VK_IMAGE_TILING_OPTIMAL,
                    VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    1
            );
            shadowMomentImage = shadowMoment.image();
            shadowMomentMemory = shadowMoment.memory();
            shadowMomentImageView = createImageView(
                    device,
                    stack,
                    shadowMomentImage,
                    shadowMomentFormat,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_IMAGE_VIEW_TYPE_2D,
                    0,
                    1
            );
            shadowMomentSampler = createMomentSampler(device, stack);
        }
        return new Allocation(
                shadowDepthImage,
                shadowDepthMemory,
                shadowDepthImageView,
                shadowDepthLayerImageViews,
                shadowSampler,
                shadowPipeline.renderPass(),
                shadowPipeline.pipelineLayout(),
                shadowPipeline.graphicsPipeline(),
                shadowFramebuffers,
                shadowMomentImage,
                shadowMomentMemory,
                shadowMomentImageView,
                shadowMomentSampler,
                shadowMomentFormat
        );
    }

    /**
     * Destroys Vulkan resources allocated for shadow mapping.
     * This method ensures proper cleanup of Vulkan objects such as framebuffers,
     * pipelines, render passes, samplers, image views, images, and memory
     * to prevent resource leaks.
     *
     * @param device    The Vulkan logical device associated with the resources.
     * @param resources The {@link Allocation} instance containing the Vulkan resources to clean up.
     */
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
        if (resources.shadowMomentSampler() != VK_NULL_HANDLE) {
            VK10.vkDestroySampler(device, resources.shadowMomentSampler(), null);
        }
        if (resources.shadowMomentImageView() != VK_NULL_HANDLE) {
            vkDestroyImageView(device, resources.shadowMomentImageView(), null);
        }
        if (resources.shadowMomentImage() != VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, resources.shadowMomentImage(), null);
        }
        if (resources.shadowMomentMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.shadowMomentMemory(), null);
        }
    }

    /**
     * Creates an array of Vulkan framebuffers for shadow mapping.
     *
     * This method initializes and allocates Vulkan framebuffers, each associated with
     * one of the provided depth layer image views, for rendering shadow maps. The framebuffers
     * are configured using the specified render pass and shadow map resolution.
     *
     * @param device                 The Vulkan logical device used for framebuffer creation.
     * @param stack                  The memory stack used for temporary allocations during Vulkan calls.
     * @param shadowRenderPass       The Vulkan render pass associated with shadow map rendering.
     * @param shadowDepthLayerImageViews An array of image views used as depth attachments for the framebuffers.
     * @param shadowMapResolution    The resolution (width and height) for each shadow map framebuffer.
     * @return                       An array of Vulkan framebuffer handles corresponding to the provided image views.
     * @throws EngineException       If framebuffer creation fails or returns an invalid handle.
     */
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

    /**
     * Creates a Vulkan image view with specified parameters, configuring how the image
     * will be accessed and interpreted within the Vulkan pipeline.
     *
     * @param device         The Vulkan logical device used for creating the image view.
     * @param stack          The memory stack used for temporary allocations during Vulkan calls.
     * @param image          The handle of the Vulkan image for which the view is to be created.
     * @param format         The format of the image view, specifying how image data should be interpreted.
     * @param aspectMask     A bitmask describing which aspects of the image should be accessible (e.g., color, depth).
     * @param viewType       The type of the image view (e.g., 2D, 3D, cube map).
     * @param baseArrayLayer The first array layer of the image to be accessed by the view.
     * @param layerCount     The number of array layers to be accessible starting from {@code baseArrayLayer}.
     * @return A handle to the created Vulkan image view.
     * @throws EngineException If the image view creation fails or an invalid handle is returned.
     */
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

    /**
     * Creates a Vulkan sampler object configured for shadow mapping.
     * This method sets up a sampler with properties optimized for shadow map sampling,
     * including linear filtering, clamp-to-edge addressing mode, and depth comparison.
     *
     * @param device The Vulkan logical device used for creating the sampler.
     * @param stack  The memory stack used for temporary allocations during Vulkan calls.
     * @return A handle to the created Vulkan sampler object.
     * @throws EngineException If the sampler creation fails or an invalid handle is returned.
     */
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

    private static long createMomentSampler(VkDevice device, MemoryStack stack) throws EngineException {
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(VK10.VK_FILTER_LINEAR)
                .minFilter(VK10.VK_FILTER_LINEAR)
                .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .anisotropyEnable(false)
                .maxAnisotropy(1.0f)
                .borderColor(VK10.VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false)
                .compareEnable(false)
                .compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .mipLodBias(0.0f)
                .minLod(0.0f)
                .maxLod(0.0f);
        var pSampler = stack.longs(VK_NULL_HANDLE);
        int result = VK10.vkCreateSampler(device, samplerInfo, null, pSampler);
        if (result != VK_SUCCESS || pSampler.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSampler(shadow moment) failed: " + result, false);
        }
        return pSampler.get(0);
    }

    /**
     * Represents a collection of Vulkan resources required for shadow mapping in
     * a Vulkan graphics pipeline. This record encapsulates the Vulkan objects
     * necessary to render shadow maps, including depth images, samplers, image
     * views, render passes, pipelines, framebuffers, and other related resources.
     *
     * Instances of this class are typically created as part of higher-level
     * functionalities for rendering shadow maps and managing Vulkan resources.
     */
    public record Allocation(
            long shadowDepthImage,
            long shadowDepthMemory,
            long shadowDepthImageView,
            long[] shadowDepthLayerImageViews,
            long shadowSampler,
            long shadowRenderPass,
            long shadowPipelineLayout,
            long shadowPipeline,
            long[] shadowFramebuffers,
            long shadowMomentImage,
            long shadowMomentMemory,
            long shadowMomentImageView,
            long shadowMomentSampler,
            int shadowMomentFormat
    ) {
    }
}
