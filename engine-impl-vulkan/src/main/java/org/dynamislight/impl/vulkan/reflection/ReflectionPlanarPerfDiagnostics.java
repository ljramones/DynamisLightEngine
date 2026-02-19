package org.dynamislight.impl.vulkan.reflection;

public record ReflectionPlanarPerfDiagnostics(
        double gpuMsEstimate,
        double gpuMsCap,
        String timingSource,
        boolean timestampAvailable,
        boolean requireGpuTimestamp,
        boolean timestampRequirementUnmet,
        double drawInflation,
        double drawInflationWarnMax,
        long memoryBytes,
        long memoryBudgetBytes,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}
