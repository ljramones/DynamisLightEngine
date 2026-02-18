package org.dynamislight.impl.vulkan.texture;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.memory.VulkanMemoryOps;
import org.dynamislight.impl.vulkan.model.VulkanBufferAlloc;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.model.VulkanImageAlloc;
import org.dynamislight.impl.vulkan.model.VulkanTexturePixelData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D_ARRAY;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;

public final class VulkanTextureResourceOps {
    private VulkanTextureResourceOps() {
    }

    @FunctionalInterface
    public interface FailureFactory {
        EngineException failure(String operation, int result);
    }

    public record Context(
            org.lwjgl.vulkan.VkDevice device,
            org.lwjgl.vulkan.VkPhysicalDevice physicalDevice,
            long commandPool,
            org.lwjgl.vulkan.VkQueue graphicsQueue,
            FailureFactory vkFailure
    ) {
    }

    public static Set<VulkanGpuTexture> collectLiveTextures(
            List<VulkanGpuMesh> meshes,
            VulkanGpuTexture iblIrr,
            VulkanGpuTexture iblRad,
            VulkanGpuTexture iblBrdf
    ) {
        Set<VulkanGpuTexture> textures = new HashSet<>();
        for (VulkanGpuMesh mesh : meshes) {
            textures.add(mesh.albedoTexture);
            textures.add(mesh.normalTexture);
            textures.add(mesh.metallicRoughnessTexture);
            textures.add(mesh.occlusionTexture);
        }
        textures.add(iblIrr);
        textures.add(iblRad);
        textures.add(iblBrdf);
        return textures;
    }

    public static void destroyTextures(org.lwjgl.vulkan.VkDevice device, Set<VulkanGpuTexture> textures) {
        if (device == null || textures == null || textures.isEmpty()) {
            return;
        }
        for (VulkanGpuTexture texture : textures) {
            if (texture == null) {
                continue;
            }
            if (texture.sampler() != VK_NULL_HANDLE) {
                VK10.vkDestroySampler(device, texture.sampler(), null);
            }
            if (texture.view() != VK_NULL_HANDLE) {
                vkDestroyImageView(device, texture.view(), null);
            }
            if (texture.image() != VK_NULL_HANDLE) {
                VK10.vkDestroyImage(device, texture.image(), null);
            }
            if (texture.memory() != VK_NULL_HANDLE) {
                vkFreeMemory(device, texture.memory(), null);
            }
        }
    }

    public static VulkanGpuTexture createTextureFromPath(Path texturePath, boolean normalMap, Context context) throws EngineException {
        VulkanTexturePixelData pixels = VulkanTexturePixelLoader.loadTexturePixels(texturePath);
        if (pixels == null) {
            ByteBuffer data = memAlloc(4);
            if (normalMap) {
                data.put((byte) 0x80).put((byte) 0x80).put((byte) 0xFF).put((byte) 0xFF).flip();
            } else {
                data.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
            }
            pixels = new VulkanTexturePixelData(data, 1, 1);
        }
        try {
            return createTextureFromPixels(pixels, context);
        } finally {
            memFree(pixels.data());
        }
    }

