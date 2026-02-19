package org.dynamislight.impl.vulkan.reflection;

public record ReflectionPlanarStabilityDiagnostics(
        double planeDelta,
        double coverageRatio,
        double planeDeltaWarnMax,
        double coverageRatioWarnMin,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}
