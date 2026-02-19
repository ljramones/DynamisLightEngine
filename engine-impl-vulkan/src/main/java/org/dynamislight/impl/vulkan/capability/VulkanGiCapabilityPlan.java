package org.dynamislight.impl.vulkan.capability;

import java.util.List;

/**
 * GI Phase 1 capability activation plan.
 */
public record VulkanGiCapabilityPlan(
        String giModeId,
        boolean giEnabled,
        boolean rtAvailable,
        List<String> activeCapabilities,
        List<String> prunedCapabilities
) {
    public VulkanGiCapabilityPlan {
        giModeId = giModeId == null ? "" : giModeId;
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
    }
}
