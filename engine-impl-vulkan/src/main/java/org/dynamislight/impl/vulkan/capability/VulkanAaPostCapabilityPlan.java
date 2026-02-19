package org.dynamislight.impl.vulkan.capability;

import java.util.List;
import org.dynamislight.spi.render.RenderFeatureCapability;

/**
 * Metadata-only activation plan for Vulkan AA/post capability composition.
 *
 * @param activeCapabilities active capability modules in execution order
 * @param prunedCapabilities capability IDs pruned by profile/quality/rules
 */
public record VulkanAaPostCapabilityPlan(
        List<RenderFeatureCapability> activeCapabilities,
        List<String> prunedCapabilities
) {
    public VulkanAaPostCapabilityPlan {
        activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
        prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
    }
}
