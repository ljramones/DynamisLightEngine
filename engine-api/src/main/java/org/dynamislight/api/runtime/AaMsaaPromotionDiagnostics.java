package org.dynamislight.api.runtime;

/**
 * Backend-agnostic AA MSAA/hybrid hardening/promotion diagnostics snapshot.
 */
public record AaMsaaPromotionDiagnostics(
        boolean available,
        boolean msaaModeActive,
        String aaMode,
        boolean smaaEnabled,
        boolean temporalPathActive,
        int materialCount,
        int msaaCandidateCount,
        double msaaCandidateRatio,
        double msaaCandidateWarnMinRatio,
        boolean hybridTemporalRequired,
        int promotionReadyMinFrames,
        int stableStreak,
        boolean envelopeBreachedLastFrame,
        boolean promotionReadyLastFrame
) {
    public AaMsaaPromotionDiagnostics {
        aaMode = aaMode == null ? "" : aaMode;
        materialCount = Math.max(0, materialCount);
        msaaCandidateCount = Math.max(0, msaaCandidateCount);
        msaaCandidateRatio = clamp01(msaaCandidateRatio);
        msaaCandidateWarnMinRatio = clamp01(msaaCandidateWarnMinRatio);
        promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
        stableStreak = Math.max(0, stableStreak);
    }

    public static AaMsaaPromotionDiagnostics unavailable() {
        return new AaMsaaPromotionDiagnostics(
                false,
                false,
                "",
                false,
                false,
                0,
                0,
                0.0,
                0.0,
                true,
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
}
