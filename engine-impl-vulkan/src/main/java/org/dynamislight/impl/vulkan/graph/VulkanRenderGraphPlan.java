package org.dynamislight.impl.vulkan.graph;

import java.util.List;
import java.util.Map;
import org.dynamislight.spi.render.RenderCapabilityValidationIssue;

/**
 * Compiled render graph metadata plan.
 *
 * @param orderedNodes deterministic node order
 * @param importedResources imported resource declarations
 * @param validationIssues compile-time validation diagnostics
 * @param resourceLifetimes resource lifetime map in ordered node space
 */
public record VulkanRenderGraphPlan(
        List<VulkanRenderGraphNode> orderedNodes,
        List<VulkanImportedResource> importedResources,
        List<RenderCapabilityValidationIssue> validationIssues,
        List<VulkanRenderGraphResourceLifetime> resourceLifetimes
) {
    public VulkanRenderGraphPlan {
        orderedNodes = orderedNodes == null ? List.of() : List.copyOf(orderedNodes);
        importedResources = importedResources == null ? List.of() : List.copyOf(importedResources);
        validationIssues = validationIssues == null ? List.of() : List.copyOf(validationIssues);
        resourceLifetimes = resourceLifetimes == null ? List.of() : List.copyOf(resourceLifetimes);
    }

    public boolean hasErrors() {
        return validationIssues.stream()
                .anyMatch(i -> i.severity() == RenderCapabilityValidationIssue.Severity.ERROR);
    }

    public Map<String, List<VulkanRenderGraphResourceAccessEvent>> resourceAccessOrder() {
        return VulkanRenderGraphAccessOrder.byResource(orderedNodes);
    }
}
