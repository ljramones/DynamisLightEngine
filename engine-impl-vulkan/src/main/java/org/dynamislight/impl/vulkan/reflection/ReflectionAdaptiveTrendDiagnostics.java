package org.dynamislight.impl.vulkan.reflection;

public record ReflectionAdaptiveTrendDiagnostics(
        int windowSamples,
        double meanSeverity,
        double peakSeverity,
        int lowCount,
        int mediumCount,
        int highCount,
        double lowRatio,
        double mediumRatio,
        double highRatio,
        double meanTemporalDelta,
        double meanSsrStrengthDelta,
        double meanSsrStepScaleDelta,
        double highRatioWarnMin,
        int highRatioWarnMinFrames,
        int highRatioWarnCooldownFrames,
        int highRatioWarnMinSamples,
        int highRatioWarnHighStreak,
        int highRatioWarnCooldownRemaining,
        boolean highRatioWarnTriggered
) {
}
