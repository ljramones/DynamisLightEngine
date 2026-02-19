package org.dynamislight.impl.vulkan.descriptor;

import java.util.List;
import java.util.Map;

/**
 * Per-pass descriptor layout composition plan for phase C.2.
 */
public record VulkanComposedDescriptorLayoutPlan(
        String targetPassId,
        Map<Integer, List<VulkanComposedDescriptorBinding>> bindingsBySet
) {
    public VulkanComposedDescriptorLayoutPlan {
        targetPassId = targetPassId == null ? "" : targetPassId.trim();
        bindingsBySet = bindingsBySet == null ? Map.of() : Map.copyOf(bindingsBySet);
    }

    public List<VulkanComposedDescriptorBinding> allBindingsSorted() {
        return bindingsBySet.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> entry.getValue().stream())
                .toList();
    }
}

