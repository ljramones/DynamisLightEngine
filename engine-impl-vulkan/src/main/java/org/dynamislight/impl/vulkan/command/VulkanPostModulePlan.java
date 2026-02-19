package org.dynamislight.impl.vulkan.command;

import java.util.List;

/**
 * Module-level activation plan for post composite recorder boundaries.
 *
 * This is structural metadata and does not alter rendering behavior.
 */
record VulkanPostModulePlan(
        List<String> activeModules,
        List<String> prunedModules
) {
    VulkanPostModulePlan {
        activeModules = activeModules == null ? List.of() : List.copyOf(activeModules);
        prunedModules = prunedModules == null ? List.of() : List.copyOf(prunedModules);
    }
}
