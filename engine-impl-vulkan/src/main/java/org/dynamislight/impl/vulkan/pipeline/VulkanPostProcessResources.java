package org.dynamislight.impl.vulkan.pipeline;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.memory.VulkanMemoryOps;
import org.dynamislight.impl.vulkan.model.VulkanImageAlloc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkDescriptorImageInfo;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

public final class VulkanPostProcessResources {
    private VulkanPostProcessResources() {
    }

    public static Allocation create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack,
            int swapchainImageFormat,
            int swapchainWidth,
            int swapchainHeight,
            long[] swapchainImageViews,
            long velocityImageView
    ) throws EngineException {
        VulkanImageAlloc intermediate = VulkanMemoryOps.createImage(
                device,
                physicalDevice,
                stack,
                swapchainWidth,
                swapchainHeight,
                swapchainImageFormat,
                VK10.VK_IMAGE_TILING_OPTIMAL,
                VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                1
        );
        long offscreenColorImage = intermediate.image();
        long offscreenColorMemory = intermediate.memory();
        long offscreenColorImageView = createImageView(device, stack, offscreenColorImage, swapchainImageFormat);
        long offscreenColorSampler = createSampler(device, stack);
        VulkanImageAlloc history = VulkanMemoryOps.createImage(
                device,
                physicalDevice,
                stack,
                swapchainWidth,
                swapchainHeight,
                swapchainImageFormat,
                VK10.VK_IMAGE_TILING_OPTIMAL,
                VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                1
        );
        long taaHistoryImage = history.image();
        long taaHistoryMemory = history.memory();
        long taaHistoryImageView = createImageView(device, stack, taaHistoryImage, swapchainImageFormat);
        long taaHistorySampler = createSampler(device, stack);
        VulkanImageAlloc historyVelocity = VulkanMemoryOps.createImage(
                device,
                physicalDevice,
                stack,
                swapchainWidth,
                swapchainHeight,
                swapchainImageFormat,
                VK10.VK_IMAGE_TILING_OPTIMAL,
                VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                1
        );
        long taaHistoryVelocityImage = historyVelocity.image();
        long taaHistoryVelocityMemory = historyVelocity.memory();
        long taaHistoryVelocityImageView = createImageView(device, stack, taaHistoryVelocityImage, swapchainImageFormat);
        long taaHistoryVelocitySampler = createSampler(device, stack);
        VulkanImageAlloc planarCapture = VulkanMemoryOps.createImage(
                device,
                physicalDevice,
                stack,
                swapchainWidth,
                swapchainHeight,
                swapchainImageFormat,
                VK10.VK_IMAGE_TILING_OPTIMAL,
                VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                1
        );
        long planarCaptureImage = planarCapture.image();
        long planarCaptureMemory = planarCapture.memory();
        long planarCaptureImageView = createImageView(device, stack, planarCaptureImage, swapchainImageFormat);
        long planarCaptureSampler = createSampler(device, stack);

        long postDescriptorSetLayout = createPostDescriptorSetLayout(device, stack);
        long postDescriptorPool = createPostDescriptorPool(device, stack);
        long postDescriptorSet = allocatePostDescriptorSet(device, stack, postDescriptorPool, postDescriptorSetLayout);
        writePostDescriptorSet(
                device,
                stack,
                postDescriptorSet,
                offscreenColorImageView,
                offscreenColorSampler,
                taaHistoryImageView,
                taaHistorySampler,
                velocityImageView,
                offscreenColorSampler,
                taaHistoryVelocityImageView,
                taaHistoryVelocitySampler,
                planarCaptureImageView,
                planarCaptureSampler
        );

        VulkanPostPipelineBuilder.Result postPipeline = VulkanPostPipelineBuilder.create(
                device,
                stack,
                swapchainImageFormat,
                swapchainWidth,
                swapchainHeight,
                postDescriptorSetLayout
        );
        long[] postFramebuffers = createPostFramebuffers(
                device,
                stack,
                postPipeline.renderPass(),
                swapchainImageViews,
                swapchainWidth,
                swapchainHeight
        );

        return new Allocation(
                offscreenColorImage,
                offscreenColorMemory,
                offscreenColorImageView,
                offscreenColorSampler,
                taaHistoryImage,
                taaHistoryMemory,
                taaHistoryImageView,
                taaHistorySampler,
                taaHistoryVelocityImage,
                taaHistoryVelocityMemory,
                taaHistoryVelocityImageView,
                taaHistoryVelocitySampler,
                planarCaptureImage,
                planarCaptureMemory,
                planarCaptureImageView,
                planarCaptureSampler,
                postDescriptorSetLayout,
                postDescriptorPool,
                postDescriptorSet,
                postPipeline.renderPass(),
                postPipeline.pipelineLayout(),
                postPipeline.graphicsPipeline(),
                postFramebuffers
        );
    }

    public static Allocation empty() {
        return new Allocation(
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
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                new long[0]
        );
    }

    public static void destroy(VkDevice device, Allocation resources) {
        if (device == null || resources == null) {
            return;
        }
        for (long fb : resources.postFramebuffers()) {
            if (fb != VK_NULL_HANDLE) {
                vkDestroyFramebuffer(device, fb, null);
            }
        }
        if (resources.postGraphicsPipeline() != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, resources.postGraphicsPipeline(), null);
        }
        if (resources.postPipelineLayout() != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, resources.postPipelineLayout(), null);
        }
        if (resources.postRenderPass() != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, resources.postRenderPass(), null);
        }
        if (resources.postDescriptorPool() != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, resources.postDescriptorPool(), null);
        }
        if (resources.postDescriptorSetLayout() != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, resources.postDescriptorSetLayout(), null);
        }
        if (resources.offscreenColorSampler() != VK_NULL_HANDLE) {
            VK10.vkDestroySampler(device, resources.offscreenColorSampler(), null);
        }
        if (resources.taaHistorySampler() != VK_NULL_HANDLE) {
            VK10.vkDestroySampler(device, resources.taaHistorySampler(), null);
        }
        if (resources.taaHistoryVelocitySampler() != VK_NULL_HANDLE) {
            VK10.vkDestroySampler(device, resources.taaHistoryVelocitySampler(), null);
        }
        if (resources.planarCaptureSampler() != VK_NULL_HANDLE) {
            VK10.vkDestroySampler(device, resources.planarCaptureSampler(), null);
        }
        if (resources.offscreenColorImageView() != VK_NULL_HANDLE) {
            vkDestroyImageView(device, resources.offscreenColorImageView(), null);
        }
        if (resources.taaHistoryImageView() != VK_NULL_HANDLE) {
            vkDestroyImageView(device, resources.taaHistoryImageView(), null);
        }
        if (resources.taaHistoryVelocityImageView() != VK_NULL_HANDLE) {
            vkDestroyImageView(device, resources.taaHistoryVelocityImageView(), null);
        }
        if (resources.planarCaptureImageView() != VK_NULL_HANDLE) {
            vkDestroyImageView(device, resources.planarCaptureImageView(), null);
        }
        if (resources.offscreenColorImage() != VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, resources.offscreenColorImage(), null);
        }
        if (resources.taaHistoryImage() != VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, resources.taaHistoryImage(), null);
        }
        if (resources.taaHistoryVelocityImage() != VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, resources.taaHistoryVelocityImage(), null);
        }
        if (resources.planarCaptureImage() != VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, resources.planarCaptureImage(), null);
        }
        if (resources.offscreenColorMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.offscreenColorMemory(), null);
        }
        if (resources.taaHistoryMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.taaHistoryMemory(), null);
        }
        if (resources.taaHistoryVelocityMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.taaHistoryVelocityMemory(), null);
        }
        if (resources.planarCaptureMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.planarCaptureMemory(), null);
        }
    }

    private static long createImageView(VkDevice device, MemoryStack stack, long image, int format) throws EngineException {
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                .format(format);
        viewInfo.subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        var pView = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateImageView(device, viewInfo, null, pView);
        if (result != VK_SUCCESS || pView.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateImageView(post) failed: " + result, false);
        }
        return pView.get(0);
    }

    private static long createSampler(VkDevice device, MemoryStack stack) throws EngineException {
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(VK10.VK_FILTER_LINEAR)
                .minFilter(VK10.VK_FILTER_LINEAR)
                .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .anisotropyEnable(false)
                .maxAnisotropy(1.0f)
                .borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false)
                .compareEnable(false)
                .compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR)
                .mipLodBias(0.0f)
                .minLod(0.0f)
                .maxLod(0.0f);
        var pSampler = stack.longs(VK_NULL_HANDLE);
        int result = VK10.vkCreateSampler(device, samplerInfo, null, pSampler);
        if (result != VK_SUCCESS || pSampler.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSampler(post) failed: " + result, false);
        }
        return pSampler.get(0);
    }

    private static long createPostDescriptorSetLayout(VkDevice device, MemoryStack stack) throws EngineException {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(5, stack);
        bindings.get(0)
                .binding(0)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        bindings.get(1)
                .binding(1)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        bindings.get(2)
                .binding(2)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        bindings.get(3)
                .binding(3)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        bindings.get(4)
                .binding(4)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings);
        var pLayout = stack.longs(VK_NULL_HANDLE);
        int layoutResult = vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout);
        if (layoutResult != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateDescriptorSetLayout(post) failed: " + layoutResult, false);
        }
        return pLayout.get(0);
    }

    private static long createPostDescriptorPool(VkDevice device, MemoryStack stack) throws EngineException {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
        poolSizes.get(0)
                .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(5);
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(1)
                .pPoolSizes(poolSizes);
        var pPool = stack.longs(VK_NULL_HANDLE);
        int poolResult = vkCreateDescriptorPool(device, poolInfo, null, pPool);
        if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateDescriptorPool(post) failed: " + poolResult, false);
        }
        return pPool.get(0);
    }

    private static long allocatePostDescriptorSet(
            VkDevice device,
            MemoryStack stack,
            long postDescriptorPool,
            long postDescriptorSetLayout
    ) throws EngineException {
        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(postDescriptorPool)
                .pSetLayouts(stack.longs(postDescriptorSetLayout));
        var pSet = stack.longs(VK_NULL_HANDLE);
        int setResult = vkAllocateDescriptorSets(device, allocInfo, pSet);
        if (setResult != VK_SUCCESS || pSet.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateDescriptorSets(post) failed: " + setResult, false);
        }
        return pSet.get(0);
    }

    private static void writePostDescriptorSet(
            VkDevice device,
            MemoryStack stack,
            long postDescriptorSet,
            long offscreenColorImageView,
            long offscreenColorSampler,
            long taaHistoryImageView,
            long taaHistorySampler,
            long velocityImageView,
            long velocitySampler,
            long taaHistoryVelocityImageView,
            long taaHistoryVelocitySampler,
            long planarCaptureImageView,
            long planarCaptureSampler
    ) {
        VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
        imageInfo.get(0)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(offscreenColorImageView)
                .sampler(offscreenColorSampler);
        VkDescriptorImageInfo.Buffer historyInfo = VkDescriptorImageInfo.calloc(1, stack);
        historyInfo.get(0)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(taaHistoryImageView)
                .sampler(taaHistorySampler);
        VkDescriptorImageInfo.Buffer velocityInfo = VkDescriptorImageInfo.calloc(1, stack);
        velocityInfo.get(0)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(velocityImageView)
                .sampler(velocitySampler);
        VkDescriptorImageInfo.Buffer historyVelocityInfo = VkDescriptorImageInfo.calloc(1, stack);
        historyVelocityInfo.get(0)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(taaHistoryVelocityImageView)
                .sampler(taaHistoryVelocitySampler);
        VkDescriptorImageInfo.Buffer planarCaptureInfo = VkDescriptorImageInfo.calloc(1, stack);
        planarCaptureInfo.get(0)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(planarCaptureImageView)
                .sampler(planarCaptureSampler);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(5, stack);
        writes.get(0)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(postDescriptorSet)
                .dstBinding(0)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(imageInfo);
        writes.get(1)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(postDescriptorSet)
                .dstBinding(1)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(historyInfo);
        writes.get(2)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(postDescriptorSet)
                .dstBinding(2)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(velocityInfo);
        writes.get(3)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(postDescriptorSet)
                .dstBinding(3)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(historyVelocityInfo);
        writes.get(4)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(postDescriptorSet)
                .dstBinding(4)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(planarCaptureInfo);
        vkUpdateDescriptorSets(device, writes, null);
    }

    private static long[] createPostFramebuffers(
            VkDevice device,
            MemoryStack stack,
            long postRenderPass,
            long[] swapchainImageViews,
            int swapchainWidth,
            int swapchainHeight
    ) throws EngineException {
        long[] postFramebuffers = new long[swapchainImageViews.length];
        for (int i = 0; i < swapchainImageViews.length; i++) {
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(postRenderPass)
                    .pAttachments(stack.longs(swapchainImageViews[i]))
                    .width(swapchainWidth)
                    .height(swapchainHeight)
                    .layers(1);
            var pFramebuffer = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer);
            if (result != VK_SUCCESS || pFramebuffer.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateFramebuffer(post) failed: " + result, false);
            }
            postFramebuffers[i] = pFramebuffer.get(0);
        }
        return postFramebuffers;
    }

    public record Allocation(
            long offscreenColorImage,
            long offscreenColorMemory,
            long offscreenColorImageView,
            long offscreenColorSampler,
            long taaHistoryImage,
            long taaHistoryMemory,
            long taaHistoryImageView,
            long taaHistorySampler,
            long taaHistoryVelocityImage,
            long taaHistoryVelocityMemory,
            long taaHistoryVelocityImageView,
            long taaHistoryVelocitySampler,
            long planarCaptureImage,
            long planarCaptureMemory,
            long planarCaptureImageView,
            long planarCaptureSampler,
            long postDescriptorSetLayout,
            long postDescriptorPool,
            long postDescriptorSet,
            long postRenderPass,
            long postPipelineLayout,
            long postGraphicsPipeline,
            long[] postFramebuffers
    ) {
    }
}
