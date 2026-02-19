package org.dynamislight.impl.vulkan.swapchain;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueryPoolCreateInfo;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanSwapchainTimestampRuntimeHelper {
    public record PlanarTimingSample(
            String gpuTimingSource,
            double planarCaptureGpuMs,
            boolean planarCaptureGpuMsValid
    ) {
    }

    @FunctionalInterface
    public interface SwapchainResizeAction {
        void recreate(int width, int height) throws EngineException;
    }

    @FunctionalInterface
    public interface SwapchainDestroyAction {
        void run() throws EngineException;
    }

    @FunctionalInterface
    public interface SwapchainCreateAction {
        void run(MemoryStack stack, int width, int height) throws EngineException;
    }

    public static int planarTimestampQueryStartIndex(VulkanBackendResources backendResources, int framesInFlight, int frameIdx) {
        if (backendResources.planarTimestampQueryPool == VK_NULL_HANDLE || framesInFlight <= 0) {
            return -1;
        }
        int safeFrame = Math.max(0, Math.min(Math.max(0, framesInFlight - 1), frameIdx));
        return safeFrame * 2;
    }

    public static int planarTimestampQueryEndIndex(VulkanBackendResources backendResources, int framesInFlight, int frameIdx) {
        int start = planarTimestampQueryStartIndex(backendResources, framesInFlight, frameIdx);
        return start < 0 ? -1 : start + 1;
    }

    public static void recreateSwapchainFromWindow(long window, SwapchainResizeAction recreateAction) throws EngineException {
        VulkanSwapchainRecreateCoordinator.recreateFromWindow(window, recreateAction::recreate);
    }

    public static void recreateSwapchain(
            VulkanBackendResources backendResources,
            int width,
            int height,
            SwapchainDestroyAction destroySwapchainResources,
            SwapchainCreateAction createSwapchainResources
    ) throws EngineException {
        VulkanSwapchainRecreateCoordinator.recreate(
                backendResources.device,
                backendResources.physicalDevice,
                backendResources.surface,
                width,
                height,
                destroySwapchainResources::run,
                createSwapchainResources::run
        );
    }

    public static void createPlanarTimestampResources(
            MemoryStack stack,
            VulkanBackendResources backendResources,
            int framesInFlight
    ) throws EngineException {
        destroyPlanarTimestampResources(backendResources);
        backendResources.planarTimestampSupported = false;
        backendResources.timestampPeriodNs = 0.0f;
        if (backendResources.device == null
                || backendResources.physicalDevice == null
                || backendResources.graphicsQueueFamilyIndex < 0) {
            return;
        }
        var pQueueFamilyCount = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(backendResources.physicalDevice, pQueueFamilyCount, null);
        int queueFamilyCount = pQueueFamilyCount.get(0);
        if (queueFamilyCount <= 0 || backendResources.graphicsQueueFamilyIndex >= queueFamilyCount) {
            return;
        }
        var queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount, stack);
        vkGetPhysicalDeviceQueueFamilyProperties(backendResources.physicalDevice, pQueueFamilyCount, queueFamilies);
        if (queueFamilies.get(backendResources.graphicsQueueFamilyIndex).timestampValidBits() <= 0) {
            return;
        }
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
        vkGetPhysicalDeviceProperties(backendResources.physicalDevice, properties);
        backendResources.timestampPeriodNs = Math.max(0.0f, properties.limits().timestampPeriod());
        int queryCount = Math.max(1, framesInFlight) * 2;
        var queryInfo = VkQueryPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO)
                .queryType(VK_QUERY_TYPE_TIMESTAMP)
                .queryCount(queryCount);
        var pQueryPool = stack.mallocLong(1);
        int createResult = vkCreateQueryPool(backendResources.device, queryInfo, null, pQueryPool);
        if (createResult != VK_SUCCESS || pQueryPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateQueryPool(planar) failed: " + createResult, false);
        }
        backendResources.planarTimestampQueryPool = pQueryPool.get(0);
        backendResources.planarTimestampSupported = true;
    }

    public static void destroyPlanarTimestampResources(VulkanBackendResources backendResources) {
        if (backendResources.device != null && backendResources.planarTimestampQueryPool != VK_NULL_HANDLE) {
            vkDestroyQueryPool(backendResources.device, backendResources.planarTimestampQueryPool, null);
        }
        backendResources.planarTimestampQueryPool = VK_NULL_HANDLE;
        backendResources.planarTimestampSupported = false;
        backendResources.timestampPeriodNs = 0.0f;
    }

    public static PlanarTimingSample samplePlanarCaptureTimingForFrame(
            MemoryStack stack,
            VulkanBackendResources backendResources,
            int framesInFlight,
            int frameIdx
    ) throws EngineException {
        String source = "frame_estimate";
        double planarCaptureGpuMs = Double.NaN;
        boolean planarCaptureGpuMsValid = false;
        if (!backendResources.planarTimestampSupported
                || backendResources.planarTimestampQueryPool == VK_NULL_HANDLE
                || backendResources.device == null) {
            return new PlanarTimingSample(source, planarCaptureGpuMs, planarCaptureGpuMsValid);
        }
        int queryStart = planarTimestampQueryStartIndex(backendResources, framesInFlight, frameIdx);
        if (queryStart < 0) {
            return new PlanarTimingSample(source, planarCaptureGpuMs, planarCaptureGpuMsValid);
        }
        var data = stack.mallocLong(4);
        int queryResult = vkGetQueryPoolResults(
                backendResources.device,
                backendResources.planarTimestampQueryPool,
                queryStart,
                2,
                data,
                2L * Long.BYTES,
                VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT
        );
        if (queryResult == VK_NOT_READY) {
            return new PlanarTimingSample(source, planarCaptureGpuMs, planarCaptureGpuMsValid);
        }
        if (queryResult != VK_SUCCESS) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkGetQueryPoolResults(planar) failed: " + queryResult, false);
        }
        long startTimestamp = data.get(0);
        long startAvailable = data.get(1);
        long endTimestamp = data.get(2);
        long endAvailable = data.get(3);
        if (startAvailable != 0L && endAvailable != 0L && endTimestamp >= startTimestamp) {
            double deltaTicks = (double) (endTimestamp - startTimestamp);
            double periodNs = Math.max(0.0, backendResources.timestampPeriodNs);
            planarCaptureGpuMs = (deltaTicks * periodNs) / 1_000_000.0;
            planarCaptureGpuMsValid = true;
            source = "gpu_timestamp";
        }
        return new PlanarTimingSample(source, planarCaptureGpuMs, planarCaptureGpuMsValid);
    }

    private VulkanSwapchainTimestampRuntimeHelper() {
    }
}
