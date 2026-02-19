package org.dynamislight.impl.vulkan.reflection;

public record ReflectionTransparencyDiagnostics(
        int transparentCandidateCount,
        int alphaTestedCandidateCount,
        int reactiveCandidateCount,
        int probeOnlyCandidateCount,
        String stageGateStatus,
        String fallbackPath,
        boolean rtLaneActive,
        double candidateReactiveMin,
        double probeOnlyRatioWarnMax,
        int highStreak,
        int warnMinFrames,
        int warnCooldownFrames,
        int warnCooldownRemaining,
        boolean breachedLastFrame
) {
}
