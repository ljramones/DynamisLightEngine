package org.dynamislight.impl.vulkan.shadow;

import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public final class VulkanShadowLifecycleCoordinator {
    private VulkanShadowLifecycleCoordinator() {
    }

    public static State create(CreateRequest request) throws EngineException {
        VulkanShadowResources.Allocation shadowResources = VulkanShadowResources.create(
                request.device(),
                request.physicalDevice(),
                request.stack(),
                request.depthFormat(),
                request.shadowMapResolution(),
                request.maxShadowMatrices(),
                request.vertexStrideBytes(),
                request.descriptorSetLayout()
        );
        return new State(
                shadowResources.shadowDepthImage(),
                shadowResources.shadowDepthMemory(),
                shadowResources.shadowDepthImageView(),
                shadowResources.shadowDepthLayerImageViews(),
                shadowResources.shadowSampler(),
                shadowResources.shadowRenderPass(),
                shadowResources.shadowPipelineLayout(),
                shadowResources.shadowPipeline(),
                shadowResources.shadowFramebuffers()
        );
    }

    public static State destroy(DestroyRequest request) {
        VulkanShadowResources.destroy(
                request.device(),
                new VulkanShadowResources.Allocation(
                        request.shadowDepthImage(),
                        request.shadowDepthMemory(),
                        request.shadowDepthImageView(),
                        request.shadowDepthLayerImageViews(),
                        request.shadowSampler(),
                        request.shadowRenderPass(),
                        request.shadowPipelineLayout(),
                        request.shadowPipeline(),
                        request.shadowFramebuffers()
                )
        );
        return State.empty();
    }

    public record CreateRequest(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack,
            int depthFormat,
            int shadowMapResolution,
            int maxShadowMatrices,
            int vertexStrideBytes,
            long descriptorSetLayout
    ) {
    }

    public record DestroyRequest(
            VkDevice device,
            long shadowDepthImage,
            long shadowDepthMemory,
            long shadowDepthImageView,
            long[] shadowDepthLayerImageViews,
            long shadowSampler,
            long shadowRenderPass,
            long shadowPipelineLayout,
            long shadowPipeline,
            long[] shadowFramebuffers
    ) {
    }

    public record State(
            long shadowDepthImage,
            long shadowDepthMemory,
            long shadowDepthImageView,
            long[] shadowDepthLayerImageViews,
            long shadowSampler,
            long shadowRenderPass,
            long shadowPipelineLayout,
            long shadowPipeline,
            long[] shadowFramebuffers
    ) {
        public static State empty() {
            return new State(
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    new long[0],
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    VK_NULL_HANDLE,
                    new long[0]
            );
        }
    }
}
