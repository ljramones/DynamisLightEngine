package org.dynamislight.api.scene;

import java.util.List;

/**
 * Advanced reflection controls for Hi-Z SSR, planar clip-plane, probe volumes,
 * and hardware RT fallback orchestration.
 */
public record ReflectionAdvancedDesc(
        boolean hiZEnabled,
        int hiZMipCount,
        int denoisePasses,
        boolean planarClipPlaneEnabled,
        float planarPlaneHeight,
        float planarFadeStart,
        float planarFadeEnd,
        boolean probeVolumeEnabled,
        boolean probeBoxProjectionEnabled,
        float probeBlendDistance,
        List<ReflectionProbeDesc> probes,
        boolean rtEnabled,
        float rtMaxRoughness,
        String rtFallbackMode
) {
    public ReflectionAdvancedDesc {
        probes = probes == null ? List.of() : List.copyOf(probes);
    }

    public ReflectionAdvancedDesc(
            boolean hiZEnabled,
            int hiZMipCount,
            int denoisePasses,
            boolean planarClipPlaneEnabled,
            float planarPlaneHeight,
            float planarFadeStart,
            float planarFadeEnd,
            boolean probeVolumeEnabled,
            boolean probeBoxProjectionEnabled,
            float probeBlendDistance,
            boolean rtEnabled,
            float rtMaxRoughness,
            String rtFallbackMode
    ) {
        this(
                hiZEnabled,
                hiZMipCount,
                denoisePasses,
                planarClipPlaneEnabled,
                planarPlaneHeight,
                planarFadeStart,
                planarFadeEnd,
                probeVolumeEnabled,
                probeBoxProjectionEnabled,
                probeBlendDistance,
                List.of(),
                rtEnabled,
                rtMaxRoughness,
                rtFallbackMode
        );
    }

    public ReflectionAdvancedDesc() {
        this(
                true,
                5,
                2,
                false,
                0.0f,
                0.5f,
                6.0f,
                false,
                false,
                2.0f,
                List.of(),
                false,
                0.75f,
                "hybrid"
        );
    }
}
