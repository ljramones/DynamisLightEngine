package org.dynamislight.impl.vulkan.bootstrap;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.swapchain.VulkanDeviceSelector;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.KHRPortabilityEnumeration;
import org.lwjgl.vulkan.KHRPortabilitySubset;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.VK_ERROR_INITIALIZATION_FAILED;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;

public final class VulkanBootstrap {
    private VulkanBootstrap() {
    }

    private static final boolean IS_MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
    private static final String EXT_RAY_QUERY = "VK_KHR_ray_query";
    private static final String EXT_ACCELERATION_STRUCTURE = "VK_KHR_acceleration_structure";
    private static final String EXT_RT_PIPELINE = "VK_KHR_ray_tracing_pipeline";
    private static final String EXT_DEFERRED_HOST_OPS = "VK_KHR_deferred_host_operations";

    public static long initWindow(String appName, int width, int height, boolean windowVisible) throws EngineException {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "GLFW initialization failed", false);
        }
        if (!glfwVulkanSupported()) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "GLFW Vulkan not supported", false);
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_VISIBLE, windowVisible ? GLFW_TRUE : GLFW_FALSE);
        long window = glfwCreateWindow(Math.max(1, width), Math.max(1, height), appName, VK_NULL_HANDLE, VK_NULL_HANDLE);
        if (window == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Failed to create Vulkan window", false);
        }
        return window;
    }

    public static VkInstance createInstance(MemoryStack stack, String appName) throws EngineException {
        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8(appName))
                .applicationVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                .pEngineName(stack.UTF8("DynamicLightEngine"))
                .engineVersion(VK10.VK_MAKE_API_VERSION(0, 0, 1, 0))
                .apiVersion(VK10.VK_MAKE_API_VERSION(0, 1, 1, 0));

        PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
        if (requiredExtensions == null) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "No required Vulkan instance extensions from GLFW", false);
        }

        PointerBuffer instanceExtensions = stack.mallocPointer(requiredExtensions.remaining() + 1);
        for (int i = requiredExtensions.position(); i < requiredExtensions.limit(); i++) {
            instanceExtensions.put(requiredExtensions.get(i));
        }
        instanceExtensions.put(stack.UTF8(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME));
        instanceExtensions.flip();

        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(appInfo)
                .flags(VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR)
                .ppEnabledExtensionNames(instanceExtensions);

        PointerBuffer pInstance = stack.mallocPointer(1);
        int result = vkCreateInstance(createInfo, null, pInstance);
        if (result != VK_SUCCESS) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateInstance failed: " + result,
                    result == VK_ERROR_INITIALIZATION_FAILED
            );
        }
        return new VkInstance(pInstance.get(0), createInfo);
    }

    public static long createSurface(VkInstance instance, long window, MemoryStack stack) throws EngineException {
        var pSurface = stack.longs(VK_NULL_HANDLE);
        int result = glfwCreateWindowSurface(instance, window, null, pSurface);
        if (result != VK_SUCCESS || pSurface.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "glfwCreateWindowSurface failed: " + result, false);
        }
        return pSurface.get(0);
    }

    public static VulkanDeviceSelector.Selection selectPhysicalDevice(VkInstance instance, long surface, MemoryStack stack) throws EngineException {
        return VulkanDeviceSelector.select(instance, surface, stack);
    }

    public static DeviceAndQueue createLogicalDevice(
            VkPhysicalDevice physicalDevice,
            int graphicsQueueFamilyIndex,
            boolean shadowRtTraversalSupported,
            boolean shadowRtBvhSupported,
            MemoryStack stack
    ) throws EngineException {
        if (physicalDevice == null || graphicsQueueFamilyIndex < 0) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Vulkan physical device not selected", false);
        }

        var queuePriority = stack.floats(1.0f);
        VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(graphicsQueueFamilyIndex)
                .pQueuePriorities(queuePriority);

        List<String> requiredDeviceExtensions = new ArrayList<>();
        requiredDeviceExtensions.add(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
        if (IS_MAC) {
            requiredDeviceExtensions.add(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME);
        }
        if (shadowRtTraversalSupported) {
            requiredDeviceExtensions.add(EXT_RAY_QUERY);
            requiredDeviceExtensions.add(EXT_ACCELERATION_STRUCTURE);
            if (shadowRtBvhSupported) {
                requiredDeviceExtensions.add(EXT_RT_PIPELINE);
                requiredDeviceExtensions.add(EXT_DEFERRED_HOST_OPS);
            }
        }
        PointerBuffer extensions = stack.mallocPointer(requiredDeviceExtensions.size());
        for (String extensionName : requiredDeviceExtensions) {
            extensions.put(stack.UTF8(extensionName));
        }
        extensions.flip();
        VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueCreateInfo)
                .ppEnabledExtensionNames(extensions);

        PointerBuffer pDevice = stack.mallocPointer(1);
        int createResult = vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice);
        if (createResult != VK_SUCCESS || pDevice.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDevice failed: " + createResult,
                    createResult == VK_ERROR_INITIALIZATION_FAILED
            );
        }

        VkDevice device = new VkDevice(pDevice.get(0), physicalDevice, deviceCreateInfo);
        PointerBuffer pQueue = stack.mallocPointer(1);
        vkGetDeviceQueue(device, graphicsQueueFamilyIndex, 0, pQueue);
        VkQueue graphicsQueue = new VkQueue(pQueue.get(0), device);
        return new DeviceAndQueue(device, graphicsQueue);
    }

    public record DeviceAndQueue(
            VkDevice device,
            VkQueue graphicsQueue
    ) {
    }
}
