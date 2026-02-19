package org.dynamislight.impl.vulkan.command;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;

import static org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.PostCompositeInputs;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.PostCompositeState;

final class VulkanPostCompositePassRecorderCore {
    private VulkanPostCompositePassRecorderCore() {
    }

    static PostCompositeState executePostCompositePass(
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
        VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
        VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
        VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                    commandBuffer,
                    in.taaHistoryInitialized() ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    historyVelocityToShaderRead
            );
        }
        boolean planarCapturePassRequested = (in.reflectionsMode() & REFLECTION_MODE_PLANAR_CAPTURE_EXEC_BIT) != 0;
        boolean planarGeometryCaptureExecuted = (in.reflectionsMode() & REFLECTION_MODE_PLANAR_GEOMETRY_CAPTURE_BIT) != 0;
        if (planarCapturePassRequested && !planarGeometryCaptureExecuted && in.planarCaptureImage() != VK_NULL_HANDLE) {
            VkImageMemoryBarrier.Buffer planarCaptureDst = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(in.taaHistoryInitialized() ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(in.planarCaptureImage());
            planarCaptureDst.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
                    in.planarCaptureImage(),
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
                    .image(in.planarCaptureImage());
            planarCaptureDstShaderRead.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
        VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
        VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
                in.taaEnabled() ? 1f : 0f, in.taaBlend(), in.taaHistoryInitialized() ? 1f : 0f, (float) in.taaDebugView(),
                in.reflectionsEnabled() ? 1f : 0f, (float) in.reflectionsMode(), in.reflectionsSsrStrength(), in.reflectionsSsrMaxRoughness(),
                in.reflectionsSsrStepScale(), in.reflectionsTemporalWeight(), in.reflectionsPlanarStrength(), in.reflectionsRtDenoiseStrength()
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
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
                VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
                VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
                VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
                VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
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
}
