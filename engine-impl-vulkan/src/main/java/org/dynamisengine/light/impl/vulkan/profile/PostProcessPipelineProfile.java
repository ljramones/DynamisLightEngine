package org.dynamisengine.light.impl.vulkan.profile;

public record PostProcessPipelineProfile(
        boolean offscreenRequested,
        boolean offscreenActive,
        String mode
) {
}
