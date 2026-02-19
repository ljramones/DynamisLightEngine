package org.dynamislight.impl.vulkan.scene;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorResourceState;
import org.dynamislight.impl.vulkan.state.VulkanDescriptorRingStats;
import org.dynamislight.impl.vulkan.state.VulkanIblState;
import org.dynamislight.impl.vulkan.state.VulkanSceneResourceState;
import org.dynamislight.impl.vulkan.texture.VulkanTextureResourceOps;
import org.lwjgl.system.MemoryStack;

import java.nio.file.Path;
import java.util.Map;

public final class VulkanSceneTextureRuntimeCoordinator {
    private VulkanSceneTextureRuntimeCoordinator() {
    }

    public static VulkanGpuTexture resolveOrCreateTexture(
            Path texturePath,
            Map<String, VulkanGpuTexture> cache,
            VulkanGpuTexture defaultTexture,
            boolean normalMap,
            VulkanSceneTextureCoordinator.TextureLoader createTextureFromPath
    ) throws EngineException {
        return VulkanSceneTextureCoordinator.resolveOrCreateTexture(
                texturePath,
                cache,
                defaultTexture,
                normalMap,
                createTextureFromPath
        );
    }

    public static void createTextureDescriptorSets(
            VulkanBackendResources backendResources,
            VulkanSceneResourceState sceneResources,
            VulkanIblState iblState,
            VulkanDescriptorResourceState descriptorResources,
            VulkanDescriptorRingStats descriptorRingStats,
            MemoryStack stack
    ) throws EngineException {
        VulkanSceneMeshCoordinator.createTextureDescriptorSets(
                new VulkanSceneMeshCoordinator.TextureDescriptorRequest(
                        backendResources,
                        sceneResources,
                        iblState,
                        descriptorResources,
                        descriptorRingStats,
                        stack
                )
        );
    }

    public static VulkanGpuTexture createTextureFromPath(
            Path texturePath,
            boolean normalMap,
            VulkanBackendResources backendResources,
            VulkanTextureResourceOps.FailureFactory vkFailure
    ) throws EngineException {
        return VulkanTextureResourceOps.createTextureFromPath(
                texturePath,
                normalMap,
                new VulkanTextureResourceOps.Context(
                        backendResources.device,
                        backendResources.physicalDevice,
                        backendResources.commandPool,
                        backendResources.graphicsQueue,
                        vkFailure
                )
        );
    }
}
