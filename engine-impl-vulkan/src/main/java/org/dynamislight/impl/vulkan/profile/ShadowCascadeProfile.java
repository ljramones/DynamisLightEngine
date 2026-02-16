package org.dynamislight.impl.vulkan.profile;

public record ShadowCascadeProfile(
        boolean enabled,
        int cascadeCount,
        int mapResolution,
        int pcfRadius,
        float bias,
        float split1Ndc,
        float split2Ndc,
        float split3Ndc
) {
}
