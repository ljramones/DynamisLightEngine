package org.dynamislight.api.resource;

/**
 * Descriptor for a resource in the runtime environment.
 * This record provides metadata about a resource, including its identifier, type,
 * source path, and hot-reloading capability. It is used as a key entity to manage
 * and track resources across engine operations such as acquisition, reloading,
 * and cache management.
 *
 * Fields:
 *
 * - {@code id}: Unique identifier for the resource, represented by a {@link ResourceId}.
 *   This identifier is critical for referencing the resource in runtime operations.
 *
 * - {@code type}: Categorization of the resource to assist with cache and ownership
 *   tracking. The type is defined using the {@link ResourceType} enum, which includes
 *   options such as MESH, MATERIAL, TEXTURE, and others.
 *
 * - {@code sourcePath}: The original file path or source identifier from which the resource
 *   can be loaded. This path is essential for resolving the resource from storage or remote
 *   locations.
 *
 * - {@code hotReloadable}: Indicates whether the resource supports hot reloading. When
 *   true, runtime mechanisms (such as file watchers) may automatically reload the resource
 *   upon detecting changes in its source.
 */
public record ResourceDescriptor(
        ResourceId id,
        ResourceType type,
        String sourcePath,
        boolean hotReloadable
) {
}
