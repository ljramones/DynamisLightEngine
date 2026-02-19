package org.dynamislight.api.runtime;

import java.util.List;

/**
 * Backend-agnostic GI capability-plan diagnostics snapshot.
 */
public record GiCapabilityDiagnostics(
        boolean available,
        String giMode,
        boolean giEnabled,
        boolean rtAvailable,
        List<String> activeCapabilities,
        List<String> prunedCapabilities
) {
    public GiCapabilityDiagnostics {
        giMode = giMode == null ? "" : giMode;
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
    }

    public static GiCapabilityDiagnostics unavailable() {
        return new GiCapabilityDiagnostics(
                false,
                "",
                false,
                false,
                List.of(),
                List.of()
        );
    }
}
