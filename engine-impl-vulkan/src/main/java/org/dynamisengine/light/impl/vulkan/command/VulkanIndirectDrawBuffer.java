package org.dynamisengine.light.impl.vulkan.command;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.dynamisengine.gpu.api.gpu.IndirectCommandBuffer;
import org.dynamisengine.light.api.error.EngineErrorCode;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.gpu.api.error.GpuException;
import org.dynamisengine.gpu.vulkan.memory.VulkanBufferOps;
import org.dynamisengine.gpu.vulkan.memory.VulkanBufferAlloc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;

public final class VulkanIndirectDrawBuffer implements IndirectCommandBuffer {
    public static final int COMMAND_STRIDE_BYTES = 5 * Integer.BYTES;
    public static final int VARIANT_COUNT = 5;
    public static final int VARIANT_STATIC = 0;
    public static final int VARIANT_MORPH = 1;
    public static final int VARIANT_SKINNED = 2;
    public static final int VARIANT_SKINNED_MORPH = 3;
    public static final int VARIANT_INSTANCED = 4;

    private static final int VARIANT_SHIFT = 29;
    private static final int FIRST_INSTANCE_MASK = (1 << VARIANT_SHIFT) - 1;

    private final VkDevice device;
    private final long buffer;
    private final long memory;
    private final long mappedAddress;
    private final int capacity;
    private final int allocatedBytes;
    private final Layout layout;
    private int commandCount;

    private VulkanIndirectDrawBuffer(
            VkDevice device,
            long buffer,
            long memory,
            long mappedAddress,
            int capacity,
            int allocatedBytes,
            Layout layout
    ) {
        this.device = device;
        this.buffer = buffer;
        this.memory = memory;
        this.mappedAddress = mappedAddress;
        this.capacity = capacity;
        this.allocatedBytes = allocatedBytes;
        this.layout = layout;
        this.commandCount = 0;
    }

