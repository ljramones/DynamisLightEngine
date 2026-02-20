package org.dynamislight.api.runtime;

import java.util.List;

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
        boolean lightLayersEnabled,
        List<String> expectedFeatures,
        List<String> activeFeatures,
        List<String> breachedFeatures,
        List<String> promotionReadyFeatures
) {
    public LightingAdvancedDiagnostics {
        expectedFeatures = expectedFeatures == null ? List.of() : List.copyOf(expectedFeatures);
        activeFeatures = activeFeatures == null ? List.of() : List.copyOf(activeFeatures);
        breachedFeatures = breachedFeatures == null ? List.of() : List.copyOf(breachedFeatures);
        promotionReadyFeatures = promotionReadyFeatures == null ? List.of() : List.copyOf(promotionReadyFeatures);
    }

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
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
