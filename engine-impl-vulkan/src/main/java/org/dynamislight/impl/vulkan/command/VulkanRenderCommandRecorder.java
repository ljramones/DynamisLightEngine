package org.dynamislight.impl.vulkan.command;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryBarrier;
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
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
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
import static org.lwjgl.vulkan.VK10.vkCmdResetQueryPool;
import static org.lwjgl.vulkan.VK10.vkCmdWriteTimestamp;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;

public final class VulkanRenderCommandRecorder {
    static final int REFLECTION_MODE_PLANAR_SELECTIVE_EXEC_BIT = 1 << 14;
    static final int REFLECTION_MODE_PLANAR_CAPTURE_EXEC_BIT = 1 << 18;
    static final int REFLECTION_MODE_PLANAR_GEOMETRY_CAPTURE_BIT = 1 << 20;
    static final int REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_AUTO_BIT = 1 << 21;
    static final int REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_PROBE_ONLY_BIT = 1 << 22;
    static final int REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_SSR_ONLY_BIT = 1 << 23;
    static final int REFLECTION_MODE_PLANAR_SCOPE_INCLUDE_OTHER_BIT = 1 << 24;
    static final int REFLECTION_MODE_PLANAR_CLIP_BIT = 1 << 7;
    private static final ThreadLocal<VulkanRuntimeBarrierTrace> ACTIVE_BARRIER_TRACE = new ThreadLocal<>();

    private VulkanRenderCommandRecorder() {
    }

    public static void installBarrierTrace(VulkanRuntimeBarrierTrace trace) {
        if (trace == null) {
            ACTIVE_BARRIER_TRACE.remove();
        } else {
            ACTIVE_BARRIER_TRACE.set(trace);
        }
    }

    public static void clearBarrierTrace() {
        ACTIVE_BARRIER_TRACE.remove();
    }

    static boolean barrierTraceInstalled() {
        return ACTIVE_BARRIER_TRACE.get() != null;
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
        resetPlanarTimestampQueries(
                commandBuffer,
                in.planarTimestampQueryPool(),
                in.planarTimestampQueryStartIndex(),
                in.planarTimestampQueryEndIndex()
        );

        recordShadowPasses(
                stack,
                commandBuffer,
                new ShadowPassInputs(
                        in.drawCount(),
                        in.shadowMapResolution(),
                        in.shadowEnabled(),
                        in.pointShadowEnabled(),
                        in.shadowCascadeCount(),
                        in.maxShadowMatrices(),
                        in.maxShadowCascades(),
                        in.pointShadowFaces(),
                        in.frameDescriptorSet(),
                        in.shadowRenderPass(),
                        in.shadowPipeline(),
                        in.shadowPipelineLayout(),
                        in.shadowFramebuffers(),
                        in.shadowMomentImage(),
                        in.shadowMomentMipLevels(),
                        in.shadowMomentPipelineRequested(),
                        in.shadowMomentInitialized()
                ),
                meshes,
                dynamicUniformOffset
        );

        recordPlanarReflectionPass(
                stack,
                commandBuffer,
                new PlanarReflectionPassInputs(
                        in.drawCount(),
                        in.swapchainWidth(),
                        in.swapchainHeight(),
                        in.frameDescriptorSet(),
                        in.renderPass(),
                        in.framebuffer(),
                        in.graphicsPipeline(),
                        in.pipelineLayout(),
                        in.reflectionsMode(),
                        in.reflectionsPlanarPlaneHeight(),
                        in.planarTimestampQueryPool(),
                        in.planarTimestampQueryStartIndex(),
                        in.planarTimestampQueryEndIndex(),
                        in.planarCaptureImage(),
                        in.swapchainImageForCapture(),
                        in.taaHistoryInitialized()
                ),
                meshes,
                dynamicUniformOffset
        );

        recordMainPass(
                stack,
                commandBuffer,
                new MainPassInputs(
                        in.drawCount(),
                        in.swapchainWidth(),
                        in.swapchainHeight(),
                        in.frameDescriptorSet(),
                        in.renderPass(),
                        in.framebuffer(),
                        in.graphicsPipeline(),
                        in.pipelineLayout(),
                        in.reflectionsMode(),
                        in.reflectionsPlanarPlaneHeight()
                ),
                meshes,
                dynamicUniformOffset
        );
    }

    public static void resetPlanarTimestampQueries(
            VkCommandBuffer commandBuffer,
            long planarTimestampQueryPool,
            int planarTimestampQueryStartIndex,
            int planarTimestampQueryEndIndex
    ) {
        if (planarTimestampQueryPool != VK_NULL_HANDLE
                && planarTimestampQueryStartIndex >= 0
                && planarTimestampQueryEndIndex >= planarTimestampQueryStartIndex) {
            vkCmdResetQueryPool(
                    commandBuffer,
                    planarTimestampQueryPool,
                    planarTimestampQueryStartIndex,
                    (planarTimestampQueryEndIndex - planarTimestampQueryStartIndex) + 1
            );
        }
    }

    static boolean isPlanarReflectionPassRequested(int reflectionsMode, long planarCaptureImage) {
        return VulkanRenderCommandRecorderCore.isPlanarReflectionPassRequested(reflectionsMode, planarCaptureImage);
    }

    public static void recordShadowPasses(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            ShadowPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanRenderCommandRecorderCore.recordShadowPasses(stack, commandBuffer, in, meshes, dynamicUniformOffset);
    }

