package org.dynamislight.api.runtime;

/**
 * Backend-agnostic AA quality-mode diagnostics (DLAA + specular AA envelope/promotion).
 */
public record AaQualityPromotionDiagnostics(
        boolean available,
        String aaMode,
        boolean dlaaModeActive,
        boolean dlaaTemporalPathActive,
        double dlaaBlend,
        double dlaaRenderScale,
        double dlaaWarnMinBlend,
        double dlaaWarnMinRenderScale,
        int dlaaPromotionReadyMinFrames,
        int dlaaStableStreak,
        boolean dlaaEnvelopeBreachedLastFrame,
        boolean dlaaPromotionReadyLastFrame,
        boolean specularPolicyActive,
        int materialCount,
        int normalMappedMaterialCount,
        double normalMappedMaterialRatio,
        double specularClipScale,
        double specularWarnMaxClipScale,
        int specularPromotionReadyMinFrames,
        int specularStableStreak,
        boolean specularEnvelopeBreachedLastFrame,
        boolean specularPromotionReadyLastFrame
) {
    public AaQualityPromotionDiagnostics {
        aaMode = aaMode == null ? "" : aaMode;
        dlaaBlend = clamp01(dlaaBlend);
        dlaaRenderScale = clamp(dlaaRenderScale, 0.1, 2.0);
        dlaaWarnMinBlend = clamp01(dlaaWarnMinBlend);
        dlaaWarnMinRenderScale = clamp(dlaaWarnMinRenderScale, 0.1, 2.0);
        dlaaPromotionReadyMinFrames = Math.max(1, dlaaPromotionReadyMinFrames);
        dlaaStableStreak = Math.max(0, dlaaStableStreak);
        materialCount = Math.max(0, materialCount);
        normalMappedMaterialCount = Math.max(0, normalMappedMaterialCount);
        normalMappedMaterialRatio = clamp01(normalMappedMaterialRatio);
        specularClipScale = clamp(specularClipScale, 0.1, 2.0);
        specularWarnMaxClipScale = clamp(specularWarnMaxClipScale, 0.1, 2.0);
        specularPromotionReadyMinFrames = Math.max(1, specularPromotionReadyMinFrames);
        specularStableStreak = Math.max(0, specularStableStreak);
    }

    public static AaQualityPromotionDiagnostics unavailable() {
        return new AaQualityPromotionDiagnostics(
                false,
                "",
                false,
                false,
                0.0,
                1.0,
                0.9,
                1.0,
                1,
                0,
                false,
                false,
                false,
                0,
                0,
                0.0,
                1.0,
                1.1,
                1,
                0,
                false,
                false
        );
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
