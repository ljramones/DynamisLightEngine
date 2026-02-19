package org.dynamislight.impl.vulkan.command;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;

import static org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.*;
import static org.lwjgl.vulkan.VK10.*;

import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.MeshDrawCmd;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.RenderPassInputs;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.ShadowPassInputs;

final class VulkanShadowPassRecorderCore {
    private VulkanShadowPassRecorderCore() {
    }

    static void recordShadowPasses(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            ShadowPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        if (in.shadowMomentPipelineRequested()
                && in.shadowMomentImage() != VK_NULL_HANDLE) {
            int mipLevels = Math.max(1, in.shadowMomentMipLevels());
            int oldLayout = in.shadowMomentInitialized()
                    ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                    : VK_IMAGE_LAYOUT_UNDEFINED;
            VkImageMemoryBarrier.Buffer toColorAttachment = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(in.shadowMomentInitialized() ? VK_ACCESS_SHADER_READ_BIT : 0)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .oldLayout(oldLayout)
                    .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.shadowMomentImage());
            toColorAttachment.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(mipLevels)
                    .baseArrayLayer(0)
                    .layerCount(in.maxShadowMatrices());
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                    commandBuffer,
                    in.shadowMomentInitialized() ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    0,
                    null,
                    null,
                    toColorAttachment
            );
        }

        int shadowPassCount = shadowPassCount(in.toRenderPassInputsShadowView());
        if (in.shadowEnabled()
                && in.shadowRenderPass() != VK_NULL_HANDLE
                && in.shadowPipeline() != VK_NULL_HANDLE
                && in.frameDescriptorSet() != VK_NULL_HANDLE
                && !meshes.isEmpty()
                && in.shadowFramebuffers().length >= shadowPassCount) {
            for (int cascadeIndex = 0; cascadeIndex < shadowPassCount; cascadeIndex++) {
                int shadowClearCount = in.shadowMomentPipelineRequested() ? 2 : 1;
                VkClearValue.Buffer shadowClearValues = VkClearValue.calloc(shadowClearCount, stack);
                shadowClearValues.get(0).depthStencil().depth(1.0f).stencil(0);
                if (in.shadowMomentPipelineRequested()) {
                    shadowClearValues.get(1).color().float32(0, 1.0f);
                    shadowClearValues.get(1).color().float32(1, 1.0f);
                    shadowClearValues.get(1).color().float32(2, 0.0f);
                    shadowClearValues.get(1).color().float32(3, 0.0f);
                }
                VkRenderPassBeginInfo shadowPassInfo = VkRenderPassBeginInfo.calloc(stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                        .renderPass(in.shadowRenderPass())
                        .framebuffer(in.shadowFramebuffers()[cascadeIndex])
                        .pClearValues(shadowClearValues);
                shadowPassInfo.renderArea()
                        .offset(it -> it.set(0, 0))
                        .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack).set(in.shadowMapResolution(), in.shadowMapResolution()));
                vkCmdBeginRenderPass(commandBuffer, shadowPassInfo, VK_SUBPASS_CONTENTS_INLINE);
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, in.shadowPipeline());
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        in.shadowPipelineLayout(),
                        0,
                        stack.longs(in.frameDescriptorSet()),
                        stack.ints(dynamicUniformOffset.applyAsInt(0))
                );
                ByteBuffer cascadePush = stack.malloc(Integer.BYTES);
                cascadePush.putInt(0, cascadeIndex);
                vkCmdPushConstants(commandBuffer, in.shadowPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, cascadePush);
                for (int meshIndex = 0; meshIndex < in.drawCount() && meshIndex < meshes.size(); meshIndex++) {
                    MeshDrawCmd mesh = meshes.get(meshIndex);
                    vkCmdBindDescriptorSets(
                            commandBuffer,
                            VK_PIPELINE_BIND_POINT_GRAPHICS,
                            in.shadowPipelineLayout(),
                            0,
                            stack.longs(in.frameDescriptorSet()),
                            stack.ints(dynamicUniformOffset.applyAsInt(meshIndex))
                    );
                    vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(mesh.vertexBuffer()), stack.longs(0));
                    vkCmdBindIndexBuffer(commandBuffer, mesh.indexBuffer(), 0, VK_INDEX_TYPE_UINT32);
                    vkCmdDrawIndexed(commandBuffer, mesh.indexCount(), 1, 0, 0, 0);
                }
                vkCmdEndRenderPass(commandBuffer);
            }
        }

        if (in.shadowMomentPipelineRequested()
                && in.shadowMomentImage() != VK_NULL_HANDLE) {
            int mipLevels = Math.max(1, in.shadowMomentMipLevels());
            if (mipLevels > 1) {
                VkImageMemoryBarrier.Buffer baseToSrc = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(in.shadowMomentImage());
                baseToSrc.get(0).subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(in.maxShadowMatrices());
                VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0,
                        null,
                        null,
                        baseToSrc
                );
                VkImageMemoryBarrier.Buffer restToDst = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(in.shadowMomentInitialized() ? VK_ACCESS_SHADER_READ_BIT : 0)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .oldLayout(in.shadowMomentInitialized()
                                ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                                : VK_IMAGE_LAYOUT_UNDEFINED)
                        .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(in.shadowMomentImage());
                restToDst.get(0).subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(1)
                        .levelCount(mipLevels - 1)
                        .baseArrayLayer(0)
                        .layerCount(in.maxShadowMatrices());
                VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                        commandBuffer,
                        in.shadowMomentInitialized() ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0,
                        null,
                        null,
                        restToDst
                );
                int width = Math.max(1, in.shadowMapResolution());
                int height = Math.max(1, in.shadowMapResolution());
                for (int mip = 1; mip < mipLevels; mip++) {
                    int srcWidth = width;
                    int srcHeight = height;
                    width = Math.max(1, width >> 1);
                    height = Math.max(1, height >> 1);
                    VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
                    blit.get(0).srcSubresource()
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(mip - 1)
                            .baseArrayLayer(0)
                            .layerCount(in.maxShadowMatrices());
                    blit.get(0).srcOffsets(0).set(0, 0, 0);
                    blit.get(0).srcOffsets(1).set(srcWidth, srcHeight, 1);
                    blit.get(0).dstSubresource()
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(mip)
                            .baseArrayLayer(0)
                            .layerCount(in.maxShadowMatrices());
                    blit.get(0).dstOffsets(0).set(0, 0, 0);
                    blit.get(0).dstOffsets(1).set(width, height, 1);
                    vkCmdBlitImage(
                            commandBuffer,
                            in.shadowMomentImage(),
                            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            in.shadowMomentImage(),
                            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            blit,
                            VK10.VK_FILTER_LINEAR
                    );
                    if (mip < mipLevels - 1) {
                        VkImageMemoryBarrier.Buffer mipToSrc = VkImageMemoryBarrier.calloc(1, stack)
                                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                                .dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                .image(in.shadowMomentImage());
                        mipToSrc.get(0).subresourceRange()
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(mip)
                                .levelCount(1)
                                .baseArrayLayer(0)
                                .layerCount(in.maxShadowMatrices());
                        VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                                commandBuffer,
                                VK_PIPELINE_STAGE_TRANSFER_BIT,
                                VK_PIPELINE_STAGE_TRANSFER_BIT,
                                0,
                                null,
                                null,
                                mipToSrc
                        );
                    }
                }
            }
            if (mipLevels > 1) {
                VkImageMemoryBarrier.Buffer srcMipsToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                        .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(in.shadowMomentImage());
                srcMipsToShaderRead.get(0).subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(mipLevels - 1)
                        .baseArrayLayer(0)
                        .layerCount(in.maxShadowMatrices());
                VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        0,
                        null,
                        null,
                        srcMipsToShaderRead
                );
                VkImageMemoryBarrier.Buffer lastMipToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(in.shadowMomentImage());
                lastMipToShaderRead.get(0).subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(mipLevels - 1)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(in.maxShadowMatrices());
                VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        0,
                        null,
                        null,
                        lastMipToShaderRead
                );
            } else {
                VkImageMemoryBarrier.Buffer toShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(in.shadowMomentImage());
                toShaderRead.get(0).subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(in.maxShadowMatrices());
                VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        0,
                        null,
                        null,
                        toShaderRead
                );
            }
        }
    }

    static int shadowPassCount(RenderPassInputs in) {
        int requested = Math.max(1, in.shadowCascadeCount());
        int maxCascades = Math.max(1, in.maxShadowCascades());
        int clamped = Math.min(Math.min(in.maxShadowMatrices(), maxCascades), requested);
        if (in.pointShadowEnabled()) {
            int pointFloor = Math.max(1, Math.min(in.pointShadowFaces(), in.maxShadowMatrices()));
            return Math.max(pointFloor, clamped);
        }
        return clamped;
    }
}
