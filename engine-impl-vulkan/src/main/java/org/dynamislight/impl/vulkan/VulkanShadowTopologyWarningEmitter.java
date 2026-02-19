package org.dynamislight.impl.vulkan;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.LightType;

final class VulkanShadowTopologyWarningEmitter {
    static final class State {
        List<LightDesc> currentSceneLights;
        int shadowCadenceSelectedLocalLightsLastFrame;
        int shadowSpotProjectedRenderedCountLastFrame;
        int shadowPointBudgetRenderedCubemapsLastFrame;
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
        int shadowTopologyWarnCooldownRemaining;
        int shadowTopologyHighStreak;
        int shadowTopologyStableStreak;
        int shadowTopologyPromotionReadyMinFrames;
        boolean shadowTopologyPromotionReadyLastFrame;
        boolean shadowTopologyEnvelopeBreachedLastFrame;
        ShadowRenderConfig currentShadows;
    }

    static void emit(List<EngineWarning> warnings, State state) {
        int candidateSpotLights = 0;
        int candidatePointLights = 0;
        if (state.currentSceneLights != null) {
            for (LightDesc light : state.currentSceneLights) {
                if (light == null || !light.castsShadows() || light.shadow() == null) {
                    continue;
                }
                if (light.type() == LightType.SPOT) {
                    candidateSpotLights++;
                } else if (light.type() == LightType.POINT) {
                    candidatePointLights++;
                }
            }
        }
        state.shadowTopologyCandidateSpotLightsLastFrame = candidateSpotLights;
        state.shadowTopologyCandidatePointLightsLastFrame = candidatePointLights;
        int renderedLocalLights = Math.max(0, state.currentShadows.renderedLocalShadowLights());
        state.shadowTopologyLocalCoverageLastFrame = state.shadowCadenceSelectedLocalLightsLastFrame <= 0
                ? 1.0
                : Math.min(1.0, (double) renderedLocalLights / (double) state.shadowCadenceSelectedLocalLightsLastFrame);
        state.shadowTopologySpotCoverageLastFrame = candidateSpotLights <= 0
                ? 1.0
                : Math.min(1.0, (double) state.shadowSpotProjectedRenderedCountLastFrame / (double) candidateSpotLights);
        state.shadowTopologyPointCoverageLastFrame = candidatePointLights <= 0
                ? 1.0
                : Math.min(1.0, (double) state.shadowPointBudgetRenderedCubemapsLastFrame / (double) candidatePointLights);
        boolean topologyEnvelopeNow = state.shadowTopologyLocalCoverageLastFrame < state.shadowTopologyLocalCoverageWarnMin
                || state.shadowTopologySpotCoverageLastFrame < state.shadowTopologySpotCoverageWarnMin
                || state.shadowTopologyPointCoverageLastFrame < state.shadowTopologyPointCoverageWarnMin;
        if (topologyEnvelopeNow) {
            state.shadowTopologyHighStreak = Math.min(10_000, state.shadowTopologyHighStreak + 1);
            state.shadowTopologyStableStreak = 0;
            state.shadowTopologyPromotionReadyLastFrame = false;
            state.shadowTopologyEnvelopeBreachedLastFrame = true;
        } else {
            state.shadowTopologyHighStreak = 0;
            state.shadowTopologyStableStreak = Math.min(10_000, state.shadowTopologyStableStreak + 1);
            state.shadowTopologyPromotionReadyLastFrame = state.shadowTopologyStableStreak >= state.shadowTopologyPromotionReadyMinFrames;
        }
        warnings.add(new EngineWarning(
                "SHADOW_TOPOLOGY_CONTRACT",
                "Shadow topology contract (selectedLocal=" + state.shadowCadenceSelectedLocalLightsLastFrame
                        + ", renderedLocal=" + renderedLocalLights
                        + ", candidateSpot=" + candidateSpotLights
                        + ", renderedSpot=" + state.shadowSpotProjectedRenderedCountLastFrame
                        + ", candidatePoint=" + candidatePointLights
                        + ", renderedPointCubemaps=" + state.shadowPointBudgetRenderedCubemapsLastFrame
                        + ", localCoverage=" + state.shadowTopologyLocalCoverageLastFrame
                        + ", spotCoverage=" + state.shadowTopologySpotCoverageLastFrame
                        + ", pointCoverage=" + state.shadowTopologyPointCoverageLastFrame
                        + ", localCoverageWarnMin=" + state.shadowTopologyLocalCoverageWarnMin
                        + ", spotCoverageWarnMin=" + state.shadowTopologySpotCoverageWarnMin
                        + ", pointCoverageWarnMin=" + state.shadowTopologyPointCoverageWarnMin
                        + ", warnMinFrames=" + state.shadowTopologyWarnMinFrames
                        + ", cooldownRemaining=" + state.shadowTopologyWarnCooldownRemaining
                        + ", stableStreak=" + state.shadowTopologyStableStreak
                        + ", promotionReadyMinFrames=" + state.shadowTopologyPromotionReadyMinFrames
                        + ", promotionReady=" + state.shadowTopologyPromotionReadyLastFrame + ")"
        ));
        if (topologyEnvelopeNow
                && state.shadowTopologyHighStreak >= state.shadowTopologyWarnMinFrames
                && state.shadowTopologyWarnCooldownRemaining == 0) {
            warnings.add(new EngineWarning(
                    "SHADOW_TOPOLOGY_CONTRACT_BREACH",
                    "Shadow topology contract breached (localCoverage=" + state.shadowTopologyLocalCoverageLastFrame
                            + ", spotCoverage=" + state.shadowTopologySpotCoverageLastFrame
                            + ", pointCoverage=" + state.shadowTopologyPointCoverageLastFrame
                            + ", localCoverageWarnMin=" + state.shadowTopologyLocalCoverageWarnMin
                            + ", spotCoverageWarnMin=" + state.shadowTopologySpotCoverageWarnMin
                            + ", pointCoverageWarnMin=" + state.shadowTopologyPointCoverageWarnMin
                            + ", highStreak=" + state.shadowTopologyHighStreak
                            + ", warnMinFrames=" + state.shadowTopologyWarnMinFrames
                            + ", cooldownFrames=" + state.shadowTopologyWarnCooldownFrames + ")"
            ));
            state.shadowTopologyWarnCooldownRemaining = state.shadowTopologyWarnCooldownFrames;
        }
        if (state.shadowTopologyPromotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "SHADOW_TOPOLOGY_PROMOTION_READY",
                    "Shadow topology promotion-ready (localCoverage=" + state.shadowTopologyLocalCoverageLastFrame
                            + ", spotCoverage=" + state.shadowTopologySpotCoverageLastFrame
                            + ", pointCoverage=" + state.shadowTopologyPointCoverageLastFrame
                            + ", stableStreak=" + state.shadowTopologyStableStreak
                            + ", promotionReadyMinFrames=" + state.shadowTopologyPromotionReadyMinFrames + ")"
            ));
        }
    }

    private VulkanShadowTopologyWarningEmitter() {
    }
}
