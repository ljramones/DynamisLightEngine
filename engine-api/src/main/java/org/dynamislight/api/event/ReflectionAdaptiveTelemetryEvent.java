package org.dynamislight.api.event;

/**
 * Per-frame reflection adaptive-policy telemetry for temporal stability tracking.
 *
 * @param frameIndex current frame index
 * @param enabled whether adaptive reflection policy is enabled
 * @param instantSeverity current-frame adaptive severity [0..1]
 * @param meanSeverity running mean adaptive severity [0..1]
 * @param peakSeverity peak adaptive severity observed since last reset [0..1]
 * @param meanTemporalDelta running mean of (activeTemporalWeight - baseTemporalWeight)
 * @param meanSsrStrengthDelta running mean of (activeSsrStrength - baseSsrStrength)
 * @param meanSsrStepScaleDelta running mean of (activeSsrStepScale - baseSsrStepScale)
 * @param samples number of telemetry samples accumulated
 */
public record ReflectionAdaptiveTelemetryEvent(
        long frameIndex,
        boolean enabled,
        double instantSeverity,
        double meanSeverity,
        double peakSeverity,
        double meanTemporalDelta,
        double meanSsrStrengthDelta,
        double meanSsrStepScaleDelta,
        long samples
) implements EngineEvent {
}
