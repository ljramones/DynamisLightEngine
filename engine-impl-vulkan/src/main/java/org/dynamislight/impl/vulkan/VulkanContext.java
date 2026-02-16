package org.dynamislight.impl.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_ERROR_INITIALIZATION_FAILED;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

final class VulkanContext {
    private VkInstance instance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue graphicsQueue;
    private int graphicsQueueFamilyIndex = -1;
    private long commandPool = VK_NULL_HANDLE;
    private VkCommandBuffer commandBuffer;
    private long renderFence = VK_NULL_HANDLE;
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private long plannedVisibleObjects = 1;

    void initialize(String appName) throws EngineException {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8(appName))
                    .applicationVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                    .pEngineName(stack.UTF8("DynamicLightEngine"))
                    .engineVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                    .apiVersion(VK10.VK_MAKE_API_VERSION(0, 1, 1, 0));

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo);

            PointerBuffer pInstance = stack.mallocPointer(1);
            int result = vkCreateInstance(createInfo, null, pInstance);
            if (result != VK_SUCCESS) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateInstance failed: " + result,
                        result == VK_ERROR_INITIALIZATION_FAILED
                );
            }

            instance = new VkInstance(pInstance.get(0), createInfo);
            selectPhysicalDevice(stack);
            createLogicalDevice(stack);
            createCommandResources(stack);
        }
    }

    VulkanFrameMetrics renderFrame() {
        long start = System.nanoTime();
        if (device != null && graphicsQueue != null && commandBuffer != null) {
            try (MemoryStack stack = stackPush()) {
                if (renderFence != VK_NULL_HANDLE) {
                    int waitResult = vkWaitForFences(device, stack.longs(renderFence), true, 1_000_000_000L);
                    if (waitResult == VK_SUCCESS) {
                        vkResetFences(device, stack.longs(renderFence));
                    }
                }
                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pCommandBuffers(stack.pointers(commandBuffer.address()));
                vkQueueSubmit(graphicsQueue, submitInfo, renderFence);
            }
        }
        double cpuMs = (System.nanoTime() - start) / 1_000_000.0;
        return new VulkanFrameMetrics(cpuMs, cpuMs * 0.7, plannedDrawCalls, plannedTriangles, plannedVisibleObjects, 0);
    }

    void setPlannedWorkload(long drawCalls, long triangles, long visibleObjects) {
        plannedDrawCalls = Math.max(1, drawCalls);
        plannedTriangles = Math.max(1, triangles);
        plannedVisibleObjects = Math.max(1, visibleObjects);
    }

    void shutdown() {
        if (device != null) {
            vkDeviceWaitIdle(device);
        }
        commandBuffer = null;
        if (renderFence != VK_NULL_HANDLE && device != null) {
            vkDestroyFence(device, renderFence, null);
            renderFence = VK_NULL_HANDLE;
        }
        if (commandPool != VK_NULL_HANDLE && device != null) {
            vkDestroyCommandPool(device, commandPool, null);
            commandPool = VK_NULL_HANDLE;
        }
        if (device != null) {
            vkDestroyDevice(device, null);
            device = null;
        }
        if (instance != null) {
            vkDestroyInstance(instance, null);
            instance = null;
        }
        physicalDevice = null;
        graphicsQueue = null;
        graphicsQueueFamilyIndex = -1;
    }

    private void selectPhysicalDevice(MemoryStack stack) throws EngineException {
        var pCount = stack.ints(0);
        int countResult = vkEnumeratePhysicalDevices(instance, pCount, null);
        if (countResult != VK_SUCCESS || pCount.get(0) == 0) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "No Vulkan physical devices available: " + countResult,
                    false
            );
        }

        PointerBuffer devices = stack.mallocPointer(pCount.get(0));
        int enumerateResult = vkEnumeratePhysicalDevices(instance, pCount, devices);
        if (enumerateResult != VK_SUCCESS) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Failed to enumerate Vulkan physical devices: " + enumerateResult,
                    false
            );
        }

        for (int i = 0; i < devices.capacity(); i++) {
            VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
            int queueIndex = findGraphicsQueueFamily(candidate, stack);
            if (queueIndex >= 0) {
                physicalDevice = candidate;
                graphicsQueueFamilyIndex = queueIndex;
                return;
            }
        }

        throw new EngineException(
                EngineErrorCode.BACKEND_INIT_FAILED,
                "No Vulkan graphics queue family found",
                false
        );
    }

    private int findGraphicsQueueFamily(VkPhysicalDevice candidate, MemoryStack stack) {
        var pQueueCount = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, pQueueCount, null);
        int queueCount = pQueueCount.get(0);
        if (queueCount <= 0) {
            return -1;
        }
        var queueProps = org.lwjgl.vulkan.VkQueueFamilyProperties.calloc(queueCount, stack);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, pQueueCount, queueProps);
        for (int i = 0; i < queueCount; i++) {
            if ((queueProps.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                return i;
            }
        }
        return -1;
    }

    private void createLogicalDevice(MemoryStack stack) throws EngineException {
        if (physicalDevice == null || graphicsQueueFamilyIndex < 0) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Vulkan physical device not selected", false);
        }

        var queuePriority = stack.floats(1.0f);
        VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(graphicsQueueFamilyIndex)
                .pQueuePriorities(queuePriority);

        VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueCreateInfo);

        PointerBuffer pDevice = stack.mallocPointer(1);
        int createResult = vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice);
        if (createResult != VK_SUCCESS || pDevice.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDevice failed: " + createResult,
                    createResult == VK_ERROR_INITIALIZATION_FAILED
            );
        }

        device = new VkDevice(pDevice.get(0), physicalDevice, deviceCreateInfo);
        PointerBuffer pQueue = stack.mallocPointer(1);
        vkGetDeviceQueue(device, graphicsQueueFamilyIndex, 0, pQueue);
        graphicsQueue = new VkQueue(pQueue.get(0), device);
    }

    private void createCommandResources(MemoryStack stack) throws EngineException {
        if (device == null || graphicsQueueFamilyIndex < 0) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Vulkan device not ready for command resources", false);
        }

        VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(graphicsQueueFamilyIndex);

        var pPool = stack.longs(VK_NULL_HANDLE);
        int poolResult = vkCreateCommandPool(device, poolInfo, null, pPool);
        if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateCommandPool failed: " + poolResult, false);
        }
        commandPool = pPool.get(0);

        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);

        PointerBuffer pCommandBuffer = stack.mallocPointer(1);
        int allocResult = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
        if (allocResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkAllocateCommandBuffers failed: " + allocResult, false);
        }

        commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), device);
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

        int beginResult = vkBeginCommandBuffer(commandBuffer, beginInfo);
        if (beginResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkBeginCommandBuffer failed: " + beginResult, false);
        }

        int endResult = vkEndCommandBuffer(commandBuffer);
        if (endResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkEndCommandBuffer failed: " + endResult, false);
        }

        VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(VK_FENCE_CREATE_SIGNALED_BIT);
        var pFence = stack.longs(VK_NULL_HANDLE);
        int fenceResult = vkCreateFence(device, fenceCreateInfo, null, pFence);
        if (fenceResult != VK_SUCCESS || pFence.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateFence failed: " + fenceResult, false);
        }
        renderFence = pFence.get(0);
    }

    record VulkanFrameMetrics(
            double cpuFrameMs,
            double gpuFrameMs,
            long drawCalls,
            long triangles,
            long visibleObjects,
            long gpuMemoryBytes
    ) {
    }
}
