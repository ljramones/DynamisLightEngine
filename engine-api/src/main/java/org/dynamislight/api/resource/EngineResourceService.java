package org.dynamislight.api.resource;

import java.util.List;
import org.dynamislight.api.error.EngineException;

/**
 * Service interface for managing the lifecycle and state of engine resources
 * within a runtime environment. This service supports acquiring, releasing,
 * and reloading resources, as well as introspecting resource telemetry and
 * cache statistics.
 *
 * Methods:
 *
 * - {@link #acquire(ResourceDescriptor)}:
 *   Acquires and optionally loads a resource based on the given descriptor.
 *   Throws {@code EngineException} if the resource cannot be loaded correctly.
 *
 * - {@link #release(ResourceId)}:
 *   Releases a previously acquired resource using its {@code ResourceId}.
 *
 * - {@link #reload(ResourceId)}:
 *   Reloads the resource identified by its {@code ResourceId}, refreshing its
 *   state and data. Throws {@code EngineException} in case of errors.
 *
 * - {@link #loadedResources()}:
 *   Retrieves a list of all currently loaded resources, providing detailed
 *   metadata for each resource.
 *
 * - {@link #stats()}:
 *   Returns a snapshot of the runtime resource cache statistics, including
 *   details on cache hits, misses, reloads, and evictions.
 */
public interface EngineResourceService {
    /**
     * Acquires a runtime resource based on the provided descriptor. This method ensures
     * that the resource is properly loaded and available for use. If the resource cannot
     * be loaded, it throws an {@code EngineException}. The returned {@code ResourceInfo}
     * object contains details about the resource's state, reference count, resolved path,
     * and other metadata.
     *
     * @param descriptor the {@link ResourceDescriptor} providing details about the resource
     *                   to be acquired, including its type, source path, and whether it
     *                   supports hot reloading.
     * @return a {@link ResourceInfo} instance containing runtime status and metadata for
     *         the acquired resource.
     * @throws EngineException if the resource fails to load or if any errors occur during
     *                         acquisition.
     */
    ResourceInfo acquire(ResourceDescriptor descriptor) throws EngineException;

    /**
     * Releases the runtime resource identified by the specified {@code ResourceId}.
     * This method decreases the reference count of the resource and, if the count reaches zero,
     * unloads the resource from memory. Resources should be released when they are
     * no longer needed to efficiently manage memory and other system resources.
     *
     * @param id the {@link ResourceId} of the resource to release.
     */
    void release(ResourceId id);

    /**
     * Reloads the resource identified by the specified {@code ResourceId}. This operation ensures
     * that the resource is reloaded from its source, refreshing its state and data. If the resource
     * is not currently loaded or reload fails, an {@code EngineException} is thrown with details
     * about the failure.
     *
     * @param id the {@link ResourceId} of the resource to reload.
     * @return a {@link ResourceInfo} instance detailing the post-reload state of the resource,
     *         including its metadata and status.
     * @throws EngineException if the reload operation fails due to invalid resource state,
     *                         unavailability of the resource source, or other runtime errors.
     */
    ResourceInfo reload(ResourceId id) throws EngineException;

    /**
     * Retrieves a list of all currently loaded runtime resources.
     * Each {@code ResourceInfo} instance provides detailed metadata about a loaded resource,
     * including its state, descriptor, reference count, and other runtime-specific details.
     *
     * @return a list of {@code ResourceInfo} objects representing the currently loaded resources.
     */
    List<ResourceInfo> loadedResources();

    /**
     * Retrieves a snapshot of runtime resource cache statistics.
     * The returned {@link ResourceCacheStats} includes telemetry information such as
     * the number of cache hits, cache misses, reload requests, reload failures,
     * evictions, and watcher events. This provides insights into resource usage
     * and caching behavior during runtime operations.
     *
     * @return a {@link ResourceCacheStats} object containing runtime cache metrics.
     */
    ResourceCacheStats stats();
}
