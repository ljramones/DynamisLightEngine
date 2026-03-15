package org.dynamisengine.light.impl.vulkan.vfx;

import org.dynamisengine.gpu.vulkan.descriptor.VulkanBindlessDescriptorHeap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tracks VFX texture IDs to bindless heap slots with fallback support.
 */
public final class VulkanVfxTextureRegistry {
    private static final Logger LOG = Logger.getLogger(VulkanVfxTextureRegistry.class.getName());

    private final VulkanBindlessDescriptorHeap bindlessHeap;
    private final Map<String, Integer> slotMap = new ConcurrentHashMap<>();
    private int fallbackSlot = -1;

    public VulkanVfxTextureRegistry(VulkanBindlessDescriptorHeap bindlessHeap) {
        this.bindlessHeap = bindlessHeap == null
                ? VulkanBindlessDescriptorHeap.disabled()
                : bindlessHeap;
    }

    public void registerTexture(String id, long imageView, long sampler) {
        if (id == null || id.isBlank()) {
            return;
        }
        int slot = reserveSlot();
        slotMap.put(id, slot);
    }

    public int resolveSlot(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            return fallbackSlot;
        }
        Integer slot = slotMap.get(textureId);
        if (slot == null) {
            LOG.warning("VFX texture not registered: " + textureId + " — using fallback");
            return fallbackSlot;
        }
        return slot;
    }

    public void registerFallback(long device, Object memoryOps) {
        fallbackSlot = reserveSlot();
    }

    public int fallbackSlot() {
        return fallbackSlot;
    }

    private int reserveSlot() {
        if (!bindlessHeap.active()) {
            return slotMap.size();
        }
        long handle = bindlessHeap.allocate(VulkanBindlessDescriptorHeap.HeapType.INSTANCE_DATA);
        return (int) handle;
    }
}
