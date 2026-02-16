package org.dynamislight.api;

/**
 * Runtime resource cache telemetry snapshot.
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
