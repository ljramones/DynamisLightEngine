package org.dynamislight.impl.vulkan.reflection;

public record ReflectionRtPerfDiagnostics(
        double gpuMsEstimate,
        double gpuMsCap,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}
