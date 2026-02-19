package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

public final class VulkanFrameCommandOrchestrator {
    private static final VulkanShadowPassRecorder SHADOW_RECORDER = new VulkanShadowPassRecorder();
    private static final VulkanPlanarReflectionPassRecorder PLANAR_RECORDER = new VulkanPlanarReflectionPassRecorder();
    private static final VulkanMainPassRecorder MAIN_RECORDER = new VulkanMainPassRecorder();
    private static final VulkanPostCompositePassRecorder POST_COMPOSITE_RECORDER = new VulkanPostCompositePassRecorder();

    private VulkanFrameCommandOrchestrator() {
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws EngineException;
    }

    public static void record(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            int imageIndex,
            int frameIdx,
            FrameHooks hooks,
            Inputs inputs
    ) throws EngineException {
        int beginResult = VulkanRenderCommandRecorder.beginOneShot(commandBuffer, stack);
        if (beginResult != VK10.VK_SUCCESS) {
            throw inputs.vkFailure().failure("vkBeginCommandBuffer", beginResult);
        }

        hooks.updateShadowMatrices().run();
        hooks.prepareUniforms().run();
        hooks.uploadUniforms().run();
        long frameDescriptorSet = inputs.descriptorSetForFrame().applyAsLong(frameIdx);
        int drawCount = inputs.gpuMeshes().isEmpty() ? 1 : Math.min(inputs.maxDynamicSceneObjects(), inputs.gpuMeshes().size());
        List<VulkanRenderCommandRecorder.MeshDrawCmd> meshes = new ArrayList<>(Math.min(drawCount, inputs.gpuMeshes().size()));
        for (int i = 0; i < drawCount && i < inputs.gpuMeshes().size(); i++) {
            VulkanGpuMesh mesh = inputs.gpuMeshes().get(i);
            meshes.add(new VulkanRenderCommandRecorder.MeshDrawCmd(
                    mesh.vertexBuffer,
                    mesh.indexBuffer,
                    mesh.indexCount,
                    mesh.textureDescriptorSet,
                    mesh.reflectionOverrideMode
            ));
        }

        VulkanRenderCommandRecorder.resetPlanarTimestampQueries(
                commandBuffer,
                inputs.planarTimestampQueryPool(),
                inputs.planarTimestampQueryStartIndex(),
                inputs.planarTimestampQueryEndIndex()
        );

        SHADOW_RECORDER.record(
                stack,
                commandBuffer,
                new VulkanRenderCommandRecorder.ShadowPassInputs(
                        drawCount,
                        inputs.shadowMapResolution(),
                        inputs.shadowEnabled(),
                        inputs.pointShadowEnabled(),
                        inputs.shadowCascadeCount(),
                        inputs.maxShadowMatrices(),
                        inputs.maxShadowCascades(),
                        inputs.pointShadowFaces(),
                        frameDescriptorSet,
                        inputs.shadowRenderPass(),
                        inputs.shadowPipeline(),
                        inputs.shadowPipelineLayout(),
                        inputs.shadowFramebuffers(),
                        inputs.shadowMomentImage(),
                        inputs.shadowMomentMipLevels(),
                        inputs.shadowMomentPipelineRequested(),
                        inputs.shadowMomentInitialized()
                ),
                meshes,
                meshIndex -> inputs.dynamicUniformOffset().applyAsInt(meshIndex)
        );

        if (VulkanRenderCommandRecorder.isPlanarReflectionPassRequested(inputs.reflectionsMode(), inputs.planarCaptureImage())) {
            PLANAR_RECORDER.record(
                    stack,
                    commandBuffer,
                    new VulkanRenderCommandRecorder.PlanarReflectionPassInputs(
                            drawCount,
                            inputs.swapchainWidth(),
                            inputs.swapchainHeight(),
                            frameDescriptorSet,
                            inputs.renderPass(),
                            inputs.framebuffers()[imageIndex],
                            inputs.graphicsPipeline(),
                            inputs.pipelineLayout(),
                            inputs.reflectionsMode(),
                            inputs.reflectionsPlanarPlaneHeight(),
                            inputs.planarTimestampQueryPool(),
                            inputs.planarTimestampQueryStartIndex(),
                            inputs.planarTimestampQueryEndIndex(),
                            inputs.planarCaptureImage(),
                            inputs.swapchainImages()[imageIndex],
                            inputs.taaHistoryInitialized()
                    ),
                    meshes,
                    meshIndex -> inputs.dynamicUniformOffset().applyAsInt(meshIndex)
            );
        }

        MAIN_RECORDER.record(
                stack,
                commandBuffer,
                new VulkanRenderCommandRecorder.MainPassInputs(
                        drawCount,
                        inputs.swapchainWidth(),
                        inputs.swapchainHeight(),
                        frameDescriptorSet,
                        inputs.renderPass(),
                        inputs.framebuffers()[imageIndex],
                        inputs.graphicsPipeline(),
                        inputs.pipelineLayout(),
                        inputs.reflectionsMode(),
                        inputs.reflectionsPlanarPlaneHeight()
                ),
                meshes,
                meshIndex -> inputs.dynamicUniformOffset().applyAsInt(meshIndex)
        );

        if (inputs.postOffscreenActive()) {
            VulkanRenderCommandRecorder.PostCompositeState postInitialized = POST_COMPOSITE_RECORDER.record(
                    stack,
                    commandBuffer,
                    new VulkanRenderCommandRecorder.PostCompositeInputs(
                            imageIndex,
                            inputs.swapchainWidth(),
                            inputs.swapchainHeight(),
                            inputs.postIntermediateInitialized(),
                            inputs.tonemapEnabled(),
                            inputs.tonemapExposure(),
                            inputs.tonemapGamma(),
                            inputs.ssaoEnabled(),
                            inputs.bloomEnabled(),
                            inputs.bloomThreshold(),
                            inputs.bloomStrength(),
                            inputs.ssaoStrength(),
                            inputs.ssaoRadius(),
                            inputs.ssaoBias(),
                            inputs.ssaoPower(),
                            inputs.smaaEnabled(),
                            inputs.smaaStrength(),
                            inputs.taaEnabled(),
                            inputs.taaBlend(),
                            inputs.taaHistoryInitialized(),
                            inputs.taaJitterUvDeltaX(),
                            inputs.taaJitterUvDeltaY(),
                            inputs.taaMotionUvX(),
                            inputs.taaMotionUvY(),
                            inputs.taaClipScale(),
                            inputs.taaRenderScale(),
                            inputs.taaLumaClipEnabled(),
                            inputs.taaSharpenStrength(),
                            inputs.reflectionsEnabled(),
                            inputs.reflectionsMode(),
                            inputs.reflectionsSsrStrength(),
                            inputs.reflectionsSsrMaxRoughness(),
                            inputs.reflectionsSsrStepScale(),
                            inputs.reflectionsTemporalWeight(),
                            inputs.reflectionsPlanarStrength(),
                            inputs.reflectionsRtDenoiseStrength(),
                            inputs.taaDebugView(),
                            inputs.postRenderPass(),
                            inputs.postGraphicsPipeline(),
                            inputs.postPipelineLayout(),
                            inputs.postDescriptorSet(),
                            inputs.offscreenColorImage(),
                            inputs.taaHistoryImage(),
                            inputs.taaHistoryVelocityImage(),
                            inputs.planarCaptureImage(),
                            inputs.velocityImage(),
                            inputs.swapchainImages()[imageIndex],
                            inputs.postFramebuffers()
                    )
            );
            hooks.postIntermediateInitializedSink().accept(postInitialized.postIntermediateInitialized());
            hooks.postTaaHistoryInitializedSink().accept(postInitialized.taaHistoryInitialized());
        }

        int endResult = VulkanRenderCommandRecorder.end(commandBuffer);
        if (endResult != VK10.VK_SUCCESS) {
            throw inputs.vkFailure().failure("vkEndCommandBuffer", endResult);
        }
    }

