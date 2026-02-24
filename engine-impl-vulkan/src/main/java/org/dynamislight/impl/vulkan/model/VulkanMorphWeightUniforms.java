package org.dynamislight.impl.vulkan.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.dynamisgpu.api.gpu.WeightBuffer;
import org.dynamisgpu.api.error.GpuException;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorResources;
import org.dynamisgpu.vulkan.memory.VulkanMemoryOps;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
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

public final class VulkanMorphWeightUniforms implements WeightBuffer {
    public static final int MAX_WEIGHTS = 256;
    private static final int WEIGHT_BYTES = MAX_WEIGHTS * Float.BYTES;

    private final VkDevice device;
    private final long buffer;
    private final long memory;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long mappedAddress;

    private VulkanMorphWeightUniforms(
            VkDevice device,
            long buffer,
            long memory,
            long descriptorPool,
            long descriptorSet,
            long mappedAddress
    ) {
        this.device = device;
        this.buffer = buffer;
        this.memory = memory;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.mappedAddress = mappedAddress;
    }

    public static VulkanMorphWeightUniforms create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            long skinnedDescriptorSetLayout,
            long morphDeltaBuffer,
            long morphDeltaRangeBytes
    ) throws EngineException {
        if (device == null || physicalDevice == null || skinnedDescriptorSetLayout == VK_NULL_HANDLE || morphDeltaBuffer == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Cannot create morph weights without device, physical device, descriptor layout, and morph delta buffer",
                    false
            );
        }
        try (MemoryStack stack = stackPush()) {
            org.dynamisgpu.vulkan.memory.VulkanBufferAlloc alloc;
            try {
                alloc = VulkanMemoryOps.createBuffer(
                        device,
                        physicalDevice,
                        stack,
                        WEIGHT_BYTES,
                        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                );
            } catch (GpuException ex) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Failed to create morph weight buffer: " + ex.getMessage(),
                        false
                );
            }
            PointerBuffer mapped = stack.mallocPointer(1);
            int mapResult = vkMapMemory(device, alloc.memory(), 0, WEIGHT_BYTES, 0, mapped);
            if (mapResult != VK_SUCCESS || mapped.get(0) == 0L) {
                vkDestroyBuffer(device, alloc.buffer(), null);
                vkFreeMemory(device, alloc.memory(), null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkMapMemory(morphWeightUniforms) failed: " + mapResult,
                        false
                );
            }
            long pool = createDescriptorPool(device, stack);
            long set = allocateDescriptorSet(device, stack, pool, skinnedDescriptorSetLayout);
            VulkanDescriptorResources.writeMorphMeshDescriptorSet(
                    device,
                    stack,
                    set,
                    morphDeltaBuffer,
                    morphDeltaRangeBytes,
                    alloc.buffer(),
                    WEIGHT_BYTES
            );
            VulkanMorphWeightUniforms uniforms = new VulkanMorphWeightUniforms(
                    device,
                    alloc.buffer(),
                    alloc.memory(),
                    pool,
                    set,
                    mapped.get(0)
            );
            uniforms.upload(null);
            return uniforms;
        }
    }

    public void upload(float[] weights) {
        float[] source = weights == null ? new float[0] : weights;
        if (source.length > MAX_WEIGHTS) {
            throw new IllegalArgumentException("weights exceed maximum morph weight count of " + MAX_WEIGHTS);
        }
        ByteBuffer srcBytes = ByteBuffer.allocateDirect(WEIGHT_BYTES).order(ByteOrder.nativeOrder());
        FloatBuffer srcFloats = srcBytes.asFloatBuffer();
        srcFloats.put(source);
        srcBytes.limit(WEIGHT_BYTES);
        memCopy(memAddress(srcBytes), mappedAddress, WEIGHT_BYTES);
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

    private static long createDescriptorPool(VkDevice device, MemoryStack stack) throws EngineException {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
        poolSizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1);
        poolSizes.get(1)
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
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
                    "vkCreateDescriptorPool(morphWeightUniforms) failed: " + result,
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
                    "vkAllocateDescriptorSets(morphWeightUniforms) failed: " + result,
                    false
            );
        }
        return pSet.get(0);
    }
}
