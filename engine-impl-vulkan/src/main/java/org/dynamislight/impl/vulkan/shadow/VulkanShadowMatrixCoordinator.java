package org.dynamislight.impl.vulkan.shadow;

public final class VulkanShadowMatrixCoordinator {
    private VulkanShadowMatrixCoordinator() {
    }

    public static void updateMatrices(UpdateInputs in, float[][] shadowLightViewProjMatrices, float[] shadowCascadeSplitNdc) {
        VulkanShadowMatrixBuilder.updateMatrices(
                new VulkanShadowMatrixBuilder.ShadowInputs(
                        in.pointLightIsSpot(),
                        in.pointLightDirX(),
                        in.pointLightDirY(),
                        in.pointLightDirZ(),
                        in.pointLightPosX(),
                        in.pointLightPosY(),
                        in.pointLightPosZ(),
                        in.pointLightOuterCos(),
                        in.pointShadowEnabled(),
                        in.pointShadowFarPlane(),
                        in.shadowCascadeCount(),
                        in.viewMatrix(),
                        in.projMatrix(),
                        in.dirLightDirX(),
                        in.dirLightDirY(),
                        in.dirLightDirZ(),
                        in.maxShadowMatrices(),
                        in.maxShadowCascades(),
                        in.pointShadowFaces()
                ),
                shadowLightViewProjMatrices,
                shadowCascadeSplitNdc
        );
    }

    public record UpdateInputs(
            float pointLightIsSpot,
            float pointLightDirX,
            float pointLightDirY,
            float pointLightDirZ,
            float pointLightPosX,
            float pointLightPosY,
            float pointLightPosZ,
            float pointLightOuterCos,
            boolean pointShadowEnabled,
            float pointShadowFarPlane,
            int shadowCascadeCount,
            float[] viewMatrix,
            float[] projMatrix,
            float dirLightDirX,
            float dirLightDirY,
            float dirLightDirZ,
            int maxShadowMatrices,
            int maxShadowCascades,
            int pointShadowFaces
    ) {
    }
}
