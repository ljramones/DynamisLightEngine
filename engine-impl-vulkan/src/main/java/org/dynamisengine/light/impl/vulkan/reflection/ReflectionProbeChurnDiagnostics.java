package org.dynamisengine.light.impl.vulkan.reflection;

public record ReflectionProbeChurnDiagnostics(
        int lastActiveCount,
        int lastDelta,
        int churnEvents,
        double meanDelta,
        int highStreak,
        int warnCooldownRemaining,
        boolean warningTriggered
) {
}
