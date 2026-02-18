package org.dynamislight.impl.vulkan.command;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdBlitImage;
import static org.lwjgl.vulkan.VK10.vkCmdClearColorImage;
import static org.lwjgl.vulkan.VK10.vkCmdCopyImage;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;

public final class VulkanRenderCommandRecorder {
    private VulkanRenderCommandRecorder() {
    }

    public static int beginOneShot(VkCommandBuffer commandBuffer, MemoryStack stack) {
        var beginInfo = org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        return vkBeginCommandBuffer(commandBuffer, beginInfo);
    }

    public static int end(VkCommandBuffer commandBuffer) {
        return vkEndCommandBuffer(commandBuffer);
    }

    public static void recordShadowAndMainPasses(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            RenderPassInputs in,
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
            vkCmdPipelineBarrier(
                    commandBuffer,
                    in.shadowMomentInitialized() ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    0,
                    null,
                    null,
                    toColorAttachment
            );
        }

        int shadowPassCount = shadowPassCount(in);
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
                vkCmdPipelineBarrier(
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
                vkCmdPipelineBarrier(
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
                        vkCmdPipelineBarrier(
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
                vkCmdPipelineBarrier(
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
                vkCmdPipelineBarrier(
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
                vkCmdPipelineBarrier(
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

        VkClearValue.Buffer clearValues = VkClearValue.calloc(3, stack);
        clearValues.get(0).color().float32(0, 0.08f);
        clearValues.get(0).color().float32(1, 0.09f);
        clearValues.get(0).color().float32(2, 0.12f);
        clearValues.get(0).color().float32(3, 1.0f);
        clearValues.get(1).color().float32(0, 0.5f);
        clearValues.get(1).color().float32(1, 0.5f);
        clearValues.get(1).color().float32(2, 0.5f);
        clearValues.get(1).color().float32(3, 1.0f);
        clearValues.get(2).depthStencil().depth(1.0f).stencil(0);

        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(in.renderPass())
                .framebuffer(in.framebuffer())
                .pClearValues(clearValues);
        renderPassInfo.renderArea()
                .offset(it -> it.set(0, 0))
                .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack).set(in.swapchainWidth(), in.swapchainHeight()));

        vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, in.graphicsPipeline());
        for (int meshIndex = 0; meshIndex < in.drawCount() && meshIndex < meshes.size(); meshIndex++) {
            MeshDrawCmd mesh = meshes.get(meshIndex);
            if (in.frameDescriptorSet() != VK_NULL_HANDLE && mesh.textureDescriptorSet() != VK_NULL_HANDLE) {
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        in.pipelineLayout(),
                        0,
                        stack.longs(in.frameDescriptorSet(), mesh.textureDescriptorSet()),
                        stack.ints(dynamicUniformOffset.applyAsInt(meshIndex))
                );
            }
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(mesh.vertexBuffer()), stack.longs(0));
            vkCmdBindIndexBuffer(commandBuffer, mesh.indexBuffer(), 0, VK_INDEX_TYPE_UINT32);
            vkCmdDrawIndexed(commandBuffer, mesh.indexCount(), 1, 0, 0, 0);
        }
        if (meshes.isEmpty()) {
            vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        }
        vkCmdEndRenderPass(commandBuffer);
    }

    static int shadowPassCount(RenderPassInputs in) {
        int requested = Math.max(1, in.shadowCascadeCount());
        int maxCascades = Math.max(1, in.maxShadowCascades());
        int clamped = Math.min(Math.min(in.maxShadowMatrices(), maxCascades), requested);
        if (in.pointShadowEnabled()) {
            // Legacy single-point mode needs at least one full cubemap face set.
            int pointFloor = Math.max(1, Math.min(in.pointShadowFaces(), in.maxShadowMatrices()));
            return Math.max(pointFloor, clamped);
        }
        return clamped;
    }

