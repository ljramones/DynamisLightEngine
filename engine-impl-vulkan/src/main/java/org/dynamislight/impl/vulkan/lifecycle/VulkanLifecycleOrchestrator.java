package org.dynamislight.impl.vulkan.lifecycle;

import java.util.List;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.bootstrap.VulkanBootstrap;
import org.dynamislight.impl.vulkan.bootstrap.VulkanShutdownCoordinator;
import org.dynamislight.impl.vulkan.command.VulkanFrameSyncLifecycleCoordinator;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.shadow.VulkanShadowLifecycleCoordinator;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamislight.impl.vulkan.state.VulkanRenderState;
import org.dynamislight.impl.vulkan.swapchain.VulkanSwapchainLifecycleCoordinator;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.system.MemoryStack.stackPush;

public final class VulkanLifecycleOrchestrator {
    private VulkanLifecycleOrchestrator() {
    }

    public static void initializeRuntime(InitializeRequest request) throws EngineException {
        request.backendResources().window = VulkanBootstrap.initWindow(
                request.appName(),
                request.width(),
                request.height(),
                request.windowVisible()
        );
        try (MemoryStack stack = stackPush()) {
            request.backendResources().instance = VulkanBootstrap.createInstance(stack, request.appName());
            request.backendResources().surface = VulkanBootstrap.createSurface(
                    request.backendResources().instance,
                    request.backendResources().window,
                    stack
            );
            var selection = VulkanBootstrap.selectPhysicalDevice(
                    request.backendResources().instance,
                    request.backendResources().surface,
                    stack
            );
            request.backendResources().physicalDevice = selection.physicalDevice();
            request.backendResources().graphicsQueueFamilyIndex = selection.graphicsQueueFamilyIndex();
            var deviceAndQueue = VulkanBootstrap.createLogicalDevice(
                    request.backendResources().physicalDevice,
                    request.backendResources().graphicsQueueFamilyIndex,
                    stack
            );
            request.backendResources().device = deviceAndQueue.device();
            request.backendResources().graphicsQueue = deviceAndQueue.graphicsQueue();

            request.createDescriptorResources().accept(stack);
            request.createSwapchainResources().accept(stack);
            request.createFrameSyncResources().accept(stack);
            request.createShadowResources().accept(stack);
            request.uploadSceneMeshes().accept(stack, request.pendingSceneMeshes());
        }
    }

    public static VulkanSwapchainLifecycleCoordinator.State createSwapchain(CreateSwapchainRequest request) throws EngineException {
        return VulkanSwapchainLifecycleCoordinator.create(
                new VulkanSwapchainLifecycleCoordinator.CreateRequest(
                        request.backendResources().physicalDevice,
                        request.backendResources().device,
                        request.stack(),
                        request.backendResources().surface,
                        request.requestedWidth(),
                        request.requestedHeight(),
                        request.backendResources().depthFormat,
                        request.vertexStrideBytes(),
                        request.descriptorResources().descriptorSetLayout,
                        request.descriptorResources().textureDescriptorSetLayout,
                        request.renderState().postOffscreenRequested
                )
        );
    }

    public static VulkanSwapchainLifecycleCoordinator.State destroySwapchain(DestroySwapchainRequest request) {
        return VulkanSwapchainLifecycleCoordinator.destroy(
                new VulkanSwapchainLifecycleCoordinator.DestroyRequest(
                        request.backendResources().device,
                        request.backendResources().framebuffers,
                        request.backendResources().graphicsPipeline,
                        request.backendResources().pipelineLayout,
                        request.backendResources().renderPass,
                        request.backendResources().swapchainImageViews,
                        request.backendResources().depthImages,
                        request.backendResources().depthMemories,
                        request.backendResources().depthImageViews,
                        request.backendResources().velocityImage,
                        request.backendResources().velocityMemory,
                        request.backendResources().velocityImageView,
                        request.backendResources().swapchain,
                        request.backendResources().swapchainImageFormat,
                        request.backendResources().swapchainWidth,
                        request.backendResources().swapchainHeight,
                        request.backendResources().offscreenColorImage,
                        request.backendResources().offscreenColorMemory,
                        request.backendResources().offscreenColorImageView,
                        request.backendResources().offscreenColorSampler,
                        request.backendResources().postTaaHistoryImage,
                        request.backendResources().postTaaHistoryMemory,
                        request.backendResources().postTaaHistoryImageView,
                        request.backendResources().postTaaHistorySampler,
                        request.backendResources().postTaaHistoryVelocityImage,
                        request.backendResources().postTaaHistoryVelocityMemory,
                        request.backendResources().postTaaHistoryVelocityImageView,
                        request.backendResources().postTaaHistoryVelocitySampler,
                        request.backendResources().postDescriptorSetLayout,
                        request.backendResources().postDescriptorPool,
                        request.backendResources().postDescriptorSet,
                        request.backendResources().postRenderPass,
                        request.backendResources().postPipelineLayout,
                        request.backendResources().postGraphicsPipeline,
                        request.backendResources().postFramebuffers
                )
        );
    }

