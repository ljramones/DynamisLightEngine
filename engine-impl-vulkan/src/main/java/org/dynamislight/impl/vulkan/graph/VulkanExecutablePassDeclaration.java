package org.dynamislight.impl.vulkan.graph;

import org.dynamislight.spi.render.RenderPassContribution;

/**
 * Vulkan-local executable pass declaration (metadata + callback).
 */
public record VulkanExecutablePassDeclaration(
        String featureId,
        RenderPassContribution contribution,
        Runnable executeCallback
) {
    public VulkanExecutablePassDeclaration {
        featureId = featureId == null ? "" : featureId.trim();
        contribution = contribution == null
                ? new RenderPassContribution("", null, java.util.List.of(), java.util.List.of(), false)
                : contribution;
        executeCallback = executeCallback == null ? () -> { } : executeCallback;
    }
}
