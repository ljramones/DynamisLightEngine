package org.dynamislight.impl.vulkan;

import java.util.List;

import org.dynamislight.api.event.EngineWarning;

final class VulkanReflectionCoreWarningEmitter {
    private VulkanReflectionCoreWarningEmitter() {
    }

    static final class State {
        int reflectionBaseMode;
        boolean reflectionContactHardeningActiveLastFrame;
        boolean reflectionContactHardeningBreachedLastFrame;
        double reflectionContactHardeningEstimatedStrengthLastFrame;
        int reflectionContactHardeningHighStreak;
        double reflectionContactHardeningMinSsrMaxRoughness;
        double reflectionContactHardeningMinSsrStrength;
        int reflectionContactHardeningWarnCooldownFrames;
        int reflectionContactHardeningWarnCooldownRemaining;
        int reflectionContactHardeningWarnMinFrames;
        boolean reflectionOverrideBreachedLastFrame;
        int reflectionOverrideHighStreak;
        int reflectionOverrideOtherWarnMax;
        double reflectionOverrideProbeOnlyRatioWarnMax;
        double reflectionOverrideSsrOnlyRatioWarnMax;
        int reflectionOverrideWarnCooldownFrames;
        int reflectionOverrideWarnCooldownRemaining;
        int reflectionOverrideWarnMinFrames;
        int reflectionProbeChurnWarnCooldownFrames;
        int reflectionProbeChurnWarnMinDelta;
        int reflectionProbeChurnWarnMinStreak;
        double reflectionProbeLodDepthScale;
        int reflectionProbeMaxVisible;
        int reflectionProbeQualityBleedRiskWarnMaxPairs;
        double reflectionProbeQualityBoxProjectionMinRatio;
        ReflectionProbeQualityDiagnostics reflectionProbeQualityDiagnostics;
        int reflectionProbeQualityInvalidBlendDistanceWarnMax;
        int reflectionProbeQualityMinOverlapPairsWhenMultiple;
        double reflectionProbeQualityOverlapCoverageWarnMin;
        int reflectionProbeQualityOverlapWarnMaxPairs;
        boolean reflectionProbeStreamingBreachedLastFrame;
        double reflectionProbeStreamingDeferredRatioWarnMax;
        int reflectionProbeStreamingHighStreak;
        double reflectionProbeStreamingLodSkewWarnMax;
        double reflectionProbeStreamingMemoryBudgetMb;
        double reflectionProbeStreamingMissRatioWarnMax;
        int reflectionProbeStreamingWarnCooldownFrames;
        int reflectionProbeStreamingWarnCooldownRemaining;
        int reflectionProbeStreamingWarnMinFrames;
        int reflectionProbeUpdateCadenceFrames;
        double reflectionsSsrMaxRoughness;
        double reflectionsSsrStrength;
    }