    public static VulkanIndirectDrawBuffer create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            int capacity
    ) throws EngineException {
        if (device == null || physicalDevice == null) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Cannot create indirect draw buffer without Vulkan device and physical device",
                    false
            );
        }
        int safeCapacity = Math.max(1, capacity);
        int bytes = safeCapacity * COMMAND_STRIDE_BYTES;
        try (MemoryStack stack = stackPush()) {
            VulkanBufferAlloc alloc;
            try {
                alloc = VulkanBufferOps.createBuffer(
                        device,
                        physicalDevice,
                        stack,
                        bytes,
                        VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                );
            } catch (GpuException ex) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Failed to create indirect draw buffer: " + ex.getMessage(),
                        false
                );
            }
            PointerBuffer mapped = stack.mallocPointer(1);
            int mapResult = vkMapMemory(device, alloc.memory(), 0, bytes, 0, mapped);
            if (mapResult != VK_SUCCESS || mapped.get(0) == 0L) {
                vkDestroyBuffer(device, alloc.buffer(), null);
                vkFreeMemory(device, alloc.memory(), null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkMapMemory(indirectDrawBuffer) failed: " + mapResult,
                        false
                );
            }
            VulkanIndirectDrawBuffer result = new VulkanIndirectDrawBuffer(
                    device,
                    alloc.buffer(),
                    alloc.memory(),
                    mapped.get(0),
                    safeCapacity,
                    bytes,
                    Layout.create(safeCapacity)
            );
            result.clear();
            return result;
        }
    }

    public int upload(List<VulkanRenderCommandRecorder.MeshDrawCmd> draws) {
        if (draws == null || draws.isEmpty()) {
            clear();
            return 0;
        }
        int writeCount = Math.min(draws.size(), capacity);
        int bytes = writeCount * COMMAND_STRIDE_BYTES;
        ByteBuffer src = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        for (int i = 0; i < writeCount; i++) {
            VulkanRenderCommandRecorder.MeshDrawCmd draw = draws.get(i);
            int variant = variantOf(draw);
            int firstInstance = Math.max(0, draw.firstInstance()) & FIRST_INSTANCE_MASK;
            int encodedFirstInstance = (variant << VARIANT_SHIFT) | firstInstance;
            src.putInt(draw.indexCount());
            src.putInt(Math.max(1, draw.instanceCount()));
            src.putInt(0); // firstIndex
            src.putInt(0); // vertexOffset
            src.putInt(encodedFirstInstance);
        }
        src.flip();
        memCopy(memAddress(src), mappedAddress, bytes);
        commandCount = writeCount;
        return commandCount;
    }

    @Override
    public void writeCommand(int variant, int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        throw new UnsupportedOperationException("Use upload(drawList) for batched indirect command writes");
    }

    public long bufferHandle() {
        return buffer;
    }

    @Override
    public long countBufferHandle() {
        return VK_NULL_HANDLE;
    }

    public int commandCount() {
        return commandCount;
    }

    public int capacity() {
        return capacity;
    }

    public Layout layout() {
        return layout;
    }

    public int variantOffsetCommands(int variant) {
        return switch (variant) {
            case VARIANT_MORPH -> layout.morphOffsetCommands();
            case VARIANT_SKINNED -> layout.skinnedOffsetCommands();
            case VARIANT_SKINNED_MORPH -> layout.skinnedMorphOffsetCommands();
            case VARIANT_INSTANCED -> layout.instancedOffsetCommands();
            default -> layout.staticOffsetCommands();
        };
    }

    @Override
    public int variantOffset(int variant) {
        return variantOffsetCommands(variant);
    }

    public int variantCapacity(int variant) {
        return switch (variant) {
            case VARIANT_MORPH -> layout.morphCapacity();
            case VARIANT_SKINNED -> layout.skinnedCapacity();
            case VARIANT_SKINNED_MORPH -> layout.skinnedMorphCapacity();
            case VARIANT_INSTANCED -> layout.instancedCapacity();
            default -> layout.staticCapacity();
        };
    }

    public void clear() {
        commandCount = 0;
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

    private static int variantOf(VulkanRenderCommandRecorder.MeshDrawCmd draw) {
        if (draw == null) {
            return VARIANT_STATIC;
        }
        if (draw.instanced()) {
            return VARIANT_INSTANCED;
        }
        if (draw.skinned() && draw.morphTargeted()) {
            return VARIANT_SKINNED_MORPH;
        }
        if (draw.skinned()) {
            return VARIANT_SKINNED;
        }
        if (draw.morphTargeted()) {
            return VARIANT_MORPH;
        }
        return VARIANT_STATIC;
    }

    public record Layout(
            int totalCapacity,
            int staticCapacity,
            int morphCapacity,
            int skinnedCapacity,
            int skinnedMorphCapacity,
            int instancedCapacity,
            int staticOffsetCommands,
            int morphOffsetCommands,
            int skinnedOffsetCommands,
            int skinnedMorphOffsetCommands,
            int instancedOffsetCommands
    ) {
        static Layout create(int totalCapacity) {
            int safeTotal = Math.max(VARIANT_COUNT, totalCapacity);
            int base = Math.max(1, safeTotal / VARIANT_COUNT);
            int remainder = safeTotal - (base * VARIANT_COUNT);
            int staticCap = base + remainder;
            int morphCap = base;
            int skinnedCap = base;
            int skinnedMorphCap = base;
            int instancedCap = base;
            int staticOffset = 0;
            int morphOffset = staticOffset + staticCap;
            int skinnedOffset = morphOffset + morphCap;
            int skinnedMorphOffset = skinnedOffset + skinnedCap;
            int instancedOffset = skinnedMorphOffset + skinnedMorphCap;
            return new Layout(
                    safeTotal,
                    staticCap,
                    morphCap,
                    skinnedCap,
                    skinnedMorphCap,
                    instancedCap,
                    staticOffset,
                    morphOffset,
                    skinnedOffset,
                    skinnedMorphOffset,
                    instancedOffset
            );
        }
    }
}
