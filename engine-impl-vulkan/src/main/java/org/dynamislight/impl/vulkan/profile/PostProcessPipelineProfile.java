package org.dynamislight.impl.vulkan.profile;

public record PostProcessPipelineProfile(
        boolean offscreenRequested,
        boolean offscreenActive,
        String mode
) {
}
