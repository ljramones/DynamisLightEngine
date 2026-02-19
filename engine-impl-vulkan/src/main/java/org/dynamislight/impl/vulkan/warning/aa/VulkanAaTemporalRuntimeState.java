package org.dynamislight.impl.vulkan.warning.aa;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.AaTemporalPromotionDiagnostics;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;

/**
 * Mutable AA temporal/material policy state for Vulkan runtime.
 */
public final class VulkanAaTemporalRuntimeState {
    public boolean temporalPathRequestedLastFrame;
    public boolean temporalPathActiveLastFrame;
    public double temporalRejectRateLastFrame;
    public double temporalConfidenceMeanLastFrame = 1.0;
    public long temporalConfidenceDropsLastFrame;
    public boolean temporalEnvelopeBreachedLastFrame;
    public int temporalStableStreak;
    public int temporalHighStreak;
    public int temporalWarnCooldownRemaining;
    public int temporalPromotionReadyMinFrames = 6;
    public boolean temporalPromotionReadyLastFrame;
    public double temporalRejectWarnMax = 0.24;
    public double temporalConfidenceWarnMin = 0.72;
    public long temporalDropWarnMin = 2L;
    public int temporalWarnMinFrames = 3;
    public int temporalWarnCooldownFrames = 120;
    public int materialCountLastFrame;
    public int reactiveAuthoredCountLastFrame;
    public double reactiveCoverageLastFrame;
    public int historyClampCustomizedCountLastFrame;
    public double historyClampCustomizedRatioLastFrame;
    public double historyClampMeanLastFrame = 1.0;
    public double reactiveMaskWarnMinCoverage = 0.0;
    public int reactiveMaskWarnMinFrames = 3;
    public int reactiveMaskWarnCooldownFrames = 120;
    public int reactiveMaskHighStreak;
    public int reactiveMaskWarnCooldownRemaining;
    public boolean reactiveMaskBreachedLastFrame;
    public double historyClampWarnMinCustomizedRatio = 0.0;
    public int historyClampWarnMinFrames = 3;
    public int historyClampWarnCooldownFrames = 120;
    public int historyClampHighStreak;
    public int historyClampWarnCooldownRemaining;
    public boolean historyClampBreachedLastFrame;
    public int temporalCorePromotionReadyMinFrames = 6;
    public int temporalCoreStableStreak;
    public boolean temporalCorePromotionReadyLastFrame;

    public void resetFrameState() {
        temporalPathRequestedLastFrame = false;
        temporalPathActiveLastFrame = false;
        temporalRejectRateLastFrame = 0.0;
        temporalConfidenceMeanLastFrame = 1.0;
        temporalConfidenceDropsLastFrame = 0L;
        temporalEnvelopeBreachedLastFrame = false;
        temporalStableStreak = 0;
        temporalHighStreak = 0;
        temporalWarnCooldownRemaining = 0;
        temporalPromotionReadyLastFrame = false;
        materialCountLastFrame = 0;
        reactiveAuthoredCountLastFrame = 0;
        reactiveCoverageLastFrame = 0.0;
        historyClampCustomizedCountLastFrame = 0;
        historyClampCustomizedRatioLastFrame = 0.0;
        historyClampMeanLastFrame = 1.0;
        reactiveMaskHighStreak = 0;
        reactiveMaskWarnCooldownRemaining = 0;
        reactiveMaskBreachedLastFrame = false;
        historyClampHighStreak = 0;
        historyClampWarnCooldownRemaining = 0;
        historyClampBreachedLastFrame = false;
        temporalCoreStableStreak = 0;
        temporalCorePromotionReadyLastFrame = false;
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        temporalRejectWarnMax = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.temporalRejectWarnMax", temporalRejectWarnMax, 0.0, 1.0);
        temporalConfidenceWarnMin = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.temporalConfidenceWarnMin", temporalConfidenceWarnMin, 0.0, 1.0);
        temporalDropWarnMin = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.temporalDropWarnMin", (int) temporalDropWarnMin, 0, Integer.MAX_VALUE);
        temporalWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.temporalWarnMinFrames", temporalWarnMinFrames, 1, 10_000);
        temporalWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.temporalWarnCooldownFrames", temporalWarnCooldownFrames, 0, 10_000);
        temporalPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.temporalPromotionReadyMinFrames", temporalPromotionReadyMinFrames, 1, 10_000);
        reactiveMaskWarnMinCoverage = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.reactiveMaskWarnMinCoverage", reactiveMaskWarnMinCoverage, 0.0, 1.0);
        reactiveMaskWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.reactiveMaskWarnMinFrames", reactiveMaskWarnMinFrames, 1, 10_000);
        reactiveMaskWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.reactiveMaskWarnCooldownFrames", reactiveMaskWarnCooldownFrames, 0, 10_000);
        historyClampWarnMinCustomizedRatio = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.historyClampWarnMinCustomizedRatio", historyClampWarnMinCustomizedRatio, 0.0, 1.0);
        historyClampWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.historyClampWarnMinFrames", historyClampWarnMinFrames, 1, 10_000);
        historyClampWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.historyClampWarnCooldownFrames", historyClampWarnCooldownFrames, 0, 10_000);
        temporalCorePromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.temporalCorePromotionReadyMinFrames", temporalCorePromotionReadyMinFrames, 1, 10_000);
    }

