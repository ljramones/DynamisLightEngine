package org.dynamislight.impl.vulkan;

import java.util.List;
import java.util.Locale;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.impl.vulkan.capability.VulkanShadowCapabilityPlanner;

final class VulkanShadowFrameWarningFlow {
    static final class State {
        ShadowRenderConfig currentShadows;
        int shadowCadenceWarnCooldownRemaining;
        int shadowPointBudgetWarnCooldownRemaining;
        int shadowCacheWarnCooldownRemaining;
        int shadowRtWarnCooldownRemaining;
        int shadowHybridWarnCooldownRemaining;
        int shadowTransparentReceiverWarnCooldownRemaining;
        int shadowTopologyWarnCooldownRemaining;
        int shadowCadenceHighStreak;
        int shadowCadenceStableStreak;
        boolean shadowCadencePromotionReadyLastFrame;
        int shadowPointBudgetHighStreak;
        int shadowPointBudgetStableStreak;
        boolean shadowPointBudgetPromotionReadyLastFrame;
        int shadowCacheHighStreak;
        int shadowRtHighStreak;
        int shadowHybridHighStreak;
        int shadowTransparentReceiverHighStreak;
        int shadowTopologyHighStreak;
        int shadowTopologyStableStreak;
        boolean shadowTopologyPromotionReadyLastFrame;
        int shadowSpotProjectedStableStreak;
        boolean shadowSpotProjectedPromotionReadyLastFrame;
        int shadowPhaseAPromotionStableStreak;
        boolean shadowPhaseAPromotionReadyLastFrame;
        int shadowPhaseDPromotionStableStreak;
        boolean shadowPhaseDPromotionReadyLastFrame;
        String shadowCapabilityFeatureIdLastFrame;
        String shadowCapabilityModeLastFrame;
        List<String> shadowCapabilitySignalsLastFrame;
        int shadowSpotProjectedRenderedCountLastFrame;
        boolean shadowSpotProjectedRequestedLastFrame;
        boolean shadowSpotProjectedActiveLastFrame;
        String shadowSpotProjectedContractStatusLastFrame;
        boolean shadowSpotProjectedContractBreachedLastFrame;
        int shadowSpotProjectedPromotionReadyMinFrames;
        int shadowCadenceSelectedLocalLightsLastFrame;
        int shadowCadenceDeferredLocalLightsLastFrame;
        int shadowCadenceStaleBypassCountLastFrame;
        double shadowCadenceDeferredRatioLastFrame;
        double shadowCadenceWarnDeferredRatioMax;
        int shadowCadenceWarnMinFrames;
        int shadowCadenceWarnCooldownFrames;
        boolean shadowCadenceEnvelopeBreachedLastFrame;
        int shadowCadencePromotionReadyMinFrames;
        int shadowMaxFacesPerFrame;
        double shadowPointFaceBudgetWarnSaturationMin;
        int shadowPointFaceBudgetWarnMinFrames;
        int shadowPointFaceBudgetWarnCooldownFrames;
        int shadowPointFaceBudgetPromotionReadyMinFrames;
        int shadowPointBudgetRenderedCubemapsLastFrame;
        int shadowPointBudgetRenderedFacesLastFrame;
        int shadowPointBudgetDeferredCountLastFrame;
        double shadowPointBudgetSaturationRatioLastFrame;
        boolean shadowPointBudgetEnvelopeBreachedLastFrame;
        int shadowPhaseAPromotionReadyMinFrames;
        int shadowMaxShadowedLocalLights;
        int shadowMaxLocalLayers;
        boolean shadowSchedulerEnabled;
        double shadowCacheChurnWarnMax;
        int shadowCacheMissWarnMax;
        int shadowCacheWarnMinFrames;
        int shadowCacheWarnCooldownFrames;
        double shadowRtDenoiseWarnMin;
        int shadowRtSampleWarnMin;
        double shadowRtPerfMaxGpuMsLow;
        double shadowRtPerfMaxGpuMsMedium;
        double shadowRtPerfMaxGpuMsHigh;
        double shadowRtPerfMaxGpuMsUltra;
        int shadowRtWarnMinFrames;
        int shadowRtWarnCooldownFrames;
        double shadowHybridRtShareWarnMin;
        double shadowHybridContactShareWarnMin;
        int shadowHybridWarnMinFrames;
        int shadowHybridWarnCooldownFrames;
        boolean shadowTransparentReceiversRequested;
        boolean shadowTransparentReceiversSupported;
        double shadowTransparentReceiverCandidateRatioWarnMax;
        int shadowTransparentReceiverWarnMinFrames;
        int shadowTransparentReceiverWarnCooldownFrames;
        boolean shadowAreaApproxRequested;
        boolean shadowAreaApproxSupported;
        boolean shadowAreaApproxRequireActive;
        boolean shadowDistanceFieldRequested;
        boolean shadowDistanceFieldSupported;
        boolean shadowDistanceFieldRequireActive;
        double shadowTopologyLocalCoverageWarnMin;
        double shadowTopologySpotCoverageWarnMin;
        double shadowTopologyPointCoverageWarnMin;
        int shadowTopologyWarnMinFrames;
        int shadowTopologyWarnCooldownFrames;
        int shadowTopologyPromotionReadyMinFrames;
        int shadowPhaseDPromotionReadyMinFrames;
        List<LightDesc> currentSceneLights;
        List<MaterialDesc> currentSceneMaterials;
    }

