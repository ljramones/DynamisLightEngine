package org.dynamislight.impl.vulkan.profile;

import java.util.List;
import org.dynamislight.impl.vulkan.descriptor.VulkanComposedDescriptorLayoutPlan;
import org.dynamislight.spi.render.RenderFeatureCapabilityV2;

/**
 * Compiled Phase C profile tuple: composed shader sources + composed descriptor plans.
 */
public record VulkanPipelineProfileCompilation(
        VulkanPipelineProfileKey key,
        List<RenderFeatureCapabilityV2> capabilities,
        String mainFragmentSource,
        String postFragmentSource,
        VulkanComposedDescriptorLayoutPlan mainGeometryDescriptorPlan,
        VulkanComposedDescriptorLayoutPlan postCompositeDescriptorPlan
) {
    public VulkanPipelineProfileCompilation {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
