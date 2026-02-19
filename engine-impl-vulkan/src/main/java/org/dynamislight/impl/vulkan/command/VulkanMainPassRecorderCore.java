package org.dynamislight.impl.vulkan.command;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.IntUnaryOperator;
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

import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.MainPassInputs;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.MeshDrawCmd;
import org.dynamislight.impl.vulkan.command.VulkanRenderCommandRecorder.PlanarReflectionPassInputs;

final class VulkanMainPassRecorderCore {
    private VulkanMainPassRecorderCore() {
    }

    static boolean isPlanarReflectionPassRequested(int reflectionsMode, long planarCaptureImage) {
        boolean planarCaptureRequested = (reflectionsMode & REFLECTION_MODE_PLANAR_CAPTURE_EXEC_BIT) != 0;
        boolean planarSelectiveRequested = (reflectionsMode & REFLECTION_MODE_PLANAR_SELECTIVE_EXEC_BIT) != 0;
        boolean planarGeometryCaptureRequested = (reflectionsMode & REFLECTION_MODE_PLANAR_GEOMETRY_CAPTURE_BIT) != 0;
        return planarCaptureRequested
                && planarSelectiveRequested
                && planarGeometryCaptureRequested
                && planarCaptureImage != VK_NULL_HANDLE;
    }

    static void recordPlanarReflectionPass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            PlanarReflectionPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        if (isPlanarReflectionPassRequested(in.reflectionsMode(), in.planarCaptureImage())) {
            if (in.planarTimestampQueryPool() != VK_NULL_HANDLE
                    && in.planarTimestampQueryStartIndex() >= 0
                    && in.planarTimestampQueryEndIndex() >= in.planarTimestampQueryStartIndex()) {
                vkCmdWriteTimestamp(
                        commandBuffer,
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                        in.planarTimestampQueryPool(),
                        in.planarTimestampQueryStartIndex()
                );
            }
            recordMainRenderPass(
                    stack,
                    commandBuffer,
                    in.toMainPassInputs(),
                    meshes,
                    dynamicUniformOffset,
                    true
            );
            copyPlanarCaptureImage(stack, commandBuffer, in);
            if (in.planarTimestampQueryPool() != VK_NULL_HANDLE
                    && in.planarTimestampQueryEndIndex() >= in.planarTimestampQueryStartIndex()) {
                vkCmdWriteTimestamp(
                        commandBuffer,
                        VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                        in.planarTimestampQueryPool(),
                        in.planarTimestampQueryEndIndex()
                );
            }
        }
    }

    static void recordMainPass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            MainPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        recordMainRenderPass(
                stack,
                commandBuffer,
                in,
                meshes,
                dynamicUniformOffset,
                false
        );
    }

    private static void recordMainRenderPass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            MainPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset,
            boolean planarSelectiveOnly
    ) {
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
        boolean planarClipEnabled = (in.reflectionsMode() & REFLECTION_MODE_PLANAR_CLIP_BIT) != 0;
        float planarCaptureFlag = planarSelectiveOnly ? 1.0f : 0.0f;
        float planarHeight = planarClipEnabled ? in.reflectionsPlanarPlaneHeight() : -10_000.0f;
        ByteBuffer planarPush = stack.malloc(4 * Float.BYTES);
        planarPush.asFloatBuffer().put(new float[]{planarCaptureFlag, planarHeight, 0.0f, 0.0f});
        vkCmdPushConstants(
                commandBuffer,
                in.pipelineLayout(),
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                0,
                planarPush
        );
        boolean anyDrawn = false;
        for (int meshIndex = 0; meshIndex < in.drawCount() && meshIndex < meshes.size(); meshIndex++) {
            MeshDrawCmd mesh = meshes.get(meshIndex);
            if (planarSelectiveOnly && !isPlanarEligible(mesh.reflectionOverrideMode(), in.reflectionsMode())) {
                continue;
            }
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
            anyDrawn = true;
        }
        if (!anyDrawn) {
            vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        }
        vkCmdEndRenderPass(commandBuffer);
    }

    private static boolean isPlanarEligible(int reflectionOverrideMode, int reflectionsMode) {
        boolean includeAuto = (reflectionsMode & REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_AUTO_BIT) != 0;
        boolean includeProbeOnly = (reflectionsMode & REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_PROBE_ONLY_BIT) != 0;
        boolean includeSsrOnly = (reflectionsMode & REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_SSR_ONLY_BIT) != 0;
        boolean includeOther = (reflectionsMode & REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_OTHER_BIT) != 0;
        if (!includeAuto && !includeProbeOnly && !includeSsrOnly && !includeOther) {
            includeAuto = true;
            includeOther = true;
        }
        return switch (Math.max(0, Math.min(3, reflectionOverrideMode))) {
            case 0 -> includeAuto;
            case 1 -> includeProbeOnly;
            case 2 -> includeSsrOnly;
            default -> includeOther;
        };
    }

    private static void copyPlanarCaptureImage(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            PlanarReflectionPassInputs in
    ) {
        VkImageMemoryBarrier.Buffer captureSrcToTransfer = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(in.swapchainImageForCapture());
        captureSrcToTransfer.get(0).subresourceRange()
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
                captureSrcToTransfer
        );

        int historyOldLayout = in.taaHistoryInitialized()
                ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                : VK_IMAGE_LAYOUT_UNDEFINED;
        VkImageMemoryBarrier.Buffer captureDstToTransfer = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(in.taaHistoryInitialized() ? VK10.VK_ACCESS_SHADER_READ_BIT : 0)
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .oldLayout(historyOldLayout)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(in.planarCaptureImage());
        captureDstToTransfer.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
        VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                commandBuffer,
                in.taaHistoryInitialized() ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                null,
                null,
                captureDstToTransfer
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
                in.swapchainImageForCapture(),
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                in.planarCaptureImage(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                copyRegion
        );

        VkImageMemoryBarrier.Buffer captureDstToShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(in.planarCaptureImage());
        captureDstToShaderRead.get(0).subresourceRange()
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
                captureDstToShaderRead
        );

        VkImageMemoryBarrier.Buffer captureSrcToColor = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(in.swapchainImageForCapture());
        captureSrcToColor.get(0).subresourceRange()
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
                captureSrcToColor
        );
    }
}
