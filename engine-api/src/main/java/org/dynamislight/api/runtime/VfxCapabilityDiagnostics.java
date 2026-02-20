package org.dynamislight.api.runtime;

import java.util.List;

/**
 * Backend-agnostic VFX/particles capability diagnostics snapshot.
 */
public record VfxCapabilityDiagnostics(
        boolean available,
        boolean cpuParticlesExpected,
        boolean cpuParticlesActive,
        boolean gpuParticlesExpected,
        boolean gpuParticlesActive,
        boolean softParticlesExpected,
        boolean softParticlesActive,
        boolean meshParticlesExpected,
        boolean meshParticlesActive,
        List<String> expectedFeatures,
        List<String> activeFeatures,
        List<String> prunedFeatures
) {
    public VfxCapabilityDiagnostics {
        expectedFeatures = expectedFeatures == null ? List.of() : List.copyOf(expectedFeatures);
        activeFeatures = activeFeatures == null ? List.of() : List.copyOf(activeFeatures);
        prunedFeatures = prunedFeatures == null ? List.of() : List.copyOf(prunedFeatures);
    }

    public static VfxCapabilityDiagnostics unavailable() {
        return new VfxCapabilityDiagnostics(
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
