package org.dynamisengine.light.api.runtime;

import java.util.List;

/**
 * Backend-agnostic sky/atmosphere capability diagnostics snapshot.
 */
public record SkyCapabilityDiagnostics(
        boolean available,
        String mode,
        boolean hdriSkyboxActive,
        boolean proceduralSkyActive,
        boolean atmosphereActive,
        boolean dynamicTimeOfDayActive,
        boolean volumetricCloudsActive,
        boolean cloudShadowProjectionActive,
        boolean aerialPerspectiveActive,
        List<String> expectedFeatures,
        List<String> activeFeatures,
        List<String> prunedFeatures
) {
    public SkyCapabilityDiagnostics {
        mode = mode == null ? "" : mode;
        expectedFeatures = expectedFeatures == null ? List.of() : List.copyOf(expectedFeatures);
        activeFeatures = activeFeatures == null ? List.of() : List.copyOf(activeFeatures);
        prunedFeatures = prunedFeatures == null ? List.of() : List.copyOf(prunedFeatures);
    }

    public static SkyCapabilityDiagnostics unavailable() {
        return new SkyCapabilityDiagnostics(
                false,
                "",
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
