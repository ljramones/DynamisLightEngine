package org.dynamislight.impl.vulkan.graph;

import org.dynamislight.spi.render.RenderResourceType;

/**
 * Imported render-graph resource supplied outside pass producers.
 */
public record VulkanImportedResource(
        String resourceName,
        RenderResourceType resourceType,
        ResourceLifetime lifetime,
        ResourceProvider provider
) {
    public VulkanImportedResource {
        resourceName = resourceName == null ? "" : resourceName.trim();
        resourceType = resourceType == null ? RenderResourceType.ATTACHMENT : resourceType;
        lifetime = lifetime == null ? ResourceLifetime.PER_FRAME : lifetime;
        provider = provider == null ? ResourceProvider.CPU_UPLOAD : provider;
    }

    public enum ResourceLifetime {
        PER_FRAME,
        PERSISTENT
    }

    public enum ResourceProvider {
        CPU_UPLOAD,
        PREVIOUS_FRAME,
        EXTERNAL_SYSTEM
    }
}
