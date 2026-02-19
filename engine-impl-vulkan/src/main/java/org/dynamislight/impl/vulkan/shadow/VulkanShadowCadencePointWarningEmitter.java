package org.dynamislight.impl.vulkan.shadow;

import org.dynamislight.impl.vulkan.runtime.model.*;

import org.dynamislight.impl.vulkan.runtime.model.*;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;

public final class VulkanShadowCadencePointWarningEmitter {
    public static final class State {
        public ShadowRenderConfig currentShadows;
        public boolean shadowSpotProjectedRequestedLastFrame;
        public boolean shadowSpotProjectedActiveLastFrame;
        public int shadowSpotProjectedRenderedCountLastFrame;
        public String shadowSpotProjectedContractStatusLastFrame;
        public boolean shadowSpotProjectedContractBreachedLastFrame;
        public int shadowSpotProjectedStableStreak;
        public boolean shadowSpotProjectedPromotionReadyLastFrame;
        public int shadowSpotProjectedPromotionReadyMinFrames;

        public int shadowCadenceSelectedLocalLightsLastFrame;
        public int shadowCadenceDeferredLocalLightsLastFrame;
        public int shadowCadenceStaleBypassCountLastFrame;
        public double shadowCadenceDeferredRatioLastFrame;
        public double shadowCadenceWarnDeferredRatioMax;
        public int shadowCadenceHighStreak;
        public int shadowCadenceWarnMinFrames;
        public int shadowCadenceWarnCooldownFrames;
        public int shadowCadenceWarnCooldownRemaining;
        public int shadowCadenceStableStreak;
        public int shadowCadencePromotionReadyMinFrames;
        public boolean shadowCadencePromotionReadyLastFrame;
        public boolean cadenceEnvelopeNow;

        public int shadowPointBudgetRenderedCubemapsLastFrame;
        public int shadowPointBudgetRenderedFacesLastFrame;
        public int shadowPointBudgetDeferredCountLastFrame;
        public double shadowPointBudgetSaturationRatioLastFrame;
        public double shadowPointFaceBudgetWarnSaturationMin;
        public int shadowPointBudgetHighStreak;
        public int shadowPointBudgetWarnCooldownRemaining;
        public int shadowPointFaceBudgetWarnMinFrames;
        public int shadowPointFaceBudgetWarnCooldownFrames;
        public int shadowPointBudgetStableStreak;
        public int shadowPointFaceBudgetPromotionReadyMinFrames;
        public boolean shadowPointBudgetPromotionReadyLastFrame;
        public boolean shadowPointBudgetEnvelopeBreachedLastFrame;
        public int shadowMaxFacesPerFrame;

        public int shadowPhaseAPromotionStableStreak;
        public boolean shadowPhaseAPromotionReadyLastFrame;
        public int shadowPhaseAPromotionReadyMinFrames;
    }

