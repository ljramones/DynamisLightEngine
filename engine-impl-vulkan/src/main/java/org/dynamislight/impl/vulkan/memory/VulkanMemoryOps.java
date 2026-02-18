package org.dynamislight.impl.vulkan.memory;

import java.nio.ByteBuffer;
import java.util.function.BiFunction;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanBufferAlloc;
import org.dynamislight.impl.vulkan.model.VulkanImageAlloc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkQueueWaitIdle;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;

public final class VulkanMemoryOps {
    private VulkanMemoryOps() {
    }

    public static VulkanBufferAlloc createBuffer(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack,
            int sizeBytes,
            int usage,
            int memoryProperties
    ) throws EngineException {
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(sizeBytes)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        var pBuffer = stack.longs(VK_NULL_HANDLE);
        int createBufferResult = vkCreateBuffer(device, bufferInfo, null, pBuffer);
        if (createBufferResult != VK_SUCCESS || pBuffer.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateBuffer failed: " + createBufferResult, false);
        }
        long buffer = pBuffer.get(0);

        VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
        vkGetBufferMemoryRequirements(device, buffer, memReq);

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReq.size())
                .memoryTypeIndex(findMemoryType(physicalDevice, memReq.memoryTypeBits(), memoryProperties));

        var pMemory = stack.longs(VK_NULL_HANDLE);
        int allocResult = vkAllocateMemory(device, allocInfo, null, pMemory);
        if (allocResult != VK_SUCCESS || pMemory.get(0) == VK_NULL_HANDLE) {
            vkDestroyBuffer(device, buffer, null);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateMemory failed: " + allocResult, false);
        }
        long memory = pMemory.get(0);
        int bindResult = vkBindBufferMemory(device, buffer, memory, 0);
        if (bindResult != VK_SUCCESS) {
            vkFreeMemory(device, memory, null);
            vkDestroyBuffer(device, buffer, null);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkBindBufferMemory failed: " + bindResult, false);
        }
        return new VulkanBufferAlloc(buffer, memory);
    }

    public static VulkanBufferAlloc createDeviceLocalBufferWithStaging(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            long commandPool,
            VkQueue graphicsQueue,
            MemoryStack stack,
            ByteBuffer source,
            int usage,
            BiFunction<String, Integer, EngineException> vkFailure
    ) throws EngineException {
        int sizeBytes = source.remaining();
        VulkanBufferAlloc staging = createBuffer(
                device,
                physicalDevice,
                stack,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        VulkanBufferAlloc deviceLocal = createBuffer(
                device,
                physicalDevice,
                stack,
                sizeBytes,
                usage | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        try {
            uploadToMemory(device, staging.memory(), source, vkFailure);
            copyBuffer(device, commandPool, graphicsQueue, staging.buffer(), deviceLocal.buffer(), sizeBytes, vkFailure);
            return deviceLocal;
        } finally {
            if (staging.buffer() != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, staging.buffer(), null);
            }
            if (staging.memory() != VK_NULL_HANDLE) {
                vkFreeMemory(device, staging.memory(), null);
            }
        }
    }

    public static VulkanImageAlloc createImage(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack,
            int width,
            int height,
            int format,
            int tiling,
            int usage,
            int properties,
            int arrayLayers
    ) throws EngineException {
        return createImage(
                device,
                physicalDevice,
                stack,
                width,
                height,
                format,
                tiling,
                usage,
                properties,
                arrayLayers,
                1
        );
    }

    public static VulkanImageAlloc createImage(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack,
            int width,
            int height,
            int format,
            int tiling,
            int usage,
            int properties,
            int arrayLayers,
            int mipLevels
    ) throws EngineException {
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK10.VK_IMAGE_TYPE_2D)
                .extent(e -> e.width(width).height(height).depth(1))
                .mipLevels(Math.max(1, mipLevels))
                .arrayLayers(Math.max(1, arrayLayers))
                .format(format)
                .tiling(tiling)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .usage(usage)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        var pImage = stack.longs(VK_NULL_HANDLE);
        int createImageResult = VK10.vkCreateImage(device, imageInfo, null, pImage);
        if (createImageResult != VK_SUCCESS || pImage.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateImage failed: " + createImageResult, false);
        }
        long image = pImage.get(0);

        VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
        VK10.vkGetImageMemoryRequirements(device, image, memReq);

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReq.size())
                .memoryTypeIndex(findMemoryType(physicalDevice, memReq.memoryTypeBits(), properties));

        var pMemory = stack.longs(VK_NULL_HANDLE);
        int allocResult = vkAllocateMemory(device, allocInfo, null, pMemory);
        if (allocResult != VK_SUCCESS || pMemory.get(0) == VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, image, null);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateMemory(image) failed: " + allocResult, false);
        }
        long memory = pMemory.get(0);
        int bindResult = VK10.vkBindImageMemory(device, image, memory, 0);
        if (bindResult != VK_SUCCESS) {
            vkFreeMemory(device, memory, null);
            VK10.vkDestroyImage(device, image, null);
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkBindImageMemory failed: " + bindResult, false);
        }
        return new VulkanImageAlloc(image, memory);
    }

    public static void transitionImageLayout(
            VkDevice device,
            long commandPool,
            VkQueue graphicsQueue,
            long image,
            int oldLayout,
            int newLayout,
            BiFunction<String, Integer, EngineException> vkFailure
    ) throws EngineException {
        transitionImageLayout(device, commandPool, graphicsQueue, image, oldLayout, newLayout, 1, 1, vkFailure);
    }

    public static void transitionImageLayout(
            VkDevice device,
            long commandPool,
            VkQueue graphicsQueue,
            long image,
            int oldLayout,
            int newLayout,
            int layerCount,
            int mipLevelCount,
            BiFunction<String, Integer, EngineException> vkFailure
    ) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VkCommandBuffer cmd = beginSingleTimeCommands(device, commandPool, stack, vkFailure);
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(Math.max(1, mipLevelCount))
                    .baseArrayLayer(0)
                    .layerCount(Math.max(1, layerCount));

            int sourceStage;
            int destinationStage;
            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                barrier.get(0).srcAccessMask(0);
                barrier.get(0).dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                    && newLayout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                barrier.get(0).srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.get(0).dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);
                sourceStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Unsupported image layout transition", false);
            }

            VK10.vkCmdPipelineBarrier(
                    cmd,
                    sourceStage,
                    destinationStage,
                    0,
                    null,
                    null,
                    barrier
            );
            endSingleTimeCommands(device, commandPool, graphicsQueue, stack, cmd, vkFailure);
        }
    }

    public static void copyBufferToImage(
            VkDevice device,
            long commandPool,
            VkQueue graphicsQueue,
            long buffer,
            long image,
            int width,
            int height,
            BiFunction<String, Integer, EngineException> vkFailure
    ) throws EngineException {
        copyBufferToImageLayers(device, commandPool, graphicsQueue, buffer, image, width, height, 1, width * height * 4, vkFailure);
    }

    public static void copyBufferToImageLayers(
            VkDevice device,
            long commandPool,
            VkQueue graphicsQueue,
            long buffer,
            long image,
            int width,
            int height,
            int layerCount,
            long bytesPerLayer,
            BiFunction<String, Integer, EngineException> vkFailure
    ) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VkCommandBuffer cmd = beginSingleTimeCommands(device, commandPool, stack, vkFailure);
            int safeLayers = Math.max(1, layerCount);
            long safeLayerBytes = Math.max(1L, bytesPerLayer);
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(safeLayers, stack);
            for (int layer = 0; layer < safeLayers; layer++) {
                region.get(layer)
                        .bufferOffset(safeLayerBytes * layer)
                        .bufferRowLength(0)
                        .bufferImageHeight(0);
                region.get(layer).imageSubresource()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(0)
                        .baseArrayLayer(layer)
                        .layerCount(1);
                region.get(layer).imageOffset().set(0, 0, 0);
                region.get(layer).imageExtent().set(width, height, 1);
            }
            VK10.vkCmdCopyBufferToImage(cmd, buffer, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
            endSingleTimeCommands(device, commandPool, graphicsQueue, stack, cmd, vkFailure);
        }
    }

    public static void copyBuffer(
            VkDevice device,
            long commandPool,
            VkQueue graphicsQueue,
            long srcBuffer,
            long dstBuffer,
            int sizeBytes,
            BiFunction<String, Integer, EngineException> vkFailure
    ) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer pCommandBuffer = stack.mallocPointer(1);
            int allocResult = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
            if (allocResult != VK_SUCCESS) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateCommandBuffers(copy) failed: " + allocResult, false);
            }
            VkCommandBuffer cmd = new VkCommandBuffer(pCommandBuffer.get(0), device);

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            int beginResult = vkBeginCommandBuffer(cmd, beginInfo);
            if (beginResult != VK_SUCCESS) {
                throw vkFailure.apply("vkBeginCommandBuffer(copy)", beginResult);
            }

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.get(0).srcOffset(0).dstOffset(0).size(sizeBytes);
            vkCmdCopyBuffer(cmd, srcBuffer, dstBuffer, copyRegion);

            int endResult = vkEndCommandBuffer(cmd);
            if (endResult != VK_SUCCESS) {
                throw vkFailure.apply("vkEndCommandBuffer(copy)", endResult);
            }

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(cmd.address()));
            int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
            if (submitResult != VK_SUCCESS) {
                throw vkFailure.apply("vkQueueSubmit(copy)", submitResult);
            }
            int waitResult = vkQueueWaitIdle(graphicsQueue);
            if (waitResult != VK_SUCCESS) {
                throw vkFailure.apply("vkQueueWaitIdle(copy)", waitResult);
            }
            vkFreeCommandBuffers(device, commandPool, stack.pointers(cmd.address()));
        }
    }

    public static void uploadToMemory(
            VkDevice device,
            long memory,
            ByteBuffer source,
            BiFunction<String, Integer, EngineException> vkFailure
    ) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int mapResult = vkMapMemory(device, memory, 0, source.remaining(), 0, pData);
            if (mapResult != VK_SUCCESS) {
                throw vkFailure.apply("vkMapMemory", mapResult);
            }
            memCopy(memAddress(source), pData.get(0), source.remaining());
            vkUnmapMemory(device, memory);
        }
    }

    private static int findMemoryType(VkPhysicalDevice physicalDevice, int typeFilter, int properties) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
            for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
                boolean typeMatch = (typeFilter & (1 << i)) != 0;
                boolean propsMatch = (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties;
                if (typeMatch && propsMatch) {
                    return i;
                }
            }
        }
        throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "No suitable Vulkan memory type found", false);
    }

    private static VkCommandBuffer beginSingleTimeCommands(
            VkDevice device,
            long commandPool,
            MemoryStack stack,
            BiFunction<String, Integer, EngineException> vkFailure
    ) throws EngineException {
        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);
        PointerBuffer pCommandBuffer = stack.mallocPointer(1);
        int allocResult = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
        if (allocResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateCommandBuffers(one-shot) failed: " + allocResult, false);
        }
        VkCommandBuffer cmd = new VkCommandBuffer(pCommandBuffer.get(0), device);
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        int beginResult = vkBeginCommandBuffer(cmd, beginInfo);
        if (beginResult != VK_SUCCESS) {
            throw vkFailure.apply("vkBeginCommandBuffer(one-shot)", beginResult);
        }
        return cmd;
    }

    private static void endSingleTimeCommands(
            VkDevice device,
            long commandPool,
            VkQueue graphicsQueue,
            MemoryStack stack,
            VkCommandBuffer cmd,
            BiFunction<String, Integer, EngineException> vkFailure
    ) throws EngineException {
        int endResult = vkEndCommandBuffer(cmd);
        if (endResult != VK_SUCCESS) {
            throw vkFailure.apply("vkEndCommandBuffer(one-shot)", endResult);
        }
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(cmd.address()));
        int submitResult = vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
        if (submitResult != VK_SUCCESS) {
            throw vkFailure.apply("vkQueueSubmit(one-shot)", submitResult);
        }
        int waitResult = vkQueueWaitIdle(graphicsQueue);
        if (waitResult != VK_SUCCESS) {
            throw vkFailure.apply("vkQueueWaitIdle(one-shot)", waitResult);
        }
        vkFreeCommandBuffers(device, commandPool, stack.pointers(cmd.address()));
    }
}
