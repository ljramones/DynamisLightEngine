package org.dynamislight.impl.vulkan.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorResources;
import org.dynamislight.impl.vulkan.memory.VulkanMemoryOps;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;

public final class VulkanInstanceBatchBuffer {
    public static final int INSTANCE_STRIDE_BYTES = (16 * Float.BYTES) + (4 * Integer.BYTES);
    private static final int DEFAULT_FLAGS = 1;

    private final VkDevice device;
    private final long buffer;
    private final long memory;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long mappedAddress;
    private final int capacity;
    private final int allocatedBytes;

    private VulkanInstanceBatchBuffer(
            VkDevice device,
            long buffer,
            long memory,
            long descriptorPool,
            long descriptorSet,
            long mappedAddress,
            int capacity,
            int allocatedBytes
    ) {
        this.device = device;
        this.buffer = buffer;
        this.memory = memory;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.mappedAddress = mappedAddress;
        this.capacity = capacity;
        this.allocatedBytes = allocatedBytes;
    }

    public static VulkanInstanceBatchBuffer create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            long skinnedDescriptorSetLayout,
            int instanceCapacity
    ) throws EngineException {
        if (device == null || physicalDevice == null || skinnedDescriptorSetLayout == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Cannot create instance batch buffer without device, physical device, and descriptor layout",
                    false
            );
        }
        int safeCapacity = Math.max(1, instanceCapacity);
        int rawBytes = safeCapacity * INSTANCE_STRIDE_BYTES;
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, props);
            long minAlign = Math.max(1L, props.limits().minStorageBufferOffsetAlignment());
            int allocBytes = alignUp(rawBytes, (int) Math.min(Integer.MAX_VALUE, minAlign));
            VulkanBufferAlloc alloc = VulkanMemoryOps.createBuffer(
                    device,
                    physicalDevice,
                    stack,
                    allocBytes,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );
            PointerBuffer mapped = stack.mallocPointer(1);
            int mapResult = vkMapMemory(device, alloc.memory(), 0, allocBytes, 0, mapped);
            if (mapResult != VK_SUCCESS || mapped.get(0) == 0L) {
                vkDestroyBuffer(device, alloc.buffer(), null);
                vkFreeMemory(device, alloc.memory(), null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkMapMemory(instanceBatchBuffer) failed: " + mapResult,
                        false
                );
            }
            long pool = createDescriptorPool(device, stack);
            long set = allocateDescriptorSet(device, stack, pool, skinnedDescriptorSetLayout);
            VulkanDescriptorResources.writeInstanceBatchDescriptorSet(device, stack, set, alloc.buffer(), allocBytes);
            return new VulkanInstanceBatchBuffer(
                    device,
                    alloc.buffer(),
                    alloc.memory(),
                    pool,
                    set,
                    mapped.get(0),
                    safeCapacity,
                    allocBytes
            );
        }
    }

    public void upload(float[][] modelMatrices, int[] materialIndices, int[] flags) {
        if (modelMatrices == null) {
            throw new IllegalArgumentException("modelMatrices must not be null");
        }
        if (modelMatrices.length > capacity) {
            throw new IllegalArgumentException("instance count exceeds batch capacity");
        }
        if (materialIndices != null && materialIndices.length < modelMatrices.length) {
            throw new IllegalArgumentException("materialIndices must be null or match model matrix count");
        }
        if (flags != null && flags.length < modelMatrices.length) {
            throw new IllegalArgumentException("flags must be null or match model matrix count");
        }
        int byteCount = modelMatrices.length * INSTANCE_STRIDE_BYTES;
        if (byteCount > allocatedBytes) {
            throw new IllegalArgumentException("instance payload exceeds allocated SSBO capacity");
        }
        ByteBuffer src = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
        for (int i = 0; i < modelMatrices.length; i++) {
            float[] matrix = modelMatrices[i];
            if (matrix == null || matrix.length != 16) {
                throw new IllegalArgumentException("Each model matrix must contain exactly 16 floats");
            }
            for (int j = 0; j < 16; j++) {
                src.putFloat(matrix[j]);
            }
            src.putInt(materialIndices == null ? 0 : materialIndices[i]);
            src.putInt(flags == null ? DEFAULT_FLAGS : flags[i]);
            src.putInt(0);
            src.putInt(0);
        }
        src.flip();
        memCopy(memAddress(src), mappedAddress, byteCount);
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
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null);
        }
    }

    public long descriptorSetHandle() {
        return descriptorSet;
    }

    public int capacity() {
        return capacity;
    }

    private static long createDescriptorPool(VkDevice device, MemoryStack stack) throws EngineException {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
        poolSizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1);
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(1)
                .pPoolSizes(poolSizes);
        var pPool = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateDescriptorPool(device, poolInfo, null, pPool);
        if (result != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDescriptorPool(instanceBatchBuffer) failed: " + result,
                    false
            );
        }
        return pPool.get(0);
    }

    private static long allocateDescriptorSet(
            VkDevice device,
            MemoryStack stack,
            long descriptorPool,
            long skinnedDescriptorSetLayout
    ) throws EngineException {
        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(stack.longs(skinnedDescriptorSetLayout));
        var pSet = stack.longs(VK_NULL_HANDLE);
        int result = vkAllocateDescriptorSets(device, allocInfo, pSet);
        if (result != VK_SUCCESS || pSet.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkAllocateDescriptorSets(instanceBatchBuffer) failed: " + result,
                    false
            );
        }
        return pSet.get(0);
    }

    private static int alignUp(int value, int alignment) {
        if (alignment <= 0) {
            return value;
        }
        int remainder = value % alignment;
        if (remainder == 0) {
            return value;
        }
        return value + (alignment - remainder);
    }
}
