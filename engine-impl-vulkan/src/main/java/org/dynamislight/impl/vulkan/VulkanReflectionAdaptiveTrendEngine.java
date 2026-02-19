package org.dynamislight.impl.vulkan;

import java.util.Deque;

final class VulkanReflectionAdaptiveTrendEngine {
    static final class State {
        double reflectionAdaptiveSeverityInstant;
        double reflectionAdaptiveSeverityPeak;
        double reflectionAdaptiveSeverityAccum;
        double reflectionAdaptiveTemporalDeltaAccum;
        double reflectionAdaptiveSsrStrengthDeltaAccum;
        double reflectionAdaptiveSsrStepScaleDeltaAccum;
        long reflectionAdaptiveTelemetrySamples;
        int reflectionSsrTaaAdaptiveTrendWarnHighStreak;
        int reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining;
        boolean reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame;
        int reflectionSsrTaaAdaptiveTrendWindowFrames;
        double reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
        int reflectionSsrTaaAdaptiveTrendWarnMinFrames;
        int reflectionSsrTaaAdaptiveTrendWarnCooldownFrames;
        int reflectionSsrTaaAdaptiveTrendWarnMinSamples;
        int reflectionSsrTaaAdaptiveTrendSloMinSamples;
        double reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax;
        double reflectionSsrTaaAdaptiveTrendSloHighRatioMax;
        float reflectionAdaptiveTemporalWeightActive;
        float reflectionAdaptiveSsrStrengthActive;
        float reflectionAdaptiveSsrStepScaleActive;
        Deque<ReflectionAdaptiveWindowSample> reflectionAdaptiveTrendSamples;
    }

    static void recordSample(
            State state,
            float baseTemporalWeight,
            float baseSsrStrength,
            float baseSsrStepScale,
            double severity
    ) {
        state.reflectionAdaptiveSeverityInstant = Math.max(0.0, Math.min(1.0, severity));
        state.reflectionAdaptiveSeverityPeak = Math.max(state.reflectionAdaptiveSeverityPeak, state.reflectionAdaptiveSeverityInstant);
        state.reflectionAdaptiveTelemetrySamples++;
        state.reflectionAdaptiveSeverityAccum += state.reflectionAdaptiveSeverityInstant;
        double temporalDelta = state.reflectionAdaptiveTemporalWeightActive - baseTemporalWeight;
        double ssrStrengthDelta = state.reflectionAdaptiveSsrStrengthActive - baseSsrStrength;
        double ssrStepScaleDelta = state.reflectionAdaptiveSsrStepScaleActive - baseSsrStepScale;
        state.reflectionAdaptiveTemporalDeltaAccum += temporalDelta;
        state.reflectionAdaptiveSsrStrengthDeltaAccum += ssrStrengthDelta;
        state.reflectionAdaptiveSsrStepScaleDeltaAccum += ssrStepScaleDelta;
        state.reflectionAdaptiveTrendSamples.addLast(new ReflectionAdaptiveWindowSample(
                state.reflectionAdaptiveSeverityInstant,
                temporalDelta,
                ssrStrengthDelta,
                ssrStepScaleDelta
        ));
        while (state.reflectionAdaptiveTrendSamples.size() > state.reflectionSsrTaaAdaptiveTrendWindowFrames) {
            state.reflectionAdaptiveTrendSamples.removeFirst();
        }
    }

    static void resetTelemetry(State state) {
        state.reflectionAdaptiveSeverityInstant = 0.0;
        state.reflectionAdaptiveSeverityPeak = 0.0;
        state.reflectionAdaptiveSeverityAccum = 0.0;
        state.reflectionAdaptiveTemporalDeltaAccum = 0.0;
        state.reflectionAdaptiveSsrStrengthDeltaAccum = 0.0;
        state.reflectionAdaptiveSsrStepScaleDeltaAccum = 0.0;
        state.reflectionAdaptiveTelemetrySamples = 0L;
        state.reflectionSsrTaaAdaptiveTrendWarnHighStreak = 0;
        state.reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining = 0;
        state.reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame = false;
        state.reflectionAdaptiveTrendSamples.clear();
    }

    static ReflectionAdaptiveTrendDiagnostics snapshotTrend(State state, boolean warningTriggered) {
        return VulkanReflectionAdaptiveMath.snapshotTrendDiagnostics(
                state.reflectionAdaptiveTrendSamples,
                state.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin,
                state.reflectionSsrTaaAdaptiveTrendWarnMinFrames,
                state.reflectionSsrTaaAdaptiveTrendWarnCooldownFrames,
                state.reflectionSsrTaaAdaptiveTrendWarnMinSamples,
                state.reflectionSsrTaaAdaptiveTrendWarnHighStreak,
                state.reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining,
                warningTriggered
        );
    }

    static boolean updateWarningGate(State state) {
        ReflectionAdaptiveTrendDiagnostics trend = snapshotTrend(state, false);
        boolean highRisk = trend.windowSamples() >= state.reflectionSsrTaaAdaptiveTrendWarnMinSamples
                && trend.highRatio() >= state.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
        boolean warningTriggered = false;
        if (highRisk) {
            state.reflectionSsrTaaAdaptiveTrendWarnHighStreak++;
            if (state.reflectionSsrTaaAdaptiveTrendWarnHighStreak >= state.reflectionSsrTaaAdaptiveTrendWarnMinFrames
                    && state.reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining <= 0) {
                state.reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining = state.reflectionSsrTaaAdaptiveTrendWarnCooldownFrames;
                warningTriggered = true;
            }
        } else {
            state.reflectionSsrTaaAdaptiveTrendWarnHighStreak = 0;
        }
        if (state.reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining > 0) {
            state.reflectionSsrTaaAdaptiveTrendWarnCooldownRemaining--;
        }
        state.reflectionSsrTaaAdaptiveTrendWarningTriggeredLastFrame = warningTriggered;
        return warningTriggered;
    }

    static VulkanEngineRuntime.TrendSloAudit evaluateSlo(State state, ReflectionAdaptiveTrendDiagnostics trend) {
        if (trend.windowSamples() < state.reflectionSsrTaaAdaptiveTrendSloMinSamples) {
            return new VulkanEngineRuntime.TrendSloAudit("pending", "insufficient_samples", false);
        }
        if (trend.meanSeverity() > state.reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax) {
            return new VulkanEngineRuntime.TrendSloAudit("fail", "mean_severity_exceeded", true);
        }
        if (trend.highRatio() > state.reflectionSsrTaaAdaptiveTrendSloHighRatioMax) {
            return new VulkanEngineRuntime.TrendSloAudit("fail", "high_ratio_exceeded", true);
        }
        return new VulkanEngineRuntime.TrendSloAudit("pass", "within_thresholds", false);
    }

    private VulkanReflectionAdaptiveTrendEngine() {
    }
}
