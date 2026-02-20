package org.dynamislight.api.runtime;

import java.util.List;

/**
 * Backend-agnostic water/ocean capability diagnostics snapshot.
 */
public record WaterCapabilityDiagnostics(
        boolean available,
        boolean flatWaterExpected,
        boolean flatWaterActive,
        boolean waveSimulationExpected,
        boolean waveSimulationActive,
        boolean foamExpected,
        boolean foamActive,
        boolean underwaterExpected,
        boolean underwaterActive,
        List<String> expectedFeatures,
        List<String> activeFeatures,
        List<String> prunedFeatures
) {
    public WaterCapabilityDiagnostics {
        expectedFeatures = expectedFeatures == null ? List.of() : List.copyOf(expectedFeatures);
        activeFeatures = activeFeatures == null ? List.of() : List.copyOf(activeFeatures);
        prunedFeatures = prunedFeatures == null ? List.of() : List.copyOf(prunedFeatures);
    }

    public static WaterCapabilityDiagnostics unavailable() {
        return new WaterCapabilityDiagnostics(
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
