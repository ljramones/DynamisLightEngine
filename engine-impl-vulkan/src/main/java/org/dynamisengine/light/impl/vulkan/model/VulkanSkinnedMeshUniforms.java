package org.dynamisengine.light.impl.vulkan.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.dynamisengine.gpu.api.gpu.JointPaletteBuffer;
import org.dynamisengine.gpu.api.error.GpuException;
import org.dynamisengine.light.api.error.EngineErrorCode;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.impl.vulkan.descriptor.VulkanDescriptorResources;
import org.dynamisengine.gpu.vulkan.memory.VulkanBufferOps;
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

public final class VulkanSkinnedMeshUniforms implements JointPaletteBuffer {
    private final VkDevice device;
    private final long buffer;
    private final long memory;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long mappedAddress;
    private final int allocatedBytes;
    private final int jointCount;

    private VulkanSkinnedMeshUniforms(
            VkDevice device,
            long buffer,
            long memory,
            long descriptorPool,
            long descriptorSet,
            long mappedAddress,
            int allocatedBytes,
            int jointCount
    ) {
        this.device = device;
        this.buffer = buffer;
        this.memory = memory;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.mappedAddress = mappedAddress;
        this.allocatedBytes = allocatedBytes;
        this.jointCount = jointCount;
    }

    public static VulkanSkinnedMeshUniforms create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            long skinnedDescriptorSetLayout,
            int jointCount
    ) throws EngineException {
        if (device == null || physicalDevice == null || skinnedDescriptorSetLayout == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Cannot create skinned uniforms without device, physical device, and descriptor layout",
                    false
            );
        }
        int safeJointCount = Math.max(1, jointCount);
        int rawBytes = safeJointCount * (16 * Float.BYTES);
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, props);
            long minAlign = Math.max(1L, props.limits().minStorageBufferOffsetAlignment());
            int allocBytes = alignUp(rawBytes, (int) Math.min(Integer.MAX_VALUE, minAlign));
            org.dynamisengine.gpu.vulkan.memory.VulkanBufferAlloc alloc;
            try {
                alloc = VulkanBufferOps.createBuffer(
                        device,
                        physicalDevice,
                        stack,
                        allocBytes,
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                );
            } catch (GpuException ex) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Failed to create skinned uniform buffer: " + ex.getMessage(),
                        false
                );
            }
            PointerBuffer mapped = stack.mallocPointer(1);
            int mapResult = vkMapMemory(device, alloc.memory(), 0, allocBytes, 0, mapped);
            if (mapResult != VK_SUCCESS || mapped.get(0) == 0L) {
                vkDestroyBuffer(device, alloc.buffer(), null);
                vkFreeMemory(device, alloc.memory(), null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkMapMemory(skinnedUniforms) failed: " + mapResult,
                        false
                );
            }
            long pool = createDescriptorPool(device, stack);
            long set = allocateDescriptorSet(device, stack, pool, skinnedDescriptorSetLayout);
            VulkanDescriptorResources.writeSkinnedMeshDescriptorSet(device, stack, set, alloc.buffer(), allocBytes);
            VulkanSkinnedMeshUniforms uniforms = new VulkanSkinnedMeshUniforms(
                    device,
                    alloc.buffer(),
                    alloc.memory(),
                    pool,
                    set,
                    mapped.get(0),
                    allocBytes,
                    safeJointCount
            );
            uniforms.upload(null);
            return uniforms;
        }
    }

    public void upload(float[] jointMatrices) {
        float[] source = jointMatrices;
        if (source == null || source.length < 16) {
            source = identity4x4();
        }
        int byteCount = source.length * Float.BYTES;
        if (byteCount > allocatedBytes) {
            throw new IllegalArgumentException("jointMatrices exceed allocated SSBO capacity");
        }
        ByteBuffer srcBytes = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
        FloatBuffer srcFloats = srcBytes.asFloatBuffer();
        srcFloats.put(source);
        srcBytes.limit(byteCount);
        memCopy(memAddress(srcBytes), mappedAddress, byteCount);
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

    public long bufferHandle() {
        return buffer;
    }

    public long descriptorSetHandle() {
        return descriptorSet;
    }

    @Override
    public int jointCount() {
        return jointCount;
    }

    private static long createDescriptorPool(VkDevice device, MemoryStack stack) throws EngineException {
        // Skinned descriptor set layout requires 4 bindings:
        // 3x STORAGE_BUFFER (joints, morph deltas, morph weights) + 1x UNIFORM_BUFFER
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
        poolSizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(3);
        poolSizes.get(1)
                .type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
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
                    "vkCreateDescriptorPool(skinnedUniforms) failed: " + result,
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
                    "vkAllocateDescriptorSets(skinnedUniforms) failed: " + result,
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

    private static float[] identity4x4() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }
}
