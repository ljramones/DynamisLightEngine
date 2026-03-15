package org.dynamisengine.light.api.runtime;

import java.util.List;

/**
 * Backend-agnostic PBR/shading capability-plan diagnostics snapshot.
 */
public record PbrCapabilityDiagnostics(
        boolean available,
        String mode,
        boolean specularGlossinessEnabled,
        boolean detailMapsEnabled,
        boolean materialLayeringEnabled,
        boolean clearCoatEnabled,
        boolean anisotropicEnabled,
        boolean transmissionEnabled,
        boolean refractionEnabled,
        boolean subsurfaceScatteringEnabled,
        boolean thinFilmIridescenceEnabled,
        boolean sheenEnabled,
        boolean parallaxOcclusionEnabled,
        boolean tessellationEnabled,
        boolean decalsEnabled,
        boolean eyeShaderEnabled,
        boolean hairShaderEnabled,
        boolean clothShaderEnabled,
        boolean vertexColorBlendEnabled,
        boolean emissiveBloomControlEnabled,
        boolean energyConservationValidationEnabled,
        List<String> activeCapabilities,
        List<String> prunedCapabilities,
        List<String> signals
) {
    public PbrCapabilityDiagnostics {
        mode = mode == null ? "" : mode;
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    public static PbrCapabilityDiagnostics unavailable() {
        return new PbrCapabilityDiagnostics(
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
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
