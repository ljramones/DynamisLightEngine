package org.dynamislight.impl.vulkan.command;

import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamislight.impl.vulkan.state.VulkanRenderState;
import org.dynamislight.impl.vulkan.state.VulkanSceneResourceState;
import org.dynamislight.impl.vulkan.state.VulkanTemporalAaCoordinator;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainTimestampRuntimeHelper;
import org.dynamislight.impl.vulkan.uniform.VulkanUniformFrameCoordinator;

public final class VulkanCommandInputCoordinator {
    public record BuildRequest(
            VulkanSceneResourceState sceneResources,
            int maxDynamicSceneObjects,
            VulkanBackendResources backendResources,
            VulkanRenderState renderState,
            VulkanDescriptorResourceState descriptorResources,
            boolean pointShadowEnabled,
            int frameIdx,
            int framesInFlight,
            int maxShadowMatrices,
            int maxShadowCascades,
            int pointShadowFaces,
            VulkanFrameCommandOrchestrator.FailureFactory vkFailure
    ) {
    }

    public static VulkanFrameCommandOrchestrator.Inputs build(BuildRequest request) {
        int queryStart = VulkanSwapchainTimestampRuntimeHelper.planarTimestampQueryStartIndex(
                request.backendResources(),
                request.framesInFlight(),
                request.frameIdx()
        );
        int queryEnd = VulkanSwapchainTimestampRuntimeHelper.planarTimestampQueryEndIndex(
                request.backendResources(),
                request.framesInFlight(),
                request.frameIdx()
        );

        return VulkanFrameCommandInputAssembler.build(
                new VulkanFrameCommandInputAssembler.AssemblyInputs(
                        request.sceneResources().gpuMeshes,
                        request.maxDynamicSceneObjects(),
                        request.backendResources().swapchainWidth,
                        request.backendResources().swapchainHeight,
                        request.backendResources().swapchainImageFormat,
                        request.backendResources().depthFormat,
                        request.renderState().shadowMapResolution,
                        request.renderState().shadowEnabled,
                        request.pointShadowEnabled(),
                        request.renderState().shadowCascadeCount,
                        request.maxShadowMatrices(),
                        request.maxShadowCascades(),
                        request.pointShadowFaces(),
                        request.backendResources().renderPass,
                        request.backendResources().framebuffers,
                        request.backendResources().graphicsPipeline,
                        request.backendResources().pipelineLayout,
                        request.backendResources().shadowRenderPass,
                        request.backendResources().shadowPipeline,
                        request.backendResources().shadowPipelineLayout,
                        request.backendResources().shadowFramebuffers,
                        request.backendResources().shadowDepthImage,
                        request.backendResources().shadowMomentImage,
                        request.backendResources().shadowMomentFormat,
                        request.backendResources().shadowMomentMipLevels,
                        request.renderState().shadowMomentPipelineRequested,
                        request.renderState().shadowMomentInitialized,
                        request.backendResources().depthImages,
                        request.backendResources().planarTimestampQueryPool,
                        queryStart,
                        queryEnd,
                        request.renderState().postOffscreenActive,
                        request.renderState().postIntermediateInitialized,
                        request.renderState().tonemapEnabled,
                        request.renderState().tonemapExposure,
                        request.renderState().tonemapGamma,
                        request.renderState().ssaoEnabled,
                        request.renderState().bloomEnabled,
                        request.renderState().bloomThreshold,
                        request.renderState().bloomStrength,
                        request.renderState().ssaoStrength,
                        request.renderState().ssaoRadius,
                        request.renderState().ssaoBias,
                        request.renderState().ssaoPower,
                        request.renderState().smaaEnabled,
                        request.renderState().smaaStrength,
                        request.renderState().taaEnabled,
                        request.renderState().taaBlend,
                        request.renderState().postTaaHistoryInitialized,
                        VulkanTemporalAaCoordinator.taaJitterUvDeltaX(request.renderState()),
                        VulkanTemporalAaCoordinator.taaJitterUvDeltaY(request.renderState()),
                        request.renderState().taaMotionUvX,
                        request.renderState().taaMotionUvY,
                        request.renderState().taaClipScale,
                        request.renderState().taaRenderScale,
                        request.renderState().taaLumaClipEnabled,
                        request.renderState().taaSharpenStrength,
                        request.renderState().reflectionsEnabled,
                        request.renderState().reflectionsMode,
                        request.renderState().reflectionsSsrStrength,
                        request.renderState().reflectionsSsrMaxRoughness,
                        request.renderState().reflectionsSsrStepScale,
                        request.renderState().reflectionsTemporalWeight,
                        request.renderState().reflectionsPlanarStrength,
                        request.renderState().reflectionsPlanarPlaneHeight,
                        request.renderState().reflectionsRtDenoiseStrength,
                        request.renderState().taaDebugView,
                        request.backendResources().postRenderPass,
                        request.backendResources().postGraphicsPipeline,
                        request.backendResources().postPipelineLayout,
                        request.backendResources().postDescriptorSet,
                        request.backendResources().offscreenColorImage,
                        request.backendResources().postTaaHistoryImage,
                        request.backendResources().postTaaHistoryVelocityImage,
                        request.backendResources().postPlanarCaptureImage,
                        request.backendResources().velocityImage,
                        request.backendResources().swapchainImages,
                        request.backendResources().postFramebuffers,
                        frame -> VulkanUniformFrameCoordinator.descriptorSetForFrame(
                                request.descriptorResources().frameDescriptorSets,
                                request.descriptorResources().descriptorSet,
                                frame
                        ),
                        meshIndex -> VulkanUniformFrameCoordinator.dynamicUniformOffset(
                                request.descriptorResources().uniformStrideBytes,
                                meshIndex
                        ),
                        request.vkFailure()
                )
        );
    }

    private VulkanCommandInputCoordinator() {
    }
}
