package org.dynamislight.impl.vulkan.shadow;

/**
 * Utility class that coordinates the generation and updating of shadow matrices for Vulkan rendering.
 * This class integrates with {@code VulkanShadowMatrixBuilder} to manage shadow matrix calculations
 * for point and directional light sources, including shadow cascades and projections.
 * The class is not instantiable and provides a single method to perform updates.
 */
public final class VulkanShadowMatrixCoordinator {
    private VulkanShadowMatrixCoordinator() {
    }

    /**
     * Updates the shadow light view-projection matrices and shadow cascade split values
     * based on the provided light source and view/projection configurations. This method
     * delegates the calculations to the {@code VulkanShadowMatrixBuilder}.
     *
     * @param in                     An {@link UpdateInputs} record containing input parameters
     *                               related to the light source and shadow configurations.
     * @param shadowLightViewProjMatrices A 2D array of floats to store the resulting shadow
     *                                    light view-projection matrices.
     * @param shadowCascadeSplitNdc A 1D array of floats to store the shadow cascade split
     *                              distances in normalized device coordinates (NDC).
     */
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

    /**
     * Represents the input parameters required for shadow calculation and light configuration updates.
     * This record is used to encapsulate various attributes of light sources and shadow rendering
     * settings, such as light direction, position, shadow-related values, and projection/view matrices.
     *
     * Attributes include:
     * - Light source properties such as direction, position, and spotlight characteristics.
     * - Shadow configuration settings including shadow enabling flag, far plane, and cascade count.
     * - Transformation matrices for view and projection.
     */
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
