package org.dynamislight.api;

import java.util.List;

/**
 * Resource service exposed by a runtime for asset cache, hot reload, and ownership tracking.
 *
 * <p>Eviction policy (v1): no time-based eviction. Resources are retained while reference count is
 * positive and removed when released to zero.</p>
 */
public interface EngineResourceService {
    ResourceInfo acquire(ResourceDescriptor descriptor) throws EngineException;

    void release(ResourceId id);

    ResourceInfo reload(ResourceId id) throws EngineException;

    List<ResourceInfo> loadedResources();

    ResourceCacheStats stats();
}
