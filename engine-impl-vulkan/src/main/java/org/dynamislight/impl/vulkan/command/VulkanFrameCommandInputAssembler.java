package org.dynamislight.impl.vulkan.command;

import java.util.List;
import java.util.function.IntUnaryOperator;

import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;

public final class VulkanFrameCommandInputAssembler {
    private VulkanFrameCommandInputAssembler() {
    }

    public static VulkanFrameCommandOrchestrator.Inputs build(AssemblyInputs in) {
        return VulkanFrameCommandInputsFactory.create(
                new VulkanFrameCommandInputsFactory.Inputs(
                        in.gpuMeshes(),
                        in.maxDynamicSceneObjects(),
                        in.swapchainWidth(),
                        in.swapchainHeight(),
                        in.shadowMapResolution(),
                        in.shadowEnabled(),
                        in.pointShadowEnabled(),
                        in.shadowCascadeCount(),
                        in.maxShadowMatrices(),
                        in.maxShadowCascades(),
                        in.pointShadowFaces(),
                        in.renderPass(),
                        in.framebuffers(),
                        in.graphicsPipeline(),
                        in.pipelineLayout(),
                        in.shadowRenderPass(),
                        in.shadowPipeline(),
                        in.shadowPipelineLayout(),
                        in.shadowFramebuffers(),
                        in.postOffscreenActive(),
                        in.postIntermediateInitialized(),
                        in.tonemapEnabled(),
                        in.tonemapExposure(),
                        in.tonemapGamma(),
                        in.ssaoEnabled(),
                        in.bloomEnabled(),
                        in.bloomThreshold(),
                        in.bloomStrength(),
                        in.ssaoStrength(),
                        in.postRenderPass(),
                        in.postGraphicsPipeline(),
                        in.postPipelineLayout(),
                        in.postDescriptorSet(),
                        in.offscreenColorImage(),
                        in.swapchainImages(),
                        in.postFramebuffers(),
                        in.descriptorSetForFrame(),
                        in.dynamicUniformOffsetForMesh(),
                        in.vkFailure()
                )
        );
    }

    public record AssemblyInputs(
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
            boolean ssaoEnabled,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength,
            float ssaoStrength,
            long postRenderPass,
            long postGraphicsPipeline,
            long postPipelineLayout,
            long postDescriptorSet,
            long offscreenColorImage,
            long[] swapchainImages,
            long[] postFramebuffers,
            VulkanFrameCommandOrchestrator.LongByInt descriptorSetForFrame,
            IntUnaryOperator dynamicUniformOffsetForMesh,
            VulkanFrameCommandOrchestrator.FailureFactory vkFailure
    ) {
    }
}
