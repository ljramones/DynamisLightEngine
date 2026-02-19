package org.dynamislight.api.runtime;

/**
 * Backend-agnostic AA upscale hardening/promotion diagnostics snapshot for TSR/TUUA paths.
 */
public record AaUpscalePromotionDiagnostics(
        boolean available,
        boolean upscaleModeActive,
        String aaMode,
        boolean temporalPathActive,
        double renderScale,
        String upscalerMode,
        boolean nativeUpscalerActive,
        String nativeUpscalerProvider,
        double minRenderScaleWarn,
        double maxRenderScaleWarn,
        int promotionReadyMinFrames,
        int stableStreak,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame
) {
    public AaUpscalePromotionDiagnostics {
        aaMode = aaMode == null ? "" : aaMode;
        upscalerMode = upscalerMode == null ? "none" : upscalerMode;
        nativeUpscalerProvider = nativeUpscalerProvider == null ? "none" : nativeUpscalerProvider;
        renderScale = clamp(renderScale, 0.1, 2.0);
        minRenderScaleWarn = clamp(minRenderScaleWarn, 0.1, 2.0);
        maxRenderScaleWarn = clamp(maxRenderScaleWarn, 0.1, 2.0);
        promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
        stableStreak = Math.max(0, stableStreak);
    }

    public static AaUpscalePromotionDiagnostics unavailable() {
        return new AaUpscalePromotionDiagnostics(
                false,
                false,
                "",
                false,
                1.0,
                "none",
                false,
                "none",
                0.5,
                1.0,
                1,
                0,
                false,
                false
        );
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
