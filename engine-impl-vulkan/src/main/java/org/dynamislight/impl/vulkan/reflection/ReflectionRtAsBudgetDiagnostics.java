package org.dynamislight.impl.vulkan.reflection;

public record ReflectionRtAsBudgetDiagnostics(
        double buildGpuMsEstimate,
        double buildGpuMsWarnMax,
        double memoryMbEstimate,
        double memoryMbBudget,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}
