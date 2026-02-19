package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.impl.vulkan.graph.VulkanAaPostRenderGraphPlanner;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphPlan;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphBuilder;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphPlanner;
import org.dynamislight.impl.vulkan.graph.VulkanResourceBindingTable;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

public final class VulkanFrameCommandOrchestrator {
    private static final VulkanShadowPassRecorder SHADOW_RECORDER = new VulkanShadowPassRecorder();
    private static final VulkanPlanarReflectionPassRecorder PLANAR_RECORDER = new VulkanPlanarReflectionPassRecorder();
    private static final VulkanMainPassRecorder MAIN_RECORDER = new VulkanMainPassRecorder();
    private static final VulkanPostCompositePassRecorder POST_COMPOSITE_RECORDER = new VulkanPostCompositePassRecorder();
    private static final VulkanExecutableRenderGraphPlanner EXECUTABLE_GRAPH_PLANNER = new VulkanExecutableRenderGraphPlanner();

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

        VulkanExecutableRenderGraphBuilder graphBuilder = new VulkanExecutableRenderGraphBuilder();

        VulkanRenderCommandRecorder.ShadowPassInputs shadowInputs = new VulkanRenderCommandRecorder.ShadowPassInputs(
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
        );

        SHADOW_RECORDER.record(
                stack,
                commandBuffer,
                shadowInputs,
                meshes,
                meshIndex -> inputs.dynamicUniformOffset().applyAsInt(meshIndex)
        );
        SHADOW_RECORDER.declarePasses(
                graphBuilder,
                stack,
                commandBuffer,
                shadowInputs,
                meshes,
                meshIndex -> inputs.dynamicUniformOffset().applyAsInt(meshIndex)
        );

        VulkanRenderCommandRecorder.PlanarReflectionPassInputs planarInputs = new VulkanRenderCommandRecorder.PlanarReflectionPassInputs(
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
        );
        if (VulkanRenderCommandRecorder.isPlanarReflectionPassRequested(inputs.reflectionsMode(), inputs.planarCaptureImage())) {
            PLANAR_RECORDER.record(
                    stack,
                    commandBuffer,
                    planarInputs,
                    meshes,
                    meshIndex -> inputs.dynamicUniformOffset().applyAsInt(meshIndex)
            );
        }
        PLANAR_RECORDER.declarePasses(
                graphBuilder,
                stack,
                commandBuffer,
                planarInputs,
                meshes,
                meshIndex -> inputs.dynamicUniformOffset().applyAsInt(meshIndex)
        );

        VulkanRenderCommandRecorder.MainPassInputs mainInputs = new VulkanRenderCommandRecorder.MainPassInputs(
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
        );
        MAIN_RECORDER.record(
                stack,
                commandBuffer,
                mainInputs,
                meshes,
                meshIndex -> inputs.dynamicUniformOffset().applyAsInt(meshIndex)
        );
        MAIN_RECORDER.declarePasses(
                graphBuilder,
                stack,
                commandBuffer,
                mainInputs,
                meshes,
                meshIndex -> inputs.dynamicUniformOffset().applyAsInt(meshIndex)
        );

        if (inputs.postOffscreenActive()) {
            VulkanRenderCommandRecorder.PostCompositeInputs postInputs = new VulkanRenderCommandRecorder.PostCompositeInputs(
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
            );
            VulkanRenderCommandRecorder.PostCompositeState postInitialized = POST_COMPOSITE_RECORDER.record(
                    stack,
                    commandBuffer,
                    postInputs
            );
            hooks.postIntermediateInitializedSink().accept(postInitialized.postIntermediateInitialized());
            hooks.postTaaHistoryInitializedSink().accept(postInitialized.taaHistoryInitialized());
            POST_COMPOSITE_RECORDER.declarePasses(
                    graphBuilder,
                    stack,
                    commandBuffer,
                    postInputs,
                    hooks.postIntermediateInitializedSink()::accept,
                    hooks.postTaaHistoryInitializedSink()::accept
            );
        }