    @FunctionalInterface
    public interface LongByInt {
        long applyAsLong(int value);
    }

    @FunctionalInterface
    public interface BooleanSink {
        void accept(boolean value);
    }

    @FunctionalInterface
    public interface FailureFactory {
        EngineException failure(String operation, int result);
    }

    public record FrameHooks(
            ThrowingRunnable updateShadowMatrices,
            ThrowingRunnable prepareUniforms,
            ThrowingRunnable uploadUniforms,
            BooleanSink postIntermediateInitializedSink,
            BooleanSink postTaaHistoryInitializedSink
    ) {
    }

    public record Inputs(
            List<VulkanGpuMesh> gpuMeshes,
            int maxDynamicSceneObjects,
            int swapchainWidth,
            int swapchainHeight,
            int shadowMapResolution,
            boolean shadowEnabled,
            boolean pointShadowEnabled,
            int shadowCascadeCount,
            int maxShadowMatrices,
            int maxShadowCascades,
            int pointShadowFaces,
            long renderPass,
            long[] framebuffers,
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
            boolean postOffscreenActive,
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
            long planarTimestampQueryPool,
            int planarTimestampQueryStartIndex,
            int planarTimestampQueryEndIndex,
            float reflectionsSsrStrength,
            float reflectionsSsrMaxRoughness,
            float reflectionsSsrStepScale,
            float reflectionsTemporalWeight,
            float reflectionsPlanarStrength,
            float reflectionsPlanarPlaneHeight,
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
            long[] swapchainImages,
            long[] postFramebuffers,
            LongByInt descriptorSetForFrame,
            IntUnaryOperator dynamicUniformOffset,
            FailureFactory vkFailure
    ) {
    }
}
