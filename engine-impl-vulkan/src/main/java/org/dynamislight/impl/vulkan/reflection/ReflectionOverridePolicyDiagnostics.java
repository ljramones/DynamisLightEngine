package org.dynamislight.impl.vulkan.reflection;

public record ReflectionOverridePolicyDiagnostics(
        int autoCount,
        int probeOnlyCount,
        int ssrOnlyCount,
        int otherCount,
        double probeOnlyRatio,
        double ssrOnlyRatio,
        double probeOnlyRatioWarnMax,
        double ssrOnlyRatioWarnMax,
        int otherWarnMax,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame,
        String planarSelectiveExcludes
) {
}
