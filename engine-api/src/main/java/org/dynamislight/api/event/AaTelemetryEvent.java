package org.dynamislight.api.event;

/**
 * Per-frame anti-aliasing telemetry for temporal stability analysis.
 *
 * @param frameIndex current frame index
 * @param historyRejectRate estimated fraction of pixels rejecting temporal history [0..1]
 * @param confidenceMean estimated mean TAA confidence [0..1]
 * @param confidenceDropEvents accumulated confidence drop events since runtime start
 */
public record AaTelemetryEvent(
        long frameIndex,
        double historyRejectRate,
        double confidenceMean,
        long confidenceDropEvents
) implements EngineEvent {
}
