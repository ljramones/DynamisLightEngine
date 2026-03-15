package org.dynamisengine.light.api.runtime;

import java.util.List;

/**
 * Backend-agnostic RT capability diagnostics snapshot.
 */
public record RtCapabilityDiagnostics(
        boolean available,
        String modeId,
        boolean rtAoExpected,
        boolean rtAoActive,
        boolean rtTranslucencyCausticsExpected,
        boolean rtTranslucencyCausticsActive,
        boolean bvhCompactionExpected,
        boolean bvhCompactionActive,
        boolean denoiserFrameworkExpected,
        boolean denoiserFrameworkActive,
        boolean hybridCompositionExpected,
        boolean hybridCompositionActive,
        boolean qualityTiersExpected,
        boolean qualityTiersActive,
        boolean inlineRayQueryExpected,
        boolean inlineRayQueryActive,
        boolean dedicatedRaygenExpected,
        boolean dedicatedRaygenActive,
        List<String> expectedFeatures,
        List<String> activeFeatures,
        List<String> prunedFeatures
) {
    public RtCapabilityDiagnostics {
        modeId = modeId == null ? "" : modeId.trim();
        expectedFeatures = expectedFeatures == null ? List.of() : List.copyOf(expectedFeatures);
        activeFeatures = activeFeatures == null ? List.of() : List.copyOf(activeFeatures);
        prunedFeatures = prunedFeatures == null ? List.of() : List.copyOf(prunedFeatures);
    }

    public static RtCapabilityDiagnostics unavailable() {
        return new RtCapabilityDiagnostics(
                false,
                "",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
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