    static void emit(
            List<EngineWarning> warnings,
            State state,
            ReflectionOverrideSummary overrideSummary,
            ReflectionProbeChurnDiagnostics churnDiagnostics,
            VulkanContext.ReflectionProbeDiagnostics probeDiagnostics
    ) {
            warnings.add(new EngineWarning(
                    "REFLECTION_OVERRIDE_POLICY",
                    "Reflection override policy (auto=" + overrideSummary.autoCount()
                            + ", probeOnly=" + overrideSummary.probeOnlyCount()
                            + ", ssrOnly=" + overrideSummary.ssrOnlyCount()
                            + ", other=" + overrideSummary.otherCount()
                            + ", planarSelectiveExcludes=probe_only|ssr_only)"
            ));
            int overrideTotal = Math.max(1, overrideSummary.totalCount());
            double overrideProbeOnlyRatio = (double) overrideSummary.probeOnlyCount() / (double) overrideTotal;
            double overrideSsrOnlyRatio = (double) overrideSummary.ssrOnlyCount() / (double) overrideTotal;
            boolean overrideRisk = overrideProbeOnlyRatio > state.reflectionOverrideProbeOnlyRatioWarnMax
                    || overrideSsrOnlyRatio > state.reflectionOverrideSsrOnlyRatioWarnMax
                    || overrideSummary.otherCount() > state.reflectionOverrideOtherWarnMax;
            if (overrideRisk) {
                state.reflectionOverrideHighStreak++;
            } else {
                state.reflectionOverrideHighStreak = 0;
            }
            if (state.reflectionOverrideWarnCooldownRemaining > 0) {
                state.reflectionOverrideWarnCooldownRemaining--;
            }
            boolean overrideTriggered = false;
            if (overrideRisk
                    && state.reflectionOverrideHighStreak >= state.reflectionOverrideWarnMinFrames
                    && state.reflectionOverrideWarnCooldownRemaining <= 0) {
                state.reflectionOverrideWarnCooldownRemaining = state.reflectionOverrideWarnCooldownFrames;
                overrideTriggered = true;
            }
            state.reflectionOverrideBreachedLastFrame = overrideTriggered;
            warnings.add(new EngineWarning(
                    "REFLECTION_OVERRIDE_POLICY_ENVELOPE",
                    "Reflection override policy envelope (probeOnlyRatio=" + overrideProbeOnlyRatio
                            + ", ssrOnlyRatio=" + overrideSsrOnlyRatio
                            + ", otherCount=" + overrideSummary.otherCount()
                            + ", probeOnlyRatioWarnMax=" + state.reflectionOverrideProbeOnlyRatioWarnMax
                            + ", ssrOnlyRatioWarnMax=" + state.reflectionOverrideSsrOnlyRatioWarnMax
                            + ", otherWarnMax=" + state.reflectionOverrideOtherWarnMax
                            + ", highStreak=" + state.reflectionOverrideHighStreak
                            + ", warnMinFrames=" + state.reflectionOverrideWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionOverrideWarnCooldownFrames
                            + ", warnCooldownRemaining=" + state.reflectionOverrideWarnCooldownRemaining
                            + ", breached=" + state.reflectionOverrideBreachedLastFrame + ")"
            ));
            if (state.reflectionOverrideBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_OVERRIDE_POLICY_ENVELOPE_BREACH",
                        "Reflection override policy envelope breached (probeOnlyRatio=" + overrideProbeOnlyRatio
                                + ", ssrOnlyRatio=" + overrideSsrOnlyRatio
                                + ", otherCount=" + overrideSummary.otherCount() + ")"
                ));
            }
            boolean contactHardeningActive = state.reflectionBaseMode > 0;
            double contactHardeningEstimatedStrength = state.reflectionsSsrStrength
                    * state.reflectionsSsrMaxRoughness;
            boolean contactHardeningRisk = contactHardeningActive
                    && (state.reflectionsSsrStrength < state.reflectionContactHardeningMinSsrStrength
                    || state.reflectionsSsrMaxRoughness < state.reflectionContactHardeningMinSsrMaxRoughness);
            if (contactHardeningRisk) {
                state.reflectionContactHardeningHighStreak++;
            } else {
                state.reflectionContactHardeningHighStreak = 0;
            }
            if (state.reflectionContactHardeningWarnCooldownRemaining > 0) {
                state.reflectionContactHardeningWarnCooldownRemaining--;
            }
            boolean contactHardeningTriggered = false;
            if (contactHardeningRisk
                    && state.reflectionContactHardeningHighStreak >= state.reflectionContactHardeningWarnMinFrames
                    && state.reflectionContactHardeningWarnCooldownRemaining <= 0) {
                state.reflectionContactHardeningWarnCooldownRemaining = state.reflectionContactHardeningWarnCooldownFrames;
                contactHardeningTriggered = true;
            }
            state.reflectionContactHardeningActiveLastFrame = contactHardeningActive;
            state.reflectionContactHardeningEstimatedStrengthLastFrame = contactHardeningEstimatedStrength;
            state.reflectionContactHardeningBreachedLastFrame = contactHardeningTriggered;
            warnings.add(new EngineWarning(
                    "REFLECTION_CONTACT_HARDENING_POLICY",
                    "Contact-hardening policy (active=" + state.reflectionContactHardeningActiveLastFrame
                            + ", estimatedStrength=" + state.reflectionContactHardeningEstimatedStrengthLastFrame
                            + ", ssrStrength=" + state.reflectionsSsrStrength
                            + ", ssrMaxRoughness=" + state.reflectionsSsrMaxRoughness
                            + ", depthWindowMin=0.01, depthWindowMax=0.16, roughnessRampMin=0.58, ssrBoostMax=1.22, planarBoostMax=1.10"
                            + ", minSsrStrength=" + state.reflectionContactHardeningMinSsrStrength
                            + ", minSsrMaxRoughness=" + state.reflectionContactHardeningMinSsrMaxRoughness
                            + ", highStreak=" + state.reflectionContactHardeningHighStreak
                            + ", warnMinFrames=" + state.reflectionContactHardeningWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionContactHardeningWarnCooldownFrames
                            + ", warnCooldownRemaining=" + state.reflectionContactHardeningWarnCooldownRemaining
                            + ", breached=" + state.reflectionContactHardeningBreachedLastFrame + ")"
            ));
            if (state.reflectionContactHardeningBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_CONTACT_HARDENING_ENVELOPE_BREACH",
                        "Contact-hardening envelope breached (ssrStrength=" + state.reflectionsSsrStrength
                                + ", ssrMaxRoughness=" + state.reflectionsSsrMaxRoughness
                                + ", minSsrStrength=" + state.reflectionContactHardeningMinSsrStrength
                                + ", minSsrMaxRoughness=" + state.reflectionContactHardeningMinSsrMaxRoughness + ")"
                ));
            }
            warnings.add(new EngineWarning(
                    "REFLECTION_PROBE_BLEND_DIAGNOSTICS",
                    "Probe blend diagnostics (configured=" + probeDiagnostics.configuredProbeCount()
                            + ", active=" + probeDiagnostics.activeProbeCount()
                            + ", slots=" + probeDiagnostics.slotCount()
                            + ", capacity=" + probeDiagnostics.metadataCapacity()
                            + ", delta=" + churnDiagnostics.lastDelta()
                            + ", churnEvents=" + churnDiagnostics.churnEvents()
                            + ", meanDelta=" + churnDiagnostics.meanDelta()
                            + ", highStreak=" + churnDiagnostics.highStreak()
                            + ", warnMinDelta=" + state.reflectionProbeChurnWarnMinDelta
                            + ", warnMinStreak=" + state.reflectionProbeChurnWarnMinStreak
                            + ", warnCooldownFrames=" + state.reflectionProbeChurnWarnCooldownFrames
                            + ", cooldownRemaining=" + churnDiagnostics.warnCooldownRemaining()
                            + ")"
            ));
            int effectiveStreamingBudget = Math.max(1, Math.min(state.reflectionProbeMaxVisible, probeDiagnostics.metadataCapacity()));
            boolean streamingBudgetPressure = probeDiagnostics.configuredProbeCount() > probeDiagnostics.activeProbeCount()
                    && (probeDiagnostics.activeProbeCount() >= effectiveStreamingBudget || probeDiagnostics.activeProbeCount() == 0);
            double missingSlotRatio = probeDiagnostics.visibleUniquePathCount() <= 0
                    ? 0.0
                    : (double) probeDiagnostics.missingSlotPathCount() / (double) probeDiagnostics.visibleUniquePathCount();
            double deferredRatio = probeDiagnostics.frustumVisibleCount() <= 0
                    ? 0.0
                    : (double) probeDiagnostics.deferredProbeCount() / (double) probeDiagnostics.frustumVisibleCount();
            int activeProbeCountSafe = Math.max(1, probeDiagnostics.activeProbeCount());
            double lodSkewRatio = (double) probeDiagnostics.lodTier3Count() / (double) activeProbeCountSafe;
            double memoryEstimateMb = probeDiagnostics.activeProbeCount() * 1.5;
            boolean streamingRisk = streamingBudgetPressure
                    || missingSlotRatio > state.reflectionProbeStreamingMissRatioWarnMax
                    || deferredRatio > state.reflectionProbeStreamingDeferredRatioWarnMax
                    || lodSkewRatio > state.reflectionProbeStreamingLodSkewWarnMax
                    || memoryEstimateMb > state.reflectionProbeStreamingMemoryBudgetMb;
            if (streamingRisk) {
                state.reflectionProbeStreamingHighStreak++;
            } else {
                state.reflectionProbeStreamingHighStreak = 0;
            }
            if (state.reflectionProbeStreamingWarnCooldownRemaining > 0) {
                state.reflectionProbeStreamingWarnCooldownRemaining--;
            }
            boolean streamingTriggered = false;
            if (streamingRisk
                    && state.reflectionProbeStreamingHighStreak >= state.reflectionProbeStreamingWarnMinFrames
                    && state.reflectionProbeStreamingWarnCooldownRemaining <= 0) {
                state.reflectionProbeStreamingWarnCooldownRemaining = state.reflectionProbeStreamingWarnCooldownFrames;
                streamingTriggered = true;
            }
            state.reflectionProbeStreamingBreachedLastFrame = streamingTriggered;
            warnings.add(new EngineWarning(
                    "REFLECTION_PROBE_STREAMING_DIAGNOSTICS",
                    "Probe streaming diagnostics (configured=" + probeDiagnostics.configuredProbeCount()
                            + ", active=" + probeDiagnostics.activeProbeCount()
                            + ", frustumVisible=" + probeDiagnostics.frustumVisibleCount()
                            + ", deferred=" + probeDiagnostics.deferredProbeCount()
                            + ", visibleUniquePaths=" + probeDiagnostics.visibleUniquePathCount()
                            + ", missingSlotPaths=" + probeDiagnostics.missingSlotPathCount()
                            + ", missingSlotRatio=" + missingSlotRatio
                            + ", deferredRatio=" + deferredRatio
                            + ", lodSkewRatio=" + lodSkewRatio
                            + ", memoryEstimateMb=" + memoryEstimateMb
                            + ", memoryBudgetMb=" + state.reflectionProbeStreamingMemoryBudgetMb
                            + ", effectiveBudget=" + effectiveStreamingBudget
                            + ", cadenceFrames=" + state.reflectionProbeUpdateCadenceFrames
                            + ", maxVisible=" + state.reflectionProbeMaxVisible
                            + ", lodDepthScale=" + state.reflectionProbeLodDepthScale
                            + ", budgetPressure=" + streamingBudgetPressure
                            + ", risk=" + streamingRisk
                            + ", highStreak=" + state.reflectionProbeStreamingHighStreak
                            + ", warnMinFrames=" + state.reflectionProbeStreamingWarnMinFrames
                            + ", warnCooldownFrames=" + state.reflectionProbeStreamingWarnCooldownFrames
                            + ", warnCooldownRemaining=" + state.reflectionProbeStreamingWarnCooldownRemaining
                            + ", breached=" + state.reflectionProbeStreamingBreachedLastFrame + ")"
            ));
            if (streamingBudgetPressure) {
                warnings.add(new EngineWarning(
                        "REFLECTION_PROBE_STREAMING_BUDGET_PRESSURE",
                        "Reflection probe streaming budget pressure detected "
                                + "(configured=" + probeDiagnostics.configuredProbeCount()
                                + ", active=" + probeDiagnostics.activeProbeCount()
                                + ", effectiveBudget=" + effectiveStreamingBudget + ")"
                ));
            }
            warnings.add(new EngineWarning(
                    "REFLECTION_PROBE_STREAMING_ENVELOPE",
                    "Probe streaming envelope (missRatioMax=" + state.reflectionProbeStreamingMissRatioWarnMax
                            + ", deferredRatioMax=" + state.reflectionProbeStreamingDeferredRatioWarnMax
                            + ", lodSkewMax=" + state.reflectionProbeStreamingLodSkewWarnMax
                            + ", memoryBudgetMb=" + state.reflectionProbeStreamingMemoryBudgetMb
                            + ", breached=" + state.reflectionProbeStreamingBreachedLastFrame + ")"
            ));
            if (state.reflectionProbeStreamingBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "REFLECTION_PROBE_STREAMING_ENVELOPE_BREACH",
                        "Probe streaming envelope breached (missingSlotRatio=" + missingSlotRatio
                                + ", deferredRatio=" + deferredRatio
                                + ", lodSkewRatio=" + lodSkewRatio
                                + ", memoryEstimateMb=" + memoryEstimateMb + ")"
                ));
            }
            warnings.add(new EngineWarning(
                    "REFLECTION_PROBE_QUALITY_SWEEP",
                    "Probe quality sweep (configured=" + state.reflectionProbeQualityDiagnostics.configuredProbeCount()
                            + ", boxProjected=" + state.reflectionProbeQualityDiagnostics.boxProjectedCount()
                            + ", boxProjectionRatio=" + state.reflectionProbeQualityDiagnostics.boxProjectionRatio()
                            + ", invalidBlendDistanceCount=" + state.reflectionProbeQualityDiagnostics.invalidBlendDistanceCount()
                            + ", invalidExtentCount=" + state.reflectionProbeQualityDiagnostics.invalidExtentCount()
                            + ", overlapPairs=" + state.reflectionProbeQualityDiagnostics.overlapPairs()
                            + ", meanOverlapCoverage=" + state.reflectionProbeQualityDiagnostics.meanOverlapCoverage()
                            + ", bleedRiskPairs=" + state.reflectionProbeQualityDiagnostics.bleedRiskPairs()
                            + ", transitionPairs=" + state.reflectionProbeQualityDiagnostics.transitionPairs()
                            + ", maxPriorityDelta=" + state.reflectionProbeQualityDiagnostics.maxPriorityDelta()
                            + ", overlapWarnMaxPairs=" + state.reflectionProbeQualityOverlapWarnMaxPairs
                            + ", bleedRiskWarnMaxPairs=" + state.reflectionProbeQualityBleedRiskWarnMaxPairs
                            + ", minOverlapPairsWhenMultiple=" + state.reflectionProbeQualityMinOverlapPairsWhenMultiple
                            + ", boxProjectionMinRatio=" + state.reflectionProbeQualityBoxProjectionMinRatio
                            + ", invalidBlendDistanceWarnMax=" + state.reflectionProbeQualityInvalidBlendDistanceWarnMax
                            + ", overlapCoverageWarnMin=" + state.reflectionProbeQualityOverlapCoverageWarnMin
                            + ")"
            ));
            if (state.reflectionProbeQualityDiagnostics.envelopeBreached()) {
                warnings.add(new EngineWarning(
                        "REFLECTION_PROBE_QUALITY_ENVELOPE_BREACH",
                        "Probe quality envelope breached (overlapPairs=" + state.reflectionProbeQualityDiagnostics.overlapPairs()
                                + ", boxProjectionRatio=" + state.reflectionProbeQualityDiagnostics.boxProjectionRatio()
                                + ", invalidBlendDistanceCount=" + state.reflectionProbeQualityDiagnostics.invalidBlendDistanceCount()
                                + ", invalidExtentCount=" + state.reflectionProbeQualityDiagnostics.invalidExtentCount()
                                + ", meanOverlapCoverage=" + state.reflectionProbeQualityDiagnostics.meanOverlapCoverage()
                                + ", bleedRiskPairs=" + state.reflectionProbeQualityDiagnostics.bleedRiskPairs()
                                + ", transitionPairs=" + state.reflectionProbeQualityDiagnostics.transitionPairs()
                                + ", reason=" + state.reflectionProbeQualityDiagnostics.breachReason() + ")"
                ));
            }
    }
}
