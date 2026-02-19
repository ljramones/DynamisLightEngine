package org.dynamislight.impl.vulkan.reflection;

public record ReflectionRtHybridDiagnostics(
        double rtShare,
        double ssrShare,
        double probeShare,
        double probeShareWarnMax,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}
