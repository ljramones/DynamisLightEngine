package org.dynamisengine.light.impl.vulkan.vfx;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

/**
 * Bridges normal/G-buffer image state for decal-style VFX sampling.
 */
public final class VulkanVfxGBufferBridge {
    private static final int NORMAL_BINDING = 6;

    private VulkanVfxGBufferBridge() {
    }

    public static void transitionNormalForVfxRead(VkCommandBuffer commandBuffer, long normalImage) {
        if (commandBuffer == null || normalImage == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = normalBarrier(
                    stack,
                    normalImage,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            );
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    barrier
            );
        }
    }

    public static void transitionNormalAfterVfxRead(VkCommandBuffer commandBuffer, long normalImage) {
        if (commandBuffer == null || normalImage == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = normalBarrier(
                    stack,
                    normalImage,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
            );
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    0,
                    null,
                    null,
                    barrier
            );
        }
    }

    public static void writeNormalDescriptor(
            VkDevice device,
            long descriptorSet,
            long gBufferNormalImageView,
            long sampler,
            int frameIndex
    ) {
        if (device == null
                || descriptorSet == VK_NULL_HANDLE
                || gBufferNormalImageView == VK_NULL_HANDLE
                || sampler == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer write = normalDescriptorWrite(
                    stack,
                    descriptorSet,
                    gBufferNormalImageView,
                    sampler
            );
            vkUpdateDescriptorSets(device, write, null);
        }
    }

    static VkImageMemoryBarrier.Buffer normalBarrier(
            MemoryStack stack,
            long normalImage,
            int oldLayout,
            int newLayout
    ) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(normalImage);
        barrier.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        return barrier;
    }

    static VkWriteDescriptorSet.Buffer normalDescriptorWrite(
            MemoryStack stack,
            long descriptorSet,
            long imageView,
            long sampler
    ) {
        VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                .sampler(sampler)
                .imageView(imageView)
                .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        return VkWriteDescriptorSet.calloc(1, stack)
                .sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(NORMAL_BINDING)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(imageInfo);
    }
}
