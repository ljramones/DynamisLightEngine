package org.dynamislight.impl.vulkan.command;

import java.util.List;

/**
 * Module-owned execution contract for a post-stack slice.
 */
record VulkanPostExecutionModuleContract(
        String moduleId,
        String ownerFeatureId,
        String executionPassId,
        boolean enabled,
        String reason,
        List<String> reads,
        List<String> writes
) {
    VulkanPostExecutionModuleContract {
        moduleId = moduleId == null ? "" : moduleId;
        ownerFeatureId = ownerFeatureId == null ? "" : ownerFeatureId;
        executionPassId = executionPassId == null ? "" : executionPassId;
        reason = reason == null ? "" : reason;
        reads = reads == null ? List.of() : List.copyOf(reads);
        writes = writes == null ? List.of() : List.copyOf(writes);
    }
}
