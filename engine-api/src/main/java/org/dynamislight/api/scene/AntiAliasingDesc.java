package org.dynamislight.api.scene;

/**
 * Anti-aliasing controls exposed to hosts via SceneDescriptor/PostProcessDesc.
 */
public record AntiAliasingDesc(
        String mode,
        boolean enabled,
        float blend,
        float clipScale,
        boolean lumaClipEnabled,
        float sharpenStrength,
        float renderScale,
        int debugView
) {
    public AntiAliasingDesc(
            String mode,
            boolean enabled,
            float blend,
            float clipScale,
            boolean lumaClipEnabled,
            float sharpenStrength,
            float renderScale
    ) {
        this(mode, enabled, blend, clipScale, lumaClipEnabled, sharpenStrength, renderScale, 0);
    }
}
