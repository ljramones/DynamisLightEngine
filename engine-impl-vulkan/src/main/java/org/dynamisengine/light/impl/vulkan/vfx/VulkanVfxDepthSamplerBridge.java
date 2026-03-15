package org.dynamisengine.light.impl.vulkan.vfx;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

/**
 * Bridges depth buffer state for VFX soft-particle sampling.
 */
public final class VulkanVfxDepthSamplerBridge {
    private static final int DEPTH_BINDING = 4;

    private VulkanVfxDepthSamplerBridge() {
    }

    public static void transitionForVfxRead(VkCommandBuffer commandBuffer, long depthImage) {
        if (commandBuffer == null || depthImage == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = depthBarrier(
                    stack,
                    depthImage,
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            );
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    barrier
            );
        }
    }

    public static void transitionAfterVfxRead(VkCommandBuffer commandBuffer, long depthImage) {
        if (commandBuffer == null || depthImage == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = depthBarrier(
                    stack,
                    depthImage,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
            );
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                    0,
                    null,
                    null,
                    barrier
            );
        }
    }

    public static void writeDepthDescriptor(
            VkDevice device,
            long descriptorSet,
            long depthImageView,
            long depthSampler,
            int frameIndex
    ) {
        if (device == null
                || descriptorSet == VK_NULL_HANDLE
                || depthImageView == VK_NULL_HANDLE
                || depthSampler == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer write = depthDescriptorWrite(
                    stack,
                    descriptorSet,
                    depthImageView,
                    depthSampler
            );
            vkUpdateDescriptorSets(device, write, null);
        }
    }

    static VkImageMemoryBarrier.Buffer depthBarrier(
            MemoryStack stack,
            long depthImage,
            int oldLayout,
            int newLayout
    ) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(depthImage);
        barrier.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        return barrier;
    }

    static VkWriteDescriptorSet.Buffer depthDescriptorWrite(
            MemoryStack stack,
            long descriptorSet,
            long depthImageView,
            long depthSampler
    ) {
        VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                .sampler(depthSampler)
                .imageView(depthImageView)
                .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        return VkWriteDescriptorSet.calloc(1, stack)
                .sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(DEPTH_BINDING)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(imageInfo);
    }
}
