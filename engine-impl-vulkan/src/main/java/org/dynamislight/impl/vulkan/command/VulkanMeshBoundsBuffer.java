package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamisgpu.api.error.GpuException;
import org.dynamisgpu.vulkan.memory.VulkanMemoryOps;
import org.dynamisgpu.vulkan.memory.VulkanBufferAlloc;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
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

public final class VulkanMeshBoundsBuffer {
    public static final int STRIDE_BYTES = 8 * Integer.BYTES;

    private final VkDevice device;
    private final long buffer;
    private final long memory;
    private final long mappedAddress;
    private final int capacity;
    private final int allocatedBytes;

    private VulkanMeshBoundsBuffer(
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

    public static VulkanMeshBoundsBuffer create(VkDevice device, VkPhysicalDevice physicalDevice, int capacity) throws EngineException {
        if (device == null || physicalDevice == null) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Cannot create mesh bounds buffer without Vulkan device and physical device",
                    false
            );
        }
        int safeCapacity = Math.max(1, capacity);
        int bytes = safeCapacity * STRIDE_BYTES;
        try (MemoryStack stack = stackPush()) {
            VulkanBufferAlloc alloc;
            try {
                alloc = VulkanMemoryOps.createBuffer(
                        device,
                        physicalDevice,
                        stack,
                        bytes,
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                );
            } catch (GpuException ex) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Failed to create mesh bounds buffer: " + ex.getMessage(),
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
                        "vkMapMemory(meshBoundsBuffer) failed: " + mapResult,
                        false
                );
            }
            VulkanMeshBoundsBuffer out = new VulkanMeshBoundsBuffer(
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

    public void upload(List<VulkanGpuMesh> meshes) {
        if (meshes == null || meshes.isEmpty()) {
            clear();
            return;
        }
        int count = Math.min(meshes.size(), capacity);
        ByteBuffer src = ByteBuffer.allocateDirect(count * STRIDE_BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++) {
            VulkanGpuMesh mesh = meshes.get(i);
            if (mesh == null) {
                putBounds(src, 0f, 0f, 0f, 0f, i);
                continue;
            }
            float[] model = mesh.modelMatrix;
            float cx = mesh.localBoundsCenterX;
            float cy = mesh.localBoundsCenterY;
            float cz = mesh.localBoundsCenterZ;
            float wcx = model[0] * cx + model[4] * cy + model[8] * cz + model[12];
            float wcy = model[1] * cx + model[5] * cy + model[9] * cz + model[13];
            float wcz = model[2] * cx + model[6] * cy + model[10] * cz + model[14];

            float sx = (float) Math.sqrt((model[0] * model[0]) + (model[1] * model[1]) + (model[2] * model[2]));
            float sy = (float) Math.sqrt((model[4] * model[4]) + (model[5] * model[5]) + (model[6] * model[6]));
            float sz = (float) Math.sqrt((model[8] * model[8]) + (model[9] * model[9]) + (model[10] * model[10]));
            float scale = Math.max(sx, Math.max(sy, sz));
            float wr = Math.max(0.0001f, mesh.localBoundsRadius * Math.max(scale, 0.0001f));
            putBounds(src, wcx, wcy, wcz, wr, i);
        }
        src.flip();
        memCopy(memAddress(src), mappedAddress, src.remaining());
    }

    private static void putBounds(ByteBuffer dst, float x, float y, float z, float radius, int meshIndex) {
        dst.putFloat(x);
        dst.putFloat(y);
        dst.putFloat(z);
        dst.putFloat(radius);
        dst.putInt(meshIndex);
        dst.putInt(0);
        dst.putInt(0);
        dst.putInt(0);
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

    public long bufferHandle() {
        return buffer;
    }

    public int capacity() {
        return capacity;
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