    static void process(Object runtime, VulkanContext context, QualityTier qualityTier, List<EngineWarning> warnings) {
        State state = new State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, state);
        VulkanRuntimeWarningResets.resetShadowFrameDefaults(state, state.shadowTransparentReceiversSupported);
        if (state.shadowCadenceWarnCooldownRemaining > 0) {
            state.shadowCadenceWarnCooldownRemaining--;
        }
        if (state.shadowPointBudgetWarnCooldownRemaining > 0) {
            state.shadowPointBudgetWarnCooldownRemaining--;
        }
        if (state.shadowCacheWarnCooldownRemaining > 0) {
            state.shadowCacheWarnCooldownRemaining--;
        }
        if (state.shadowRtWarnCooldownRemaining > 0) {
            state.shadowRtWarnCooldownRemaining--;
        }
        if (state.shadowHybridWarnCooldownRemaining > 0) {
            state.shadowHybridWarnCooldownRemaining--;
        }
        if (state.shadowTransparentReceiverWarnCooldownRemaining > 0) {
            state.shadowTransparentReceiverWarnCooldownRemaining--;
        }
        if (state.shadowTopologyWarnCooldownRemaining > 0) {
            state.shadowTopologyWarnCooldownRemaining--;
        }
        if (!state.currentShadows.enabled()) {
            state.shadowCadenceHighStreak = 0;
            state.shadowCadenceWarnCooldownRemaining = 0;
            state.shadowCadenceStableStreak = 0;
            state.shadowCadencePromotionReadyLastFrame = false;
            state.shadowPointBudgetHighStreak = 0;
            state.shadowPointBudgetWarnCooldownRemaining = 0;
            state.shadowPointBudgetStableStreak = 0;
            state.shadowPointBudgetPromotionReadyLastFrame = false;
            state.shadowCacheHighStreak = 0;
            state.shadowCacheWarnCooldownRemaining = 0;
            state.shadowRtHighStreak = 0;
            state.shadowRtWarnCooldownRemaining = 0;
            state.shadowHybridHighStreak = 0;
            state.shadowHybridWarnCooldownRemaining = 0;
            state.shadowTransparentReceiverHighStreak = 0;
            state.shadowTransparentReceiverWarnCooldownRemaining = 0;
            state.shadowTopologyHighStreak = 0;
            state.shadowTopologyWarnCooldownRemaining = 0;
            state.shadowTopologyStableStreak = 0;
            state.shadowTopologyPromotionReadyLastFrame = false;
            state.shadowSpotProjectedStableStreak = 0;
            state.shadowSpotProjectedPromotionReadyLastFrame = false;
            state.shadowPhaseAPromotionStableStreak = 0;
            state.shadowPhaseAPromotionReadyLastFrame = false;
            state.shadowPhaseDPromotionStableStreak = 0;
            state.shadowPhaseDPromotionReadyLastFrame = false;
            VulkanTelemetryStateBinder.copyMatchingFields(state, runtime);
            return;
        }
        VulkanShadowCapabilityPlanner.Plan shadowCapabilityPlan = VulkanShadowCapabilityPlanner.plan(
                new VulkanShadowCapabilityPlanner.PlanInput(
                        qualityTier,
                        state.currentShadows.filterPath(),
                        state.currentShadows.contactShadowsRequested(),
                        state.currentShadows.rtShadowMode(),
                        state.shadowMaxShadowedLocalLights > 0 ? state.shadowMaxShadowedLocalLights : state.currentShadows.maxShadowedLocalLights(),
                        state.shadowMaxLocalLayers,
                        state.shadowMaxFacesPerFrame,
                        state.currentShadows.selectedLocalShadowLights(),
                        state.currentShadows.deferredShadowLightCount(),
                        state.currentShadows.renderedSpotShadowLights(),
                        state.currentShadows.renderedPointShadowCubemaps(),
                        state.shadowSchedulerEnabled,
                        false,
                        false,
                        state.currentShadows.renderedSpotShadowLights() > 0,
                        false,
                        false
                )
        );
        state.shadowCapabilityFeatureIdLastFrame = shadowCapabilityPlan.capability().featureId();
        state.shadowCapabilityModeLastFrame = shadowCapabilityPlan.mode().id();
        state.shadowCapabilitySignalsLastFrame = shadowCapabilityPlan.signals();
        state.shadowSpotProjectedRenderedCountLastFrame = state.currentShadows.renderedSpotShadowLights();
        state.shadowSpotProjectedRequestedLastFrame = "spot_projected".equals(state.shadowCapabilityModeLastFrame)
                || state.shadowCapabilitySignalsLastFrame.stream().anyMatch(s -> "spotProjected=true".equals(s));
        state.shadowSpotProjectedActiveLastFrame = state.shadowSpotProjectedRenderedCountLastFrame > 0;
        if (state.shadowSpotProjectedRequestedLastFrame && state.shadowSpotProjectedActiveLastFrame) {
            state.shadowSpotProjectedContractStatusLastFrame = "active";
        } else if (state.shadowSpotProjectedRequestedLastFrame) {
            state.shadowSpotProjectedContractStatusLastFrame = "requested_unavailable";
        } else if (state.shadowSpotProjectedActiveLastFrame) {
            state.shadowSpotProjectedContractStatusLastFrame = "active_not_selected";
        } else {
            state.shadowSpotProjectedContractStatusLastFrame = "inactive";
        }
        state.shadowSpotProjectedContractBreachedLastFrame =
                state.shadowSpotProjectedRequestedLastFrame && !state.shadowSpotProjectedActiveLastFrame;
        if (state.shadowSpotProjectedContractBreachedLastFrame) {
            state.shadowSpotProjectedStableStreak = 0;
            state.shadowSpotProjectedPromotionReadyLastFrame = false;
        } else {
            state.shadowSpotProjectedStableStreak = Math.min(10_000, state.shadowSpotProjectedStableStreak + 1);
            state.shadowSpotProjectedPromotionReadyLastFrame =
                    state.shadowSpotProjectedRequestedLastFrame
                            && state.shadowSpotProjectedActiveLastFrame
                            && state.shadowSpotProjectedStableStreak >= state.shadowSpotProjectedPromotionReadyMinFrames;
        }
        state.shadowCadenceSelectedLocalLightsLastFrame = state.currentShadows.selectedLocalShadowLights();
        state.shadowCadenceDeferredLocalLightsLastFrame = state.currentShadows.deferredShadowLightCount();
        state.shadowCadenceStaleBypassCountLastFrame = state.currentShadows.staleBypassShadowLightCount();
        state.shadowCadenceDeferredRatioLastFrame = state.shadowCadenceSelectedLocalLightsLastFrame <= 0
                ? 0.0
                : (double) state.shadowCadenceDeferredLocalLightsLastFrame / (double) state.shadowCadenceSelectedLocalLightsLastFrame;
        boolean cadenceEnvelopeNow = state.shadowCadenceSelectedLocalLightsLastFrame >= 2
                && state.shadowCadenceDeferredRatioLastFrame > state.shadowCadenceWarnDeferredRatioMax;
        if (cadenceEnvelopeNow) {
            state.shadowCadenceHighStreak = Math.min(10_000, state.shadowCadenceHighStreak + 1);
            state.shadowCadenceStableStreak = 0;
            state.shadowCadencePromotionReadyLastFrame = false;
            state.shadowCadenceEnvelopeBreachedLastFrame = true;
        } else {
            state.shadowCadenceHighStreak = 0;
            state.shadowCadenceStableStreak = Math.min(10_000, state.shadowCadenceStableStreak + 1);
            state.shadowCadencePromotionReadyLastFrame =
                    state.shadowCadenceStableStreak >= state.shadowCadencePromotionReadyMinFrames;
        }
        warnings.add(new EngineWarning(
                "SHADOW_CAPABILITY_MODE_ACTIVE",
                "Shadow capability mode active: featureId="
                        + shadowCapabilityPlan.capability().featureId()
                        + " mode=" + shadowCapabilityPlan.mode().id()
                        + " signals=[" + String.join(", ", shadowCapabilityPlan.signals()) + "]"
        ));
        warnings.add(new EngineWarning(
                "SHADOW_TELEMETRY_PROFILE_ACTIVE",
                "Shadow telemetry profile active (tier=" + qualityTier.name().toLowerCase(Locale.ROOT)
                        + ", cadenceDeferredRatioWarnMax=" + state.shadowCadenceWarnDeferredRatioMax
                        + ", cadenceWarnMinFrames=" + state.shadowCadenceWarnMinFrames
                        + ", cadenceWarnCooldownFrames=" + state.shadowCadenceWarnCooldownFrames
                        + ", cadencePromotionReadyMinFrames=" + state.shadowCadencePromotionReadyMinFrames
                        + ", pointFaceBudgetSaturationWarnMin=" + state.shadowPointFaceBudgetWarnSaturationMin
                        + ", pointFaceBudgetWarnMinFrames=" + state.shadowPointFaceBudgetWarnMinFrames
                        + ", pointFaceBudgetWarnCooldownFrames=" + state.shadowPointFaceBudgetWarnCooldownFrames
                        + ", pointFaceBudgetPromotionReadyMinFrames=" + state.shadowPointFaceBudgetPromotionReadyMinFrames
                        + ", cacheChurnWarnMax=" + state.shadowCacheChurnWarnMax
                        + ", cacheMissWarnMax=" + state.shadowCacheMissWarnMax
                        + ", cacheWarnMinFrames=" + state.shadowCacheWarnMinFrames
                        + ", cacheWarnCooldownFrames=" + state.shadowCacheWarnCooldownFrames
                        + ", rtDenoiseWarnMin=" + state.shadowRtDenoiseWarnMin
                        + ", rtSampleWarnMin=" + state.shadowRtSampleWarnMin
                        + ", rtPerfMaxGpuMsLow=" + state.shadowRtPerfMaxGpuMsLow
                        + ", rtPerfMaxGpuMsMedium=" + state.shadowRtPerfMaxGpuMsMedium
                        + ", rtPerfMaxGpuMsHigh=" + state.shadowRtPerfMaxGpuMsHigh
                        + ", rtPerfMaxGpuMsUltra=" + state.shadowRtPerfMaxGpuMsUltra
                        + ", rtWarnMinFrames=" + state.shadowRtWarnMinFrames
                        + ", rtWarnCooldownFrames=" + state.shadowRtWarnCooldownFrames
                        + ", hybridRtShareWarnMin=" + state.shadowHybridRtShareWarnMin
                        + ", hybridContactShareWarnMin=" + state.shadowHybridContactShareWarnMin
                        + ", hybridWarnMinFrames=" + state.shadowHybridWarnMinFrames
                        + ", hybridWarnCooldownFrames=" + state.shadowHybridWarnCooldownFrames
                        + ", transparentReceiversRequested=" + state.shadowTransparentReceiversRequested
                        + ", transparentReceiversSupported=" + state.shadowTransparentReceiversSupported
                        + ", transparentReceiverCandidateRatioWarnMax=" + state.shadowTransparentReceiverCandidateRatioWarnMax
                        + ", transparentReceiverWarnMinFrames=" + state.shadowTransparentReceiverWarnMinFrames
                        + ", transparentReceiverWarnCooldownFrames=" + state.shadowTransparentReceiverWarnCooldownFrames
                        + ", areaApproxRequested=" + state.shadowAreaApproxRequested
                        + ", areaApproxSupported=" + state.shadowAreaApproxSupported
                        + ", areaApproxRequireActive=" + state.shadowAreaApproxRequireActive
                        + ", distanceFieldRequested=" + state.shadowDistanceFieldRequested
                        + ", distanceFieldSupported=" + state.shadowDistanceFieldSupported
                        + ", distanceFieldRequireActive=" + state.shadowDistanceFieldRequireActive
                        + ", spotProjectedPromotionReadyMinFrames=" + state.shadowSpotProjectedPromotionReadyMinFrames
                        + ", topologyLocalCoverageWarnMin=" + state.shadowTopologyLocalCoverageWarnMin
                        + ", topologySpotCoverageWarnMin=" + state.shadowTopologySpotCoverageWarnMin
                        + ", topologyPointCoverageWarnMin=" + state.shadowTopologyPointCoverageWarnMin
                        + ", topologyWarnMinFrames=" + state.shadowTopologyWarnMinFrames
                        + ", topologyWarnCooldownFrames=" + state.shadowTopologyWarnCooldownFrames
                        + ", topologyPromotionReadyMinFrames=" + state.shadowTopologyPromotionReadyMinFrames
                        + ", phaseAPromotionReadyMinFrames=" + state.shadowPhaseAPromotionReadyMinFrames
                        + ", phaseDPromotionReadyMinFrames=" + state.shadowPhaseDPromotionReadyMinFrames
                        + ")"
        ));
        VulkanShadowCadencePointWarningEmitter.State shadowCadencePointState = new VulkanShadowCadencePointWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(state, shadowCadencePointState);
        shadowCadencePointState.cadenceEnvelopeNow = cadenceEnvelopeNow;
        VulkanShadowCadencePointWarningEmitter.emit(warnings, shadowCadencePointState);
        VulkanTelemetryStateBinder.copyMatchingFields(shadowCadencePointState, state);
        VulkanShadowTopologyWarningEmitter.State shadowTopologyState = new VulkanShadowTopologyWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(state, shadowTopologyState);
        VulkanShadowTopologyWarningEmitter.emit(warnings, shadowTopologyState);
        VulkanTelemetryStateBinder.copyMatchingFields(shadowTopologyState, state);
        VulkanShadowCacheHybridExtendedWarningEmitter.State shadowCacheHybridExtendedState = new VulkanShadowCacheHybridExtendedWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(state, shadowCacheHybridExtendedState);
        VulkanShadowCacheHybridExtendedWarningEmitter.emit(warnings, shadowCacheHybridExtendedState);
        VulkanTelemetryStateBinder.copyMatchingFields(shadowCacheHybridExtendedState, state);
        String momentPhase = "pending";
        if (context.hasShadowMomentResources()) {
            momentPhase = context.isShadowMomentInitialized() ? "active" : "initializing";
        }
        VulkanShadowPolicyWarningEmitter.State shadowPolicyState = new VulkanShadowPolicyWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(state, shadowPolicyState);
        shadowPolicyState.shadowDepthFormatTag = context.shadowDepthFormatTag();
        shadowPolicyState.shadowMomentResourcesAllocated = context.hasShadowMomentResources();
        shadowPolicyState.shadowMomentFormatTag = context.shadowMomentFormatTag();
        shadowPolicyState.shadowMomentInitialized = context.isShadowMomentInitialized();
        shadowPolicyState.momentPhase = momentPhase;
        warnings.add(VulkanShadowPolicyWarningEmitter.warning(shadowPolicyState));
        VulkanShadowCoverageMomentWarningEmitter.State shadowCoverageMomentState = new VulkanShadowCoverageMomentWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(state, shadowCoverageMomentState);
        shadowCoverageMomentState.shadowMomentResourcesAvailable = context.hasShadowMomentResources();
        shadowCoverageMomentState.shadowMomentInitialized = context.isShadowMomentInitialized();
        VulkanShadowCoverageMomentWarningEmitter.emit(warnings, shadowCoverageMomentState);
        VulkanShadowRtWarningEmitter.State shadowRtState = new VulkanShadowRtWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(state, shadowRtState);
        VulkanShadowRtWarningEmitter.emit(warnings, shadowRtState);
        VulkanTelemetryStateBinder.copyMatchingFields(shadowRtState, state);
        VulkanTelemetryStateBinder.copyMatchingFields(state, runtime);
    }

    private VulkanShadowFrameWarningFlow() {
    }
}
