package org.dynamislight.impl.vulkan.capability;

import java.util.List;

/**
 * Deterministic lighting capability plan snapshot for runtime telemetry/CI.
 */
public record VulkanLightingCapabilityPlan(
        String modeId,
        int directionalLights,
        int pointLights,
        int spotLights,
        boolean physicallyBasedUnitsEnabled,
        boolean prioritizationEnabled,
        boolean emissiveMeshEnabled,
        List<String> activeCapabilities,
        List<String> prunedCapabilities,
        List<String> signals
) {
    public VulkanLightingCapabilityPlan {
        modeId = modeId == null ? "baseline_directional_point_spot" : modeId;
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
        signals = signals == null ? List.of() : List.copyOf(signals);
    }
}
