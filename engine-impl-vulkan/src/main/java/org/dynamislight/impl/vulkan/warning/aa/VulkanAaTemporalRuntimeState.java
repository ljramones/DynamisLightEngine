package org.dynamislight.impl.vulkan.warning.aa;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.AaTemporalPromotionDiagnostics;
import org.dynamislight.api.runtime.AaUpscalePromotionDiagnostics;
import org.dynamislight.api.runtime.AaMsaaPromotionDiagnostics;
import org.dynamislight.api.runtime.AaQualityPromotionDiagnostics;
import org.dynamislight.impl.vulkan.runtime.config.UpscalerMode;
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
    public boolean upscaleModeActiveLastFrame;
    public String upscaleModeIdLastFrame = "";
    public boolean upscaleTemporalPathActiveLastFrame;
    public double upscaleRenderScaleLastFrame = 1.0;
    public String upscaleModePathLastFrame = "none";
    public boolean upscaleNativeUpscalerActiveLastFrame;
    public String upscaleNativeUpscalerProviderLastFrame = "none";
    public double upscaleWarnMinRenderScale = 0.5;
    public double upscaleWarnMaxRenderScaleTsr = 0.95;
    public double upscaleWarnMaxRenderScaleTuua = 0.95;
    public int upscaleWarnMinFrames = 3;
    public int upscaleWarnCooldownFrames = 120;
    public int upscalePromotionReadyMinFrames = 6;
    public int upscaleStableStreak;
    public int upscaleHighStreak;
    public int upscaleWarnCooldownRemaining;
    public boolean upscaleEnvelopeBreachedLastFrame;
    public boolean upscalePromotionReadyLastFrame;
    public boolean msaaModeActiveLastFrame;
    public String msaaModeIdLastFrame = "";
    public boolean msaaSmaaEnabledLastFrame;
    public boolean msaaTemporalPathActiveLastFrame;
    public int msaaMaterialCountLastFrame;
    public int msaaCandidateCountLastFrame;
    public double msaaCandidateRatioLastFrame;
    public double msaaCandidateWarnMinRatio = 0.05;
    public boolean msaaHybridTemporalRequired = true;
    public int msaaWarnMinFrames = 3;
    public int msaaWarnCooldownFrames = 120;
    public int msaaPromotionReadyMinFrames = 6;
    public int msaaStableStreak;
    public int msaaHighStreak;
    public int msaaWarnCooldownRemaining;
    public boolean msaaEnvelopeBreachedLastFrame;
    public boolean msaaPromotionReadyLastFrame;
    public String qualityModeIdLastFrame = "";
    public boolean dlaaModeActiveLastFrame;
    public boolean dlaaTemporalPathActiveLastFrame;
    public double dlaaBlendLastFrame;
    public double dlaaRenderScaleLastFrame = 1.0;
    public double dlaaWarnMinBlend = 0.90;
    public double dlaaWarnMinRenderScale = 1.0;
    public int dlaaWarnMinFrames = 3;
    public int dlaaWarnCooldownFrames = 120;
    public int dlaaPromotionReadyMinFrames = 6;
    public int dlaaStableStreak;
    public int dlaaHighStreak;
    public int dlaaWarnCooldownRemaining;
    public boolean dlaaEnvelopeBreachedLastFrame;
    public boolean dlaaPromotionReadyLastFrame;
    public boolean specularPolicyActiveLastFrame;
    public int specularMaterialCountLastFrame;
    public int specularNormalMappedCountLastFrame;
    public double specularNormalMappedRatioLastFrame;
    public double specularClipScaleLastFrame = 1.0;
    public double specularWarnMaxClipScale = 1.1;
    public int specularWarnMinFrames = 3;
    public int specularWarnCooldownFrames = 120;
    public int specularPromotionReadyMinFrames = 6;
    public int specularStableStreak;
    public int specularHighStreak;
    public int specularWarnCooldownRemaining;
    public boolean specularEnvelopeBreachedLastFrame;
    public boolean specularPromotionReadyLastFrame;
    public boolean geometricPolicyActiveLastFrame;
    public int geometricThinFeatureCountLastFrame;
    public double geometricThinFeatureRatioLastFrame;
    public double geometricWarnMinThinFeatureRatio = 0.05;
    public int geometricWarnMinFrames = 3;
    public int geometricWarnCooldownFrames = 120;
    public int geometricPromotionReadyMinFrames = 6;
    public int geometricStableStreak;
    public int geometricHighStreak;
    public int geometricWarnCooldownRemaining;
    public boolean geometricEnvelopeBreachedLastFrame;
    public boolean geometricPromotionReadyLastFrame;
    public boolean alphaToCoveragePolicyActiveLastFrame;
    public double alphaToCoverageWarnMinThinFeatureRatio = 0.05;
    public int alphaToCoverageWarnMinFrames = 3;
    public int alphaToCoverageWarnCooldownFrames = 120;
    public int alphaToCoveragePromotionReadyMinFrames = 6;
    public int alphaToCoverageStableStreak;
    public int alphaToCoverageHighStreak;
    public int alphaToCoverageWarnCooldownRemaining;
    public boolean alphaToCoverageEnvelopeBreachedLastFrame;
    public boolean alphaToCoveragePromotionReadyLastFrame;

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
        upscaleModeActiveLastFrame = false;
        upscaleModeIdLastFrame = "";
        upscaleTemporalPathActiveLastFrame = false;
        upscaleRenderScaleLastFrame = 1.0;
        upscaleModePathLastFrame = "none";
        upscaleNativeUpscalerActiveLastFrame = false;
        upscaleNativeUpscalerProviderLastFrame = "none";
        upscaleStableStreak = 0;
        upscaleHighStreak = 0;
        upscaleWarnCooldownRemaining = 0;
        upscaleEnvelopeBreachedLastFrame = false;
        upscalePromotionReadyLastFrame = false;
        msaaModeActiveLastFrame = false;
        msaaModeIdLastFrame = "";
        msaaSmaaEnabledLastFrame = false;
        msaaTemporalPathActiveLastFrame = false;
        msaaMaterialCountLastFrame = 0;
        msaaCandidateCountLastFrame = 0;
        msaaCandidateRatioLastFrame = 0.0;
        msaaStableStreak = 0;
        msaaHighStreak = 0;
        msaaWarnCooldownRemaining = 0;
        msaaEnvelopeBreachedLastFrame = false;
        msaaPromotionReadyLastFrame = false;
        qualityModeIdLastFrame = "";
        dlaaModeActiveLastFrame = false;
        dlaaTemporalPathActiveLastFrame = false;
        dlaaBlendLastFrame = 0.0;
        dlaaRenderScaleLastFrame = 1.0;
        dlaaStableStreak = 0;
        dlaaHighStreak = 0;
        dlaaWarnCooldownRemaining = 0;
        dlaaEnvelopeBreachedLastFrame = false;
        dlaaPromotionReadyLastFrame = false;
        specularPolicyActiveLastFrame = false;
        specularMaterialCountLastFrame = 0;
        specularNormalMappedCountLastFrame = 0;
        specularNormalMappedRatioLastFrame = 0.0;
        specularClipScaleLastFrame = 1.0;
        specularStableStreak = 0;
        specularHighStreak = 0;
        specularWarnCooldownRemaining = 0;
        specularEnvelopeBreachedLastFrame = false;
        specularPromotionReadyLastFrame = false;
        geometricPolicyActiveLastFrame = false;
        geometricThinFeatureCountLastFrame = 0;
        geometricThinFeatureRatioLastFrame = 0.0;
        geometricStableStreak = 0;
        geometricHighStreak = 0;
        geometricWarnCooldownRemaining = 0;
        geometricEnvelopeBreachedLastFrame = false;
        geometricPromotionReadyLastFrame = false;
        alphaToCoveragePolicyActiveLastFrame = false;
        alphaToCoverageStableStreak = 0;
        alphaToCoverageHighStreak = 0;
        alphaToCoverageWarnCooldownRemaining = 0;
        alphaToCoverageEnvelopeBreachedLastFrame = false;
        alphaToCoveragePromotionReadyLastFrame = false;
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
        upscaleWarnMinRenderScale = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.upscaleWarnMinRenderScale", upscaleWarnMinRenderScale, 0.1, 2.0);
        upscaleWarnMaxRenderScaleTsr = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.upscaleWarnMaxRenderScaleTsr", upscaleWarnMaxRenderScaleTsr, 0.1, 2.0);
        upscaleWarnMaxRenderScaleTuua = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.upscaleWarnMaxRenderScaleTuua", upscaleWarnMaxRenderScaleTuua, 0.1, 2.0);
        upscaleWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.upscaleWarnMinFrames", upscaleWarnMinFrames, 1, 10_000);
        upscaleWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.upscaleWarnCooldownFrames", upscaleWarnCooldownFrames, 0, 10_000);
        upscalePromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.upscalePromotionReadyMinFrames", upscalePromotionReadyMinFrames, 1, 10_000);
        msaaCandidateWarnMinRatio = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.msaaCandidateWarnMinRatio", msaaCandidateWarnMinRatio, 0.0, 1.0);
        msaaHybridTemporalRequired = Boolean.parseBoolean(
                backendOptions.getOrDefault("vulkan.aa.msaaWarnRequireTemporalForHybrid", String.valueOf(msaaHybridTemporalRequired)));
        msaaWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.msaaWarnMinFrames", msaaWarnMinFrames, 1, 10_000);
        msaaWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.msaaWarnCooldownFrames", msaaWarnCooldownFrames, 0, 10_000);
        msaaPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.msaaPromotionReadyMinFrames", msaaPromotionReadyMinFrames, 1, 10_000);
        dlaaWarnMinBlend = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.dlaaWarnMinBlend", dlaaWarnMinBlend, 0.0, 1.0);
        dlaaWarnMinRenderScale = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.dlaaWarnMinRenderScale", dlaaWarnMinRenderScale, 0.1, 2.0);
        dlaaWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.dlaaWarnMinFrames", dlaaWarnMinFrames, 1, 10_000);
        dlaaWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.dlaaWarnCooldownFrames", dlaaWarnCooldownFrames, 0, 10_000);
        dlaaPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.dlaaPromotionReadyMinFrames", dlaaPromotionReadyMinFrames, 1, 10_000);
        specularWarnMaxClipScale = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.specularWarnMaxClipScale", specularWarnMaxClipScale, 0.1, 2.0);
        specularWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.specularWarnMinFrames", specularWarnMinFrames, 1, 10_000);
        specularWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.specularWarnCooldownFrames", specularWarnCooldownFrames, 0, 10_000);
        specularPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.specularPromotionReadyMinFrames", specularPromotionReadyMinFrames, 1, 10_000);
        geometricWarnMinThinFeatureRatio = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.geometricWarnMinThinFeatureRatio", geometricWarnMinThinFeatureRatio, 0.0, 1.0);
        geometricWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.geometricWarnMinFrames", geometricWarnMinFrames, 1, 10_000);
        geometricWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.geometricWarnCooldownFrames", geometricWarnCooldownFrames, 0, 10_000);
        geometricPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.geometricPromotionReadyMinFrames", geometricPromotionReadyMinFrames, 1, 10_000);
        alphaToCoverageWarnMinThinFeatureRatio = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                backendOptions, "vulkan.aa.a2cWarnMinThinFeatureRatio", alphaToCoverageWarnMinThinFeatureRatio, 0.0, 1.0);
        alphaToCoverageWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.a2cWarnMinFrames", alphaToCoverageWarnMinFrames, 1, 10_000);
        alphaToCoverageWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.a2cWarnCooldownFrames", alphaToCoverageWarnCooldownFrames, 0, 10_000);
        alphaToCoveragePromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                backendOptions, "vulkan.aa.a2cPromotionReadyMinFrames", alphaToCoveragePromotionReadyMinFrames, 1, 10_000);
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

    public void applyUpscaleEmission(VulkanAaUpscaleWarningEmitter.Result emission) {
        upscaleModeActiveLastFrame = emission.upscaleModeActive();
        upscaleModeIdLastFrame = emission.aaModeId();
        upscaleTemporalPathActiveLastFrame = emission.temporalPathActive();
        upscaleRenderScaleLastFrame = emission.renderScale();
        upscaleModePathLastFrame = emission.upscalerModeId();
        upscaleNativeUpscalerActiveLastFrame = emission.nativeUpscalerActive();
        upscaleNativeUpscalerProviderLastFrame = emission.nativeUpscalerProvider();
        upscaleWarnMinRenderScale = emission.warnMinRenderScale();
        if ("tuua".equalsIgnoreCase(emission.aaModeId())) {
            upscaleWarnMaxRenderScaleTuua = emission.warnMaxRenderScale();
        } else if ("tsr".equalsIgnoreCase(emission.aaModeId())) {
            upscaleWarnMaxRenderScaleTsr = emission.warnMaxRenderScale();
        }
        upscaleStableStreak = emission.stableStreak();
        upscaleHighStreak = emission.nextHighStreak();
        upscaleWarnCooldownRemaining = emission.nextCooldownRemaining();
        upscaleEnvelopeBreachedLastFrame = emission.envelopeBreachedLastFrame();
        upscalePromotionReadyLastFrame = emission.promotionReadyLastFrame();
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

    public AaUpscalePromotionDiagnostics upscaleDiagnostics() {
        double warnMaxRenderScale = "tuua".equalsIgnoreCase(upscaleModeIdLastFrame)
                ? upscaleWarnMaxRenderScaleTuua
                : upscaleWarnMaxRenderScaleTsr;
        return new AaUpscalePromotionDiagnostics(
                true,
                upscaleModeActiveLastFrame,
                upscaleModeIdLastFrame,
                upscaleTemporalPathActiveLastFrame,
                upscaleRenderScaleLastFrame,
                upscaleModePathLastFrame,
                upscaleNativeUpscalerActiveLastFrame,
                upscaleNativeUpscalerProviderLastFrame,
                upscaleWarnMinRenderScale,
                warnMaxRenderScale,
                upscalePromotionReadyMinFrames,
                upscaleStableStreak,
                upscaleEnvelopeBreachedLastFrame,
                upscalePromotionReadyLastFrame
        );
    }

    public VulkanAaUpscaleWarningEmitter.Result emitUpscale(
            org.dynamislight.impl.vulkan.runtime.config.AaMode aaMode,
            double renderScale,
            UpscalerMode upscalerMode,
            boolean nativeUpscalerActive,
            String nativeUpscalerProvider,
            boolean temporalPathActive
    ) {
        return VulkanAaUpscaleWarningEmitter.emit(new VulkanAaUpscaleWarningEmitter.Input(
                aaMode,
                temporalPathActive,
                renderScale,
                upscalerMode,
                nativeUpscalerActive,
                nativeUpscalerProvider,
                upscaleWarnMinRenderScale,
                upscaleWarnMaxRenderScaleTsr,
                upscaleWarnMaxRenderScaleTuua,
                upscaleWarnMinFrames,
                upscaleWarnCooldownFrames,
                upscalePromotionReadyMinFrames,
                upscaleStableStreak,
                upscaleHighStreak,
                upscaleWarnCooldownRemaining
        ));
    }

    public VulkanAaMsaaWarningEmitter.Result emitMsaa(
            org.dynamislight.impl.vulkan.runtime.config.AaMode aaMode,
            java.util.List<org.dynamislight.api.scene.MaterialDesc> materials,
            boolean smaaEnabled,
            boolean temporalPathActive
    ) {
        return VulkanAaMsaaWarningEmitter.emit(new VulkanAaMsaaWarningEmitter.Input(
                aaMode,
                materials,
                smaaEnabled,
                temporalPathActive,
                msaaCandidateWarnMinRatio,
                msaaHybridTemporalRequired,
                msaaWarnMinFrames,
                msaaWarnCooldownFrames,
                msaaPromotionReadyMinFrames,
                msaaStableStreak,
                msaaHighStreak,
                msaaWarnCooldownRemaining
        ));
    }

    public void applyMsaaEmission(VulkanAaMsaaWarningEmitter.Result emission) {
        msaaModeActiveLastFrame = emission.msaaModeActive();
        msaaModeIdLastFrame = emission.aaModeId();
        msaaSmaaEnabledLastFrame = emission.smaaEnabled();
        msaaTemporalPathActiveLastFrame = emission.temporalPathActive();
        msaaMaterialCountLastFrame = emission.materialCount();
        msaaCandidateCountLastFrame = emission.msaaCandidateCount();
        msaaCandidateRatioLastFrame = emission.msaaCandidateRatio();
        msaaCandidateWarnMinRatio = emission.msaaCandidateWarnMinRatio();
        msaaHybridTemporalRequired = emission.hybridTemporalRequired();
        msaaStableStreak = emission.stableStreak();
        msaaHighStreak = emission.nextHighStreak();
        msaaWarnCooldownRemaining = emission.nextCooldownRemaining();
        msaaEnvelopeBreachedLastFrame = emission.envelopeBreachedLastFrame();
        msaaPromotionReadyLastFrame = emission.promotionReadyLastFrame();
    }

    public AaMsaaPromotionDiagnostics msaaDiagnostics() {
        return new AaMsaaPromotionDiagnostics(
                true,
                msaaModeActiveLastFrame,
                msaaModeIdLastFrame,
                msaaSmaaEnabledLastFrame,
                msaaTemporalPathActiveLastFrame,
                msaaMaterialCountLastFrame,
                msaaCandidateCountLastFrame,
                msaaCandidateRatioLastFrame,
                msaaCandidateWarnMinRatio,
                msaaHybridTemporalRequired,
                msaaPromotionReadyMinFrames,
                msaaStableStreak,
                msaaEnvelopeBreachedLastFrame,
                msaaPromotionReadyLastFrame
        );
    }

    public VulkanAaQualityWarningEmitter.Result emitQuality(
            org.dynamislight.impl.vulkan.runtime.config.AaMode aaMode,
            java.util.List<org.dynamislight.api.scene.MaterialDesc> materials,
            boolean temporalPathActive,
            double taaBlend,
            double taaRenderScale,
            double taaClipScale
    ) {
        return VulkanAaQualityWarningEmitter.emit(new VulkanAaQualityWarningEmitter.Input(
                aaMode,
                materials,
                temporalPathActive,
                taaBlend,
                taaRenderScale,
                taaClipScale,
                dlaaWarnMinBlend,
                dlaaWarnMinRenderScale,
                dlaaWarnMinFrames,
                dlaaWarnCooldownFrames,
                dlaaPromotionReadyMinFrames,
                dlaaStableStreak,
                dlaaHighStreak,
                dlaaWarnCooldownRemaining,
                specularWarnMaxClipScale,
                specularWarnMinFrames,
                specularWarnCooldownFrames,
                specularPromotionReadyMinFrames,
                specularStableStreak,
                specularHighStreak,
                specularWarnCooldownRemaining,
                geometricWarnMinThinFeatureRatio,
                geometricWarnMinFrames,
                geometricWarnCooldownFrames,
                geometricPromotionReadyMinFrames,
                geometricStableStreak,
                geometricHighStreak,
                geometricWarnCooldownRemaining,
                alphaToCoverageWarnMinThinFeatureRatio,
                alphaToCoverageWarnMinFrames,
                alphaToCoverageWarnCooldownFrames,
                alphaToCoveragePromotionReadyMinFrames,
                alphaToCoverageStableStreak,
                alphaToCoverageHighStreak,
                alphaToCoverageWarnCooldownRemaining
        ));
    }

    public void applyQualityEmission(VulkanAaQualityWarningEmitter.Result emission) {
        qualityModeIdLastFrame = emission.aaModeId();
        dlaaModeActiveLastFrame = emission.dlaaModeActive();
        dlaaTemporalPathActiveLastFrame = emission.dlaaTemporalPathActive();
        dlaaBlendLastFrame = emission.dlaaBlend();
        dlaaRenderScaleLastFrame = emission.dlaaRenderScale();
        dlaaWarnMinBlend = emission.dlaaWarnMinBlend();
        dlaaWarnMinRenderScale = emission.dlaaWarnMinRenderScale();
        dlaaStableStreak = emission.dlaaStableStreak();
        dlaaHighStreak = emission.nextDlaaHighStreak();
        dlaaWarnCooldownRemaining = emission.nextDlaaCooldownRemaining();
        dlaaEnvelopeBreachedLastFrame = emission.dlaaEnvelopeBreachedLastFrame();
        dlaaPromotionReadyLastFrame = emission.dlaaPromotionReadyLastFrame();
        specularPolicyActiveLastFrame = emission.specularPolicyActive();
        specularMaterialCountLastFrame = emission.materialCount();
        specularNormalMappedCountLastFrame = emission.normalMappedMaterialCount();
        specularNormalMappedRatioLastFrame = emission.normalMappedMaterialRatio();
        specularClipScaleLastFrame = emission.specularClipScale();
        specularWarnMaxClipScale = emission.specularWarnMaxClipScale();
        specularStableStreak = emission.specularStableStreak();
        specularHighStreak = emission.nextSpecularHighStreak();
        specularWarnCooldownRemaining = emission.nextSpecularCooldownRemaining();
        specularEnvelopeBreachedLastFrame = emission.specularEnvelopeBreachedLastFrame();
        specularPromotionReadyLastFrame = emission.specularPromotionReadyLastFrame();
        geometricPolicyActiveLastFrame = emission.geometricPolicyActive();
        geometricThinFeatureCountLastFrame = emission.thinFeatureMaterialCount();
        geometricThinFeatureRatioLastFrame = emission.thinFeatureMaterialRatio();
        geometricWarnMinThinFeatureRatio = emission.geometricWarnMinThinFeatureRatio();
        geometricStableStreak = emission.geometricStableStreak();
        geometricHighStreak = emission.nextGeometricHighStreak();
        geometricWarnCooldownRemaining = emission.nextGeometricCooldownRemaining();
        geometricEnvelopeBreachedLastFrame = emission.geometricEnvelopeBreachedLastFrame();
        geometricPromotionReadyLastFrame = emission.geometricPromotionReadyLastFrame();
        alphaToCoveragePolicyActiveLastFrame = emission.alphaToCoveragePolicyActive();
        alphaToCoverageWarnMinThinFeatureRatio = emission.alphaToCoverageWarnMinThinFeatureRatio();
        alphaToCoverageStableStreak = emission.alphaToCoverageStableStreak();
        alphaToCoverageHighStreak = emission.nextAlphaToCoverageHighStreak();
        alphaToCoverageWarnCooldownRemaining = emission.nextAlphaToCoverageCooldownRemaining();
        alphaToCoverageEnvelopeBreachedLastFrame = emission.alphaToCoverageEnvelopeBreachedLastFrame();
        alphaToCoveragePromotionReadyLastFrame = emission.alphaToCoveragePromotionReadyLastFrame();
    }

    public AaQualityPromotionDiagnostics qualityDiagnostics() {
        return new AaQualityPromotionDiagnostics(
                true,
                qualityModeIdLastFrame,
                dlaaModeActiveLastFrame,
                dlaaTemporalPathActiveLastFrame,
                dlaaBlendLastFrame,
                dlaaRenderScaleLastFrame,
                dlaaWarnMinBlend,
                dlaaWarnMinRenderScale,
                dlaaPromotionReadyMinFrames,
                dlaaStableStreak,
                dlaaEnvelopeBreachedLastFrame,
                dlaaPromotionReadyLastFrame,
                specularPolicyActiveLastFrame,
                specularMaterialCountLastFrame,
                specularNormalMappedCountLastFrame,
                specularNormalMappedRatioLastFrame,
                specularClipScaleLastFrame,
                specularWarnMaxClipScale,
                specularPromotionReadyMinFrames,
                specularStableStreak,
                specularEnvelopeBreachedLastFrame,
                specularPromotionReadyLastFrame,
                geometricPolicyActiveLastFrame,
                geometricThinFeatureCountLastFrame,
                geometricThinFeatureRatioLastFrame,
                geometricWarnMinThinFeatureRatio,
                geometricPromotionReadyMinFrames,
                geometricStableStreak,
                geometricEnvelopeBreachedLastFrame,
                geometricPromotionReadyLastFrame,
                alphaToCoveragePolicyActiveLastFrame,
                alphaToCoverageWarnMinThinFeatureRatio,
                alphaToCoveragePromotionReadyMinFrames,
                alphaToCoverageStableStreak,
                alphaToCoverageEnvelopeBreachedLastFrame,
                alphaToCoveragePromotionReadyLastFrame
        );
    }
}
