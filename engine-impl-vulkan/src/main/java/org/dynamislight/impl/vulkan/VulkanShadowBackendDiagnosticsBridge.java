package org.dynamislight.impl.vulkan;

import java.util.List;
import org.dynamislight.api.runtime.ShadowCacheDiagnostics;
import org.dynamislight.api.runtime.ShadowCadenceDiagnostics;
import org.dynamislight.api.runtime.ShadowCapabilityDiagnostics;
import org.dynamislight.api.runtime.ShadowExtendedModeDiagnostics;
import org.dynamislight.api.runtime.ShadowHybridDiagnostics;
import org.dynamislight.api.runtime.ShadowPhaseAPromotionDiagnostics;
import org.dynamislight.api.runtime.ShadowPhaseDPromotionDiagnostics;
import org.dynamislight.api.runtime.ShadowPointBudgetDiagnostics;
import org.dynamislight.api.runtime.ShadowRtDiagnostics;
import org.dynamislight.api.runtime.ShadowSpotProjectedDiagnostics;
import org.dynamislight.api.runtime.ShadowTopologyDiagnostics;
import org.dynamislight.api.runtime.ShadowTransparentReceiverDiagnostics;

final class VulkanShadowBackendDiagnosticsBridge {
    static final class State {
        String shadowCapabilityFeatureIdLastFrame;
        String shadowCapabilityModeLastFrame;
        List<String> shadowCapabilitySignalsLastFrame;
        int shadowCadenceSelectedLocalLightsLastFrame;
        int shadowCadenceDeferredLocalLightsLastFrame;
        int shadowCadenceStaleBypassCountLastFrame;
        double shadowCadenceDeferredRatioLastFrame;
        double shadowCadenceWarnDeferredRatioMax;
        int shadowCadenceWarnMinFrames;
        int shadowCadenceWarnCooldownFrames;
        int shadowCadenceHighStreak;
        int shadowCadenceWarnCooldownRemaining;
        int shadowCadenceStableStreak;
        int shadowCadencePromotionReadyMinFrames;
        boolean shadowCadencePromotionReadyLastFrame;
        boolean shadowCadenceEnvelopeBreachedLastFrame;
        int shadowMaxFacesPerFrame;
        int shadowPointBudgetRenderedCubemapsLastFrame;
        int shadowPointBudgetRenderedFacesLastFrame;
        int shadowPointBudgetDeferredCountLastFrame;
        double shadowPointBudgetSaturationRatioLastFrame;
        double shadowPointFaceBudgetWarnSaturationMin;
        int shadowPointFaceBudgetWarnMinFrames;
        int shadowPointFaceBudgetWarnCooldownFrames;
        int shadowPointBudgetHighStreak;
        int shadowPointBudgetWarnCooldownRemaining;
        int shadowPointBudgetStableStreak;
        int shadowPointFaceBudgetPromotionReadyMinFrames;
        boolean shadowPointBudgetPromotionReadyLastFrame;
        boolean shadowPointBudgetEnvelopeBreachedLastFrame;
        boolean shadowSpotProjectedRequestedLastFrame;
        boolean shadowSpotProjectedActiveLastFrame;
        int shadowSpotProjectedRenderedCountLastFrame;
        String shadowSpotProjectedContractStatusLastFrame;
        boolean shadowSpotProjectedContractBreachedLastFrame;
        int shadowSpotProjectedStableStreak;
        int shadowSpotProjectedPromotionReadyMinFrames;
        boolean shadowSpotProjectedPromotionReadyLastFrame;
        int shadowCacheMissCountLastFrame;
        int shadowCacheHitCountLastFrame;
        int shadowCacheEvictionCountLastFrame;
        double shadowCacheHitRatioLastFrame;
        double shadowCacheChurnRatioLastFrame;
        String shadowCacheInvalidationReasonLastFrame;
        double shadowCacheChurnWarnMax;
        int shadowCacheMissWarnMax;
        int shadowCacheWarnMinFrames;
        int shadowCacheWarnCooldownFrames;
        int shadowCacheHighStreak;
        int shadowCacheWarnCooldownRemaining;
        boolean shadowCacheEnvelopeBreachedLastFrame;
        ShadowRenderConfig currentShadows;
        float shadowRtDenoiseStrength;
        float shadowRtProductionDenoiseStrength;
        float shadowRtDedicatedDenoiseStrength;
        int shadowRtSampleCount;
        int shadowRtProductionSampleCount;
        int shadowRtDedicatedSampleCount;
        double shadowRtDenoiseWarnMin;
        int shadowRtSampleWarnMin;
        double shadowRtPerfGpuMsEstimateLastFrame;
        double shadowRtPerfGpuMsWarnMaxLastFrame;
        int shadowRtWarnMinFrames;
        int shadowRtWarnCooldownFrames;
        int shadowRtHighStreak;
        int shadowRtWarnCooldownRemaining;
        boolean shadowRtEnvelopeBreachedLastFrame;
        double shadowHybridCascadeShareLastFrame;
        double shadowHybridContactShareLastFrame;
        double shadowHybridRtShareLastFrame;
        double shadowHybridRtShareWarnMin;
        double shadowHybridContactShareWarnMin;
        int shadowHybridWarnMinFrames;
        int shadowHybridWarnCooldownFrames;
        int shadowHybridHighStreak;
        int shadowHybridWarnCooldownRemaining;
        boolean shadowHybridEnvelopeBreachedLastFrame;
        boolean shadowTransparentReceiversRequested;
        boolean shadowTransparentReceiversSupported;
        String shadowTransparentReceiverPolicyLastFrame;
        int shadowTransparentReceiverCandidateCountLastFrame;
        double shadowTransparentReceiverCandidateRatioLastFrame;
        double shadowTransparentReceiverCandidateRatioWarnMax;
        int shadowTransparentReceiverWarnMinFrames;
        int shadowTransparentReceiverWarnCooldownFrames;
        int shadowTransparentReceiverHighStreak;
        int shadowTransparentReceiverWarnCooldownRemaining;
        boolean shadowTransparentReceiverEnvelopeBreachedLastFrame;
        boolean shadowAreaApproxRequested;
        boolean shadowAreaApproxSupported;
        boolean shadowAreaApproxBreachedLastFrame;
        boolean shadowDistanceFieldRequested;
        boolean shadowDistanceFieldSupported;
        boolean shadowDistanceFieldBreachedLastFrame;
        int shadowTopologyCandidateSpotLightsLastFrame;
        int shadowTopologyCandidatePointLightsLastFrame;
        double shadowTopologyLocalCoverageLastFrame;
        double shadowTopologySpotCoverageLastFrame;
        double shadowTopologyPointCoverageLastFrame;
        double shadowTopologyLocalCoverageWarnMin;
        double shadowTopologySpotCoverageWarnMin;
        double shadowTopologyPointCoverageWarnMin;
        int shadowTopologyWarnMinFrames;
        int shadowTopologyWarnCooldownFrames;
        int shadowTopologyHighStreak;
        int shadowTopologyWarnCooldownRemaining;
        int shadowTopologyStableStreak;
        int shadowTopologyPromotionReadyMinFrames;
        boolean shadowTopologyPromotionReadyLastFrame;
        boolean shadowTopologyEnvelopeBreachedLastFrame;
        int shadowPhaseAPromotionReadyMinFrames;
        int shadowPhaseAPromotionStableStreak;
        boolean shadowPhaseAPromotionReadyLastFrame;
        int shadowPhaseDPromotionReadyMinFrames;
        int shadowPhaseDPromotionStableStreak;
        boolean shadowPhaseDPromotionReadyLastFrame;
    }

