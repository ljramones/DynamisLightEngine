package org.dynamislight.impl.vulkan.shadow;

public final class VulkanShadowMatrixStateCoordinator {
    public record UpdateRequest(
            long previousStateKey,
            float pointLightIsSpot,
            float pointLightDirX,
            float pointLightDirY,
            float pointLightDirZ,
            float pointLightPosX,
            float pointLightPosY,
            float pointLightPosZ,
            float pointLightOuterCos,
            float pointShadowFarPlane,
            boolean pointShadowEnabled,
            int shadowCascadeCount,
            int shadowMapResolution,
            boolean shadowDirectionalTexelSnapEnabled,
            float shadowDirectionalTexelSnapScale,
            float dirLightDirX,
            float dirLightDirY,
            float dirLightDirZ,
            int localLightCount,
            float[] localLightPosRange,
            float[] localLightDirInner,
            float[] localLightOuterTypeShadow,
            float[] viewMatrix,
            float[] projMatrix,
            int maxShadowMatrices,
            int maxShadowCascades,
            int pointShadowFaces
    ) {
    }

    public record UpdateResult(long stateKey, boolean updated) {
    }

    public static UpdateResult updateMatricesIfDirty(UpdateRequest request, float[][] shadowLightViewProjMatrices, float[] shadowCascadeSplitNdc) {
        long stateKey = computeStateKey(request);
        if (stateKey == request.previousStateKey()) {
            return new UpdateResult(stateKey, false);
        }
        VulkanShadowMatrixCoordinator.updateMatrices(
                new VulkanShadowMatrixCoordinator.UpdateInputs(
                        request.pointLightIsSpot(),
                        request.pointLightDirX(),
                        request.pointLightDirY(),
                        request.pointLightDirZ(),
                        request.pointLightPosX(),
                        request.pointLightPosY(),
                        request.pointLightPosZ(),
                        request.pointLightOuterCos(),
                        request.pointShadowEnabled(),
                        request.pointShadowFarPlane(),
                        request.shadowCascadeCount(),
                        request.shadowMapResolution(),
                        request.shadowDirectionalTexelSnapEnabled(),
                        request.shadowDirectionalTexelSnapScale(),
                        request.viewMatrix(),
                        request.projMatrix(),
                        request.localLightCount(),
                        request.localLightPosRange(),
                        request.localLightDirInner(),
                        request.localLightOuterTypeShadow(),
                        request.dirLightDirX(),
                        request.dirLightDirY(),
                        request.dirLightDirZ(),
                        request.maxShadowMatrices(),
                        request.maxShadowCascades(),
                        request.pointShadowFaces()
                ),
                shadowLightViewProjMatrices,
                shadowCascadeSplitNdc
        );
        return new UpdateResult(stateKey, true);
    }

    private static long computeStateKey(UpdateRequest request) {
        long key = 17L;
        key = 31L * key + Float.floatToIntBits(request.pointLightIsSpot());
        key = 31L * key + Float.floatToIntBits(request.pointLightDirX());
        key = 31L * key + Float.floatToIntBits(request.pointLightDirY());
        key = 31L * key + Float.floatToIntBits(request.pointLightDirZ());
        key = 31L * key + Float.floatToIntBits(request.pointLightPosX());
        key = 31L * key + Float.floatToIntBits(request.pointLightPosY());
        key = 31L * key + Float.floatToIntBits(request.pointLightPosZ());
        key = 31L * key + Float.floatToIntBits(request.pointLightOuterCos());
        key = 31L * key + Float.floatToIntBits(request.pointShadowFarPlane());
        key = 31L * key + (request.pointShadowEnabled() ? 1 : 0);
        key = 31L * key + request.shadowCascadeCount();
        key = 31L * key + (request.shadowDirectionalTexelSnapEnabled() ? 1 : 0);
        key = 31L * key + Float.floatToIntBits(request.shadowDirectionalTexelSnapScale());
        key = 31L * key + Float.floatToIntBits(request.dirLightDirX());
        key = 31L * key + Float.floatToIntBits(request.dirLightDirY());
        key = 31L * key + Float.floatToIntBits(request.dirLightDirZ());
        key = 31L * key + request.localLightCount();
        int localFloats = Math.min(request.localLightCount() * 4, request.localLightPosRange().length);
        for (int i = 0; i < localFloats; i++) {
            key = 31L * key + Float.floatToIntBits(request.localLightPosRange()[i]);
            key = 31L * key + Float.floatToIntBits(request.localLightDirInner()[i]);
            key = 31L * key + Float.floatToIntBits(request.localLightOuterTypeShadow()[i]);
        }
        for (int i = 0; i < 16; i++) {
            key = 31L * key + Float.floatToIntBits(request.viewMatrix()[i]);
            key = 31L * key + Float.floatToIntBits(request.projMatrix()[i]);
        }
        return key;
    }

    private VulkanShadowMatrixStateCoordinator() {
    }
}
