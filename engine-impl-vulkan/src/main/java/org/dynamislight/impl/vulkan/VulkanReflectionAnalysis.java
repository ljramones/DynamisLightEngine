package org.dynamislight.impl.vulkan;

import java.util.List;

import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.ReflectionProbeDesc;
import org.dynamislight.api.scene.ReflectionOverrideMode;

final class VulkanReflectionAnalysis {
    private VulkanReflectionAnalysis() {
    }

    record ProbeQualityThresholds(
            int overlapWarnMaxPairs,
            int bleedRiskWarnMaxPairs,
            int minOverlapPairsWhenMultiple,
            double boxProjectionMinRatio,
            int invalidBlendDistanceWarnMax,
            double overlapCoverageWarnMin
    ) {
    }

    static ReflectionOverrideSummary summarizeReflectionOverrides(List<Integer> modes) {
        int autoCount = 0;
        int probeOnlyCount = 0;
        int ssrOnlyCount = 0;
        int otherCount = 0;
        for (Integer rawMode : modes) {
            int mode = rawMode == null ? 0 : rawMode;
            switch (mode) {
                case 0 -> autoCount++;
                case 1 -> probeOnlyCount++;
                case 2 -> ssrOnlyCount++;
                default -> otherCount++;
            }
        }
        return new ReflectionOverrideSummary(autoCount, probeOnlyCount, ssrOnlyCount, otherCount);
    }

    static ReflectionProbeQualityDiagnostics analyzeReflectionProbeQuality(
            List<ReflectionProbeDesc> probes,
            ProbeQualityThresholds thresholds
    ) {
        List<ReflectionProbeDesc> safe = probes == null ? List.of() : probes;
        if (safe.isEmpty()) {
            return ReflectionProbeQualityDiagnostics.zero();
        }
        int configured = 0;
        int boxProjected = 0;
        int invalidBlendDistanceCount = 0;
        int invalidExtentCount = 0;
        int overlapPairs = 0;
        int bleedRiskPairs = 0;
        int transitionPairs = 0;
        int maxPriorityDelta = 0;
        double overlapCoverageAccum = 0.0;
        for (ReflectionProbeDesc probe : safe) {
            if (probe == null) {
                continue;
            }
            configured++;
            if (probe.boxProjection()) {
                boxProjected++;
            }
            if (probe.blendDistance() <= 0.0f) {
                invalidBlendDistanceCount++;
            }
            if (!isValidExtents(probe)) {
                invalidExtentCount++;
            }
        }
        for (int i = 0; i < safe.size(); i++) {
            ReflectionProbeDesc a = safe.get(i);
            if (a == null) {
                continue;
            }
            for (int j = i + 1; j < safe.size(); j++) {
                ReflectionProbeDesc b = safe.get(j);
                if (b == null) {
                    continue;
                }
                if (!overlapsAabb(a, b)) {
                    continue;
                }
                overlapPairs++;
                double overlapCoverage = overlapCoverageRatio(a, b);
                overlapCoverageAccum += overlapCoverage;
                int priorityDelta = Math.abs(a.priority() - b.priority());
                maxPriorityDelta = Math.max(maxPriorityDelta, priorityDelta);
                if (priorityDelta == 0 && overlapCoverage >= 0.20) {
                    bleedRiskPairs++;
                } else {
                    transitionPairs++;
                }
            }
        }
        double boxProjectionRatio = configured <= 0 ? 0.0 : (double) boxProjected / (double) configured;
        double meanOverlapCoverage = overlapPairs <= 0 ? 0.0 : overlapCoverageAccum / (double) overlapPairs;
        boolean tooManyOverlaps = overlapPairs > thresholds.overlapWarnMaxPairs();
        boolean tooManyBleedRisks = bleedRiskPairs > thresholds.bleedRiskWarnMaxPairs();
        boolean tooFewTransitions = configured > 1
                && overlapPairs < thresholds.minOverlapPairsWhenMultiple();
        boolean tooFewBoxProjected = configured > 1
                && boxProjectionRatio < thresholds.boxProjectionMinRatio();
        boolean tooManyInvalidBlendDistances = invalidBlendDistanceCount > thresholds.invalidBlendDistanceWarnMax();
        boolean poorOverlapCoverage = overlapPairs > 0
                && meanOverlapCoverage < thresholds.overlapCoverageWarnMin();
        boolean invalidExtents = invalidExtentCount > 0;
        boolean envelopeBreached = tooManyOverlaps
                || tooManyBleedRisks
                || tooFewTransitions
                || tooFewBoxProjected
                || tooManyInvalidBlendDistances
                || poorOverlapCoverage
                || invalidExtents;
        String breachReason = !envelopeBreached
                ? "none"
                : (tooManyBleedRisks
                ? "bleed_risk_pairs_exceeded"
                : (tooManyOverlaps
                ? "overlap_pairs_exceeded"
                : (invalidExtents
                ? "invalid_probe_extents"
                : (tooManyInvalidBlendDistances
                ? "invalid_blend_distance_count_exceeded"
                : (tooFewBoxProjected
                ? "box_projection_ratio_below_min"
                : (poorOverlapCoverage ? "overlap_coverage_below_min" : "insufficient_overlap_pairs"))))));
        return new ReflectionProbeQualityDiagnostics(
                configured,
                boxProjected,
                boxProjectionRatio,
                invalidBlendDistanceCount,
                invalidExtentCount,
                overlapPairs,
                meanOverlapCoverage,
                bleedRiskPairs,
                transitionPairs,
                maxPriorityDelta,
                envelopeBreached,
                breachReason
        );
    }

