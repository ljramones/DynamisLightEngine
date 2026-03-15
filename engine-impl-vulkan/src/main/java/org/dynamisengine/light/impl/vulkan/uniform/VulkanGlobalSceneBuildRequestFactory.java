package org.dynamisengine.light.impl.vulkan.uniform;

import org.dynamisengine.light.impl.vulkan.state.VulkanIblState;
import org.dynamisengine.light.impl.vulkan.state.VulkanLightingParameterMutator;
import org.dynamisengine.light.impl.vulkan.state.VulkanRenderState;
import org.vectrix.core.Matrix4f;

public final class VulkanGlobalSceneBuildRequestFactory {
    public record Inputs(
            int globalSceneUniformBytes,
            Matrix4f viewMatrix,
            Matrix4f projMatrix,
            boolean taaPrevViewProjValid,
            Matrix4f taaPrevViewProj,
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
        Matrix4f planeReflection = planarReflectionMatrix(planarHeight);
        Matrix4f planarViewMatrix = new Matrix4f(in.viewMatrix()).mul(planeReflection, new Matrix4f());
        Matrix4f planarProjMatrix = new Matrix4f(in.projMatrix());
        Matrix4f viewMatrix = new Matrix4f(in.viewMatrix());
        Matrix4f projMatrix = new Matrix4f(in.projMatrix());
        Matrix4f planarPrevViewProj = in.taaPrevViewProjValid()
                ? new Matrix4f(in.taaPrevViewProj()).mul(planeReflection, new Matrix4f())
                : new Matrix4f(planarProjMatrix).mul(planarViewMatrix, new Matrix4f());
        Matrix4f prevViewProj = in.taaPrevViewProjValid()
                ? new Matrix4f(in.taaPrevViewProj())
                : new Matrix4f(projMatrix).mul(viewMatrix, new Matrix4f());
        Matrix4f[] shadowLightViewProjMatrices = toMatrixArray(in.renderState().shadowLightViewProjMatrices);

        return new VulkanGlobalSceneUniformCoordinator.BuildRequest(
                in.globalSceneUniformBytes(),
                viewMatrix,
                projMatrix,
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
                prevViewProj,
                shadowLightViewProjMatrices,
                planarViewMatrix,
                planarProjMatrix,
                planarPrevViewProj
        );
    }

    private static Matrix4f planarReflectionMatrix(float planeHeight) {
        return new Matrix4f().set(new float[]{
                1f, 0f, 0f, 0f,
                0f, -1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 2f * planeHeight, 0f, 1f
        });
    }

    private static Matrix4f[] toMatrixArray(float[][] matrices) {
        if (matrices == null || matrices.length == 0) {
            return new Matrix4f[0];
        }
        Matrix4f[] result = new Matrix4f[matrices.length];
        for (int i = 0; i < matrices.length; i++) {
            float[] source = matrices[i];
            result[i] = (source != null && source.length == 16)
                    ? new Matrix4f().set(source)
                    : new Matrix4f().identity();
        }
        return result;
    }

    private VulkanGlobalSceneBuildRequestFactory() {
    }
}