    public static VulkanGpuTexture createTextureFromPixels(VulkanTexturePixelData pixels, Context context) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VulkanBufferAlloc staging = VulkanMemoryOps.createBuffer(
                    context.device(),
                    context.physicalDevice(),
                    stack,
                    pixels.data().remaining(),
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );
            try {
                VulkanMemoryOps.uploadToMemory(context.device(), staging.memory(), pixels.data(), context.vkFailure()::failure);
                VulkanImageAlloc imageAlloc = VulkanMemoryOps.createImage(
                        context.device(),
                        context.physicalDevice(),
                        stack,
                        pixels.width(),
                        pixels.height(),
                        VK10.VK_FORMAT_R8G8B8A8_SRGB,
                        VK10.VK_IMAGE_TILING_OPTIMAL,
                        VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                        1
                );
                VulkanMemoryOps.transitionImageLayout(
                        context.device(),
                        context.commandPool(),
                        context.graphicsQueue(),
                        imageAlloc.image(),
                        VK_IMAGE_LAYOUT_UNDEFINED,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        context.vkFailure()::failure
                );
                VulkanMemoryOps.copyBufferToImage(
                        context.device(),
                        context.commandPool(),
                        context.graphicsQueue(),
                        staging.buffer(),
                        imageAlloc.image(),
                        pixels.width(),
                        pixels.height(),
                        context.vkFailure()::failure
                );
                VulkanMemoryOps.transitionImageLayout(
                        context.device(),
                        context.commandPool(),
                        context.graphicsQueue(),
                        imageAlloc.image(),
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        context.vkFailure()::failure
                );

                long imageView = createImageView(context.device(), stack, imageAlloc.image(), VK10.VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT);
                long sampler = createSampler(context.device(), stack);
                return new VulkanGpuTexture(imageAlloc.image(), imageAlloc.memory(), imageView, sampler, (long) pixels.width() * pixels.height() * 4L);
            } finally {
                if (staging.buffer() != VK_NULL_HANDLE) {
                    vkDestroyBuffer(context.device(), staging.buffer(), null);
                }
                if (staging.memory() != VK_NULL_HANDLE) {
                    vkFreeMemory(context.device(), staging.memory(), null);
                }
            }
        }
    }

    public static VulkanGpuTexture createTextureArrayFromPixels(
            List<VulkanTexturePixelData> layers,
            Context context
    ) throws EngineException {
        if (layers == null || layers.isEmpty()) {
            throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "Texture array requires at least one layer", false);
        }
        VulkanTexturePixelData first = layers.get(0);
        if (first == null || first.width() <= 0 || first.height() <= 0) {
            throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "Texture array first layer is invalid", false);
        }
        int width = first.width();
        int height = first.height();
        int layerCount = layers.size();
        int bytesPerLayer = width * height * 4;
        for (int i = 0; i < layerCount; i++) {
            VulkanTexturePixelData layer = layers.get(i);
            if (layer == null || layer.width() != width || layer.height() != height || layer.data().remaining() != bytesPerLayer) {
                throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "Texture array layers must match size and format", false);
            }
        }
        long totalBytes = (long) bytesPerLayer * layerCount;
        if (totalBytes > Integer.MAX_VALUE) {
            throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "Texture array upload exceeds supported staging size", false);
        }
        ByteBuffer interleaved = memAlloc((int) totalBytes);
        for (int i = 0; i < layerCount; i++) {
            ByteBuffer src = layers.get(i).data().duplicate();
            src.position(0);
            src.limit(bytesPerLayer);
            interleaved.put(src);
        }
        interleaved.flip();

        try (MemoryStack stack = stackPush()) {
            VulkanBufferAlloc staging = VulkanMemoryOps.createBuffer(
                    context.device(),
                    context.physicalDevice(),
                    stack,
                    (int) totalBytes,
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );
            try {
                VulkanMemoryOps.uploadToMemory(context.device(), staging.memory(), interleaved, context.vkFailure()::failure);
                VulkanImageAlloc imageAlloc = VulkanMemoryOps.createImage(
                        context.device(),
                        context.physicalDevice(),
                        stack,
                        width,
                        height,
                        VK10.VK_FORMAT_R8G8B8A8_SRGB,
                        VK10.VK_IMAGE_TILING_OPTIMAL,
                        VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                        layerCount
                );
                VulkanMemoryOps.transitionImageLayout(
                        context.device(),
                        context.commandPool(),
                        context.graphicsQueue(),
                        imageAlloc.image(),
                        VK_IMAGE_LAYOUT_UNDEFINED,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        layerCount,
                        1,
                        context.vkFailure()::failure
                );
                VulkanMemoryOps.copyBufferToImageLayers(
                        context.device(),
                        context.commandPool(),
                        context.graphicsQueue(),
                        staging.buffer(),
                        imageAlloc.image(),
                        width,
                        height,
                        layerCount,
                        bytesPerLayer,
                        context.vkFailure()::failure
                );
                VulkanMemoryOps.transitionImageLayout(
                        context.device(),
                        context.commandPool(),
                        context.graphicsQueue(),
                        imageAlloc.image(),
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        layerCount,
                        1,
                        context.vkFailure()::failure
                );

                long imageView = createImageView(
                        context.device(),
                        stack,
                        imageAlloc.image(),
                        VK10.VK_FORMAT_R8G8B8A8_SRGB,
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_VIEW_TYPE_2D_ARRAY,
                        layerCount
                );
                long sampler = createSampler(context.device(), stack);
                return new VulkanGpuTexture(imageAlloc.image(), imageAlloc.memory(), imageView, sampler, totalBytes);
            } finally {
                if (staging.buffer() != VK_NULL_HANDLE) {
                    vkDestroyBuffer(context.device(), staging.buffer(), null);
                }
                if (staging.memory() != VK_NULL_HANDLE) {
                    vkFreeMemory(context.device(), staging.memory(), null);
                }
            }
        } finally {
            memFree(interleaved);
        }
    }

    private static long createImageView(
            org.lwjgl.vulkan.VkDevice device,
            MemoryStack stack,
            long image,
            int format,
            int aspectMask
    ) throws EngineException {
        return createImageView(device, stack, image, format, aspectMask, VK_IMAGE_VIEW_TYPE_2D, 1);
    }

    private static long createImageView(
            org.lwjgl.vulkan.VkDevice device,
            MemoryStack stack,
            long image,
            int format,
            int aspectMask,
            int viewType,
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
                .baseArrayLayer(0)
                .layerCount(Math.max(1, layerCount));
        var pView = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateImageView(device, viewInfo, null, pView);
        if (result != VK_SUCCESS || pView.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateImageView(texture) failed: " + result, false);
        }
        return pView.get(0);
    }

    private static long createSampler(org.lwjgl.vulkan.VkDevice device, MemoryStack stack) throws EngineException {
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
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSampler failed: " + result, false);
        }
        return pSampler.get(0);
    }
}
