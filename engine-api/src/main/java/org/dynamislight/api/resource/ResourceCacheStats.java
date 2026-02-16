package org.dynamislight.api.resource;

/**
 * Immutable representation of resource cache statistics.
 * This record encapsulates telemetry data regarding the performance and usage of the
 * resource cache during engine runtime. It provides insights into cache efficiency,
 * resource reload activity, and eviction behavior.
 *
 * Fields:
 *
 * - {@code cacheHits}:
 *   The total number of successful cache lookups where a resource was found.
 *
 * - {@code cacheMisses}:
 *   The total number of cache lookups where a resource was not found, prompting an initial load.
 *
 * - {@code reloadRequests}:
 *   The total number of requests to reload resources from their source paths.
 *
 * - {@code reloadFailures}:
 *   The total number of resource reload requests that failed due to errors or invalid states.
 *
 * - {@code evictions}:
 *   The total number of resources evicted from the cache to reclaim memory or other resources.
 *
 * - {@code watcherEvents}:
 *   The total number of events triggered by resource watcher mechanisms, such as file changes
 *   for hot-reloadable resources.
 */
public record ResourceCacheStats(
        long cacheHits,
        long cacheMisses,
        long reloadRequests,
        long reloadFailures,
        long evictions,
        long watcherEvents
) {
}