    public static void applySwapchainState(
            VulkanBackendResources backendResources,
            VulkanRenderState renderState,
            VulkanSwapchainLifecycleCoordinator.State state
    ) {
        if (state == null) {
            return;
        }
        backendResources.swapchain = state.swapchain();
        backendResources.swapchainImageFormat = state.swapchainImageFormat();
        backendResources.swapchainWidth = state.swapchainWidth();
        backendResources.swapchainHeight = state.swapchainHeight();
        backendResources.swapchainImages = state.swapchainImages();
        backendResources.swapchainImageViews = state.swapchainImageViews();
        backendResources.depthImages = state.depthImages();
        backendResources.depthMemories = state.depthMemories();
        backendResources.depthImageViews = state.depthImageViews();
        backendResources.velocityImage = state.velocityImage();
        backendResources.velocityMemory = state.velocityMemory();
        backendResources.velocityImageView = state.velocityImageView();
        backendResources.renderPass = state.renderPass();
        backendResources.pipelineLayout = state.pipelineLayout();
        backendResources.graphicsPipeline = state.graphicsPipeline();
        backendResources.framebuffers = state.framebuffers();
        renderState.postOffscreenActive = state.postOffscreenActive();
        backendResources.offscreenColorImage = state.offscreenColorImage();
        backendResources.offscreenColorMemory = state.offscreenColorMemory();
        backendResources.offscreenColorImageView = state.offscreenColorImageView();
        backendResources.offscreenColorSampler = state.offscreenColorSampler();
        backendResources.postTaaHistoryImage = state.postTaaHistoryImage();
        backendResources.postTaaHistoryMemory = state.postTaaHistoryMemory();
        backendResources.postTaaHistoryImageView = state.postTaaHistoryImageView();
        backendResources.postTaaHistorySampler = state.postTaaHistorySampler();
        backendResources.postTaaHistoryVelocityImage = state.postTaaHistoryVelocityImage();
        backendResources.postTaaHistoryVelocityMemory = state.postTaaHistoryVelocityMemory();
        backendResources.postTaaHistoryVelocityImageView = state.postTaaHistoryVelocityImageView();
        backendResources.postTaaHistoryVelocitySampler = state.postTaaHistoryVelocitySampler();
        backendResources.postDescriptorSetLayout = state.postDescriptorSetLayout();
        backendResources.postDescriptorPool = state.postDescriptorPool();
        backendResources.postDescriptorSet = state.postDescriptorSet();
        backendResources.postRenderPass = state.postRenderPass();
        backendResources.postPipelineLayout = state.postPipelineLayout();
        backendResources.postGraphicsPipeline = state.postGraphicsPipeline();
        backendResources.postFramebuffers = state.postFramebuffers();
        renderState.postIntermediateInitialized = state.postIntermediateInitialized();
        renderState.postTaaHistoryInitialized = state.postTaaHistoryInitialized();
    }

    public static VulkanShadowLifecycleCoordinator.State createShadow(CreateShadowRequest request) throws EngineException {
        return VulkanShadowLifecycleCoordinator.create(
                new VulkanShadowLifecycleCoordinator.CreateRequest(
                        request.backendResources().device,
                        request.backendResources().physicalDevice,
                        request.stack(),
                        request.backendResources().depthFormat,
                        request.renderState().shadowMapResolution,
                        request.maxShadowMatrices(),
                        request.vertexStrideBytes(),
                        request.descriptorResources().descriptorSetLayout,
                        request.renderState().shadowMomentPipelineRequested,
                        request.renderState().shadowMomentMode
                )
        );
    }

    public static VulkanShadowLifecycleCoordinator.State destroyShadow(VulkanBackendResources backendResources) {
        return VulkanShadowLifecycleCoordinator.destroy(
                new VulkanShadowLifecycleCoordinator.DestroyRequest(
                        backendResources.device,
                        backendResources.shadowDepthImage,
                        backendResources.shadowDepthMemory,
                        backendResources.shadowDepthImageView,
                        backendResources.shadowDepthLayerImageViews,
                        backendResources.shadowSampler,
                        backendResources.shadowRenderPass,
                        backendResources.shadowPipelineLayout,
                        backendResources.shadowPipeline,
                        backendResources.shadowFramebuffers,
                        backendResources.shadowMomentImage,
                        backendResources.shadowMomentMemory,
                        backendResources.shadowMomentImageView,
                        backendResources.shadowMomentSampler,
                        backendResources.shadowMomentFormat
                )
        );
    }