    public static void emit(List<EngineWarning> warnings, State state) {
        warnings.add(new EngineWarning(
                "SHADOW_SPOT_PROJECTED_CONTRACT",
                "Shadow spot projected contract (requested="
                        + state.shadowSpotProjectedRequestedLastFrame
                        + ", active=" + state.shadowSpotProjectedActiveLastFrame
                        + ", renderedSpotShadows=" + state.shadowSpotProjectedRenderedCountLastFrame
                        + ", status=" + state.shadowSpotProjectedContractStatusLastFrame + ")"
        ));
        if (state.shadowSpotProjectedContractBreachedLastFrame) {
            warnings.add(new EngineWarning(
                    "SHADOW_SPOT_PROJECTED_CONTRACT_BREACH",
                    "Shadow spot projected contract breached (requested=true, renderedSpotShadows="
                            + state.shadowSpotProjectedRenderedCountLastFrame
                            + ", status=" + state.shadowSpotProjectedContractStatusLastFrame + ")"
            ));
        }
        if (state.shadowSpotProjectedPromotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "SHADOW_SPOT_PROJECTED_PROMOTION_READY",
                    "Shadow spot projected promotion-ready (status=" + state.shadowSpotProjectedContractStatusLastFrame
                            + ", renderedSpotShadows=" + state.shadowSpotProjectedRenderedCountLastFrame
                            + ", stableStreak=" + state.shadowSpotProjectedStableStreak
                            + ", promotionReadyMinFrames=" + state.shadowSpotProjectedPromotionReadyMinFrames + ")"
            ));
        }
        warnings.add(new EngineWarning(
                "SHADOW_CADENCE_ENVELOPE",
                "Shadow cadence envelope (selectedLocal="
                        + state.shadowCadenceSelectedLocalLightsLastFrame
                        + ", deferredLocal=" + state.shadowCadenceDeferredLocalLightsLastFrame
                        + ", staleBypass=" + state.shadowCadenceStaleBypassCountLastFrame
                        + ", deferredRatio=" + state.shadowCadenceDeferredRatioLastFrame
                        + ", deferredRatioWarnMax=" + state.shadowCadenceWarnDeferredRatioMax
                        + ", highStreak=" + state.shadowCadenceHighStreak
                        + ", warnMinFrames=" + state.shadowCadenceWarnMinFrames
                        + ", cooldownRemaining=" + state.shadowCadenceWarnCooldownRemaining
                        + ", stableStreak=" + state.shadowCadenceStableStreak
                        + ", promotionReadyMinFrames=" + state.shadowCadencePromotionReadyMinFrames
                        + ", promotionReady=" + state.shadowCadencePromotionReadyLastFrame + ")"
        ));
        if (state.cadenceEnvelopeNow
                && state.shadowCadenceHighStreak >= state.shadowCadenceWarnMinFrames
                && state.shadowCadenceWarnCooldownRemaining == 0) {
            warnings.add(new EngineWarning(
                    "SHADOW_CADENCE_ENVELOPE_BREACH",
                    "Shadow cadence deferred-ratio envelope breached (selectedLocal="
                            + state.shadowCadenceSelectedLocalLightsLastFrame
                            + ", deferredLocal=" + state.shadowCadenceDeferredLocalLightsLastFrame
                            + ", deferredRatio=" + state.shadowCadenceDeferredRatioLastFrame
                            + ", deferredRatioWarnMax=" + state.shadowCadenceWarnDeferredRatioMax
                            + ", highStreak=" + state.shadowCadenceHighStreak
                            + ", warnMinFrames=" + state.shadowCadenceWarnMinFrames
                            + ", cooldownFrames=" + state.shadowCadenceWarnCooldownFrames + ")"
            ));
            state.shadowCadenceWarnCooldownRemaining = state.shadowCadenceWarnCooldownFrames;
        }
        if (state.shadowCadencePromotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "SHADOW_CADENCE_PROMOTION_READY",
                    "Shadow cadence promotion-ready (selectedLocal=" + state.shadowCadenceSelectedLocalLightsLastFrame
                            + ", deferredLocal=" + state.shadowCadenceDeferredLocalLightsLastFrame
                            + ", deferredRatio=" + state.shadowCadenceDeferredRatioLastFrame
                            + ", stableStreak=" + state.shadowCadenceStableStreak
                            + ", promotionReadyMinFrames=" + state.shadowCadencePromotionReadyMinFrames + ")"
            ));
        }
        state.shadowPointBudgetRenderedCubemapsLastFrame = state.currentShadows.renderedPointShadowCubemaps();
        state.shadowPointBudgetRenderedFacesLastFrame = state.shadowPointBudgetRenderedCubemapsLastFrame * 6;
        state.shadowPointBudgetDeferredCountLastFrame = state.currentShadows.deferredShadowLightCount();
        int pointFaceBudgetConfigured = Math.max(0, state.shadowMaxFacesPerFrame);
        state.shadowPointBudgetSaturationRatioLastFrame = pointFaceBudgetConfigured <= 0
                ? 0.0
                : Math.min(1.0, (double) state.shadowPointBudgetRenderedFacesLastFrame / (double) pointFaceBudgetConfigured);
        boolean pointBudgetEnvelopeNow = pointFaceBudgetConfigured > 0
                && state.shadowPointBudgetRenderedFacesLastFrame > 0
                && state.shadowPointBudgetSaturationRatioLastFrame >= state.shadowPointFaceBudgetWarnSaturationMin
                && state.shadowPointBudgetDeferredCountLastFrame > 0;
        if (pointBudgetEnvelopeNow) {
            state.shadowPointBudgetHighStreak = Math.min(10_000, state.shadowPointBudgetHighStreak + 1);
            state.shadowPointBudgetStableStreak = 0;
            state.shadowPointBudgetPromotionReadyLastFrame = false;
            state.shadowPointBudgetEnvelopeBreachedLastFrame = true;
        } else {
            state.shadowPointBudgetHighStreak = 0;
            state.shadowPointBudgetStableStreak = Math.min(10_000, state.shadowPointBudgetStableStreak + 1);
            state.shadowPointBudgetPromotionReadyLastFrame =
                    state.shadowPointBudgetStableStreak >= state.shadowPointFaceBudgetPromotionReadyMinFrames;
        }
        warnings.add(new EngineWarning(
                "SHADOW_POINT_FACE_BUDGET_ENVELOPE",
                "Shadow point face-budget envelope (configuredMaxFacesPerFrame="
                        + pointFaceBudgetConfigured
                        + ", renderedPointCubemaps=" + state.shadowPointBudgetRenderedCubemapsLastFrame
                        + ", renderedPointFaces=" + state.shadowPointBudgetRenderedFacesLastFrame
                        + ", deferredShadowLightCount=" + state.shadowPointBudgetDeferredCountLastFrame
                        + ", saturationRatio=" + state.shadowPointBudgetSaturationRatioLastFrame
                        + ", saturationWarnMin=" + state.shadowPointFaceBudgetWarnSaturationMin
                        + ", highStreak=" + state.shadowPointBudgetHighStreak
                        + ", warnMinFrames=" + state.shadowPointFaceBudgetWarnMinFrames
                        + ", cooldownRemaining=" + state.shadowPointBudgetWarnCooldownRemaining
                        + ", stableStreak=" + state.shadowPointBudgetStableStreak
                        + ", promotionReadyMinFrames=" + state.shadowPointFaceBudgetPromotionReadyMinFrames
                        + ", promotionReady=" + state.shadowPointBudgetPromotionReadyLastFrame + ")"
        ));
        if (pointBudgetEnvelopeNow
                && state.shadowPointBudgetHighStreak >= state.shadowPointFaceBudgetWarnMinFrames
                && state.shadowPointBudgetWarnCooldownRemaining == 0) {
            warnings.add(new EngineWarning(
                    "SHADOW_POINT_FACE_BUDGET_ENVELOPE_BREACH",
                    "Shadow point face-budget envelope breached (configuredMaxFacesPerFrame="
                            + pointFaceBudgetConfigured
                            + ", renderedPointFaces=" + state.shadowPointBudgetRenderedFacesLastFrame
                            + ", saturationRatio=" + state.shadowPointBudgetSaturationRatioLastFrame
                            + ", saturationWarnMin=" + state.shadowPointFaceBudgetWarnSaturationMin
                            + ", deferredShadowLightCount=" + state.shadowPointBudgetDeferredCountLastFrame
                            + ", highStreak=" + state.shadowPointBudgetHighStreak
                            + ", warnMinFrames=" + state.shadowPointFaceBudgetWarnMinFrames
                            + ", cooldownFrames=" + state.shadowPointFaceBudgetWarnCooldownFrames + ")"
            ));
            state.shadowPointBudgetWarnCooldownRemaining = state.shadowPointFaceBudgetWarnCooldownFrames;
        }
        if (state.shadowPointBudgetPromotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "SHADOW_POINT_FACE_BUDGET_PROMOTION_READY",
                    "Shadow point face-budget promotion-ready (configuredMaxFacesPerFrame="
                            + pointFaceBudgetConfigured
                            + ", renderedPointFaces=" + state.shadowPointBudgetRenderedFacesLastFrame
                            + ", deferredShadowLightCount=" + state.shadowPointBudgetDeferredCountLastFrame
                            + ", stableStreak=" + state.shadowPointBudgetStableStreak
                            + ", promotionReadyMinFrames=" + state.shadowPointFaceBudgetPromotionReadyMinFrames + ")"
            ));
        }
        boolean phaseAPromotionNow = state.shadowCadencePromotionReadyLastFrame
                && state.shadowPointBudgetPromotionReadyLastFrame
                && state.shadowSpotProjectedPromotionReadyLastFrame;
        if (phaseAPromotionNow) {
            state.shadowPhaseAPromotionStableStreak = Math.min(10_000, state.shadowPhaseAPromotionStableStreak + 1);
            state.shadowPhaseAPromotionReadyLastFrame =
                    state.shadowPhaseAPromotionStableStreak >= state.shadowPhaseAPromotionReadyMinFrames;
        } else {
            state.shadowPhaseAPromotionStableStreak = 0;
            state.shadowPhaseAPromotionReadyLastFrame = false;
        }
        if (state.shadowPhaseAPromotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "SHADOW_PHASEA_PROMOTION_READY",
                    "Shadow Phase A promotion-ready (cadenceReady=" + state.shadowCadencePromotionReadyLastFrame
                            + ", pointBudgetReady=" + state.shadowPointBudgetPromotionReadyLastFrame
                            + ", spotProjectedReady=" + state.shadowSpotProjectedPromotionReadyLastFrame
                            + ", stableStreak=" + state.shadowPhaseAPromotionStableStreak
                            + ", promotionReadyMinFrames=" + state.shadowPhaseAPromotionReadyMinFrames + ")"
            ));
        }
    }

    private VulkanShadowCadencePointWarningEmitter() {
    }
}
