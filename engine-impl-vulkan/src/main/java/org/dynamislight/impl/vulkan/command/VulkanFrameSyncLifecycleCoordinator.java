package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public final class VulkanFrameSyncLifecycleCoordinator {
    private VulkanFrameSyncLifecycleCoordinator() {
    }

    public static State create(CreateRequest request) throws EngineException {
        VulkanFrameSyncResources.Allocation frameSyncResources = VulkanFrameSyncResources.create(
                request.device(),
                request.stack(),
                request.graphicsQueueFamilyIndex(),
                request.framesInFlight()
        );
        return new State(
                frameSyncResources.commandPool(),
                frameSyncResources.commandBuffers(),
                frameSyncResources.imageAvailableSemaphores(),
                frameSyncResources.renderFinishedSemaphores(),
                frameSyncResources.renderFences(),
                0
        );
    }

    public static State empty() {
        return new State(
                VK_NULL_HANDLE,
                new VkCommandBuffer[0],
                new long[0],
                new long[0],
                new long[0],
                0
        );
    }

    public record CreateRequest(
            VkDevice device,
            MemoryStack stack,
            int graphicsQueueFamilyIndex,
            int framesInFlight
    ) {
    }

    public record State(
            long commandPool,
            VkCommandBuffer[] commandBuffers,
            long[] imageAvailableSemaphores,
            long[] renderFinishedSemaphores,
            long[] renderFences,
            int currentFrame
    ) {
    }
}
