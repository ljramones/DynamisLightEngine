package org.dynamislight.impl.vulkan.state;

public final class VulkanLightingParameterMutator {
    private VulkanLightingParameterMutator() {
    }

    public static LightingResult applyLighting(LightingState current, LightingUpdate update) {
        boolean changed = false;
        float dirLightDirX = current.dirLightDirX();
        float dirLightDirY = current.dirLightDirY();
        float dirLightDirZ = current.dirLightDirZ();
        float dirLightColorR = current.dirLightColorR();
        float dirLightColorG = current.dirLightColorG();
        float dirLightColorB = current.dirLightColorB();
        float dirLightIntensity = current.dirLightIntensity();
        float pointLightPosX = current.pointLightPosX();
        float pointLightPosY = current.pointLightPosY();
        float pointLightPosZ = current.pointLightPosZ();
        float pointLightColorR = current.pointLightColorR();
        float pointLightColorG = current.pointLightColorG();
        float pointLightColorB = current.pointLightColorB();
        float pointLightIntensity = current.pointLightIntensity();
        float pointLightDirX = current.pointLightDirX();
        float pointLightDirY = current.pointLightDirY();
        float pointLightDirZ = current.pointLightDirZ();
        float pointLightInnerCos = current.pointLightInnerCos();
        float pointLightOuterCos = current.pointLightOuterCos();
        float pointLightIsSpot = current.pointLightIsSpot();
        float pointShadowFarPlane = current.pointShadowFarPlane();
        boolean pointShadowEnabled = current.pointShadowEnabled();

        if (update.dirDir() != null && update.dirDir().length == 3) {
            if (!floatEquals(dirLightDirX, update.dirDir()[0])
                    || !floatEquals(dirLightDirY, update.dirDir()[1])
                    || !floatEquals(dirLightDirZ, update.dirDir()[2])) {
                dirLightDirX = update.dirDir()[0];
                dirLightDirY = update.dirDir()[1];
                dirLightDirZ = update.dirDir()[2];
                changed = true;
            }
        }
        if (update.dirColor() != null && update.dirColor().length == 3) {
            if (!floatEquals(dirLightColorR, update.dirColor()[0])
                    || !floatEquals(dirLightColorG, update.dirColor()[1])
                    || !floatEquals(dirLightColorB, update.dirColor()[2])) {
                dirLightColorR = update.dirColor()[0];
                dirLightColorG = update.dirColor()[1];
                dirLightColorB = update.dirColor()[2];
                changed = true;
            }
        }
        float clampedDirIntensity = Math.max(0f, update.dirIntensity());
        if (!floatEquals(dirLightIntensity, clampedDirIntensity)) {
            dirLightIntensity = clampedDirIntensity;
            changed = true;
        }
        if (update.pointPos() != null && update.pointPos().length == 3) {
            if (!floatEquals(pointLightPosX, update.pointPos()[0])
                    || !floatEquals(pointLightPosY, update.pointPos()[1])
                    || !floatEquals(pointLightPosZ, update.pointPos()[2])) {
                pointLightPosX = update.pointPos()[0];
                pointLightPosY = update.pointPos()[1];
                pointLightPosZ = update.pointPos()[2];
                changed = true;
            }
        }
        if (update.pointColor() != null && update.pointColor().length == 3) {
            if (!floatEquals(pointLightColorR, update.pointColor()[0])
                    || !floatEquals(pointLightColorG, update.pointColor()[1])
                    || !floatEquals(pointLightColorB, update.pointColor()[2])) {
                pointLightColorR = update.pointColor()[0];
                pointLightColorG = update.pointColor()[1];
                pointLightColorB = update.pointColor()[2];
                changed = true;
            }
        }
        float clampedPointIntensity = Math.max(0f, update.pointIntensity());
        if (!floatEquals(pointLightIntensity, clampedPointIntensity)) {
            pointLightIntensity = clampedPointIntensity;
            changed = true;
        }
        if (update.pointDirection() != null && update.pointDirection().length == 3) {
            float[] normalized = normalize3(update.pointDirection()[0], update.pointDirection()[1], update.pointDirection()[2]);
            if (!floatEquals(pointLightDirX, normalized[0])
                    || !floatEquals(pointLightDirY, normalized[1])
                    || !floatEquals(pointLightDirZ, normalized[2])) {
                pointLightDirX = normalized[0];
                pointLightDirY = normalized[1];
                pointLightDirZ = normalized[2];
                changed = true;
            }
        }
        float clampedInner = clamp01(update.pointInnerCos());
        float clampedOuter = clamp01(update.pointOuterCos());
        if (clampedOuter > clampedInner) {
            clampedOuter = clampedInner;
        }
        if (!floatEquals(pointLightInnerCos, clampedInner) || !floatEquals(pointLightOuterCos, clampedOuter)) {
            pointLightInnerCos = clampedInner;
            pointLightOuterCos = clampedOuter;
            changed = true;
        }
        float spotValue = update.pointIsSpot() ? 1f : 0f;
        if (!floatEquals(pointLightIsSpot, spotValue)) {
            pointLightIsSpot = spotValue;
            changed = true;
        }
        float clampedRange = Math.max(1.0f, update.pointRange());
        if (!floatEquals(pointShadowFarPlane, clampedRange)) {
            pointShadowFarPlane = clampedRange;
            changed = true;
        }
        if (pointShadowEnabled != update.pointCastsShadows()) {
            pointShadowEnabled = update.pointCastsShadows();
            changed = true;
        }
        return new LightingResult(new LightingState(
                dirLightDirX, dirLightDirY, dirLightDirZ,
                dirLightColorR, dirLightColorG, dirLightColorB,
                dirLightIntensity,
                pointLightPosX, pointLightPosY, pointLightPosZ,
                pointLightColorR, pointLightColorG, pointLightColorB,
                pointLightIntensity,
                pointLightDirX, pointLightDirY, pointLightDirZ,
                pointLightInnerCos, pointLightOuterCos,
                pointLightIsSpot,
                pointShadowFarPlane,
                pointShadowEnabled
        ), changed);
    }

