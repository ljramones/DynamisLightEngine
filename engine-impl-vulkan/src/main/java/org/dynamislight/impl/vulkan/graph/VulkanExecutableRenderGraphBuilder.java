package org.dynamislight.impl.vulkan.graph;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.spi.render.RenderPassContribution;

/**
 * Builder for Vulkan-local executable pass declarations.
 */
public final class VulkanExecutableRenderGraphBuilder {
    private final List<VulkanExecutablePassDeclaration> declarations = new ArrayList<>();

    public VulkanExecutableRenderGraphBuilder addPass(
            String featureId,
            RenderPassContribution contribution,
            Runnable executeCallback
    ) {
        declarations.add(new VulkanExecutablePassDeclaration(featureId, contribution, executeCallback));
        return this;
    }

    public List<VulkanExecutablePassDeclaration> build() {
        return List.copyOf(declarations);
    }
}
