package org.dynamislight.impl.vulkan;

import org.dynamislight.impl.vulkan.runtime.model.*;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.MaterialDesc;

final class VulkanShadowCacheHybridExtendedWarningEmitter {
    static final class State {
        String shadowCapabilityModeLastFrame;
        int shadowAllocatorReusedAssignments;
        int shadowAllocatorEvictions;
        int shadowCadenceSelectedLocalLightsLastFrame;
        int shadowCadenceDeferredLocalLightsLastFrame;
        double shadowCacheChurnWarnMax;
        int shadowCacheMissWarnMax;
        int shadowCacheWarnMinFrames;
        int shadowCacheWarnCooldownFrames;
        int shadowCacheWarnCooldownRemaining;
        int shadowCacheHighStreak;
        boolean shadowCacheEnvelopeBreachedLastFrame;
        int shadowCacheHitCountLastFrame;
        int shadowCacheMissCountLastFrame;
        int shadowCacheEvictionCountLastFrame;
        double shadowCacheHitRatioLastFrame;
        double shadowCacheChurnRatioLastFrame;
        String shadowCacheInvalidationReasonLastFrame;

        ShadowRenderConfig currentShadows;
        double shadowHybridRtShareWarnMin;
        double shadowHybridContactShareWarnMin;
        int shadowHybridWarnMinFrames;
        int shadowHybridWarnCooldownFrames;
        int shadowHybridWarnCooldownRemaining;
        int shadowHybridHighStreak;
        boolean shadowHybridEnvelopeBreachedLastFrame;
        double shadowHybridCascadeShareLastFrame;
        double shadowHybridContactShareLastFrame;
        double shadowHybridRtShareLastFrame;

        List<MaterialDesc> currentSceneMaterials;
        boolean shadowTransparentReceiversRequested;
        boolean shadowTransparentReceiversSupported;
        double shadowTransparentReceiverCandidateRatioWarnMax;
        int shadowTransparentReceiverWarnMinFrames;
        int shadowTransparentReceiverWarnCooldownFrames;
        int shadowTransparentReceiverWarnCooldownRemaining;
        int shadowTransparentReceiverHighStreak;
        boolean shadowTransparentReceiverEnvelopeBreachedLastFrame;
        int shadowTransparentReceiverCandidateCountLastFrame;
        double shadowTransparentReceiverCandidateRatioLastFrame;
        String shadowTransparentReceiverPolicyLastFrame;

        boolean shadowAreaApproxRequested;
        boolean shadowAreaApproxSupported;
        boolean shadowAreaApproxRequireActive;
        boolean shadowAreaApproxBreachedLastFrame;

        boolean shadowDistanceFieldRequested;
        boolean shadowDistanceFieldSupported;
        boolean shadowDistanceFieldRequireActive;
        boolean shadowDistanceFieldBreachedLastFrame;

        boolean shadowRtEnvelopeBreachedLastFrame;
        int shadowRtWarnCooldownRemaining;
        int shadowPhaseDPromotionStableStreak;
        boolean shadowPhaseDPromotionReadyLastFrame;
        int shadowPhaseDPromotionReadyMinFrames;
    }

