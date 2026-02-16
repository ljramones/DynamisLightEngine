package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;

public final class VulkanFrameSyncResources {
    private VulkanFrameSyncResources() {
    }

    public static Allocation create(
            VkDevice device,
            MemoryStack stack,
            int graphicsQueueFamilyIndex,
            int framesInFlight
    ) throws EngineException {
        long commandPool = createCommandPool(device, stack, graphicsQueueFamilyIndex);
        VkCommandBuffer[] commandBuffers = allocateCommandBuffers(device, stack, commandPool, framesInFlight);
        SyncPrimitives sync = createSyncPrimitives(device, stack, framesInFlight);
        return new Allocation(commandPool, commandBuffers, sync.imageAvailableSemaphores(), sync.renderFinishedSemaphores(), sync.renderFences());
    }

    public static void destroy(VkDevice device, Allocation resources) {
        if (device == null || resources == null) {
            return;
        }
        for (long fence : resources.renderFences()) {
            if (fence != VK_NULL_HANDLE) {
                vkDestroyFence(device, fence, null);
            }
        }
        for (long sem : resources.renderFinishedSemaphores()) {
            if (sem != VK_NULL_HANDLE) {
                vkDestroySemaphore(device, sem, null);
            }
        }
        for (long sem : resources.imageAvailableSemaphores()) {
            if (sem != VK_NULL_HANDLE) {
                vkDestroySemaphore(device, sem, null);
            }
        }
        if (resources.commandPool() != VK_NULL_HANDLE) {
            vkDestroyCommandPool(device, resources.commandPool(), null);
        }
    }

    private static long createCommandPool(VkDevice device, MemoryStack stack, int graphicsQueueFamilyIndex) throws EngineException {
        VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(graphicsQueueFamilyIndex);
        var pPool = stack.longs(VK_NULL_HANDLE);
        int poolResult = vkCreateCommandPool(device, poolInfo, null, pPool);
        if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateCommandPool failed: " + poolResult, false);
        }
        return pPool.get(0);
    }

    private static VkCommandBuffer[] allocateCommandBuffers(
            VkDevice device,
            MemoryStack stack,
            long commandPool,
            int framesInFlight
    ) throws EngineException {
        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(framesInFlight);
        PointerBuffer pCommandBuffer = stack.mallocPointer(framesInFlight);
        int allocResult = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
        if (allocResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateCommandBuffers failed: " + allocResult, false);
        }
        VkCommandBuffer[] commandBuffers = new VkCommandBuffer[framesInFlight];
        for (int i = 0; i < framesInFlight; i++) {
            commandBuffers[i] = new VkCommandBuffer(pCommandBuffer.get(i), device);
        }
        return commandBuffers;
    }

    private static SyncPrimitives createSyncPrimitives(VkDevice device, MemoryStack stack, int framesInFlight) throws EngineException {
        VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        long[] imageAvailableSemaphores = new long[framesInFlight];
        long[] renderFinishedSemaphores = new long[framesInFlight];
        long[] renderFences = new long[framesInFlight];

        for (int i = 0; i < framesInFlight; i++) {
            var pSemaphore = stack.longs(VK_NULL_HANDLE);
            int semaphoreResult = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
            if (semaphoreResult != VK_SUCCESS || pSemaphore.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSemaphore(imageAvailable) failed: " + semaphoreResult, false);
            }
            imageAvailableSemaphores[i] = pSemaphore.get(0);

            pSemaphore.put(0, VK_NULL_HANDLE);
            semaphoreResult = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
            if (semaphoreResult != VK_SUCCESS || pSemaphore.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateSemaphore(renderFinished) failed: " + semaphoreResult, false);
            }
            renderFinishedSemaphores[i] = pSemaphore.get(0);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);
            var pFence = stack.longs(VK_NULL_HANDLE);
            int fenceResult = vkCreateFence(device, fenceInfo, null, pFence);
            if (fenceResult != VK_SUCCESS || pFence.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateFence failed: " + fenceResult, false);
            }
            renderFences[i] = pFence.get(0);
        }
        return new SyncPrimitives(imageAvailableSemaphores, renderFinishedSemaphores, renderFences);
    }

    private record SyncPrimitives(long[] imageAvailableSemaphores, long[] renderFinishedSemaphores, long[] renderFences) {
    }

    public record Allocation(
            long commandPool,
            VkCommandBuffer[] commandBuffers,
            long[] imageAvailableSemaphores,
            long[] renderFinishedSemaphores,
            long[] renderFences
    ) {
    }
}
