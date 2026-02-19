package org.dynamislight.impl.vulkan.command;

import java.util.List;

/**
 * Module-level execution plan for post composite boundaries.
 */
record VulkanPostExecutionPlan(
        List<String> activeModules,
        List<String> prunedModules,
        List<VulkanPostExecutionModuleContract> moduleContracts
) {
    VulkanPostExecutionPlan {
        activeModules = activeModules == null ? List.of() : List.copyOf(activeModules);
        prunedModules = prunedModules == null ? List.of() : List.copyOf(prunedModules);
        moduleContracts = moduleContracts == null ? List.of() : List.copyOf(moduleContracts);
    }
}
