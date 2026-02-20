package org.dynamislight.api.runtime;

/**
 * Backend-agnostic emissive-light policy diagnostics snapshot.
 */
public record LightingEmissiveDiagnostics(
        boolean available,
        boolean emissiveEnabled,
        int candidateCount,
        int totalMaterials,
        double candidateRatio,
        double warnMinCandidateRatio,
        boolean envelopeBreached
) {
    public static LightingEmissiveDiagnostics unavailable() {
        return new LightingEmissiveDiagnostics(false, false, 0, 0, 0.0, 0.0, false);
    }
}
