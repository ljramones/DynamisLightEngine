package org.dynamislight.impl.vulkan.shadow;

import org.dynamislight.api.error.EngineException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

/**
 * A utility class responsible for managing the lifecycle of Vulkan shadow resources,
 * encapsulating creation and destruction logic.
 *
 * This class provides static methods for creating and destroying Vulkan shadow-related resources,
 * ensuring consistency and proper resource management in a Vulkan application.
 *
 * The class is not instantiable and operates entirely through static methods.
 */
public final class VulkanShadowLifecycleCoordinator {
    private VulkanShadowLifecycleCoordinator() {
    }

    /**
     * Creates a new {@link State} instance by allocating Vulkan shadow resources based on the
     * parameters provided in the {@link CreateRequest}.
     *
     * @param request the {@link CreateRequest} containing Vulkan device, physical device, memory stack,
     *                depth format, shadow map resolution, maximum shadow matrices, vertex stride in bytes,
     *                and the descriptor set layout required for resource allocation.
     * @return a newly created {@link State} encapsulating the allocated Vulkan shadow resources.
     * @throws EngineException if an error occurs during the allocation of Vulkan shadow resources.
     */
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

    /**
     * Destroys Vulkan shadow resources based on the parameters provided in the {@link DestroyRequest}.
     * This method deallocates all associated Vulkan resources linked to shadow rendering.
     *
     * @param request the {@link DestroyRequest} containing the Vulkan device and all handles
     *                to the shadow resources that need to be destroyed, including the shadow
     *                depth image, memory, image view, sampler, render pass, pipeline layout,
     *                pipeline, and framebuffers.
     * @return an empty {@link State} instance representing the absence of allocated resources.
     */
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

    /**
     * Represents a creation request for Vulkan shadow rendering resources.
     * This class is used to encapsulate the parameters required for the allocation of Vulkan
     * resources necessary for shadow rendering operations.
     *
     * The parameters include Vulkan device and physical device instances, a memory stack for
     * allocations, details about the depth format, shadow map resolution, constraints on the
     * maximum number of shadow matrices, the vertex stride in bytes, and a descriptor set layout.
     *
     * It is primarily utilized by methods such as `create` within the `VulkanShadowLifecycleCoordinator`
     * class to manage the lifecycle of Vulkan shadow resources efficiently.
     *
     * Fields:
     * - `device`: The Vulkan device used for resource allocation.
     * - `physicalDevice`: The Vulkan physical device associated with the logical device.
     * - `stack`: The memory stack used for temporary memory allocations.
     * - `depthFormat`: The format of the depth buffer used for shadow maps.
     * - `shadowMapResolution`: The resolution of the shadow map texture.
     * - `maxShadowMatrices`: The maximum number of shadow matrices that can be supported.
     * - `vertexStrideBytes`: The vertex stride in bytes for shadow rendering pipelines.
     * - `descriptorSetLayout`: The descriptor set layout handle required for resource binding.
     */
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

    /**
     * Represents a request for destroying Vulkan shadow-related resources.
     * This record is used to encapsulate all the Vulkan handles and resources
     * that need to be cleaned up as part of the shadow rendering lifecycle.
     *
     * The fields of this record correspond to different Vulkan objects, including:
     * - The Vulkan logical device that owns the resources to be destroyed.
     * - Handles to the shadow depth image, memory, image view, and its layer-specific image views.
     * - The shadow sampler used for rendering.
     * - The shadow render pass for managing rendering operations.
     * - The shadow pipeline layout and pipeline used for graphics rendering.
     * - Framebuffers associated with shadow rendering.
     *
     * Proper destruction of these resources is necessary to ensure that
     * Vulkan resources are properly deallocated and no resource leaks occur.
     */
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

    /**
     * Represents a collection of Vulkan shadow-related resources used for rendering shadow maps.
     * This record is immutable and encapsulates all necessary handles for Vulkan shadow rendering.
     *
     * The class provides utility methods for resource allocation and deallocation,
     * ensuring safe and efficient management of Vulkan resources.
     */
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
        /**
         * Creates and returns an empty State instance where all Vulkan handles are set
         * to VK_NULL_HANDLE and all arrays are initialized to empty arrays.
         * This method provides a default, uninitialized representation of the State.
         *
         * @return an empty State instance with all Vulkan resource handles set to default values.
         */
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
