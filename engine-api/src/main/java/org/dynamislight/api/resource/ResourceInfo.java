package org.dynamislight.api.resource;

/**
 * Represents detailed runtime information for a managed resource.
 *
 * This record encapsulates metadata and state information about a resource
 * tracked within the system. It provides insights into the resource's current
 * load state, reference count, error status, and other runtime properties.
 *
 * Fields:
 *
 * - {@code descriptor}:
 *   The {@link ResourceDescriptor} storing static metadata about the resource,
 *   including its identifier, type, source path, and hot-reload status.
 *
 * - {@code state}:
 *   The {@link ResourceState} of the resource, representing its runtime load state.
 *   Possible values are UNLOADED, LOADED, or FAILED.
 *
 * - {@code refCount}:
 *   An integer representing the number of active references to this resource.
 *   This is used for tracking resource usage and lifetime management.
 *
 * - {@code lastLoadedEpochMs}:
 *   A timestamp (in milliseconds since the epoch) representing the last time
 *   the resource was successfully loaded.
 *
 * - {@code errorMessage}:
 *   A human-readable message describing the most recent error encountered
 *   during resource loading or reloading. This field is null if no errors are present.
 *
 * - {@code resolvedPath}:
 *   The fully resolved path used to load the resource. This path may differ
 *   from the original source path in the {@code ResourceDescriptor} if
 *   symbolic links, runtime adjustments, or other resolution mechanisms are applied.
 *
 * - {@code lastChecksum}:
 *   A checksum representing the most recent state of the resource's content.
 *   This value is used for change detection, particularly for enabling
 *   hot-reloading of resources.
 */
public record ResourceInfo(
        ResourceDescriptor descriptor,
        ResourceState state,
        int refCount,
        long lastLoadedEpochMs,
        String errorMessage,
        String resolvedPath,
        String lastChecksum
) {
}
