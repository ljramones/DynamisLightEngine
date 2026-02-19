package org.dynamislight.impl.vulkan.reflection;

public record ReflectionProbeStreamingDiagnostics(
        int configuredProbeCount,
        int activeProbeCount,
        int maxVisibleBudget,
        int effectiveStreamingBudget,
        int updateCadenceFrames,
        double lodDepthScale,
        int frustumVisibleCount,
        int deferredProbeCount,
        int visibleUniquePathCount,
        int missingSlotPathCount,
        double missingSlotRatio,
        double deferredRatio,
        double lodSkewRatio,
        double memoryBudgetMb,
        double memoryEstimateMb,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean budgetPressure,
        boolean breachedLastFrame
) {
}
