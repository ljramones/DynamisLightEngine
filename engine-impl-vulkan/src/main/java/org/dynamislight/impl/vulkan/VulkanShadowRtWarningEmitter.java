package org.dynamislight.impl.vulkan;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;

final class VulkanShadowRtWarningEmitter {
    static final class State {
        ShadowRenderConfig currentShadows;
        QualityTier qualityTier;
        double lastFrameGpuMs;
        boolean shadowRtBvhSupported;
        boolean shadowRtTraversalSupported;
        float shadowRtDenoiseStrength;
        float shadowRtProductionDenoiseStrength;
        float shadowRtDedicatedDenoiseStrength;
        int shadowRtSampleCount;
        int shadowRtProductionSampleCount;
        int shadowRtDedicatedSampleCount;
        float shadowRtRayLength;
        float shadowRtProductionRayLength;
        float shadowRtDedicatedRayLength;
        double shadowRtDenoiseWarnMin;
        int shadowRtSampleWarnMin;
        double shadowRtPerfMaxGpuMsLow;
        double shadowRtPerfMaxGpuMsMedium;
        double shadowRtPerfMaxGpuMsHigh;
        double shadowRtPerfMaxGpuMsUltra;
        int shadowRtWarnMinFrames;
        int shadowRtWarnCooldownFrames;
        int shadowRtWarnCooldownRemaining;
        int shadowRtHighStreak;
        boolean shadowRtEnvelopeBreachedLastFrame;
        double shadowRtPerfGpuMsEstimateLastFrame;
        double shadowRtPerfGpuMsWarnMaxLastFrame;
    }

