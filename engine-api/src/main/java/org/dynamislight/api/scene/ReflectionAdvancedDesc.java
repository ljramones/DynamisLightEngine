package org.dynamislight.api.scene;

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
        boolean rtEnabled,
        float rtMaxRoughness,
        String rtFallbackMode
) {
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
                false,
                0.75f,
                "hybrid"
        );
    }
}
