package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueue;

public final class VulkanFrameSubmitCoordinator {
    private VulkanFrameSubmitCoordinator() {
    }

    public static int acquireRecordSubmitPresent(Inputs in) throws EngineException {
        return VulkanCommandSubmitter.acquireRecordSubmitPresent(
                in.stack(),
                in.device(),
                in.graphicsQueue(),
                in.swapchain(),
                in.commandBuffer(),
                in.imageAvailableSemaphore(),
                in.renderFinishedSemaphore(),
                in.renderFence(),
                imageIndex -> in.frameRecorder().record(imageIndex),
                () -> in.fenceReadyHook().onFenceReady()
        );
    }

    @FunctionalInterface
    public interface FrameRecorder {
        void record(int imageIndex) throws EngineException;
    }

    @FunctionalInterface
    public interface FenceReadyHook {
        void onFenceReady() throws EngineException;
    }

    public record Inputs(
            MemoryStack stack,
            VkDevice device,
            VkQueue graphicsQueue,
            long swapchain,
            VkCommandBuffer commandBuffer,
            long imageAvailableSemaphore,
            long renderFinishedSemaphore,
            long renderFence,
            FrameRecorder frameRecorder,
            FenceReadyHook fenceReadyHook
    ) {
    }
}
