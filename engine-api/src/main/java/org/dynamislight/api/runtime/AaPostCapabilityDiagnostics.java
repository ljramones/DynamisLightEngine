package org.dynamislight.api.runtime;

import java.util.List;

/**
 * Backend-agnostic AA/post capability-plan diagnostics snapshot.
 */
public record AaPostCapabilityDiagnostics(
        boolean available,
        String aaMode,
        boolean aaEnabled,
        boolean temporalHistoryActive,
        List<String> activeCapabilities,
        List<String> prunedCapabilities
) {
    public AaPostCapabilityDiagnostics {
        aaMode = aaMode == null ? "" : aaMode;
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
    }

    public static AaPostCapabilityDiagnostics unavailable() {
        return new AaPostCapabilityDiagnostics(
                false,
                "",
                false,
                false,
                List.of(),
                List.of()
        );
    }
}
