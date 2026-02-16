package org.dynamislight.impl.vulkan.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.junit.jupiter.api.Test;

class VulkanSceneTextureCoordinatorTest {
    @Test
    void textureCacheKeyUsesExpectedPrefixes() {
        assertEquals("albedo:__default__", VulkanSceneTextureCoordinator.textureCacheKey(null, false));
        assertEquals("normal:__default__", VulkanSceneTextureCoordinator.textureCacheKey(null, true));
    }

    @Test
    void resolveOrCreateTextureReturnsDefaultWhenPathMissing() throws Exception {
        var defaultTexture = new VulkanGpuTexture(1L, 2L, 3L, 4L, 5L);
        var cache = new HashMap<String, VulkanGpuTexture>();
        var loaderCalls = new AtomicInteger();

        VulkanGpuTexture out = VulkanSceneTextureCoordinator.resolveOrCreateTexture(
                Path.of("does-not-exist.png"),
                cache,
                defaultTexture,
                false,
                (path, normalMap) -> {
                    loaderCalls.incrementAndGet();
                    return new VulkanGpuTexture(10L, 20L, 30L, 40L, 50L);
                }
        );

        assertSame(defaultTexture, out);
        assertEquals(0, loaderCalls.get());
        assertEquals(0, cache.size());
    }

    @Test
    void resolveOrCreateTextureCachesLoadedTextures() throws Exception {
        Path texturePath = Files.createTempFile("dle-vk-tex-", ".png");
        try {
            var defaultTexture = new VulkanGpuTexture(1L, 2L, 3L, 4L, 5L);
            var loadedTexture = new VulkanGpuTexture(11L, 12L, 13L, 14L, 15L);
            var cache = new HashMap<String, VulkanGpuTexture>();
            var loaderCalls = new AtomicInteger();

            VulkanGpuTexture first = VulkanSceneTextureCoordinator.resolveOrCreateTexture(
                    texturePath,
                    cache,
                    defaultTexture,
                    true,
                    (path, normalMap) -> {
                        loaderCalls.incrementAndGet();
                        return loadedTexture;
                    }
            );
            VulkanGpuTexture second = VulkanSceneTextureCoordinator.resolveOrCreateTexture(
                    texturePath,
                    cache,
                    defaultTexture,
                    true,
                    (path, normalMap) -> {
                        loaderCalls.incrementAndGet();
                        return new VulkanGpuTexture(21L, 22L, 23L, 24L, 25L);
                    }
            );

            assertSame(loadedTexture, first);
            assertSame(loadedTexture, second);
            assertEquals(1, loaderCalls.get());
            assertEquals(1, cache.size());
        } finally {
            Files.deleteIfExists(texturePath);
        }
    }
}
