package org.dynamislight.impl.vulkan.reflection;

public record ReflectionContactHardeningDiagnostics(
        boolean activeLastFrame,
        double estimatedStrengthLastFrame,
        double minSsrStrength,
        double minSsrMaxRoughness,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}