    static void emit(List<EngineWarning> warnings, State state) {
        if (state == null || state.currentShadows == null || "off".equals(state.currentShadows.rtShadowMode())) {
            return;
        }
        float rtEffectiveDenoise = VulkanShadowRuntimeTuning.effectiveShadowRtDenoiseStrength(
                state.currentShadows.rtShadowMode(),
                state.shadowRtDenoiseStrength,
                state.shadowRtProductionDenoiseStrength,
                state.shadowRtDedicatedDenoiseStrength
        );
        int rtEffectiveSamples = VulkanShadowRuntimeTuning.effectiveShadowRtSampleCount(
                state.currentShadows.rtShadowMode(),
                state.shadowRtSampleCount,
                state.shadowRtProductionSampleCount,
                state.shadowRtDedicatedSampleCount
        );
        float rtEffectiveRayLength = VulkanShadowRuntimeTuning.effectiveShadowRtRayLength(
                state.currentShadows.rtShadowMode(),
                state.shadowRtRayLength,
                state.shadowRtProductionRayLength,
                state.shadowRtDedicatedRayLength
        );
        double rtLaneWeight = 0.22 + 0.06 * Math.max(0, rtEffectiveSamples - 1) + 0.004 * Math.max(0.0f, rtEffectiveRayLength);
        state.shadowRtPerfGpuMsEstimateLastFrame = Math.max(0.0, state.lastFrameGpuMs) * rtLaneWeight;
        state.shadowRtPerfGpuMsWarnMaxLastFrame = VulkanShadowRuntimeTuning.shadowRtPerfCapForTier(
                state.qualityTier,
                state.shadowRtPerfMaxGpuMsLow,
                state.shadowRtPerfMaxGpuMsMedium,
                state.shadowRtPerfMaxGpuMsHigh,
                state.shadowRtPerfMaxGpuMsUltra
        );
        boolean shadowRtEnvelopeNow = rtEffectiveDenoise < state.shadowRtDenoiseWarnMin
                || rtEffectiveSamples < state.shadowRtSampleWarnMin
                || state.shadowRtPerfGpuMsEstimateLastFrame > state.shadowRtPerfGpuMsWarnMaxLastFrame;
        if (shadowRtEnvelopeNow) {
            state.shadowRtHighStreak = Math.min(10_000, state.shadowRtHighStreak + 1);
            state.shadowRtEnvelopeBreachedLastFrame = true;
        } else {
            state.shadowRtHighStreak = 0;
        }
        warnings.add(new EngineWarning(
                "SHADOW_RT_DENOISE_ENVELOPE",
                "Shadow RT denoise/perf envelope (mode=" + state.currentShadows.rtShadowMode()
                        + ", active=" + state.currentShadows.rtShadowActive()
                        + ", denoiseStrength=" + rtEffectiveDenoise
                        + ", denoiseWarnMin=" + state.shadowRtDenoiseWarnMin
                        + ", sampleCount=" + rtEffectiveSamples
                        + ", sampleWarnMin=" + state.shadowRtSampleWarnMin
                        + ", perfGpuMsEstimate=" + state.shadowRtPerfGpuMsEstimateLastFrame
                        + ", perfGpuMsWarnMax=" + state.shadowRtPerfGpuMsWarnMaxLastFrame
                        + ", highStreak=" + state.shadowRtHighStreak
                        + ", warnMinFrames=" + state.shadowRtWarnMinFrames
                        + ", cooldownRemaining=" + state.shadowRtWarnCooldownRemaining + ")"
        ));
        if (shadowRtEnvelopeNow
                && state.shadowRtHighStreak >= state.shadowRtWarnMinFrames
                && state.shadowRtWarnCooldownRemaining == 0) {
            warnings.add(new EngineWarning(
                    "SHADOW_RT_DENOISE_ENVELOPE_BREACH",
                    "Shadow RT denoise/perf envelope breached (mode=" + state.currentShadows.rtShadowMode()
                            + ", denoiseStrength=" + rtEffectiveDenoise
                            + ", denoiseWarnMin=" + state.shadowRtDenoiseWarnMin
                            + ", sampleCount=" + rtEffectiveSamples
                            + ", sampleWarnMin=" + state.shadowRtSampleWarnMin
                            + ", perfGpuMsEstimate=" + state.shadowRtPerfGpuMsEstimateLastFrame
                            + ", perfGpuMsWarnMax=" + state.shadowRtPerfGpuMsWarnMaxLastFrame
                            + ", highStreak=" + state.shadowRtHighStreak
                            + ", warnMinFrames=" + state.shadowRtWarnMinFrames
                            + ", cooldownFrames=" + state.shadowRtWarnCooldownFrames + ")"
            ));
            state.shadowRtWarnCooldownRemaining = state.shadowRtWarnCooldownFrames;
        }
        warnings.add(new EngineWarning(
                "SHADOW_RT_PATH_REQUESTED",
                "RT shadow mode requested: " + state.currentShadows.rtShadowMode()
                        + " (active=" + state.currentShadows.rtShadowActive()
                        + ", fallback stack in use"
                        + ", denoiseStrength=" + state.shadowRtDenoiseStrength
                        + ", rayLength=" + state.shadowRtRayLength
                        + ", sampleCount=" + state.shadowRtSampleCount
                        + ", effectiveDenoiseStrength=" + rtEffectiveDenoise
                        + ", effectiveRayLength=" + rtEffectiveRayLength
                        + ", effectiveSampleCount=" + rtEffectiveSamples
                        + ")"
        ));
        if (!state.currentShadows.rtShadowActive()) {
            warnings.add(new EngineWarning(
                    "SHADOW_RT_PATH_FALLBACK_ACTIVE",
                    "RT shadow traversal/denoise path unavailable; using non-RT shadow fallback stack"
                            + ("bvh".equals(state.currentShadows.rtShadowMode())
                            ? " (BVH mode requested but dedicated BVH traversal pipeline is not active)"
                            : ("bvh_production".equals(state.currentShadows.rtShadowMode())
                            ? " (Production BVH mode requested but hardware BVH traversal pipeline is not active)"
                            : ("bvh_dedicated".equals(state.currentShadows.rtShadowMode())
                            ? " (Dedicated BVH mode requested but dedicated BVH traversal pipeline is not active)"
                            : ("rt_native".equals(state.currentShadows.rtShadowMode()) || "rt_native_denoised".equals(state.currentShadows.rtShadowMode())
                            ? " (Native RT mode requested but hardware ray traversal support is not active)"
                            : "")
                            )
                            )
                            )
            ));
        }
        if ("bvh".equals(state.currentShadows.rtShadowMode())
                || "bvh_dedicated".equals(state.currentShadows.rtShadowMode())
                || "bvh_production".equals(state.currentShadows.rtShadowMode())) {
            String activePath = "bvh_dedicated".equals(state.currentShadows.rtShadowMode())
                    ? (state.currentShadows.rtShadowActive() ? "dedicated-preview traversal" : "fallback")
                    : ("bvh_production".equals(state.currentShadows.rtShadowMode())
                    ? (state.currentShadows.rtShadowActive() ? "production-preview traversal+denoise" : "fallback")
                    : "hybrid traversal");
            warnings.add(new EngineWarning(
                    "SHADOW_RT_BVH_PIPELINE_PENDING",
                    "BVH RT shadow mode requested, but runtime is using the "
                            + activePath
                            + " path "
                            + "(rtActive=" + state.currentShadows.rtShadowActive()
                            + ", rtBvhSupported=" + state.shadowRtBvhSupported
                            + "); dedicated BVH traversal/denoise pipeline remains pending"
            ));
        }
        if ("rt_native".equals(state.currentShadows.rtShadowMode())
                || "rt_native_denoised".equals(state.currentShadows.rtShadowMode())) {
            warnings.add(new EngineWarning(
                    "SHADOW_RT_NATIVE_PATH_ACTIVE",
                    "Native RT shadow traversal path requested "
                            + "(mode=" + state.currentShadows.rtShadowMode()
                            + ", active=" + state.currentShadows.rtShadowActive()
                            + ", traversalSupported=" + state.shadowRtTraversalSupported
                            + ", denoiseMode=" + ("rt_native_denoised".equals(state.currentShadows.rtShadowMode()) ? "dedicated" : "standard")
                            + ")"
            ));
        }
    }

    private VulkanShadowRtWarningEmitter() {
    }
}