    static TransparencyCandidateSummary summarizeReflectionTransparencyCandidates(
            List<MaterialDesc> materials,
            double candidateReactiveMin
    ) {
        if (materials == null || materials.isEmpty()) {
            return TransparencyCandidateSummary.zero();
        }
        int total = 0;
        int alphaTested = 0;
        int reactive = 0;
        int probeOnlyOverrides = 0;
        float reactiveThreshold = (float) Math.max(0.0, Math.min(1.0, candidateReactiveMin));
        for (MaterialDesc material : materials) {
            if (material == null) {
                continue;
            }
            boolean alphaCandidate = material.alphaTested();
            boolean reactiveCandidate = material.reactiveStrength() >= reactiveThreshold;
            boolean candidate = alphaCandidate || reactiveCandidate;
            if (!candidate) {
                continue;
            }
            total++;
            if (alphaCandidate) {
                alphaTested++;
            }
            if (reactiveCandidate) {
                reactive++;
            }
            if (material.reflectionOverride() == ReflectionOverrideMode.PROBE_ONLY) {
                probeOnlyOverrides++;
            }
        }
        return new TransparencyCandidateSummary(total, alphaTested, reactive, probeOnlyOverrides);
    }

    private static boolean overlapsAabb(ReflectionProbeDesc a, ReflectionProbeDesc b) {
        return a.extentsMin().x() <= b.extentsMax().x()
                && a.extentsMax().x() >= b.extentsMin().x()
                && a.extentsMin().y() <= b.extentsMax().y()
                && a.extentsMax().y() >= b.extentsMin().y()
                && a.extentsMin().z() <= b.extentsMax().z()
                && a.extentsMax().z() >= b.extentsMin().z();
    }

    private static boolean isValidExtents(ReflectionProbeDesc probe) {
        if (probe == null) {
            return false;
        }
        return probe.extentsMin().x() <= probe.extentsMax().x()
                && probe.extentsMin().y() <= probe.extentsMax().y()
                && probe.extentsMin().z() <= probe.extentsMax().z();
    }

    private static double overlapCoverageRatio(ReflectionProbeDesc a, ReflectionProbeDesc b) {
        if (a == null || b == null) {
            return 0.0;
        }
        double overlapX = Math.max(0.0, Math.min(a.extentsMax().x(), b.extentsMax().x()) - Math.max(a.extentsMin().x(), b.extentsMin().x()));
        double overlapY = Math.max(0.0, Math.min(a.extentsMax().y(), b.extentsMax().y()) - Math.max(a.extentsMin().y(), b.extentsMin().y()));
        double overlapZ = Math.max(0.0, Math.min(a.extentsMax().z(), b.extentsMax().z()) - Math.max(a.extentsMin().z(), b.extentsMin().z()));
        double overlapVolume = overlapX * overlapY * overlapZ;
        if (overlapVolume <= 0.0) {
            return 0.0;
        }
        double volumeA = aabbVolume(a);
        double volumeB = aabbVolume(b);
        double minVolume = Math.max(Math.min(volumeA, volumeB), 1.0e-6);
        return overlapVolume / minVolume;
    }

    private static double aabbVolume(ReflectionProbeDesc probe) {
        if (probe == null) {
            return 0.0;
        }
        double x = Math.max(0.0, probe.extentsMax().x() - probe.extentsMin().x());
        double y = Math.max(0.0, probe.extentsMax().y() - probe.extentsMin().y());
        double z = Math.max(0.0, probe.extentsMax().z() - probe.extentsMin().z());
        return x * y * z;
    }
}
