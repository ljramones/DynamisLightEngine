package org.dynamisengine.light.impl.vulkan.capability;

import java.util.List;

/**
 * Deterministic planned sky capability state used for runtime warnings and diagnostics.
 */
public record VulkanSkyCapabilityPlan(
        String modeId,
        boolean hdriExpected,
        boolean hdriActive,
        boolean proceduralExpected,
        boolean proceduralActive,
        boolean atmosphereExpected,
        boolean atmosphereActive,
        boolean dynamicTimeOfDayExpected,
        boolean dynamicTimeOfDayActive,
        boolean volumetricCloudsExpected,
        boolean volumetricCloudsActive,
        boolean cloudShadowProjectionExpected,
        boolean cloudShadowProjectionActive,
        boolean aerialPerspectiveExpected,
        boolean aerialPerspectiveActive,
        List<String> activeCapabilities,
        List<String> prunedCapabilities,
        List<String> signals
) {
    public VulkanSkyCapabilityPlan {
        modeId = modeId == null || modeId.isBlank() ? "off" : modeId;
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
        signals = signals == null ? List.of() : List.copyOf(signals);
    }
}
