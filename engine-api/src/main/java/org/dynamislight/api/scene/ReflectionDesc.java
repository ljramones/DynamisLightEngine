package org.dynamislight.api.scene;

/**
 * Reflection controls exposed to hosts via SceneDescriptor/PostProcessDesc.
 */
public record ReflectionDesc(
        boolean enabled,
        String mode,
        float ssrStrength,
        float ssrMaxRoughness,
        float ssrStepScale,
        float temporalWeight,
        float planarStrength
) {
    public ReflectionDesc(
            boolean enabled,
            String mode
    ) {
        this(enabled, mode, 0.6f, 0.78f, 1.0f, 0.80f, 0.35f);
    }
}
