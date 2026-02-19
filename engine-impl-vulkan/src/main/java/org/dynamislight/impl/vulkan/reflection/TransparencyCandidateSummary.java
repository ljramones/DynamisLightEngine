package org.dynamislight.impl.vulkan.reflection;

public record TransparencyCandidateSummary(
        int totalCount,
        int alphaTestedCount,
        int reactiveCandidateCount,
        int probeOnlyOverrideCount
) {
    public static TransparencyCandidateSummary zero() {
        return new TransparencyCandidateSummary(0, 0, 0, 0);
    }
}
