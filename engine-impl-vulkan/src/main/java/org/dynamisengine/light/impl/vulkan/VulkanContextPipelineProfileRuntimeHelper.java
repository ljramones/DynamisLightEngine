package org.dynamisengine.light.impl.vulkan;

import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.impl.vulkan.profile.VulkanPipelineProfileCache;
import org.dynamisengine.light.impl.vulkan.profile.VulkanPipelineProfileCompilation;
import org.dynamisengine.light.impl.vulkan.profile.VulkanPipelineProfileKey;
import org.dynamisengine.light.impl.vulkan.profile.VulkanPipelineProfileResolver;
import org.dynamisengine.light.impl.vulkan.scene.VulkanSceneTextureRuntimeCoordinator;
import org.dynamisengine.light.impl.vulkan.state.VulkanBackendResources;
import org.dynamisengine.light.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamisengine.light.impl.vulkan.state.VulkanDescriptorRingStats;
import org.dynamisengine.light.impl.vulkan.state.VulkanIblState;
import org.dynamisengine.light.impl.vulkan.state.VulkanRenderState;
import org.dynamisengine.light.impl.vulkan.state.VulkanSceneResourceState;
import org.dynamisengine.light.spi.render.RenderFeatureMode;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

final class VulkanContextPipelineProfileRuntimeHelper {
    private VulkanContextPipelineProfileRuntimeHelper() {
    }

    static RefreshResult refreshActiveProfile(
            QualityTier tier,
            VulkanRenderState renderState,
            int localLightCount,
            RenderFeatureMode lightingModeOverride,
            RenderFeatureMode pbrModeOverride,
            RenderFeatureMode giModeOverride,
            RenderFeatureMode skyModeOverride,
            RenderFeatureMode rtModeOverride,
            VulkanPipelineProfileCache cache,
            VulkanPipelineProfileKey currentKey
    ) {
        VulkanPipelineProfileKey resolved = VulkanPipelineProfileResolver.resolve(
                tier,
                renderState,
                localLightCount,
                lightingModeOverride,
                pbrModeOverride,
                giModeOverride,
                skyModeOverride,
                rtModeOverride,
                0,
                0,
                0,
                true,
                false,
                false,
                false,
                false,
                false
        );
        boolean changed = !resolved.equals(currentKey);
        VulkanPipelineProfileCompilation compilation = cache.getOrCompile(resolved);
        return new RefreshResult(resolved, compilation, changed);
    }

    static void rebuildRuntimeResources(
            VulkanBackendResources backendResources,
            VulkanSceneResourceState sceneResources,
            VulkanIblState iblState,
            VulkanDescriptorResourceState descriptorResources,
            VulkanDescriptorRingStats descriptorRingStats,
            DestroyStep destroyShadowResources,
            DestroyStep destroySwapchainResources,
            DestroyStep destroyDescriptorResources,
            StackCreateStep createDescriptorResources,
            StackResizeCreateStep createSwapchainResources,
            StackCreateStep createShadowResources
    ) throws EngineException {
        if (backendResources.device == null || backendResources.swapchain == VK_NULL_HANDLE) {
            return;
        }
        vkDeviceWaitIdle(backendResources.device);
        int width = Math.max(1, backendResources.swapchainWidth);
        int height = Math.max(1, backendResources.swapchainHeight);
        destroyShadowResources.run();
        destroySwapchainResources.run();
        destroyDescriptorResources.run();
        try (MemoryStack stack = stackPush()) {
            createDescriptorResources.run(stack);
            createSwapchainResources.run(stack, width, height);
            createShadowResources.run(stack);
            if (!sceneResources.gpuMeshes.isEmpty()) {
                VulkanSceneTextureRuntimeCoordinator.createTextureDescriptorSets(
                        backendResources,
                        sceneResources,
                        iblState,
                        descriptorResources,
                        descriptorRingStats,
                        stack
                );
            }
        }
    }

    record RefreshResult(
            VulkanPipelineProfileKey key,
            VulkanPipelineProfileCompilation compilation,
            boolean changed
    ) {
    }

    @FunctionalInterface
    interface DestroyStep {
        void run();
    }

    @FunctionalInterface
    interface StackCreateStep {
        void run(MemoryStack stack) throws EngineException;
    }

    @FunctionalInterface
    interface StackResizeCreateStep {
        void run(MemoryStack stack, int width, int height) throws EngineException;
    }
}
