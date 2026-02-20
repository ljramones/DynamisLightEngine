package org.dynamislight.api.runtime;

import java.util.List;

/**
 * Backend-agnostic geometry/detail capability diagnostics snapshot.
 */
public record GeometryCapabilityDiagnostics(
        boolean available,
        boolean staticMeshRenderingActive,
        boolean instancedRenderingExpected,
        boolean instancedRenderingActive,
        boolean frustumCullingExpected,
        boolean frustumCullingActive,
        boolean meshStreamingExpected,
        boolean meshStreamingActive,
        long geometryCacheHits,
        long geometryCacheMisses,
        long geometryCacheEvictions,
        int geometryCacheEntries,
        int geometryCacheMaxEntries,
        List<String> expectedFeatures,
        List<String> activeFeatures,
        List<String> prunedFeatures
) {
    public GeometryCapabilityDiagnostics {
        geometryCacheHits = Math.max(0L, geometryCacheHits);
        geometryCacheMisses = Math.max(0L, geometryCacheMisses);
        geometryCacheEvictions = Math.max(0L, geometryCacheEvictions);
        geometryCacheEntries = Math.max(0, geometryCacheEntries);
        geometryCacheMaxEntries = Math.max(0, geometryCacheMaxEntries);
        expectedFeatures = expectedFeatures == null ? List.of() : List.copyOf(expectedFeatures);
        activeFeatures = activeFeatures == null ? List.of() : List.copyOf(activeFeatures);
        prunedFeatures = prunedFeatures == null ? List.of() : List.copyOf(prunedFeatures);
    }

    public static GeometryCapabilityDiagnostics unavailable() {
        return new GeometryCapabilityDiagnostics(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0L,
                0L,
                0L,
                0,
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
