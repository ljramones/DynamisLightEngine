package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.memory.VulkanMemoryOps;
import org.dynamislight.impl.vulkan.model.VulkanBufferAlloc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanDrawMetaBuffer {
    public static final int STRIDE_BYTES = 8 * Integer.BYTES;
    public static final int INVALID_INDEX = 0xFFFF_FFFF;

    private static final int DRAW_FLAG_SKINNED = 1 << 0;
    private static final int DRAW_FLAG_MORPH = 1 << 1;
    private static final int DRAW_FLAG_INSTANCED = 1 << 2;

    private final VkDevice device;
    private final long buffer;
    private final long memory;
    private final long mappedAddress;
    private final int capacity;
    private final int allocatedBytes;

    private VulkanDrawMetaBuffer(
            VkDevice device,
            long buffer,
            long memory,
            long mappedAddress,
            int capacity,
            int allocatedBytes
    ) {
        this.device = device;
        this.buffer = buffer;
        this.memory = memory;
        this.mappedAddress = mappedAddress;
        this.capacity = capacity;
        this.allocatedBytes = allocatedBytes;
    }

    public static VulkanDrawMetaBuffer create(VkDevice device, VkPhysicalDevice physicalDevice, int capacity) throws EngineException {
        if (device == null || physicalDevice == null) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Cannot create draw metadata buffer without Vulkan device and physical device",
                    false
            );
        }
        int safeCapacity = Math.max(1, capacity);
        int bytes = safeCapacity * STRIDE_BYTES;
        try (MemoryStack stack = stackPush()) {
            VulkanBufferAlloc alloc = VulkanMemoryOps.createBuffer(
                    device,
                    physicalDevice,
                    stack,
                    bytes,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );
            PointerBuffer mapped = stack.mallocPointer(1);
            int mapResult = vkMapMemory(device, alloc.memory(), 0, bytes, 0, mapped);
            if (mapResult != VK_SUCCESS || mapped.get(0) == 0L) {
                vkDestroyBuffer(device, alloc.buffer(), null);
                vkFreeMemory(device, alloc.memory(), null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkMapMemory(drawMetaBuffer) failed: " + mapResult,
                        false
                );
            }
            VulkanDrawMetaBuffer out = new VulkanDrawMetaBuffer(
                    device,
                    alloc.buffer(),
                    alloc.memory(),
                    mapped.get(0),
                    safeCapacity,
                    bytes
            );
            out.clear();
            return out;
        }
    }

    public int upload(List<VulkanRenderCommandRecorder.MeshDrawCmd> draws) {
        if (draws == null || draws.isEmpty()) {
            clear();
            return 0;
        }
        int count = Math.min(capacity, draws.size());
        ByteBuffer src = ByteBuffer.allocateDirect(count * STRIDE_BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++) {
            VulkanRenderCommandRecorder.MeshDrawCmd draw = draws.get(i);
            int flags = 0;
            if (draw.skinned()) {
                flags |= DRAW_FLAG_SKINNED;
            }
            if (draw.morphTargeted()) {
                flags |= DRAW_FLAG_MORPH;
            }
            if (draw.instanced()) {
                flags |= DRAW_FLAG_INSTANCED;
            }
            src.putInt(INVALID_INDEX); // jointPaletteIndex
            src.putInt(INVALID_INDEX); // morphDeltaIndex
            src.putInt(INVALID_INDEX); // morphWeightIndex
            src.putInt(INVALID_INDEX); // instanceDataIndex
            src.putInt(0);             // materialIndex (reserved)
            src.putInt(flags);         // drawFlags
            src.putInt(Math.max(0, draw.uniformMeshIndex())); // meshIndex
            src.putInt(0);             // reserved
        }
        src.flip();
        memCopy(memAddress(src), mappedAddress, src.remaining());
        return count;
    }

    public long bufferHandle() {
        return buffer;
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        if (mappedAddress == 0L || allocatedBytes <= 0) {
            return;
        }
        ByteBuffer mapped = memByteBuffer(mappedAddress, allocatedBytes);
        mapped.clear();
        while (mapped.hasRemaining()) {
            mapped.put((byte) 0);
        }
        mapped.clear();
    }

    public void destroy() {
        if (mappedAddress != 0L && memory != VK_NULL_HANDLE) {
            vkUnmapMemory(device, memory);
        }
        if (buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, buffer, null);
        }
        if (memory != VK_NULL_HANDLE) {
            vkFreeMemory(device, memory, null);
        }
    }
}
