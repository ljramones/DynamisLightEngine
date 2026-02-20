package org.dynamislight.impl.vulkan.profile;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class VulkanPipelineProfileCacheTest {
    @Test
    void cacheReusesCompilationForSameKey() {
        VulkanPipelineProfileCache cache = new VulkanPipelineProfileCache();
        VulkanPipelineProfileKey key = VulkanPipelineProfileKey.defaults();

        VulkanPipelineProfileCompilation a = cache.getOrCompile(key);
        VulkanPipelineProfileCompilation b = cache.getOrCompile(key);

        assertSame(a, b);
    }
}
