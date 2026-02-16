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
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
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
        if (in.shadowEnabled()
                && in.shadowRenderPass() != VK_NULL_HANDLE
                && in.shadowPipeline() != VK_NULL_HANDLE
                && in.frameDescriptorSet() != VK_NULL_HANDLE
                && !meshes.isEmpty()
                && in.shadowFramebuffers().length >= Math.min(in.maxShadowMatrices(), Math.max(1, in.shadowCascadeCount()))) {
            int cascades = in.pointShadowEnabled()
                    ? in.pointShadowFaces()
                    : Math.min(in.maxShadowCascades(), Math.max(1, in.shadowCascadeCount()));
            for (int cascadeIndex = 0; cascadeIndex < cascades; cascadeIndex++) {
                VkClearValue.Buffer shadowClearValues = VkClearValue.calloc(1, stack);
                shadowClearValues.get(0).depthStencil().depth(1.0f).stencil(0);
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

        VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
        clearValues.get(0).color().float32(0, 0.08f);
        clearValues.get(0).color().float32(1, 0.09f);
        clearValues.get(0).color().float32(2, 0.12f);
        clearValues.get(0).color().float32(3, 1.0f);
        clearValues.get(1).depthStencil().depth(1.0f).stencil(0);

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

    public static boolean executePostCompositePass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            PostCompositeInputs in
    ) {
        if (in.postRenderPass() == VK_NULL_HANDLE
                || in.postGraphicsPipeline() == VK_NULL_HANDLE
                || in.postPipelineLayout() == VK_NULL_HANDLE
                || in.postDescriptorSet() == VK_NULL_HANDLE
                || in.postFramebuffers().length <= in.imageIndex()
                || in.offscreenColorImage() == VK_NULL_HANDLE) {
            return in.postIntermediateInitialized();
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
        ByteBuffer postPush = stack.malloc(12 * Float.BYTES);
        postPush.asFloatBuffer().put(new float[]{
                in.tonemapEnabled() ? 1f : 0f, in.tonemapExposure(), in.tonemapGamma(), in.ssaoEnabled() ? 1f : 0f,
                in.bloomEnabled() ? 1f : 0f, in.bloomThreshold(), in.bloomStrength(), in.ssaoStrength(),
                in.ssaoRadius(), in.ssaoBias(), in.ssaoPower(), in.smaaEnabled() ? in.smaaStrength() : 0f
        });
        vkCmdPushConstants(commandBuffer, in.postPipelineLayout(), VK_SHADER_STAGE_FRAGMENT_BIT, 0, postPush);
        vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        vkCmdEndRenderPass(commandBuffer);
        return true;
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
            long[] shadowFramebuffers
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
            long postRenderPass,
            long postGraphicsPipeline,
            long postPipelineLayout,
            long postDescriptorSet,
            long offscreenColorImage,
            long swapchainImage,
            long[] postFramebuffers
    ) {
    }
}
