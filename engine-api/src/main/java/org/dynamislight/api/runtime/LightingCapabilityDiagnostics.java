package org.dynamislight.api.runtime;

import java.util.List;

/**
 * Backend-agnostic lighting capability-plan diagnostics snapshot.
 */
public record LightingCapabilityDiagnostics(
        boolean available,
        String mode,
        int directionalLights,
        int pointLights,
        int spotLights,
        boolean physicallyBasedUnitsEnabled,
        boolean prioritizationEnabled,
        boolean emissiveMeshEnabled,
        boolean areaApproxEnabled,
        boolean iesProfilesEnabled,
        boolean cookiesEnabled,
        boolean volumetricShaftsEnabled,
        boolean clusteringEnabled,
        boolean lightLayersEnabled,
        List<String> activeCapabilities,
        List<String> prunedCapabilities,
        List<String> signals
) {
    public LightingCapabilityDiagnostics {
        mode = mode == null ? "" : mode;
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    public static LightingCapabilityDiagnostics unavailable() {
        return new LightingCapabilityDiagnostics(
                false,
                "",
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