        // B.3.2 wiring only: compile callback-carrying declarations in parallel with linear execution.
        VulkanExecutableRenderGraphPlan executablePlan = EXECUTABLE_GRAPH_PLANNER.compile(
                graphBuilder.build(),
                VulkanAaPostRenderGraphPlanner.defaultImportedResources()
        );
        VulkanResourceBindingTable bindingTable = buildResourceBindingTable(inputs, imageIndex);
        Set<String> unboundResources = bindingTable.unboundResources(executablePlan.metadataPlan());
        if (!unboundResources.isEmpty()) {
            throw new EngineException(
                    EngineErrorCode.INTERNAL_ERROR,
                    "Render graph has unbound Vulkan resources: " + String.join(", ", unboundResources),
                    false
            );
        }
        Set<String> invalidBindings = bindingTable.invalidBindings(executablePlan.metadataPlan());
        if (!invalidBindings.isEmpty()) {
            throw new EngineException(
                    EngineErrorCode.INTERNAL_ERROR,
                    "Render graph has invalid Vulkan resource bindings: " + String.join(", ", invalidBindings),
                    false
            );
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

    static VulkanResourceBindingTable buildResourceBindingTable(Inputs inputs, int imageIndex) {
        long currentSwapchainImage = imageIndex >= 0 && imageIndex < inputs.swapchainImages().length
                ? inputs.swapchainImages()[imageIndex]
                : 0L;
        long currentDepthImage = imageIndex >= 0 && imageIndex < inputs.depthImages().length
                ? inputs.depthImages()[imageIndex]
                : 0L;
        long sceneColorImage = inputs.postOffscreenActive() ? inputs.offscreenColorImage() : currentSwapchainImage;
        long resolvedColorImage = currentSwapchainImage != 0L ? currentSwapchainImage : sceneColorImage;

        VulkanResourceBindingTable table = new VulkanResourceBindingTable()
                .bind(
                        "shadow_depth",
                        inputs.shadowDepthImage(),
                        inputs.depthFormat(),
                        VK_IMAGE_ASPECT_DEPTH_BIT,
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                )
                .bind(
                        "shadow_moment_atlas",
                        inputs.shadowMomentImage(),
                        inputs.shadowMomentFormat(),
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                )
                .bind(
                        "planar_capture",
                        inputs.planarCaptureImage(),
                        inputs.swapchainImageFormat(),
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                )
                .bind(
                        "scene_color",
                        sceneColorImage,
                        inputs.swapchainImageFormat(),
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        inputs.postOffscreenActive() ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
                )
                .bind(
                        "velocity",
                        inputs.velocityImage(),
                        inputs.swapchainImageFormat(),
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                )
                .bind(
                        "depth",
                        currentDepthImage,
                        inputs.depthFormat(),
                        VK_IMAGE_ASPECT_DEPTH_BIT,
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                )
                .bind(
                        "history_color",
                        inputs.taaHistoryImage(),
                        inputs.swapchainImageFormat(),
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                )
                .bind(
                        "history_velocity",
                        inputs.taaHistoryVelocityImage(),
                        inputs.swapchainImageFormat(),
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                )
                .bind(
                        "resolved_color",
                        resolvedColorImage,
                        inputs.swapchainImageFormat(),
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
                )
                .bind(
                        "history_color_next",
                        inputs.taaHistoryImage(),
                        inputs.swapchainImageFormat(),
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_LAYOUT_GENERAL
                )
                .bind(
                        "history_velocity_next",
                        inputs.taaHistoryVelocityImage(),
                        inputs.swapchainImageFormat(),
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        VK_IMAGE_LAYOUT_GENERAL
                );

        if (!inputs.postOffscreenActive()) {
            table.updateLayout("resolved_color", VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        }
        return table;
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
            int swapchainImageFormat,
            int depthFormat,
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
            long shadowDepthImage,
            long shadowMomentImage,
            int shadowMomentFormat,
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
            long[] depthImages,
            long[] swapchainImages,
            long[] postFramebuffers,
            LongByInt descriptorSetForFrame,
            IntUnaryOperator dynamicUniformOffset,
            FailureFactory vkFailure
    ) {
    }
}