    public static ShadowResult applyShadow(ShadowState current, ShadowUpdate update) {
        boolean changed = false;
        boolean shadowEnabled = current.shadowEnabled();
        float shadowStrength = current.shadowStrength();
        float shadowBias = current.shadowBias();
        float shadowNormalBiasScale = current.shadowNormalBiasScale();
        float shadowSlopeBiasScale = current.shadowSlopeBiasScale();
        int shadowPcfRadius = current.shadowPcfRadius();
        int shadowCascadeCount = current.shadowCascadeCount();
        int shadowMapResolution = current.shadowMapResolution();

        if (shadowEnabled != update.enabled()) {
            shadowEnabled = update.enabled();
            changed = true;
        }
        float clampedStrength = Math.max(0f, Math.min(1f, update.strength()));
        if (!floatEquals(shadowStrength, clampedStrength)) {
            shadowStrength = clampedStrength;
            changed = true;
        }
        float clampedBias = Math.max(0.00002f, update.bias());
        if (!floatEquals(shadowBias, clampedBias)) {
            shadowBias = clampedBias;
            changed = true;
        }
        float clampedNormalScale = Math.max(0.25f, Math.min(4.0f, update.normalBiasScale()));
        if (!floatEquals(shadowNormalBiasScale, clampedNormalScale)) {
            shadowNormalBiasScale = clampedNormalScale;
            changed = true;
        }
        float clampedSlopeScale = Math.max(0.25f, Math.min(4.0f, update.slopeBiasScale()));
        if (!floatEquals(shadowSlopeBiasScale, clampedSlopeScale)) {
            shadowSlopeBiasScale = clampedSlopeScale;
            changed = true;
        }
        int clampedPcf = Math.max(0, update.pcfRadius());
        if (shadowPcfRadius != clampedPcf) {
            shadowPcfRadius = clampedPcf;
            changed = true;
        }
        int clampedCascadeCount = Math.max(1, Math.min(update.maxShadowMatrices(), update.cascadeCount()));
        if (shadowCascadeCount != clampedCascadeCount) {
            shadowCascadeCount = clampedCascadeCount;
            changed = true;
        }
        int clampedResolution = Math.max(256, Math.min(4096, update.mapResolution()));
        boolean resolutionChanged = shadowMapResolution != clampedResolution;
        if (resolutionChanged) {
            shadowMapResolution = clampedResolution;
            changed = true;
        }
        return new ShadowResult(new ShadowState(
                shadowEnabled,
                shadowStrength,
                shadowBias,
                shadowNormalBiasScale,
                shadowSlopeBiasScale,
                shadowPcfRadius,
                shadowCascadeCount,
                shadowMapResolution
        ), changed, resolutionChanged);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float[] normalize3(float x, float y, float z) {
        float lenSq = x * x + y * y + z * z;
        if (lenSq <= 0.000001f) {
            return new float[]{0f, -1f, 0f};
        }
        float invLen = (float) (1.0 / Math.sqrt(lenSq));
        return new float[]{x * invLen, y * invLen, z * invLen};
    }

    private static boolean floatEquals(float a, float b) {
        return Math.abs(a - b) <= 0.000001f;
    }

    public record LightingState(
            float dirLightDirX,
            float dirLightDirY,
            float dirLightDirZ,
            float dirLightColorR,
            float dirLightColorG,
            float dirLightColorB,
            float dirLightIntensity,
            float pointLightPosX,
            float pointLightPosY,
            float pointLightPosZ,
            float pointLightColorR,
            float pointLightColorG,
            float pointLightColorB,
            float pointLightIntensity,
            float pointLightDirX,
            float pointLightDirY,
            float pointLightDirZ,
            float pointLightInnerCos,
            float pointLightOuterCos,
            float pointLightIsSpot,
            float pointShadowFarPlane,
            boolean pointShadowEnabled
    ) {
    }

    public record LightingUpdate(
            float[] dirDir,
            float[] dirColor,
            float dirIntensity,
            float[] pointPos,
            float[] pointColor,
            float pointIntensity,
            float[] pointDirection,
            float pointInnerCos,
            float pointOuterCos,
            boolean pointIsSpot,
            float pointRange,
            boolean pointCastsShadows
    ) {
    }

    public record LightingResult(LightingState state, boolean changed) {
    }

    public record ShadowState(
            boolean shadowEnabled,
            float shadowStrength,
            float shadowBias,
            float shadowNormalBiasScale,
            float shadowSlopeBiasScale,
            int shadowPcfRadius,
            int shadowCascadeCount,
            int shadowMapResolution
    ) {
    }

    public record ShadowUpdate(
            boolean enabled,
            float strength,
            float bias,
            float normalBiasScale,
            float slopeBiasScale,
            int pcfRadius,
            int cascadeCount,
            int mapResolution,
            int maxShadowMatrices
    ) {
    }

    public record ShadowResult(ShadowState state, boolean changed, boolean resolutionChanged) {
    }
}
