package org.dynamislight.api.runtime;

import java.util.List;

/**
 * Backend-agnostic shadow capability mode diagnostics snapshot.
 */
public record ShadowCapabilityDiagnostics(
        boolean available,
        String featureId,
        String mode,
        List<String> signals
) {
    public ShadowCapabilityDiagnostics {
        featureId = featureId == null ? "unavailable" : featureId;
        mode = mode == null ? "unavailable" : mode;
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    public static ShadowCapabilityDiagnostics unavailable() {
        return new ShadowCapabilityDiagnostics(false, "unavailable", "unavailable", List.of());
    }
}