    public void applyTemporalEmission(VulkanAaTemporalWarningEmitter.Result emission) {
        temporalPathRequestedLastFrame = emission.temporalPathRequested();
        temporalPathActiveLastFrame = emission.temporalPathActive();
        temporalRejectRateLastFrame = emission.rejectRate();
        temporalConfidenceMeanLastFrame = emission.confidenceMean();
        temporalConfidenceDropsLastFrame = emission.confidenceDropEvents();
        temporalStableStreak = emission.stableStreak();
        temporalHighStreak = emission.nextHighStreak();
        temporalWarnCooldownRemaining = emission.nextCooldownRemaining();
        temporalEnvelopeBreachedLastFrame = emission.envelopeBreachedLastFrame();
        temporalPromotionReadyLastFrame = emission.promotionReadyLastFrame();
    }

    public void applyMaterialEmission(VulkanAaTemporalMaterialWarningEmitter.Result emission) {
        materialCountLastFrame = emission.materialCount();
        reactiveAuthoredCountLastFrame = emission.reactiveAuthoredCount();
        reactiveCoverageLastFrame = emission.reactiveCoverage();
        historyClampCustomizedCountLastFrame = emission.historyClampCustomizedCount();
        historyClampCustomizedRatioLastFrame = emission.historyClampCustomizedRatio();
        historyClampMeanLastFrame = emission.historyClampMean();
        reactiveMaskHighStreak = emission.nextReactiveHighStreak();
        reactiveMaskWarnCooldownRemaining = emission.nextReactiveCooldownRemaining();
        historyClampHighStreak = emission.nextHistoryHighStreak();
        historyClampWarnCooldownRemaining = emission.nextHistoryCooldownRemaining();
        reactiveMaskBreachedLastFrame = emission.reactiveBreach();
        historyClampBreachedLastFrame = emission.historyBreach();
    }

    public void updateCorePromotion(
            VulkanAaTemporalWarningEmitter.Result temporalEmission,
            VulkanAaTemporalMaterialWarningEmitter.Result materialEmission,
            List<EngineWarning> warnings,
            String aaModeId
    ) {
        boolean temporalCoreStable = temporalEmission.temporalPathActive()
                && !temporalEmission.envelopeRiskLastFrame()
                && !materialEmission.reactiveRisk()
                && !materialEmission.historyRisk();
        temporalCoreStableStreak = temporalCoreStable ? temporalCoreStableStreak + 1 : 0;
        temporalCorePromotionReadyLastFrame = temporalCoreStableStreak >= temporalCorePromotionReadyMinFrames;
        if (temporalCorePromotionReadyLastFrame && warnings != null) {
            warnings.add(new EngineWarning(
                    "AA_TEMPORAL_CORE_PROMOTION_READY",
                    "AA temporal core promotion-ready envelope satisfied (mode=" + aaModeId + ")"
            ));
        }
    }

    public AaTemporalPromotionDiagnostics diagnostics(String aaModeId) {
        return new AaTemporalPromotionDiagnostics(
                aaModeId != null && !aaModeId.isBlank(),
                aaModeId == null ? "" : aaModeId,
                temporalPathRequestedLastFrame,
                temporalPathActiveLastFrame,
                temporalRejectRateLastFrame,
                temporalConfidenceMeanLastFrame,
                temporalConfidenceDropsLastFrame,
                temporalRejectWarnMax,
                temporalConfidenceWarnMin,
                temporalDropWarnMin,
                temporalPromotionReadyMinFrames,
                temporalStableStreak,
                temporalEnvelopeBreachedLastFrame,
                temporalPromotionReadyLastFrame,
                materialCountLastFrame,
                reactiveAuthoredCountLastFrame,
                reactiveCoverageLastFrame,
                reactiveMaskWarnMinCoverage,
                historyClampCustomizedCountLastFrame,
                historyClampCustomizedRatioLastFrame,
                historyClampWarnMinCustomizedRatio,
                historyClampMeanLastFrame,
                reactiveMaskBreachedLastFrame,
                historyClampBreachedLastFrame,
                temporalCorePromotionReadyMinFrames,
                temporalCoreStableStreak,
                temporalCorePromotionReadyLastFrame
        );
    }
}
