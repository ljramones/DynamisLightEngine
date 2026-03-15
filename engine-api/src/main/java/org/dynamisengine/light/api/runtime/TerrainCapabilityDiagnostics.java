package org.dynamisengine.light.api.runtime;

import java.util.List;

/**
 * Backend-agnostic terrain capability diagnostics snapshot.
 */
public record TerrainCapabilityDiagnostics(
        boolean available,
        boolean heightmapExpected,
        boolean heightmapActive,
        boolean virtualTexturingExpected,
        boolean virtualTexturingActive,
        boolean splattingExpected,
        boolean splattingActive,
        boolean streamingExpected,
        boolean streamingActive,
        List<String> expectedFeatures,
        List<String> activeFeatures,
        List<String> prunedFeatures
) {
    public TerrainCapabilityDiagnostics {
        expectedFeatures = expectedFeatures == null ? List.of() : List.copyOf(expectedFeatures);
        activeFeatures = activeFeatures == null ? List.of() : List.copyOf(activeFeatures);
        prunedFeatures = prunedFeatures == null ? List.of() : List.copyOf(prunedFeatures);
    }

    public static TerrainCapabilityDiagnostics unavailable() {
        return new TerrainCapabilityDiagnostics(
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
