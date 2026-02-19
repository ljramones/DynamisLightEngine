package org.dynamislight.impl.vulkan.graph;

import java.util.List;
import org.dynamislight.spi.render.RenderCapabilityValidationIssue;

/**
 * Compiled render graph metadata plan.
 *
 * @param orderedNodes deterministic node order
 * @param validationIssues compile-time validation diagnostics
 * @param resourceLifetimes resource lifetime map in ordered node space
 */
public record VulkanRenderGraphPlan(
        List<VulkanRenderGraphNode> orderedNodes,
        List<RenderCapabilityValidationIssue> validationIssues,
        List<VulkanRenderGraphResourceLifetime> resourceLifetimes
) {
    public VulkanRenderGraphPlan {
        orderedNodes = orderedNodes == null ? List.of() : List.copyOf(orderedNodes);
        validationIssues = validationIssues == null ? List.of() : List.copyOf(validationIssues);
        resourceLifetimes = resourceLifetimes == null ? List.of() : List.copyOf(resourceLifetimes);
    }

    public boolean hasErrors() {
        return validationIssues.stream()
                .anyMatch(i -> i.severity() == RenderCapabilityValidationIssue.Severity.ERROR);
    }
}
