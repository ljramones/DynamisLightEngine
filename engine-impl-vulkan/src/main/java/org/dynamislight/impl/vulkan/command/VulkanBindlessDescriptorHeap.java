package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayDeque;
import java.nio.IntBuffer;
import java.util.logging.Logger;

import static org.lwjgl.vulkan.EXTDescriptorIndexing.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;
import static org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;

public final class VulkanBindlessDescriptorHeap {
    public static final int JOINT_CAPACITY = 8192;
    public static final int MORPH_DELTA_CAPACITY = 4096;
    public static final int MORPH_WEIGHT_CAPACITY = 4096;
    public static final int INSTANCE_CAPACITY = 4096;
    public static final int DRAW_META_CAPACITY = 8192;

    private static final Logger LOG = Logger.getLogger(VulkanBindlessDescriptorHeap.class.getName());

    private static final long SLOT_MASK = 0xFFFF_FFFFL;
    private static final long GEN_MASK = 0x00FF_FFFFL;
    private static final long TYPE_MASK = 0xFFL;

    private final VkDevice device;
    private final boolean active;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final int retirementFrames;

    private final TypeState jointState;
    private final TypeState morphDeltaState;
    private final TypeState morphWeightState;
    private final TypeState instanceState;

    private final ArrayDeque<Retirement> retirements = new ArrayDeque<>();
    private long allocationCount;
    private long freesQueuedCount;
    private long freesRetiredCount;
    private long staleHandleRejectCount;
    private int drawMetaCount;
    private int invalidIndexWriteCount;

    private VulkanBindlessDescriptorHeap(
            VkDevice device,
            boolean active,
            long descriptorSetLayout,
            long descriptorPool,
            long descriptorSet,
            int retirementFrames,
            TypeState jointState,
            TypeState morphDeltaState,
            TypeState morphWeightState,
            TypeState instanceState
    ) {
        this.device = device;
        this.active = active;
        this.descriptorSetLayout = descriptorSetLayout;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.retirementFrames = Math.max(1, retirementFrames);
        this.jointState = jointState;
        this.morphDeltaState = morphDeltaState;
        this.morphWeightState = morphWeightState;
        this.instanceState = instanceState;
    }

