package org.dynamislight.api.runtime;

/**
 * Backend-agnostic advanced-lighting diagnostics snapshot.
 */
public record LightingAdvancedDiagnostics(
        boolean available,
        int expectedAdvancedCapabilityCount,
        int activeAdvancedCapabilityCount,
        int requiredAdvancedCapabilityCount,
        int advancedStableStreak,
        int advancedPromotionReadyMinFrames,
        boolean advancedPromotionReady,
        boolean advancedRequireActive,
        int advancedRequireMinFrames,
        int advancedRequireCooldownFrames,
        int advancedRequireCooldownRemaining,
        int advancedRequireUnavailableStreak,
        boolean advancedRequiredUnavailableBreached,
        int advancedEnvelopeWarnMinFrames,
        int advancedEnvelopeCooldownFrames,
        int advancedEnvelopeCooldownRemaining,
        int advancedEnvelopeMismatchStreak,
        boolean advancedEnvelopeBreached,
        boolean areaApproxEnabled,
        boolean iesProfilesEnabled,
        boolean cookiesEnabled,
        boolean volumetricShaftsEnabled,
        boolean clusteringEnabled,
        boolean lightLayersEnabled
) {
    public static LightingAdvancedDiagnostics unavailable() {
        return new LightingAdvancedDiagnostics(
                false,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                0,
                0,
                0,
                0,
                false,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }
}