    public static void recordPlanarReflectionPass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            PlanarReflectionPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanRenderCommandRecorderCore.recordPlanarReflectionPass(stack, commandBuffer, in, meshes, dynamicUniformOffset);
    }

    public static void recordMainPass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            MainPassInputs in,
            List<MeshDrawCmd> meshes,
            IntUnaryOperator dynamicUniformOffset
    ) {
        VulkanRenderCommandRecorderCore.recordMainPass(stack, commandBuffer, in, meshes, dynamicUniformOffset);
    }

    static void vkCmdPipelineBarrier(
            VkCommandBuffer commandBuffer,
            int srcStageMask,
            int dstStageMask,
            int dependencyFlags,
            VkMemoryBarrier.Buffer memoryBarriers,
            VkBufferMemoryBarrier.Buffer bufferBarriers,
            VkImageMemoryBarrier.Buffer imageBarriers
    ) {
        VK10.vkCmdPipelineBarrier(
                commandBuffer,
                srcStageMask,
                dstStageMask,
                dependencyFlags,
                memoryBarriers,
                bufferBarriers,
                imageBarriers
        );
        VulkanRuntimeBarrierTrace trace = ACTIVE_BARRIER_TRACE.get();
        if (trace == null || imageBarriers == null) {
            return;
        }
        for (int i = 0; i < imageBarriers.remaining(); i++) {
            VkImageMemoryBarrier barrier = imageBarriers.get(i);
            trace.recordImageBarrier(new VulkanRuntimeBarrierTrace.ImageBarrierEvent(
                    srcStageMask,
                    dstStageMask,
                    barrier.srcAccessMask(),
                    barrier.dstAccessMask(),
                    barrier.oldLayout(),
                    barrier.newLayout(),
                    barrier.image()
            ));
        }
    }

    static int shadowPassCount(RenderPassInputs in) {
        return VulkanRenderCommandRecorderCore.shadowPassCount(in);
    }

    public static PostCompositeState executePostCompositePass(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            PostCompositeInputs in
    ) {
        return VulkanRenderCommandRecorderCore.executePostCompositePass(stack, commandBuffer, in);
    }

    public record MeshDrawCmd(long vertexBuffer, long indexBuffer, int indexCount, long textureDescriptorSet, int reflectionOverrideMode) {
    }

    public record ShadowPassInputs(
            int drawCount,
            int shadowMapResolution,
            boolean shadowEnabled,
            boolean pointShadowEnabled,
            int shadowCascadeCount,
            int maxShadowMatrices,
            int maxShadowCascades,
            int pointShadowFaces,
            long frameDescriptorSet,
            long shadowRenderPass,
            long shadowPipeline,
            long shadowPipelineLayout,
            long[] shadowFramebuffers,
            long shadowMomentImage,
            int shadowMomentMipLevels,
            boolean shadowMomentPipelineRequested,
            boolean shadowMomentInitialized
    ) {
        RenderPassInputs toRenderPassInputsShadowView() {
            return new RenderPassInputs(
                    drawCount, 0, 0, shadowMapResolution, shadowEnabled, pointShadowEnabled, shadowCascadeCount, maxShadowMatrices,
                    maxShadowCascades, pointShadowFaces, frameDescriptorSet, 0L, 0L, 0L, 0L, shadowRenderPass,
                    shadowPipeline, shadowPipelineLayout, shadowFramebuffers, shadowMomentImage, shadowMomentMipLevels,
                    shadowMomentPipelineRequested, shadowMomentInitialized, 0, 0L, -1, -1, false, 0L, 0L, 0f
            );
        }
    }

    public record MainPassInputs(
            int drawCount,
            int swapchainWidth,
            int swapchainHeight,
            long frameDescriptorSet,
            long renderPass,
            long framebuffer,
            long graphicsPipeline,
            long pipelineLayout,
            int reflectionsMode,
            float reflectionsPlanarPlaneHeight
    ) {
    }

    public record PlanarReflectionPassInputs(
            int drawCount,
            int swapchainWidth,
            int swapchainHeight,
            long frameDescriptorSet,
            long renderPass,
            long framebuffer,
            long graphicsPipeline,
            long pipelineLayout,
            int reflectionsMode,
            float reflectionsPlanarPlaneHeight,
            long planarTimestampQueryPool,
            int planarTimestampQueryStartIndex,
            int planarTimestampQueryEndIndex,
            long planarCaptureImage,
            long swapchainImageForCapture,
            boolean taaHistoryInitialized
    ) {
        MainPassInputs toMainPassInputs() {
            return new MainPassInputs(
                    drawCount,
                    swapchainWidth,
                    swapchainHeight,
                    frameDescriptorSet,
                    renderPass,
                    framebuffer,
                    graphicsPipeline,
                    pipelineLayout,
                    reflectionsMode,
                    reflectionsPlanarPlaneHeight
            );
        }
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
            boolean shadowMomentInitialized,
            int reflectionsMode,
            long planarTimestampQueryPool,
            int planarTimestampQueryStartIndex,
            int planarTimestampQueryEndIndex,
            boolean taaHistoryInitialized,
            long planarCaptureImage,
            long swapchainImageForCapture,
            float reflectionsPlanarPlaneHeight
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
            long planarCaptureImage,
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
