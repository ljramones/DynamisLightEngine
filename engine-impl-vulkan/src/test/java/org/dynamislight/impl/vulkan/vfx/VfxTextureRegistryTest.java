package org.dynamislight.impl.vulkan.vfx;

import org.dynamisgpu.vulkan.descriptor.VulkanBindlessDescriptorHeap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VfxTextureRegistryTest {

    @Test
    void resolveSlotReturnsCorrectIndexAfterRegister() {
        VulkanVfxTextureRegistry registry = new VulkanVfxTextureRegistry(VulkanBindlessDescriptorHeap.disabled());
        registry.registerTexture("fx/smoke", 1L, 2L);
        assertEquals(0, registry.resolveSlot("fx/smoke"));
    }

    @Test
    void resolveSlotReturnsFallbackForUnknownId() {
        VulkanVfxTextureRegistry registry = new VulkanVfxTextureRegistry(VulkanBindlessDescriptorHeap.disabled());
        registry.registerFallback(0L, null);
        assertEquals(registry.fallbackSlot(), registry.resolveSlot("unknown"));
    }

    @Test
    void registerTextureOverwritesPreviousSlot() {
        VulkanVfxTextureRegistry registry = new VulkanVfxTextureRegistry(VulkanBindlessDescriptorHeap.disabled());
        registry.registerTexture("fx/fire", 1L, 2L);
        int first = registry.resolveSlot("fx/fire");
        registry.registerTexture("fx/fire", 3L, 4L);
        int second = registry.resolveSlot("fx/fire");
        assertNotEquals(first, second);
    }

    @Test
    void fallbackSlotIsValidAfterRegisterFallback() {
        VulkanVfxTextureRegistry registry = new VulkanVfxTextureRegistry(VulkanBindlessDescriptorHeap.disabled());
        registry.registerFallback(0L, null);
        assertNotEquals(-1, registry.fallbackSlot());
    }

    @Test
    void registerFallbackCreatesNonNullSlot() {
        VulkanVfxTextureRegistry registry = new VulkanVfxTextureRegistry(VulkanBindlessDescriptorHeap.disabled());
        registry.registerFallback(123L, null);
        assertNotEquals(-1, registry.fallbackSlot());
    }
}