    public static PostCompositeState executePostCompositePass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            PostCompositeInputs in
    ) {
        if (in.postRenderPass() == VK_NULL_HANDLE
                || in.postGraphicsPipeline() == VK_NULL_HANDLE
                || in.postPipelineLayout() == VK_NULL_HANDLE
                || in.postDescriptorSet() == VK_NULL_HANDLE
                || in.postFramebuffers().length <= in.imageIndex()
                || in.offscreenColorImage() == VK_NULL_HANDLE
                || in.velocityImage() == VK_NULL_HANDLE) {
            return new PostCompositeState(in.postIntermediateInitialized(), in.taaHistoryInitialized());
        }

        VkImageMemoryBarrier.Buffer swapToTransferSrc = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(in.swapchainImage());
        swapToTransferSrc.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                null,
                null,
                swapToTransferSrc
        );

        int intermediateOldLayout = in.postIntermediateInitialized()
                ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                : VK_IMAGE_LAYOUT_UNDEFINED;
        VkImageMemoryBarrier.Buffer intermediateToTransferDst = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(in.postIntermediateInitialized() ? VK10.VK_ACCESS_SHADER_READ_BIT : 0)
                .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                .oldLayout(intermediateOldLayout)
                .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(in.offscreenColorImage());
        intermediateToTransferDst.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(
                commandBuffer,
                in.postIntermediateInitialized() ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                null,
                null,
                intermediateToTransferDst
        );

        VkImageCopy.Buffer copyRegion = VkImageCopy.calloc(1, stack);
        copyRegion.get(0)
                .srcSubresource(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1))
                .srcOffset(it -> it.set(0, 0, 0))
                .dstSubresource(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1))
                .dstOffset(it -> it.set(0, 0, 0))
                .extent(it -> it.set(in.swapchainWidth(), in.swapchainHeight(), 1));
        vkCmdCopyImage(
                commandBuffer,
                in.swapchainImage(),
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                in.offscreenColorImage(),
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                copyRegion
        );

        VkImageMemoryBarrier.Buffer intermediateToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(in.offscreenColorImage());
        intermediateToShaderRead.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                0,
                null,
                null,
                intermediateToShaderRead
        );

        if (in.taaEnabled() && in.taaHistoryImage() != VK_NULL_HANDLE) {
            VkImageMemoryBarrier.Buffer historyToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(0)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(in.taaHistoryInitialized() ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.taaHistoryImage());
            historyToShaderRead.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    in.taaHistoryInitialized() ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    historyToShaderRead
            );
        }
        if (in.taaEnabled() && in.taaHistoryVelocityImage() != VK_NULL_HANDLE) {
            VkImageMemoryBarrier.Buffer historyVelocityToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(0)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(in.taaHistoryInitialized() ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.taaHistoryVelocityImage());
            historyVelocityToShaderRead.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    in.taaHistoryInitialized() ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    historyVelocityToShaderRead
            );
        }
        boolean planarCapturePassRequested = (in.reflectionsMode() & (1 << 18)) != 0;
        if (planarCapturePassRequested && in.taaHistoryVelocityImage() != VK_NULL_HANDLE) {
            VkImageMemoryBarrier.Buffer planarCaptureDst = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(in.taaHistoryInitialized() ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.taaHistoryVelocityImage());
            planarCaptureDst.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    null,
                    null,
                    planarCaptureDst
            );

            VkImageMemoryBarrier.Buffer planarCaptureSrc = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.offscreenColorImage());
            planarCaptureSrc.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    null,
                    null,
                    planarCaptureSrc
            );

            vkCmdCopyImage(
                    commandBuffer,
                    in.offscreenColorImage(),
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    in.taaHistoryVelocityImage(),
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    copyRegion
            );

            VkImageMemoryBarrier.Buffer planarCaptureDstShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.taaHistoryVelocityImage());
            planarCaptureDstShaderRead.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    planarCaptureDstShaderRead
            );

            VkImageMemoryBarrier.Buffer planarCaptureSrcShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.offscreenColorImage());
            planarCaptureSrcShaderRead.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    planarCaptureSrcShaderRead
            );
        }

        VkImageMemoryBarrier.Buffer velocityToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(in.velocityImage());
        velocityToShaderRead.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                0,
                null,
                null,
                velocityToShaderRead
        );

        VkImageMemoryBarrier.Buffer swapToColorAttachment = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(in.swapchainImage());
        swapToColorAttachment.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0,
                null,
                null,
                swapToColorAttachment
        );

        VkClearValue.Buffer clear = VkClearValue.calloc(1, stack);
        clear.get(0).color().float32(0, 0.08f);
        clear.get(0).color().float32(1, 0.09f);
        clear.get(0).color().float32(2, 0.12f);
        clear.get(0).color().float32(3, 1.0f);
        VkRenderPassBeginInfo postPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(in.postRenderPass())
                .framebuffer(in.postFramebuffers()[in.imageIndex()])
                .pClearValues(clear);
        postPassInfo.renderArea()
                .offset(it -> it.set(0, 0))
                .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack).set(in.swapchainWidth(), in.swapchainHeight()));

        vkCmdBeginRenderPass(commandBuffer, postPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, in.postGraphicsPipeline());
        vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                in.postPipelineLayout(),
                0,
                stack.longs(in.postDescriptorSet()),
                null
        );
        ByteBuffer postPush = stack.malloc(32 * Float.BYTES);
        postPush.asFloatBuffer().put(new float[]{
                in.tonemapEnabled() ? 1f : 0f, in.tonemapExposure(), in.tonemapGamma(), in.ssaoEnabled() ? 1f : 0f,
                in.bloomEnabled() ? 1f : 0f, in.bloomThreshold(), in.bloomStrength(), in.ssaoStrength(),
                in.ssaoRadius(), in.ssaoBias(), in.ssaoPower(), in.taaRenderScale(),
                in.smaaEnabled() ? 1f : 0f, in.smaaStrength(), in.taaJitterUvDeltaX(), in.taaJitterUvDeltaY(),
                in.taaMotionUvX(), in.taaMotionUvY(),
                (in.taaLumaClipEnabled() ? 1f : 0f) + in.taaSharpenStrength(),
                in.taaClipScale(),
                in.taaEnabled() ? 1f : 0f, in.taaBlend(), in.taaHistoryInitialized() ? 1f : 0f, (float) in.taaDebugView()
                , in.reflectionsEnabled() ? 1f : 0f, (float) in.reflectionsMode(), in.reflectionsSsrStrength(), in.reflectionsSsrMaxRoughness()
                , in.reflectionsSsrStepScale(), in.reflectionsTemporalWeight(), in.reflectionsPlanarStrength(), in.reflectionsRtDenoiseStrength()
        });
        vkCmdPushConstants(commandBuffer, in.postPipelineLayout(), VK_SHADER_STAGE_FRAGMENT_BIT, 0, postPush);
        vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        vkCmdEndRenderPass(commandBuffer);
        boolean taaHistoryInitialized = in.taaHistoryInitialized();
        if (in.taaEnabled() && in.taaHistoryImage() != VK_NULL_HANDLE) {
            VkImageMemoryBarrier.Buffer historyToTransferDst = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.taaHistoryImage());
            historyToTransferDst.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    null,
                    null,
                    historyToTransferDst
            );

            VkImageMemoryBarrier.Buffer intermediateToTransferSrc = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.offscreenColorImage());
            intermediateToTransferSrc.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    null,
                    null,
                    intermediateToTransferSrc
            );

            vkCmdCopyImage(
                    commandBuffer,
                    in.offscreenColorImage(),
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    in.taaHistoryImage(),
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    copyRegion
            );

            VkImageMemoryBarrier.Buffer historyBackToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.taaHistoryImage());
            historyBackToShaderRead.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    historyBackToShaderRead
            );

            if (in.taaHistoryVelocityImage() != VK_NULL_HANDLE) {
                VkImageMemoryBarrier.Buffer historyVelocityToTransferDst = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                        .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                        .oldLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(in.taaHistoryVelocityImage());
                historyVelocityToTransferDst.get(0).subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                vkCmdPipelineBarrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0,
                        null,
                        null,
                        historyVelocityToTransferDst
                );

                VkImageMemoryBarrier.Buffer velocityToTransferSrc = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                        .dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                        .oldLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(in.velocityImage());
                velocityToTransferSrc.get(0).subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                vkCmdPipelineBarrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0,
                        null,
                        null,
                        velocityToTransferSrc
                );

                vkCmdCopyImage(
                        commandBuffer,
                        in.velocityImage(),
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        in.taaHistoryVelocityImage(),
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        copyRegion
                );

                VkImageMemoryBarrier.Buffer historyVelocityBackToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                        .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(in.taaHistoryVelocityImage());
                historyVelocityBackToShaderRead.get(0).subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                vkCmdPipelineBarrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        0,
                        null,
                        null,
                        historyVelocityBackToShaderRead
                );

                VkImageMemoryBarrier.Buffer velocityBackToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .srcAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                        .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                        .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(in.velocityImage());
                velocityBackToShaderRead.get(0).subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                vkCmdPipelineBarrier(
                        commandBuffer,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        0,
                        null,
                        null,
                        velocityBackToShaderRead
                );
            }

            VkImageMemoryBarrier.Buffer intermediateBackToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.offscreenColorImage());
            intermediateBackToShaderRead.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    intermediateBackToShaderRead
            );
            taaHistoryInitialized = true;
        }
        return new PostCompositeState(true, taaHistoryInitialized);
    }

    public record MeshDrawCmd(long vertexBuffer, long indexBuffer, int indexCount, long textureDescriptorSet) {
    }

    public record RenderPassInputs(
            int drawCount,
            int swapchainWidth,
            int swapchainHeight,
            int shadowMapResolution,
            boolean shadowEnabled,
            boolean pointShadowEnabled,
            int shadowCascadeCount,
            int maxShadowMatrices,
            int maxShadowCascades,
            int pointShadowFaces,
            long frameDescriptorSet,
            long renderPass,
            long framebuffer,
            long graphicsPipeline,
            long pipelineLayout,
            long shadowRenderPass,
            long shadowPipeline,
            long shadowPipelineLayout,
            long[] shadowFramebuffers,
            long shadowMomentImage,
            int shadowMomentMipLevels,
            boolean shadowMomentPipelineRequested,
            boolean shadowMomentInitialized
    ) {
    }

    public record PostCompositeInputs(
            int imageIndex,
            int swapchainWidth,
            int swapchainHeight,
            boolean postIntermediateInitialized,
            boolean tonemapEnabled,
            float tonemapExposure,
            float tonemapGamma,
            boolean ssaoEnabled,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength,
            float ssaoStrength,
            float ssaoRadius,
            float ssaoBias,
            float ssaoPower,
            boolean smaaEnabled,
            float smaaStrength,
            boolean taaEnabled,
            float taaBlend,
            boolean taaHistoryInitialized,
            float taaJitterUvDeltaX,
            float taaJitterUvDeltaY,
            float taaMotionUvX,
            float taaMotionUvY,
            float taaClipScale,
            float taaRenderScale,
            boolean taaLumaClipEnabled,
            float taaSharpenStrength,
            boolean reflectionsEnabled,
            int reflectionsMode,
            float reflectionsSsrStrength,
            float reflectionsSsrMaxRoughness,
            float reflectionsSsrStepScale,
            float reflectionsTemporalWeight,
            float reflectionsPlanarStrength,
            float reflectionsRtDenoiseStrength,
            int taaDebugView,
            long postRenderPass,
            long postGraphicsPipeline,
            long postPipelineLayout,
            long postDescriptorSet,
            long offscreenColorImage,
            long taaHistoryImage,
            long taaHistoryVelocityImage,
            long velocityImage,
            long swapchainImage,
            long[] postFramebuffers
    ) {
    }

    public record PostCompositeState(
            boolean postIntermediateInitialized,
            boolean taaHistoryInitialized
    ) {
    }
}