    public static VulkanBindlessDescriptorHeap create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            boolean requestedEnabled,
            int framesInFlight
    ) throws EngineException {
        if (!requestedEnabled || device == null || physicalDevice == null) {
            return disabled();
        }
        GateResult gate = checkDescriptorIndexingGate(physicalDevice);
        if (!gate.passed()) {
            LOG.warning("BINDLESS_DESCRIPTOR_INDEXING_UNAVAILABLE " + gate.reason());
            return disabled();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long layout = createDescriptorSetLayout(device, stack);
            long pool = createDescriptorPool(device, stack);
            long set = allocateDescriptorSet(device, stack, pool, layout);
            return new VulkanBindlessDescriptorHeap(
                    device,
                    true,
                    layout,
                    pool,
                    set,
                    Math.max(1, framesInFlight),
                    TypeState.create(HeapType.JOINT_PALETTE),
                    TypeState.create(HeapType.MORPH_DELTA),
                    TypeState.create(HeapType.MORPH_WEIGHT),
                    TypeState.create(HeapType.INSTANCE_DATA)
            );
        }
    }

    public static VulkanBindlessDescriptorHeap disabled() {
        return new VulkanBindlessDescriptorHeap(
                null,
                false,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                VK_NULL_HANDLE,
                1,
                TypeState.empty(HeapType.JOINT_PALETTE),
                TypeState.empty(HeapType.MORPH_DELTA),
                TypeState.empty(HeapType.MORPH_WEIGHT),
                TypeState.empty(HeapType.INSTANCE_DATA)
        );
    }

    public boolean active() {
        return active;
    }

    public long descriptorSetLayout() {
        return descriptorSetLayout;
    }

    public long descriptorPool() {
        return descriptorPool;
    }

    public long descriptorSet() {
        return descriptorSet;
    }

    public synchronized long allocate(HeapType type) {
        TypeState state = state(type);
        if (!active || state == null || state.top <= 0) {
            LOG.warning("BINDLESS_HEAP_CAPACITY_EXHAUSTED type=" + safeTypeName(type));
            return 0L;
        }
        int slot = state.freeStack[--state.top];
        int gen = state.generations[slot];
        if (gen <= 0) {
            gen = 1;
            state.generations[slot] = gen;
        }
        allocationCount++;
        return packHandle(type, gen, slot);
    }

    public synchronized void retire(long handle, long currentFrame) {
        if (!active || handle == 0L) {
            return;
        }
        HeapType type = unpackType(handle);
        TypeState state = state(type);
        int slot = unpackSlot(handle);
        int handleGen = unpackGeneration(handle);
        int currentGen = currentGeneration(state, slot);
        if (state == null || slot < 0 || slot >= state.capacity || handleGen != currentGen) {
            logStale(type, slot, handleGen, currentGen, currentFrame);
            return;
        }
        retirements.addLast(new Retirement(type, slot, handleGen, currentFrame + retirementFrames));
        freesQueuedCount++;
    }

    public synchronized int processRetirements(long currentFrame) {
        if (!active || retirements.isEmpty()) {
            return 0;
        }
        int processed = 0;
        int count = retirements.size();
        for (int i = 0; i < count; i++) {
            Retirement retirement = retirements.removeFirst();
            if (retirement.retireFrame > currentFrame) {
                retirements.addLast(retirement);
                continue;
            }
            TypeState state = state(retirement.type);
            if (state == null || retirement.slot < 0 || retirement.slot >= state.capacity) {
                continue;
            }
            int currentGen = state.generations[retirement.slot];
            if (currentGen != retirement.generation) {
                logStale(retirement.type, retirement.slot, retirement.generation, currentGen, currentFrame);
                continue;
            }
            state.generations[retirement.slot] = Math.max(1, currentGen + 1);
            if (state.top < state.freeStack.length) {
                state.freeStack[state.top++] = retirement.slot;
                processed++;
                freesRetiredCount++;
            }
        }
        return processed;
    }

    public synchronized int resolveSlot(long handle, long currentFrame) {
        if (!active || handle == 0L) {
            return -1;
        }
        HeapType type = unpackType(handle);
        TypeState state = state(type);
        int slot = unpackSlot(handle);
        int handleGen = unpackGeneration(handle);
        int currentGen = currentGeneration(state, slot);
        if (state == null || slot < 0 || slot >= state.capacity || handleGen != currentGen) {
            logStale(type, slot, handleGen, currentGen, currentFrame);
            return -1;
        }
        return slot;
    }

    public synchronized boolean updateJointPaletteDescriptor(long handle, long currentFrame, long bufferHandle, long rangeBytes) {
        if (!active || handle == 0L || bufferHandle == VK_NULL_HANDLE || rangeBytes <= 0L) {
            return false;
        }
        int slot = resolveSlot(handle, currentFrame);
        if (slot < 0) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0)
                    .buffer(bufferHandle)
                    .offset(0L)
                    .range(rangeBytes);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(0)
                    .dstArrayElement(slot)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pBufferInfo(bufferInfo);
            vkUpdateDescriptorSets(device, write, null);
            return true;
        }
    }

    public synchronized boolean updateMorphDeltaDescriptor(long handle, long currentFrame, long bufferHandle, long rangeBytes) {
        return updateStorageBufferDescriptor(handle, currentFrame, bufferHandle, rangeBytes, 1);
    }

    public synchronized boolean updateMorphWeightDescriptor(long handle, long currentFrame, long bufferHandle, long rangeBytes) {
        return updateUniformBufferDescriptor(handle, currentFrame, bufferHandle, rangeBytes, 2);
    }

    public synchronized boolean updateInstanceDataDescriptor(long handle, long currentFrame, long bufferHandle, long rangeBytes) {
        return updateStorageBufferDescriptor(handle, currentFrame, bufferHandle, rangeBytes, 3);
    }

    public synchronized void updateDrawMetaStats(int drawMetaCount, int invalidIndexWrites) {
        this.drawMetaCount = Math.max(0, drawMetaCount);
        this.invalidIndexWriteCount = Math.max(0, invalidIndexWrites);
    }

    public synchronized BindlessHeapStats stats() {
        return new BindlessHeapStats(
                usedCount(jointState),
                JOINT_CAPACITY,
                usedCount(morphDeltaState),
                MORPH_DELTA_CAPACITY,
                usedCount(morphWeightState),
                MORPH_WEIGHT_CAPACITY,
                usedCount(instanceState),
                INSTANCE_CAPACITY,
                allocationCount,
                freesQueuedCount,
                freesRetiredCount,
                staleHandleRejectCount,
                drawMetaCount,
                invalidIndexWriteCount
        );
    }

    public void destroy(VkDevice device) {
        if (device == null) {
            return;
        }
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null);
        }
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        }
    }

    private static long createDescriptorSetLayout(VkDevice device, MemoryStack stack) throws EngineException {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(5, stack);
        bindings.get(0)
                .binding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(JOINT_CAPACITY)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
        bindings.get(1)
                .binding(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(MORPH_DELTA_CAPACITY)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
        bindings.get(2)
                .binding(2)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(MORPH_WEIGHT_CAPACITY)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
        bindings.get(3)
                .binding(3)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(INSTANCE_CAPACITY)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
        bindings.get(4)
                .binding(4)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(DRAW_META_CAPACITY)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

        IntBuffer bindingFlags = stack.mallocInt(5);
        for (int i = 0; i < 5; i++) {
            bindingFlags.put(i,
                    VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT
                            | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT_EXT
            );
        }

        VkDescriptorSetLayoutBindingFlagsCreateInfoEXT flagsInfo = VkDescriptorSetLayoutBindingFlagsCreateInfoEXT.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO_EXT)
                .pBindingFlags(bindingFlags);

        VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT_EXT)
                .pBindings(bindings)
                .pNext(flagsInfo.address());

        var pLayout = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateDescriptorSetLayout(device, info, null, pLayout);
        if (result != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDescriptorSetLayout(bindless) failed: " + result,
                    false
            );
        }
        return pLayout.get(0);
    }

    private boolean updateStorageBufferDescriptor(
            long handle,
            long currentFrame,
            long bufferHandle,
            long rangeBytes,
            int binding
    ) {
        if (!active || handle == 0L || bufferHandle == VK_NULL_HANDLE || rangeBytes <= 0L) {
            return false;
        }
        int slot = resolveSlot(handle, currentFrame);
        if (slot < 0) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0)
                    .buffer(bufferHandle)
                    .offset(0L)
                    .range(rangeBytes);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(binding)
                    .dstArrayElement(slot)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pBufferInfo(bufferInfo);
            vkUpdateDescriptorSets(device, write, null);
            return true;
        }
    }

    private boolean updateUniformBufferDescriptor(
            long handle,
            long currentFrame,
            long bufferHandle,
            long rangeBytes,
            int binding
    ) {
        if (!active || handle == 0L || bufferHandle == VK_NULL_HANDLE || rangeBytes <= 0L) {
            return false;
        }
        int slot = resolveSlot(handle, currentFrame);
        if (slot < 0) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0)
                    .buffer(bufferHandle)
                    .offset(0L)
                    .range(rangeBytes);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(binding)
                    .dstArrayElement(slot)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pBufferInfo(bufferInfo);
            vkUpdateDescriptorSets(device, write, null);
            return true;
        }
    }

    private static long createDescriptorPool(VkDevice device, MemoryStack stack) throws EngineException {
        VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(2, stack);
        sizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(JOINT_CAPACITY + MORPH_DELTA_CAPACITY + INSTANCE_CAPACITY + DRAW_META_CAPACITY);
        sizes.get(1)
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(MORPH_WEIGHT_CAPACITY);

        VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT_EXT)
                .maxSets(1)
                .pPoolSizes(sizes);
        var pPool = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateDescriptorPool(device, info, null, pPool);
        if (result != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDescriptorPool(bindless) failed: " + result,
                    false
            );
        }
        return pPool.get(0);
    }

    private static long allocateDescriptorSet(VkDevice device, MemoryStack stack, long descriptorPool, long descriptorSetLayout)
            throws EngineException {
        VkDescriptorSetAllocateInfo info = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(stack.longs(descriptorSetLayout));
        var pSet = stack.longs(VK_NULL_HANDLE);
        int result = vkAllocateDescriptorSets(device, info, pSet);
        if (result != VK_SUCCESS || pSet.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkAllocateDescriptorSets(bindless) failed: " + result,
                    false
            );
        }
        return pSet.get(0);
    }

    private static GateResult checkDescriptorIndexingGate(VkPhysicalDevice physicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceDescriptorIndexingFeaturesEXT indexing = VkPhysicalDeviceDescriptorIndexingFeaturesEXT.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT);
            VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2)
                    .pNext(indexing.address());
            vkGetPhysicalDeviceFeatures2(physicalDevice, features2);

            VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(physicalDevice, props);

            StringBuilder reason = new StringBuilder();
            boolean ok = true;
            ok &= checkFeature(indexing.runtimeDescriptorArray(), "runtimeDescriptorArray", reason);
            ok &= checkFeature(indexing.descriptorBindingPartiallyBound(), "descriptorBindingPartiallyBound", reason);
            ok &= checkFeature(indexing.descriptorBindingVariableDescriptorCount(), "descriptorBindingVariableDescriptorCount", reason);
            ok &= checkFeature(indexing.descriptorBindingStorageBufferUpdateAfterBind(), "descriptorBindingStorageBufferUpdateAfterBind", reason);
            ok &= checkFeature(indexing.shaderStorageBufferArrayNonUniformIndexing(), "shaderStorageBufferArrayNonUniformIndexing", reason);
            ok &= checkFeature(indexing.shaderUniformBufferArrayNonUniformIndexing(), "shaderUniformBufferArrayNonUniformIndexing", reason);

            VkPhysicalDeviceLimits limits = props.limits();
            ok &= checkLimit(limits.maxBoundDescriptorSets(), 4, "maxBoundDescriptorSets", reason);
            ok &= checkLimit(limits.maxPerStageDescriptorStorageBuffers(), 1024, "maxPerStageDescriptorStorageBuffers", reason);
            ok &= checkLimit(limits.maxDescriptorSetStorageBuffers(), 2048, "maxDescriptorSetStorageBuffers", reason);
            ok &= checkLimit(limits.maxPerStageDescriptorUniformBuffers(), 512, "maxPerStageDescriptorUniformBuffers", reason);
            ok &= checkLimit(limits.maxDescriptorSetUniformBuffers(), 512, "maxDescriptorSetUniformBuffers", reason);

            return new GateResult(ok, reason.toString());
        }
    }

    private static boolean checkFeature(boolean enabled, String name, StringBuilder reason) {
        if (enabled) {
            return true;
        }
        appendReason(reason, "missingFeature=" + name);
        return false;
    }

    private static boolean checkLimit(int actual, int required, String name, StringBuilder reason) {
        if (actual >= required) {
            return true;
        }
        appendReason(reason, "limitShortfall=" + name + "(" + actual + "<" + required + ")");
        return false;
    }

    private static void appendReason(StringBuilder reason, String part) {
        if (reason.isEmpty()) {
            reason.append(part);
        } else {
            reason.append(", ").append(part);
        }
    }

    private static long packHandle(HeapType type, int generation, int slot) {
        long t = (long) (type.id & TYPE_MASK);
        long g = ((long) generation) & GEN_MASK;
        long s = ((long) slot) & SLOT_MASK;
        return (t << 56) | (g << 32) | s;
    }

    private static HeapType unpackType(long handle) {
        int id = (int) ((handle >>> 56) & TYPE_MASK);
        for (HeapType type : HeapType.values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }

    private static int unpackGeneration(long handle) {
        return (int) ((handle >>> 32) & GEN_MASK);
    }

    private static int unpackSlot(long handle) {
        return (int) (handle & SLOT_MASK);
    }

    private static String safeTypeName(HeapType type) {
        return type == null ? "UNKNOWN" : type.name();
    }

    private int currentGeneration(TypeState state, int slot) {
        if (state == null || slot < 0 || slot >= state.capacity) {
            return -1;
        }
        return state.generations[slot];
    }

    private TypeState state(HeapType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case JOINT_PALETTE -> jointState;
            case MORPH_DELTA -> morphDeltaState;
            case MORPH_WEIGHT -> morphWeightState;
            case INSTANCE_DATA -> instanceState;
        };
    }

    private void logStale(HeapType type, int slot, int handleGen, int currentGen, long frame) {
        staleHandleRejectCount++;
        LOG.warning("[BINDLESS_HEAP] stale_handle type=" + safeTypeName(type)
                + " slot=" + Integer.toUnsignedString(Math.max(0, slot))
                + " handleGen=" + Integer.toUnsignedString(Math.max(0, handleGen))
                + " currentGen=" + Integer.toUnsignedString(Math.max(0, currentGen))
                + " frame=" + Long.toUnsignedString(Math.max(0L, frame)));
    }

    private static int usedCount(TypeState state) {
        if (state == null || state.capacity <= 0) {
            return 0;
        }
        return Math.max(0, state.capacity - state.top);
    }

    public enum HeapType {
        JOINT_PALETTE(0, JOINT_CAPACITY),
        MORPH_DELTA(1, MORPH_DELTA_CAPACITY),
        MORPH_WEIGHT(2, MORPH_WEIGHT_CAPACITY),
        INSTANCE_DATA(3, INSTANCE_CAPACITY);

        private final int id;
        private final int capacity;

        HeapType(int id, int capacity) {
            this.id = id;
            this.capacity = capacity;
        }
    }

    private static final class TypeState {
        final HeapType type;
        final int capacity;
        final int[] generations;
        final int[] freeStack;
        int top;

        private TypeState(HeapType type, int capacity, int[] generations, int[] freeStack, int top) {
            this.type = type;
            this.capacity = capacity;
            this.generations = generations;
            this.freeStack = freeStack;
            this.top = top;
        }

        static TypeState create(HeapType type) {
            int capacity = Math.max(1, type.capacity);
            int[] generations = new int[capacity];
            int[] free = new int[capacity];
            for (int i = 0; i < capacity; i++) {
                generations[i] = 1;
                free[i] = capacity - 1 - i;
            }
            return new TypeState(type, capacity, generations, free, capacity);
        }

        static TypeState empty(HeapType type) {
            return new TypeState(type, 0, new int[0], new int[0], 0);
        }
    }

    private record Retirement(HeapType type, int slot, int generation, long retireFrame) {
    }

    private record GateResult(boolean passed, String reason) {
    }
}
