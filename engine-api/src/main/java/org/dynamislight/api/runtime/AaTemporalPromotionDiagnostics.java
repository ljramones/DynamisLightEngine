package org.dynamislight.api.runtime;

/**
 * Backend-agnostic AA temporal hardening/promotion diagnostics snapshot.
 */
public record AaTemporalPromotionDiagnostics(
        boolean available,
        String aaMode,
        boolean temporalPathRequested,
        boolean temporalPathActive,
        double rejectRate,
        double confidenceMean,
        long confidenceDropEvents,
        double rejectWarnMax,
        double confidenceWarnMin,
        long dropWarnMin,
        int promotionReadyMinFrames,
        int stableStreak,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame,
        int materialCount,
        int reactiveAuthoredCount,
        double reactiveCoverage,
        double reactiveCoverageWarnMin,
        int historyClampCustomizedCount,
        double historyClampCustomizedRatio,
        double historyClampCustomizedWarnMin,
        double historyClampMean,
        boolean reactiveMaskBreachedLastFrame,
        boolean historyClampBreachedLastFrame,
        int temporalCorePromotionReadyMinFrames,
        int temporalCoreStableStreak,
        boolean temporalCorePromotionReadyLastFrame
) {
    public AaTemporalPromotionDiagnostics {
        aaMode = aaMode == null ? "" : aaMode;
        rejectRate = clamp01(rejectRate);
        confidenceMean = clamp01(confidenceMean);
        rejectWarnMax = clampPositive(rejectWarnMax);
        confidenceWarnMin = clampPositive(confidenceWarnMin);
        confidenceDropEvents = Math.max(0L, confidenceDropEvents);
        dropWarnMin = Math.max(0L, dropWarnMin);
        promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
        stableStreak = Math.max(0, stableStreak);
        materialCount = Math.max(0, materialCount);
        reactiveAuthoredCount = Math.max(0, reactiveAuthoredCount);
        reactiveCoverage = clamp01(reactiveCoverage);
        reactiveCoverageWarnMin = clampPositive(reactiveCoverageWarnMin);
        historyClampCustomizedCount = Math.max(0, historyClampCustomizedCount);
        historyClampCustomizedRatio = clamp01(historyClampCustomizedRatio);
        historyClampCustomizedWarnMin = clampPositive(historyClampCustomizedWarnMin);
        historyClampMean = clamp01(historyClampMean);
        temporalCorePromotionReadyMinFrames = Math.max(1, temporalCorePromotionReadyMinFrames);
        temporalCoreStableStreak = Math.max(0, temporalCoreStableStreak);
    }

    public static AaTemporalPromotionDiagnostics unavailable() {
        return new AaTemporalPromotionDiagnostics(
                false,
                "",
                false,
                false,
                0.0,
                1.0,
                0L,
                0.0,
                1.0,
                0L,
                1,
                0,
                false,
                false,
                0,
                0,
                0.0,
                0.0,
                0,
                0.0,
                0.0,
                1.0,
                false,
                false,
                1,
                0,
                false
        );
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampPositive(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, value);
    }
}
