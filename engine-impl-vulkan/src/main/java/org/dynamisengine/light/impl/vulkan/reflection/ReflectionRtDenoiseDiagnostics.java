package org.dynamisengine.light.impl.vulkan.reflection;

public record ReflectionRtDenoiseDiagnostics(
        double spatialVariance,
        double spatialVarianceWarnMax,
        double temporalLag,
        double temporalLagWarnMax,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}
