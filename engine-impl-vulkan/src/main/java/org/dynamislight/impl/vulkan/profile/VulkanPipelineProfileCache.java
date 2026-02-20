package org.dynamislight.impl.vulkan.profile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase C.2.2/C.3 cache for compiled pipeline profiles.
 */
public final class VulkanPipelineProfileCache {
    private final Map<VulkanPipelineProfileKey, VulkanPipelineProfileCompilation> cache = new ConcurrentHashMap<>();

    public VulkanPipelineProfileCompilation getOrCompile(VulkanPipelineProfileKey key) {
        VulkanPipelineProfileKey effective = key == null ? VulkanPipelineProfileKey.defaults() : key;
        return cache.computeIfAbsent(effective, VulkanPipelineProfileCompiler::compile);
    }

    public void clear() {
        cache.clear();
    }
}