    private static State stateFrom(Object runtime) {
        State state = new State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, state);
        return state;
    }

    static ShadowCapabilityDiagnostics capability(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.capability(
                state.shadowCapabilityFeatureIdLastFrame,
                state.shadowCapabilityModeLastFrame,
                state.shadowCapabilitySignalsLastFrame
        );
    }

    static ShadowCadenceDiagnostics cadence(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.cadence(
                capability(runtime).available(),
                state.shadowCadenceSelectedLocalLightsLastFrame,
                state.shadowCadenceDeferredLocalLightsLastFrame,
                state.shadowCadenceStaleBypassCountLastFrame,
                state.shadowCadenceDeferredRatioLastFrame,
                state.shadowCadenceWarnDeferredRatioMax,
                state.shadowCadenceWarnMinFrames,
                state.shadowCadenceWarnCooldownFrames,
                state.shadowCadenceHighStreak,
                state.shadowCadenceWarnCooldownRemaining,
                state.shadowCadenceStableStreak,
                state.shadowCadencePromotionReadyMinFrames,
                state.shadowCadencePromotionReadyLastFrame,
                state.shadowCadenceEnvelopeBreachedLastFrame
        );
    }

    static ShadowPointBudgetDiagnostics pointBudget(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.pointBudget(
                capability(runtime).available(),
                state.shadowMaxFacesPerFrame,
                state.shadowPointBudgetRenderedCubemapsLastFrame,
                state.shadowPointBudgetRenderedFacesLastFrame,
                state.shadowPointBudgetDeferredCountLastFrame,
                state.shadowPointBudgetSaturationRatioLastFrame,
                state.shadowPointFaceBudgetWarnSaturationMin,
                state.shadowPointFaceBudgetWarnMinFrames,
                state.shadowPointFaceBudgetWarnCooldownFrames,
                state.shadowPointBudgetHighStreak,
                state.shadowPointBudgetWarnCooldownRemaining,
                state.shadowPointBudgetStableStreak,
                state.shadowPointFaceBudgetPromotionReadyMinFrames,
                state.shadowPointBudgetPromotionReadyLastFrame,
                state.shadowPointBudgetEnvelopeBreachedLastFrame
        );
    }

    static ShadowSpotProjectedDiagnostics spotProjected(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.spotProjected(
                capability(runtime).available(),
                state.shadowSpotProjectedRequestedLastFrame,
                state.shadowSpotProjectedActiveLastFrame,
                state.shadowSpotProjectedRenderedCountLastFrame,
                state.shadowSpotProjectedContractStatusLastFrame,
                state.shadowSpotProjectedContractBreachedLastFrame,
                state.shadowSpotProjectedStableStreak,
                state.shadowSpotProjectedPromotionReadyMinFrames,
                state.shadowSpotProjectedPromotionReadyLastFrame
        );
    }

    static ShadowCacheDiagnostics cache(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.cache(
                capability(runtime).available(),
                state.shadowCapabilityModeLastFrame,
                state.shadowCacheMissCountLastFrame,
                state.shadowCadenceDeferredLocalLightsLastFrame,
                state.shadowCacheHitCountLastFrame,
                state.shadowCacheEvictionCountLastFrame,
                state.shadowCacheHitRatioLastFrame,
                state.shadowCacheChurnRatioLastFrame,
                state.shadowCacheInvalidationReasonLastFrame,
                state.shadowCacheChurnWarnMax,
                state.shadowCacheMissWarnMax,
                state.shadowCacheWarnMinFrames,
                state.shadowCacheWarnCooldownFrames,
                state.shadowCacheHighStreak,
                state.shadowCacheWarnCooldownRemaining,
                state.shadowCacheEnvelopeBreachedLastFrame
        );
    }

    static ShadowRtDiagnostics rt(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.rt(
                capability(runtime).available(),
                state.currentShadows.rtShadowMode(),
                state.currentShadows.rtShadowActive(),
                VulkanShadowRuntimeTuning.effectiveShadowRtDenoiseStrength(
                        state.currentShadows.rtShadowMode(),
                        state.shadowRtDenoiseStrength,
                        state.shadowRtProductionDenoiseStrength,
                        state.shadowRtDedicatedDenoiseStrength
                ),
                state.shadowRtDenoiseWarnMin,
                VulkanShadowRuntimeTuning.effectiveShadowRtSampleCount(
                        state.currentShadows.rtShadowMode(),
                        state.shadowRtSampleCount,
                        state.shadowRtProductionSampleCount,
                        state.shadowRtDedicatedSampleCount
                ),
                state.shadowRtSampleWarnMin,
                state.shadowRtPerfGpuMsEstimateLastFrame,
                state.shadowRtPerfGpuMsWarnMaxLastFrame,
                state.shadowRtWarnMinFrames,
                state.shadowRtWarnCooldownFrames,
                state.shadowRtHighStreak,
                state.shadowRtWarnCooldownRemaining,
                state.shadowRtEnvelopeBreachedLastFrame
        );
    }

    static ShadowHybridDiagnostics hybrid(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.hybrid(
                capability(runtime).available(),
                state.shadowCapabilityModeLastFrame,
                state.shadowHybridCascadeShareLastFrame,
                state.shadowHybridContactShareLastFrame,
                state.shadowHybridRtShareLastFrame,
                state.shadowHybridRtShareWarnMin,
                state.shadowHybridContactShareWarnMin,
                state.shadowHybridWarnMinFrames,
                state.shadowHybridWarnCooldownFrames,
                state.shadowHybridHighStreak,
                state.shadowHybridWarnCooldownRemaining,
                state.shadowHybridEnvelopeBreachedLastFrame
        );
    }

    static ShadowTransparentReceiverDiagnostics transparentReceivers(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.transparentReceivers(
                capability(runtime).available(),
                state.shadowTransparentReceiversRequested,
                state.shadowTransparentReceiversSupported,
                state.shadowTransparentReceiverPolicyLastFrame,
                state.shadowTransparentReceiverCandidateCountLastFrame,
                state.shadowTransparentReceiverCandidateRatioLastFrame,
                state.shadowTransparentReceiverCandidateRatioWarnMax,
                state.shadowTransparentReceiverWarnMinFrames,
                state.shadowTransparentReceiverWarnCooldownFrames,
                state.shadowTransparentReceiverHighStreak,
                state.shadowTransparentReceiverWarnCooldownRemaining,
                state.shadowTransparentReceiverEnvelopeBreachedLastFrame
        );
    }

    static ShadowExtendedModeDiagnostics extendedModes(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.extendedModes(
                capability(runtime).available(),
                state.shadowAreaApproxRequested,
                state.shadowAreaApproxSupported,
                state.shadowAreaApproxBreachedLastFrame,
                state.shadowDistanceFieldRequested,
                state.shadowDistanceFieldSupported,
                state.shadowDistanceFieldBreachedLastFrame
        );
    }

    static ShadowTopologyDiagnostics topology(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.topology(
                capability(runtime).available(),
                state.shadowCadenceSelectedLocalLightsLastFrame,
                state.currentShadows.renderedLocalShadowLights(),
                state.shadowTopologyCandidateSpotLightsLastFrame,
                state.shadowSpotProjectedRenderedCountLastFrame,
                state.shadowTopologyCandidatePointLightsLastFrame,
                state.shadowPointBudgetRenderedCubemapsLastFrame,
                state.shadowTopologyLocalCoverageLastFrame,
                state.shadowTopologySpotCoverageLastFrame,
                state.shadowTopologyPointCoverageLastFrame,
                state.shadowTopologyLocalCoverageWarnMin,
                state.shadowTopologySpotCoverageWarnMin,
                state.shadowTopologyPointCoverageWarnMin,
                state.shadowTopologyWarnMinFrames,
                state.shadowTopologyWarnCooldownFrames,
                state.shadowTopologyHighStreak,
                state.shadowTopologyWarnCooldownRemaining,
                state.shadowTopologyStableStreak,
                state.shadowTopologyPromotionReadyMinFrames,
                state.shadowTopologyPromotionReadyLastFrame,
                state.shadowTopologyEnvelopeBreachedLastFrame
        );
    }

    static ShadowPhaseAPromotionDiagnostics phaseA(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.phaseA(
                capability(runtime).available(),
                state.shadowCadencePromotionReadyLastFrame,
                state.shadowPointBudgetPromotionReadyLastFrame,
                state.shadowSpotProjectedPromotionReadyLastFrame,
                state.shadowPhaseAPromotionReadyMinFrames,
                state.shadowPhaseAPromotionStableStreak,
                state.shadowPhaseAPromotionReadyLastFrame
        );
    }

    static ShadowPhaseDPromotionDiagnostics phaseD(Object runtime) {
        State state = stateFrom(runtime);
        return VulkanShadowDiagnosticsMapper.phaseD(
                capability(runtime).available(),
                state.shadowCacheEnvelopeBreachedLastFrame,
                state.shadowCacheWarnCooldownRemaining,
                state.shadowRtEnvelopeBreachedLastFrame,
                state.shadowRtWarnCooldownRemaining,
                state.shadowHybridEnvelopeBreachedLastFrame,
                state.shadowHybridWarnCooldownRemaining,
                state.shadowTransparentReceiverEnvelopeBreachedLastFrame,
                state.shadowTransparentReceiverWarnCooldownRemaining,
                state.shadowAreaApproxBreachedLastFrame,
                state.shadowDistanceFieldBreachedLastFrame,
                state.shadowPhaseDPromotionReadyMinFrames,
                state.shadowPhaseDPromotionStableStreak,
                state.shadowPhaseDPromotionReadyLastFrame
        );
    }

    private VulkanShadowBackendDiagnosticsBridge() {
    }
}
