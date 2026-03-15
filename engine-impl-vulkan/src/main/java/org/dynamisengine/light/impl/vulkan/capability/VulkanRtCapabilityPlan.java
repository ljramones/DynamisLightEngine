package org.dynamisengine.light.impl.vulkan.capability;

import java.util.List;

/**
 * Deterministic RT capability plan snapshot for runtime telemetry/CI.
 */
public record VulkanRtCapabilityPlan(
        String modeId,
        boolean rtAvailable,
        boolean rtAoRequested,
        boolean rtAoActive,
        boolean rtTranslucencyCausticsRequested,
        boolean rtTranslucencyCausticsActive,
        boolean bvhCompactionRequested,
        boolean bvhCompactionActive,
        boolean denoiserFrameworkRequested,
        boolean denoiserFrameworkActive,
        boolean hybridCompositionRequested,
        boolean hybridCompositionActive,
        boolean qualityTiersRequested,
        boolean qualityTiersActive,
        boolean inlineRayQueryRequested,
        boolean inlineRayQueryActive,
        boolean dedicatedRaygenRequested,
        boolean dedicatedRaygenActive,
        List<String> activeCapabilities,
        List<String> prunedCapabilities,
        List<String> signals
) {
    public VulkanRtCapabilityPlan {
        modeId = modeId == null ? VulkanRtCapabilityDescriptorV2.MODE_QUALITY_TIERS.id() : modeId;
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
        signals = signals == null ? List.of() : List.copyOf(signals);
    }
}