    public static void applyShadowState(VulkanBackendResources backendResources, VulkanShadowLifecycleCoordinator.State state) {
        backendResources.shadowDepthImage = state.shadowDepthImage();
        backendResources.shadowDepthMemory = state.shadowDepthMemory();
        backendResources.shadowDepthImageView = state.shadowDepthImageView();
        backendResources.shadowDepthLayerImageViews = state.shadowDepthLayerImageViews();
        backendResources.shadowSampler = state.shadowSampler();
        backendResources.shadowRenderPass = state.shadowRenderPass();
        backendResources.shadowPipelineLayout = state.shadowPipelineLayout();
        backendResources.shadowPipeline = state.shadowPipeline();
        backendResources.shadowFramebuffers = state.shadowFramebuffers();
        backendResources.shadowMomentImage = state.shadowMomentImage();
        backendResources.shadowMomentMemory = state.shadowMomentMemory();
        backendResources.shadowMomentImageView = state.shadowMomentImageView();
        backendResources.shadowMomentSampler = state.shadowMomentSampler();
        backendResources.shadowMomentFormat = state.shadowMomentFormat();
    }

    public static VulkanFrameSyncLifecycleCoordinator.State createFrameSync(CreateFrameSyncRequest request) throws EngineException {
        return VulkanFrameSyncLifecycleCoordinator.create(
                new VulkanFrameSyncLifecycleCoordinator.CreateRequest(
                        request.backendResources().device,
                        request.stack(),
                        request.backendResources().graphicsQueueFamilyIndex,
                        request.framesInFlight()
                )
        );
    }

    public static void applyFrameSyncState(VulkanBackendResources backendResources, VulkanFrameSyncLifecycleCoordinator.State state) {
        backendResources.commandPool = state.commandPool();
        backendResources.commandBuffers = state.commandBuffers();
        backendResources.imageAvailableSemaphores = state.imageAvailableSemaphores();
        backendResources.renderFinishedSemaphores = state.renderFinishedSemaphores();
        backendResources.renderFences = state.renderFences();
        backendResources.currentFrame = state.currentFrame();
    }

    public static VulkanShutdownCoordinator.Result shutdown(ShutdownRequest request) {
        return VulkanShutdownCoordinator.shutdown(
                new VulkanShutdownCoordinator.Inputs(
                        request.backendResources().instance,
                        request.backendResources().physicalDevice,
                        request.backendResources().device,
                        request.backendResources().graphicsQueue,
                        request.backendResources().graphicsQueueFamilyIndex,
                        request.backendResources().window,
                        request.backendResources().surface,
                        request.backendResources().commandPool,
                        request.backendResources().commandBuffers,
                        request.backendResources().imageAvailableSemaphores,
                        request.backendResources().renderFinishedSemaphores,
                        request.backendResources().renderFences,
                        request.destroySceneMeshes(),
                        request.destroyShadowResources(),
                        request.destroySwapchainResources(),
                        request.destroyDescriptorResources()
                )
        );
    }

    public static void applyShutdownState(VulkanBackendResources backendResources, VulkanShutdownCoordinator.Result state) {
        backendResources.instance = state.instance();
        backendResources.physicalDevice = state.physicalDevice();
        backendResources.device = state.device();
        backendResources.graphicsQueue = state.graphicsQueue();
        backendResources.graphicsQueueFamilyIndex = state.graphicsQueueFamilyIndex();
        backendResources.window = state.window();
        backendResources.surface = state.surface();
        backendResources.commandPool = state.commandPool();
        backendResources.commandBuffers = state.commandBuffers();
        backendResources.imageAvailableSemaphores = state.imageAvailableSemaphores();
        backendResources.renderFinishedSemaphores = state.renderFinishedSemaphores();
        backendResources.renderFences = state.renderFences();
    }

    @FunctionalInterface
    public interface StackStep {
        void accept(MemoryStack stack) throws EngineException;
    }

    @FunctionalInterface
    public interface StackSceneStep {
        void accept(MemoryStack stack, List<VulkanSceneMeshData> sceneMeshes) throws EngineException;
    }

    public record InitializeRequest(
            String appName,
            int width,
            int height,
            boolean windowVisible,
            VulkanBackendResources backendResources,
            List<VulkanSceneMeshData> pendingSceneMeshes,
            StackStep createDescriptorResources,
            StackStep createSwapchainResources,
            StackStep createFrameSyncResources,
            StackStep createShadowResources,
            StackSceneStep uploadSceneMeshes
    ) {
    }

    public record CreateSwapchainRequest(
            VulkanBackendResources backendResources,
            VulkanDescriptorResourceState descriptorResources,
            VulkanRenderState renderState,
            MemoryStack stack,
            int requestedWidth,
            int requestedHeight,
            int vertexStrideBytes
    ) {
    }

    public record DestroySwapchainRequest(
            VulkanBackendResources backendResources
    ) {
    }

    public record CreateShadowRequest(
            VulkanBackendResources backendResources,
            VulkanDescriptorResourceState descriptorResources,
            VulkanRenderState renderState,
            MemoryStack stack,
            int maxShadowMatrices,
            int vertexStrideBytes
    ) {
    }

    public record CreateFrameSyncRequest(
            VulkanBackendResources backendResources,
            MemoryStack stack,
            int framesInFlight
    ) {
    }

    public record ShutdownRequest(
            VulkanBackendResources backendResources,
            Runnable destroySceneMeshes,
            Runnable destroyShadowResources,
            Runnable destroySwapchainResources,
            Runnable destroyDescriptorResources
    ) {
    }
}
