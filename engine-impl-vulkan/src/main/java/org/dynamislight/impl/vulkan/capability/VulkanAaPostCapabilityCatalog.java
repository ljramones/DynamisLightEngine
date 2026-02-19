package org.dynamislight.impl.vulkan.capability;

import java.util.Arrays;
import java.util.List;
import org.dynamislight.spi.render.RenderFeatureCapability;

/**
 * Catalog of Vulkan AA/post capability declarations.
 *
 * This catalog is metadata-only in Phase 2 and intentionally does not perform
 * runtime pipeline wiring.
 */
public final class VulkanAaPostCapabilityCatalog {
    private VulkanAaPostCapabilityCatalog() {
    }

    public static List<RenderFeatureCapability> aaCapabilities() {
        return Arrays.stream(VulkanAaCapabilityMode.values())
                .map(VulkanAaCapability::of)
                .map(cap -> (RenderFeatureCapability) cap)
                .toList();
    }

    public static List<RenderFeatureCapability> postCapabilities() {
        return Arrays.stream(VulkanPostCapabilityId.values())
                .map(VulkanPostCapability::of)
                .map(cap -> (RenderFeatureCapability) cap)
                .toList();
    }

    public static List<RenderFeatureCapability> allCapabilities() {
        List<RenderFeatureCapability> aa = aaCapabilities();
        List<RenderFeatureCapability> post = postCapabilities();
        return java.util.stream.Stream.concat(aa.stream(), post.stream()).toList();
    }
}