    static void emit(List<EngineWarning> warnings, State state) {
        state.shadowCacheHitCountLastFrame = Math.max(0, state.shadowAllocatorReusedAssignments);
        state.shadowCacheMissCountLastFrame = Math.max(0,
                state.shadowCadenceSelectedLocalLightsLastFrame - state.shadowCacheHitCountLastFrame);
        state.shadowCacheEvictionCountLastFrame = Math.max(0, state.shadowAllocatorEvictions);
        int cacheTotalOps = state.shadowCacheHitCountLastFrame + state.shadowCacheMissCountLastFrame;
        state.shadowCacheHitRatioLastFrame = cacheTotalOps <= 0
                ? 1.0
                : (double) state.shadowCacheHitCountLastFrame / (double) cacheTotalOps;
        state.shadowCacheChurnRatioLastFrame = Math.min(1.0,
                (double) state.shadowCacheEvictionCountLastFrame
                        / (double) Math.max(1, state.shadowCadenceSelectedLocalLightsLastFrame));
        if (state.shadowCacheEvictionCountLastFrame > 0) {
            state.shadowCacheInvalidationReasonLastFrame = "atlas_eviction";
        } else if (state.shadowCacheMissCountLastFrame > 0) {
            state.shadowCacheInvalidationReasonLastFrame = "new_assignment";
        } else if (state.shadowCadenceDeferredLocalLightsLastFrame > 0) {
            state.shadowCacheInvalidationReasonLastFrame = "deferred_overlay";
        } else {
            state.shadowCacheInvalidationReasonLastFrame = "none";
        }
        boolean cacheEnvelopeNow = state.shadowCacheChurnRatioLastFrame > state.shadowCacheChurnWarnMax
                || state.shadowCacheMissCountLastFrame > state.shadowCacheMissWarnMax;
        if (cacheEnvelopeNow) {
            state.shadowCacheHighStreak = Math.min(10_000, state.shadowCacheHighStreak + 1);
            state.shadowCacheEnvelopeBreachedLastFrame = true;
        } else {
            state.shadowCacheHighStreak = 0;
        }
        warnings.add(new EngineWarning(
                "SHADOW_CACHE_POLICY_ACTIVE",
                "Shadow cache policy (mode=" + state.shadowCapabilityModeLastFrame
                        + ", staticCacheActive=" + "cached_static_dynamic".equals(state.shadowCapabilityModeLastFrame)
                        + ", dynamicOverlayActive=" + (state.shadowCacheMissCountLastFrame > 0 || state.shadowCadenceDeferredLocalLightsLastFrame > 0)
                        + ", cacheHitCount=" + state.shadowCacheHitCountLastFrame
                        + ", cacheMissCount=" + state.shadowCacheMissCountLastFrame
                        + ", cacheEvictions=" + state.shadowCacheEvictionCountLastFrame
                        + ", cacheHitRatio=" + state.shadowCacheHitRatioLastFrame
                        + ", churnRatio=" + state.shadowCacheChurnRatioLastFrame
                        + ", invalidationReason=" + state.shadowCacheInvalidationReasonLastFrame
                        + ", churnWarnMax=" + state.shadowCacheChurnWarnMax
                        + ", missWarnMax=" + state.shadowCacheMissWarnMax
                        + ", warnMinFrames=" + state.shadowCacheWarnMinFrames
                        + ", cooldownRemaining=" + state.shadowCacheWarnCooldownRemaining + ")"
        ));
        if (cacheEnvelopeNow
                && state.shadowCacheHighStreak >= state.shadowCacheWarnMinFrames
                && state.shadowCacheWarnCooldownRemaining == 0) {
            warnings.add(new EngineWarning(
                    "SHADOW_CACHE_CHURN_HIGH",
                    "Shadow cache envelope breached (cacheMissCount=" + state.shadowCacheMissCountLastFrame
                            + ", cacheEvictions=" + state.shadowCacheEvictionCountLastFrame
                            + ", cacheHitRatio=" + state.shadowCacheHitRatioLastFrame
                            + ", churnRatio=" + state.shadowCacheChurnRatioLastFrame
                            + ", churnWarnMax=" + state.shadowCacheChurnWarnMax
                            + ", missWarnMax=" + state.shadowCacheMissWarnMax
                            + ", highStreak=" + state.shadowCacheHighStreak
                            + ", warnMinFrames=" + state.shadowCacheWarnMinFrames
                            + ", cooldownFrames=" + state.shadowCacheWarnCooldownFrames
                            + ", invalidationReason=" + state.shadowCacheInvalidationReasonLastFrame + ")"
            ));
            state.shadowCacheWarnCooldownRemaining = state.shadowCacheWarnCooldownFrames;
        }

        boolean hybridModeActive = "hybrid_cascade_contact_rt".equals(state.shadowCapabilityModeLastFrame);
        double cascadeWeight = 1.0;
        double contactWeight = state.currentShadows.contactShadowsRequested() ? 0.6 : 0.0;
        double rtWeight = "off".equals(state.currentShadows.rtShadowMode()) ? 0.0 : (state.currentShadows.rtShadowActive() ? 0.8 : 0.2);
        double hybridWeightTotal = Math.max(1e-6, cascadeWeight + contactWeight + rtWeight);
        state.shadowHybridCascadeShareLastFrame = cascadeWeight / hybridWeightTotal;
        state.shadowHybridContactShareLastFrame = contactWeight / hybridWeightTotal;
        state.shadowHybridRtShareLastFrame = rtWeight / hybridWeightTotal;
        boolean hybridEnvelopeNow = hybridModeActive
                && (state.shadowHybridRtShareLastFrame < state.shadowHybridRtShareWarnMin
                || state.shadowHybridContactShareLastFrame < state.shadowHybridContactShareWarnMin);
        if (hybridEnvelopeNow) {
            state.shadowHybridHighStreak = Math.min(10_000, state.shadowHybridHighStreak + 1);
            state.shadowHybridEnvelopeBreachedLastFrame = true;
        } else {
            state.shadowHybridHighStreak = 0;
        }
        warnings.add(new EngineWarning(
                "SHADOW_HYBRID_COMPOSITION",
                "Shadow hybrid composition (modeActive=" + hybridModeActive
                        + ", cascadeShare=" + state.shadowHybridCascadeShareLastFrame
                        + ", contactShare=" + state.shadowHybridContactShareLastFrame
                        + ", rtShare=" + state.shadowHybridRtShareLastFrame
                        + ", rtShareWarnMin=" + state.shadowHybridRtShareWarnMin
                        + ", contactShareWarnMin=" + state.shadowHybridContactShareWarnMin
                        + ", warnMinFrames=" + state.shadowHybridWarnMinFrames
                        + ", cooldownRemaining=" + state.shadowHybridWarnCooldownRemaining + ")"
        ));
        if (hybridEnvelopeNow
                && state.shadowHybridHighStreak >= state.shadowHybridWarnMinFrames
                && state.shadowHybridWarnCooldownRemaining == 0) {
            warnings.add(new EngineWarning(
                    "SHADOW_HYBRID_COMPOSITION_BREACH",
                    "Shadow hybrid composition envelope breached (cascadeShare=" + state.shadowHybridCascadeShareLastFrame
                            + ", contactShare=" + state.shadowHybridContactShareLastFrame
                            + ", rtShare=" + state.shadowHybridRtShareLastFrame
                            + ", rtShareWarnMin=" + state.shadowHybridRtShareWarnMin
                            + ", contactShareWarnMin=" + state.shadowHybridContactShareWarnMin
                            + ", highStreak=" + state.shadowHybridHighStreak
                            + ", warnMinFrames=" + state.shadowHybridWarnMinFrames
                            + ", cooldownFrames=" + state.shadowHybridWarnCooldownFrames + ")"
            ));
            state.shadowHybridWarnCooldownRemaining = state.shadowHybridWarnCooldownFrames;
        }

        int transparentCandidateCount = 0;
        if (state.currentSceneMaterials != null) {
            for (MaterialDesc material : state.currentSceneMaterials) {
                if (material != null && material.alphaTested()) {
                    transparentCandidateCount++;
                }
            }
        }
        state.shadowTransparentReceiverCandidateCountLastFrame = transparentCandidateCount;
        state.shadowTransparentReceiverCandidateRatioLastFrame = state.currentSceneMaterials == null || state.currentSceneMaterials.isEmpty()
                ? 0.0
                : (double) transparentCandidateCount / (double) state.currentSceneMaterials.size();
        state.shadowTransparentReceiverPolicyLastFrame = state.shadowTransparentReceiversRequested
                ? (state.shadowTransparentReceiversSupported ? "enabled" : "fallback_opaque_only")
                : "disabled";
        boolean transparentEnvelopeNow = state.shadowTransparentReceiversRequested
                && !state.shadowTransparentReceiversSupported
                && state.shadowTransparentReceiverCandidateRatioLastFrame > state.shadowTransparentReceiverCandidateRatioWarnMax;
        if (transparentEnvelopeNow) {
            state.shadowTransparentReceiverHighStreak = Math.min(10_000, state.shadowTransparentReceiverHighStreak + 1);
            state.shadowTransparentReceiverEnvelopeBreachedLastFrame = true;
        } else {
            state.shadowTransparentReceiverHighStreak = 0;
        }
        warnings.add(new EngineWarning(
                "SHADOW_TRANSPARENT_RECEIVER_POLICY",
                "Shadow transparent receiver policy (requested=" + state.shadowTransparentReceiversRequested
                        + ", supported=" + state.shadowTransparentReceiversSupported
                        + ", activePolicy=" + state.shadowTransparentReceiverPolicyLastFrame
                        + ", candidateMaterials=" + state.shadowTransparentReceiverCandidateCountLastFrame
                        + ", candidateRatio=" + state.shadowTransparentReceiverCandidateRatioLastFrame
                        + ", candidateRatioWarnMax=" + state.shadowTransparentReceiverCandidateRatioWarnMax
                        + ", warnMinFrames=" + state.shadowTransparentReceiverWarnMinFrames
                        + ", cooldownRemaining=" + state.shadowTransparentReceiverWarnCooldownRemaining + ")"
        ));
        if (transparentEnvelopeNow
                && state.shadowTransparentReceiverHighStreak >= state.shadowTransparentReceiverWarnMinFrames
                && state.shadowTransparentReceiverWarnCooldownRemaining == 0) {
            warnings.add(new EngineWarning(
                    "SHADOW_TRANSPARENT_RECEIVER_ENVELOPE_BREACH",
                    "Shadow transparent receiver envelope breached (requested=true, supported=false"
                            + ", candidateMaterials=" + state.shadowTransparentReceiverCandidateCountLastFrame
                            + ", candidateRatio=" + state.shadowTransparentReceiverCandidateRatioLastFrame
                            + ", candidateRatioWarnMax=" + state.shadowTransparentReceiverCandidateRatioWarnMax
                            + ", highStreak=" + state.shadowTransparentReceiverHighStreak
                            + ", warnMinFrames=" + state.shadowTransparentReceiverWarnMinFrames
                            + ", cooldownFrames=" + state.shadowTransparentReceiverWarnCooldownFrames + ")"
            ));
            state.shadowTransparentReceiverWarnCooldownRemaining = state.shadowTransparentReceiverWarnCooldownFrames;
        }

        warnings.add(new EngineWarning(
                "SHADOW_AREA_APPROX_POLICY",
                "Shadow area-approx policy (requested=" + state.shadowAreaApproxRequested
                        + ", supported=" + state.shadowAreaApproxSupported
                        + ", activePolicy=" + (state.shadowAreaApproxSupported ? "enabled" : "fallback_standard_shadow")
                        + ", requireActive=" + state.shadowAreaApproxRequireActive + ")"
        ));
        state.shadowAreaApproxBreachedLastFrame = state.shadowAreaApproxRequested
                && state.shadowAreaApproxRequireActive
                && !state.shadowAreaApproxSupported;
        if (state.shadowAreaApproxBreachedLastFrame) {
            warnings.add(new EngineWarning(
                    "SHADOW_AREA_APPROX_REQUIRED_UNAVAILABLE_BREACH",
                    "Shadow area-approx required but unavailable (requested=true, supported=false)"
            ));
        }

        warnings.add(new EngineWarning(
                "SHADOW_DISTANCE_FIELD_SOFT_POLICY",
                "Shadow distance-field policy (requested=" + state.shadowDistanceFieldRequested
                        + ", supported=" + state.shadowDistanceFieldSupported
                        + ", activePolicy=" + (state.shadowDistanceFieldSupported ? "enabled" : "fallback_standard_shadow")
                        + ", requireActive=" + state.shadowDistanceFieldRequireActive + ")"
        ));
        state.shadowDistanceFieldBreachedLastFrame = state.shadowDistanceFieldRequested
                && state.shadowDistanceFieldRequireActive
                && !state.shadowDistanceFieldSupported;
        if (state.shadowDistanceFieldBreachedLastFrame) {
            warnings.add(new EngineWarning(
                    "SHADOW_DISTANCE_FIELD_REQUIRED_UNAVAILABLE_BREACH",
                    "Shadow distance-field soft shadows required but unavailable (requested=true, supported=false)"
            ));
        }

        boolean phaseDPromotionNow =
                !state.shadowCacheEnvelopeBreachedLastFrame
                        && state.shadowCacheWarnCooldownRemaining == 0
                        && !state.shadowRtEnvelopeBreachedLastFrame
                        && state.shadowRtWarnCooldownRemaining == 0
                        && !state.shadowHybridEnvelopeBreachedLastFrame
                        && state.shadowHybridWarnCooldownRemaining == 0
                        && !state.shadowTransparentReceiverEnvelopeBreachedLastFrame
                        && state.shadowTransparentReceiverWarnCooldownRemaining == 0
                        && !state.shadowAreaApproxBreachedLastFrame
                        && !state.shadowDistanceFieldBreachedLastFrame;
        if (phaseDPromotionNow) {
            state.shadowPhaseDPromotionStableStreak = Math.min(10_000, state.shadowPhaseDPromotionStableStreak + 1);
            state.shadowPhaseDPromotionReadyLastFrame =
                    state.shadowPhaseDPromotionStableStreak >= state.shadowPhaseDPromotionReadyMinFrames;
        } else {
            state.shadowPhaseDPromotionStableStreak = 0;
            state.shadowPhaseDPromotionReadyLastFrame = false;
        }
        if (state.shadowPhaseDPromotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "SHADOW_PHASED_PROMOTION_READY",
                    "Shadow Phase D promotion-ready (cacheStable=" + (!state.shadowCacheEnvelopeBreachedLastFrame
                            && state.shadowCacheWarnCooldownRemaining == 0)
                            + ", rtStable=" + (!state.shadowRtEnvelopeBreachedLastFrame
                            && state.shadowRtWarnCooldownRemaining == 0)
                            + ", hybridStable=" + (!state.shadowHybridEnvelopeBreachedLastFrame
                            && state.shadowHybridWarnCooldownRemaining == 0)
                            + ", transparentReceiverStable=" + (!state.shadowTransparentReceiverEnvelopeBreachedLastFrame
                            && state.shadowTransparentReceiverWarnCooldownRemaining == 0)
                            + ", areaApproxStable=" + (!state.shadowAreaApproxBreachedLastFrame)
                            + ", distanceFieldStable=" + (!state.shadowDistanceFieldBreachedLastFrame)
                            + ", stableStreak=" + state.shadowPhaseDPromotionStableStreak
                            + ", promotionReadyMinFrames=" + state.shadowPhaseDPromotionReadyMinFrames + ")"
            ));
        }
    }

    private VulkanShadowCacheHybridExtendedWarningEmitter() {
    }
}
