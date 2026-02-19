package org.dynamislight.impl.vulkan.reflection;

public record ReflectionRtPathDiagnostics(
        boolean laneRequested,
        boolean laneActive,
        boolean singleBounceEnabled,
        boolean multiBounceEnabled,
        boolean requireActive,
        boolean requireActiveUnmetLastFrame,
        boolean requireMultiBounce,
        boolean requireMultiBounceUnmetLastFrame,
        boolean requireDedicatedPipeline,
        boolean requireDedicatedPipelineUnmetLastFrame,
        boolean dedicatedPipelineEnabled,
        boolean traversalSupported,
        boolean dedicatedCapabilitySupported,
        boolean dedicatedHardwarePipelineActive,
        boolean dedicatedDenoisePipelineEnabled,
        double denoiseStrength,
        String fallbackChain
) {
}
