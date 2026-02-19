package org.dynamislight.api.runtime;

/**
 * Backend-agnostic diagnostics for shadow expansion modes that may be policy-gated.
 */
public record ShadowExtendedModeDiagnostics(
        boolean available,
        boolean areaApproxRequested,
        boolean areaApproxSupported,
        String areaApproxPolicy,
        boolean areaApproxBreachedLastFrame,
        boolean distanceFieldRequested,
        boolean distanceFieldSupported,
        String distanceFieldPolicy,
        boolean distanceFieldBreachedLastFrame
) {
    public static ShadowExtendedModeDiagnostics unavailable() {
        return new ShadowExtendedModeDiagnostics(
                false,
                false,
                false,
                "unavailable",
                false,
                false,
                false,
                "unavailable",
                false
        );
    }
}
