package org.dynamislight.impl.vulkan.descriptor;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.memory.VulkanMemoryOps;
import org.dynamislight.impl.vulkan.model.VulkanBufferAlloc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

public final class VulkanDescriptorResources {
    private VulkanDescriptorResources() {
    }

    public static Allocation create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack,
            int framesInFlight,
            int maxDynamicSceneObjects,
            int maxReflectionProbes,
            int objectUniformBytes,
            int globalSceneUniformBytes
    ) throws EngineException {
        long descriptorSetLayout = createMainDescriptorSetLayout(device, stack);
        long textureDescriptorSetLayout = createTextureDescriptorSetLayout(device, stack);

        VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
        VK10.vkGetPhysicalDeviceProperties(physicalDevice, props);
        long minAlign = Math.max(1L, props.limits().minUniformBufferOffsetAlignment());
        int uniformStrideBytes = alignUp(objectUniformBytes, (int) Math.min(Integer.MAX_VALUE, minAlign));
        int uniformFrameSpanBytes = uniformStrideBytes * maxDynamicSceneObjects;
        int globalUniformFrameSpanBytes = alignUp(globalSceneUniformBytes, (int) Math.min(Integer.MAX_VALUE, minAlign));
        int totalObjectUniformBytes = uniformFrameSpanBytes * framesInFlight;
        int totalGlobalUniformBytes = globalUniformFrameSpanBytes * framesInFlight;

        VulkanBufferAlloc objectUniformDeviceAlloc = VulkanMemoryOps.createBuffer(
                device,
                physicalDevice,
                stack,
                totalObjectUniformBytes,
                VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        VulkanBufferAlloc objectUniformStagingAlloc = VulkanMemoryOps.createBuffer(
                device,
                physicalDevice,
                stack,
                totalObjectUniformBytes,
                VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        PointerBuffer pObjectMapped = stack.mallocPointer(1);
        int mapObjectStagingResult = vkMapMemory(device, objectUniformStagingAlloc.memory(), 0, totalObjectUniformBytes, 0, pObjectMapped);
        if (mapObjectStagingResult != VK_SUCCESS || pObjectMapped.get(0) == 0L) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkMapMemory(objectStagingPersistent) failed: " + mapObjectStagingResult,
                    false
            );
        }
        long objectUniformStagingMappedAddress = pObjectMapped.get(0);

        VulkanBufferAlloc globalUniformDeviceAlloc = VulkanMemoryOps.createBuffer(
                device,
                physicalDevice,
                stack,
                totalGlobalUniformBytes,
                VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        VulkanBufferAlloc globalUniformStagingAlloc = VulkanMemoryOps.createBuffer(
                device,
                physicalDevice,
                stack,
                totalGlobalUniformBytes,
                VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        PointerBuffer pGlobalMapped = stack.mallocPointer(1);
        int mapGlobalStagingResult = vkMapMemory(device, globalUniformStagingAlloc.memory(), 0, totalGlobalUniformBytes, 0, pGlobalMapped);
        if (mapGlobalStagingResult != VK_SUCCESS || pGlobalMapped.get(0) == 0L) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkMapMemory(globalStagingPersistent) failed: " + mapGlobalStagingResult,
                    false
            );
        }
        long sceneGlobalUniformStagingMappedAddress = pGlobalMapped.get(0);

        int reflectionProbeMetadataMaxCount = Math.max(1, maxReflectionProbes);
        int reflectionProbeMetadataStrideBytes = 80;
        int reflectionProbeMetadataBufferBytes = 16 + (reflectionProbeMetadataMaxCount * reflectionProbeMetadataStrideBytes);
        VulkanBufferAlloc reflectionProbeMetadataAlloc = VulkanMemoryOps.createBuffer(
                device,
                physicalDevice,
                stack,
                reflectionProbeMetadataBufferBytes,
                VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        );
        PointerBuffer pProbeMapped = stack.mallocPointer(1);
        int mapProbeResult = vkMapMemory(
                device,
                reflectionProbeMetadataAlloc.memory(),
                0,
                reflectionProbeMetadataBufferBytes,
                0,
                pProbeMapped
        );
        if (mapProbeResult != VK_SUCCESS || pProbeMapped.get(0) == 0L) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkMapMemory(reflectionProbeMetadataPersistent) failed: " + mapProbeResult,
                    false
            );
        }
        long reflectionProbeMetadataMappedAddress = pProbeMapped.get(0);

        long descriptorPool = createMainDescriptorPool(device, stack, framesInFlight);
        long[] frameDescriptorSets = allocateFrameDescriptorSets(device, stack, descriptorPool, descriptorSetLayout, framesInFlight);
        updateFrameDescriptorSets(
                device,
                stack,
                frameDescriptorSets,
                globalUniformDeviceAlloc.buffer(),
                objectUniformDeviceAlloc.buffer(),
                reflectionProbeMetadataAlloc.buffer(),
                globalUniformFrameSpanBytes,
                uniformFrameSpanBytes,
                globalSceneUniformBytes,
                objectUniformBytes,
                framesInFlight
        );

        long estimatedGpuMemoryBytes = ((long) totalObjectUniformBytes * 2L)
                + ((long) totalGlobalUniformBytes * 2L)
                + reflectionProbeMetadataBufferBytes;
        return new Allocation(
                descriptorSetLayout,
                textureDescriptorSetLayout,
                descriptorPool,
                frameDescriptorSets,
                objectUniformDeviceAlloc.buffer(),
                objectUniformDeviceAlloc.memory(),
                objectUniformStagingAlloc.buffer(),
                objectUniformStagingAlloc.memory(),
                objectUniformStagingMappedAddress,
                globalUniformDeviceAlloc.buffer(),
                globalUniformDeviceAlloc.memory(),
                globalUniformStagingAlloc.buffer(),
                globalUniformStagingAlloc.memory(),
                sceneGlobalUniformStagingMappedAddress,
                reflectionProbeMetadataAlloc.buffer(),
                reflectionProbeMetadataAlloc.memory(),
                reflectionProbeMetadataMappedAddress,
                reflectionProbeMetadataMaxCount,
                reflectionProbeMetadataStrideBytes,
                reflectionProbeMetadataBufferBytes,
                uniformStrideBytes,
                uniformFrameSpanBytes,
                globalUniformFrameSpanBytes,
                estimatedGpuMemoryBytes
        );
    }

    public static void destroy(VkDevice device, Allocation resources) {
        if (device == null || resources == null) {
            return;
        }
        if (resources.objectUniformBuffer() != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, resources.objectUniformBuffer(), null);
        }
        if (resources.objectUniformMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.objectUniformMemory(), null);
        }
        if (resources.objectUniformStagingBuffer() != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, resources.objectUniformStagingBuffer(), null);
        }
        if (resources.objectUniformStagingMappedAddress() != 0L && resources.objectUniformStagingMemory() != VK_NULL_HANDLE) {
            vkUnmapMemory(device, resources.objectUniformStagingMemory());
        }
        if (resources.objectUniformStagingMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.objectUniformStagingMemory(), null);
        }
        if (resources.sceneGlobalUniformBuffer() != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, resources.sceneGlobalUniformBuffer(), null);
        }
        if (resources.sceneGlobalUniformMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.sceneGlobalUniformMemory(), null);
        }
        if (resources.sceneGlobalUniformStagingBuffer() != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, resources.sceneGlobalUniformStagingBuffer(), null);
        }
        if (resources.sceneGlobalUniformStagingMappedAddress() != 0L && resources.sceneGlobalUniformStagingMemory() != VK_NULL_HANDLE) {
            vkUnmapMemory(device, resources.sceneGlobalUniformStagingMemory());
        }
        if (resources.sceneGlobalUniformStagingMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.sceneGlobalUniformStagingMemory(), null);
        }
        if (resources.reflectionProbeMetadataBuffer() != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, resources.reflectionProbeMetadataBuffer(), null);
        }
        if (resources.reflectionProbeMetadataMappedAddress() != 0L && resources.reflectionProbeMetadataMemory() != VK_NULL_HANDLE) {
            vkUnmapMemory(device, resources.reflectionProbeMetadataMemory());
        }
        if (resources.reflectionProbeMetadataMemory() != VK_NULL_HANDLE) {
            vkFreeMemory(device, resources.reflectionProbeMetadataMemory(), null);
        }
        if (resources.descriptorPool() != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, resources.descriptorPool(), null);
        }
        if (resources.descriptorSetLayout() != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, resources.descriptorSetLayout(), null);
        }
        if (resources.textureDescriptorSetLayout() != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, resources.textureDescriptorSetLayout(), null);
        }
    }

    private static long createMainDescriptorSetLayout(VkDevice device, MemoryStack stack) throws EngineException {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(3, stack);
        bindings.get(0)
                .binding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT | VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        bindings.get(1)
                .binding(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT | VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        bindings.get(2)
                .binding(2)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);

        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings);
        var pLayout = stack.longs(VK_NULL_HANDLE);
        int layoutResult = vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout);
        if (layoutResult != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateDescriptorSetLayout failed: " + layoutResult, false);
        }
        return pLayout.get(0);
    }

    private static long createTextureDescriptorSetLayout(VkDevice device, MemoryStack stack) throws EngineException {
        VkDescriptorSetLayoutBinding.Buffer textureBindings = VkDescriptorSetLayoutBinding.calloc(9, stack);
        for (int i = 0; i < 9; i++) {
            textureBindings.get(i)
                    .binding(i)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        }
        VkDescriptorSetLayoutCreateInfo textureLayoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(textureBindings);
        var pTextureLayout = stack.longs(VK_NULL_HANDLE);
        int textureLayoutResult = vkCreateDescriptorSetLayout(device, textureLayoutInfo, null, pTextureLayout);
        if (textureLayoutResult != VK_SUCCESS || pTextureLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDescriptorSetLayout(texture) failed: " + textureLayoutResult,
                    false
            );
        }
        return pTextureLayout.get(0);
    }

    private static long createMainDescriptorPool(VkDevice device, MemoryStack stack, int framesInFlight) throws EngineException {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(3, stack);
        poolSizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(framesInFlight);
        poolSizes.get(1)
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                .descriptorCount(framesInFlight);
        poolSizes.get(2)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(framesInFlight);
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(framesInFlight)
                .pPoolSizes(poolSizes);
        var pPool = stack.longs(VK_NULL_HANDLE);
        int poolResult = vkCreateDescriptorPool(device, poolInfo, null, pPool);
        if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateDescriptorPool failed: " + poolResult, false);
        }
        return pPool.get(0);
    }

    private static long[] allocateFrameDescriptorSets(
            VkDevice device,
            MemoryStack stack,
            long descriptorPool,
            long descriptorSetLayout,
            int framesInFlight
    ) throws EngineException {
        java.nio.LongBuffer layouts = stack.mallocLong(framesInFlight);
        for (int i = 0; i < framesInFlight; i++) {
            layouts.put(i, descriptorSetLayout);
        }
        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(layouts);
        java.nio.LongBuffer pSet = stack.mallocLong(framesInFlight);
        int setResult = vkAllocateDescriptorSets(device, allocInfo, pSet);
        if (setResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateDescriptorSets failed: " + setResult, false);
        }
        long[] frameDescriptorSets = new long[framesInFlight];
        for (int i = 0; i < framesInFlight; i++) {
            frameDescriptorSets[i] = pSet.get(i);
        }
        return frameDescriptorSets;
    }

    private static void updateFrameDescriptorSets(
            VkDevice device,
            MemoryStack stack,
            long[] frameDescriptorSets,
            long sceneGlobalUniformBuffer,
            long objectUniformBuffer,
            long reflectionProbeMetadataBuffer,
            int globalUniformFrameSpanBytes,
            int uniformFrameSpanBytes,
            int globalSceneUniformBytes,
            int objectUniformBytes,
            int framesInFlight
    ) {
        VkDescriptorBufferInfo.Buffer globalBufferInfos = VkDescriptorBufferInfo.calloc(framesInFlight, stack);
        VkDescriptorBufferInfo.Buffer objectBufferInfos = VkDescriptorBufferInfo.calloc(framesInFlight, stack);
        VkDescriptorBufferInfo.Buffer probeBufferInfos = VkDescriptorBufferInfo.calloc(framesInFlight, stack);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(framesInFlight * 3, stack);
        for (int i = 0; i < framesInFlight; i++) {
            long globalFrameBase = (long) i * globalUniformFrameSpanBytes;
            long objectFrameBase = (long) i * uniformFrameSpanBytes;
            globalBufferInfos.get(i)
                    .buffer(sceneGlobalUniformBuffer)
                    .offset(globalFrameBase)
                    .range(globalSceneUniformBytes);
            objectBufferInfos.get(i)
                    .buffer(objectUniformBuffer)
                    .offset(objectFrameBase)
                    .range(objectUniformBytes);
            probeBufferInfos.get(i)
                    .buffer(reflectionProbeMetadataBuffer)
                    .offset(0)
                    .range(VK10.VK_WHOLE_SIZE);
            writes.get(i * 3)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(frameDescriptorSets[i])
                    .dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).put(0, globalBufferInfos.get(i)));
            writes.get((i * 3) + 1)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(frameDescriptorSets[i])
                    .dstBinding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).put(0, objectBufferInfos.get(i)));
            writes.get((i * 3) + 2)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(frameDescriptorSets[i])
                    .dstBinding(2)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).put(0, probeBufferInfos.get(i)));
        }
        vkUpdateDescriptorSets(device, writes, null);
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

    public record Allocation(
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            long descriptorPool,
            long[] frameDescriptorSets,
            long objectUniformBuffer,
            long objectUniformMemory,
            long objectUniformStagingBuffer,
            long objectUniformStagingMemory,
            long objectUniformStagingMappedAddress,
            long sceneGlobalUniformBuffer,
            long sceneGlobalUniformMemory,
            long sceneGlobalUniformStagingBuffer,
            long sceneGlobalUniformStagingMemory,
            long sceneGlobalUniformStagingMappedAddress,
            long reflectionProbeMetadataBuffer,
            long reflectionProbeMetadataMemory,
            long reflectionProbeMetadataMappedAddress,
            int reflectionProbeMetadataMaxCount,
            int reflectionProbeMetadataStrideBytes,
            int reflectionProbeMetadataBufferBytes,
            int uniformStrideBytes,
            int uniformFrameSpanBytes,
            int globalUniformFrameSpanBytes,
            long estimatedGpuMemoryBytes
    ) {
    }
}
