package org.dynamislight.impl.vulkan.uniform;

import org.dynamislight.impl.vulkan.state.VulkanIblState;
import org.dynamislight.impl.vulkan.state.VulkanLightingParameterMutator;
import org.dynamislight.impl.vulkan.state.VulkanRenderState;

import static org.dynamislight.impl.vulkan.math.VulkanMath.mul;

public final class VulkanGlobalSceneBuildRequestFactory {
    public record Inputs(
            int globalSceneUniformBytes,
            float[] viewMatrix,
            float[] projMatrix,
            boolean taaPrevViewProjValid,
            float[] taaPrevViewProj,
            VulkanLightingParameterMutator.LightingState lightingState,
            VulkanRenderState renderState,
            int localLightCount,
            int maxLocalLights,
            float[] localLightPosRange,
            float[] localLightColorIntensity,
            float[] localLightDirInner,
            float[] localLightOuterTypeShadow,
            boolean shadowRtTraversalSupported,
            int swapchainWidth,
            int swapchainHeight,
            VulkanIblState iblState
    ) {
    }

    public static VulkanGlobalSceneUniformCoordinator.BuildRequest build(Inputs in) {
        float planarHeight = in.renderState().reflectionsPlanarPlaneHeight;
        float[] planeReflection = planarReflectionMatrix(planarHeight);
        float[] planarViewMatrix = mul(in.viewMatrix(), planeReflection);
        float[] planarProjMatrix = in.projMatrix();
        float[] planarPrevViewProj = in.taaPrevViewProjValid()
                ? mul(in.taaPrevViewProj(), planeReflection)
                : mul(planarProjMatrix, planarViewMatrix);

        return new VulkanGlobalSceneUniformCoordinator.BuildRequest(
                in.globalSceneUniformBytes(),
                in.viewMatrix(),
                in.projMatrix(),
                in.lightingState().dirLightDirX(),
                in.lightingState().dirLightDirY(),
                in.lightingState().dirLightDirZ(),
                in.renderState().shadowPcssSoftness,
                in.lightingState().dirLightColorR(),
                in.lightingState().dirLightColorG(),
                in.lightingState().dirLightColorB(),
                in.renderState().shadowMomentBlend,
                in.lightingState().pointLightPosX(),
                in.lightingState().pointLightPosY(),
                in.lightingState().pointLightPosZ(),
                in.lightingState().pointShadowFarPlane(),
                in.lightingState().pointLightColorR(),
                in.lightingState().pointLightColorG(),
                in.lightingState().pointLightColorB(),
                in.renderState().shadowMomentBleedReduction,
                in.lightingState().pointLightDirX(),
                in.lightingState().pointLightDirY(),
                in.lightingState().pointLightDirZ(),
                in.renderState().shadowContactStrength,
                in.lightingState().pointLightInnerCos(),
                in.lightingState().pointLightOuterCos(),
                in.lightingState().pointLightIsSpot(),
                in.lightingState().pointShadowEnabled(),
                in.localLightCount(),
                in.maxLocalLights(),
                in.localLightPosRange(),
                in.localLightColorIntensity(),
                in.localLightDirInner(),
                in.localLightOuterTypeShadow(),
                in.renderState().shadowFilterMode,
                in.renderState().shadowRtMode,
                in.shadowRtTraversalSupported() && in.renderState().shadowRtMode > 0,
                in.renderState().shadowRtDenoiseStrength,
                in.renderState().shadowRtRayLength,
                in.renderState().shadowRtSampleCount,
                in.renderState().shadowContactShadows,
                in.lightingState().dirLightIntensity(),
                in.lightingState().pointLightIntensity(),
                in.renderState().shadowContactTemporalMotionScale,
                in.renderState().shadowContactTemporalMinStability,
                in.renderState().shadowEnabled,
                in.renderState().shadowStrength,
                in.renderState().shadowBias,
                in.renderState().shadowNormalBiasScale,
                in.renderState().shadowSlopeBiasScale,
                in.renderState().shadowPcfRadius,
                in.renderState().shadowCascadeCount,
                in.renderState().shadowMapResolution,
                in.renderState().shadowCascadeSplitNdc,
                in.renderState().fogEnabled,
                in.renderState().fogDensity,
                in.renderState().fogR,
                in.renderState().fogG,
                in.renderState().fogB,
                in.renderState().fogSteps,
                in.renderState().smokeEnabled,
                in.renderState().smokeIntensity,
                in.swapchainWidth(),
                in.swapchainHeight(),
                in.renderState().smokeR,
                in.renderState().smokeG,
                in.renderState().smokeB,
                in.iblState().enabled,
                in.iblState().diffuseStrength,
                in.iblState().specularStrength,
                in.iblState().prefilterStrength,
                in.renderState().postOffscreenActive,
                in.renderState().tonemapEnabled,
                in.renderState().tonemapExposure,
                in.renderState().tonemapGamma,
                in.renderState().bloomEnabled,
                in.renderState().bloomThreshold,
                in.renderState().bloomStrength,
                in.renderState().ssaoEnabled,
                in.renderState().ssaoStrength,
                in.renderState().ssaoRadius,
                in.renderState().ssaoBias,
                in.renderState().ssaoPower,
                in.renderState().smaaEnabled,
                in.renderState().smaaStrength,
                in.taaPrevViewProjValid() ? in.taaPrevViewProj() : mul(in.projMatrix(), in.viewMatrix()),
                in.renderState().shadowLightViewProjMatrices,
                planarViewMatrix,
                planarProjMatrix,
                planarPrevViewProj
        );
    }

    private static float[] planarReflectionMatrix(float planeHeight) {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, -1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 2f * planeHeight, 0f, 1f
        };
    }

    private VulkanGlobalSceneBuildRequestFactory() {
    }
}
