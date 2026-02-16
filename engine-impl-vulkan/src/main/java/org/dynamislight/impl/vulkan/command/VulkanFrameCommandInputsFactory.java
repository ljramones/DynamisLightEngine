package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;

public final class VulkanFrameCommandInputsFactory {
    private VulkanFrameCommandInputsFactory() {
    }

    public static VulkanFrameCommandOrchestrator.Inputs create(Inputs inputs) {
        return new VulkanFrameCommandOrchestrator.Inputs(
                inputs.gpuMeshes(),
                inputs.maxDynamicSceneObjects(),
                inputs.swapchainWidth(),
                inputs.swapchainHeight(),
                inputs.shadowMapResolution(),
                inputs.shadowEnabled(),
                inputs.pointShadowEnabled(),
                inputs.shadowCascadeCount(),
                inputs.maxShadowMatrices(),
                inputs.maxShadowCascades(),
                inputs.pointShadowFaces(),
                inputs.renderPass(),
                inputs.framebuffers(),
                inputs.graphicsPipeline(),
                inputs.pipelineLayout(),
                inputs.shadowRenderPass(),
                inputs.shadowPipeline(),
                inputs.shadowPipelineLayout(),
                inputs.shadowFramebuffers(),
                inputs.postOffscreenActive(),
                inputs.postIntermediateInitialized(),
                inputs.tonemapEnabled(),
                inputs.tonemapExposure(),
                inputs.tonemapGamma(),
                inputs.bloomEnabled(),
                inputs.bloomThreshold(),
                inputs.bloomStrength(),
                inputs.postRenderPass(),
                inputs.postGraphicsPipeline(),
                inputs.postPipelineLayout(),
                inputs.postDescriptorSet(),
                inputs.offscreenColorImage(),
                inputs.swapchainImages(),
                inputs.postFramebuffers(),
                inputs.descriptorSetForFrame(),
                inputs.dynamicUniformOffset(),
                inputs.vkFailure()
        );
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
            boolean postOffscreenActive,
            boolean postIntermediateInitialized,
            boolean tonemapEnabled,
            float tonemapExposure,
            float tonemapGamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength,
            long postRenderPass,
            long postGraphicsPipeline,
            long postPipelineLayout,
            long postDescriptorSet,
            long offscreenColorImage,
            long[] swapchainImages,
            long[] postFramebuffers,
            VulkanFrameCommandOrchestrator.LongByInt descriptorSetForFrame,
            IntUnaryOperator dynamicUniformOffset,
            VulkanFrameCommandOrchestrator.FailureFactory vkFailure
    ) {
    }
}
