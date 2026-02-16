package org.dynamislight.api.resource;

/**
 * Host/runtime descriptor for a resource and its source.
 */
public record ResourceDescriptor(
        ResourceId id,
        ResourceType type,
        String sourcePath,
        boolean hotReloadable
) {
}
