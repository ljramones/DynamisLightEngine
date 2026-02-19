package org.dynamislight.api.runtime;

/**
 * Backend-agnostic shadow-cache diagnostics snapshot.
 */
public record ShadowCacheDiagnostics(
        boolean available,
        boolean staticCacheActive,
        boolean dynamicOverlayActive,
        int cacheHitCount,
        int cacheMissCount,
        int cacheEvictionCount,
        double cacheHitRatio,
        double churnRatio,
        String invalidationReason,
        double churnWarnMax,
        int missWarnMax,
        int warnMinFrames,
        int warnCooldownFrames,
        int highStreak,
        int warnCooldownRemaining,
        boolean envelopeBreachedLastFrame
) {
    public static ShadowCacheDiagnostics unavailable() {
        return new ShadowCacheDiagnostics(
                false,
                false,
                false,
                0,
                0,
                0,
                0.0,
                0.0,
                "unavailable",
                0.0,
                0,
                0,
                0,
                0,
                0,
                false
        );
    }
}
