package org.dynamislight.impl.vulkan.scene;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

public final class VulkanSceneLifecycleCoordinator {
    private VulkanSceneLifecycleCoordinator() {
    }

    public static VulkanDynamicSceneUpdater.Result updateDynamicSceneState(
            List<VulkanGpuMesh> gpuMeshes,
            List<VulkanSceneMeshData> sceneMeshes
    ) {
        return VulkanDynamicSceneUpdater.update(gpuMeshes, sceneMeshes);
    }

    public static VulkanSceneUploadCoordinator.UploadResult upload(UploadInvocation in) throws EngineException {
        return VulkanSceneUploadCoordinator.upload(
                new VulkanSceneUploadCoordinator.UploadInputs(
                        in.meshBufferRebuildCount(),
                        in.destroySceneMeshes(),
                        in.device(),
                        in.physicalDevice(),
                        in.commandPool(),
                        in.graphicsQueue(),
                        in.stack(),
                        in.sceneMeshes(),
                        in.gpuMeshes(),
                        in.iblIrradiancePath(),
                        in.iblRadiancePath(),
                        in.iblBrdfLutPath(),
                        in.uniformFrameSpanBytes(),
                        in.framesInFlight(),
                        in.createTextureFromPath(),
                        in.resolveOrCreateTexture(),
                        in.textureCacheKey(),
                        in.createTextureDescriptorSets(),
                        in.vkFailure()
                )
        );
    }

    public static VulkanSceneUploadCoordinator.RebindResult rebind(RebindInvocation in) throws EngineException {
        return VulkanSceneUploadCoordinator.rebind(
                new VulkanSceneUploadCoordinator.RebindInputs(
                        in.device(),
                        in.sceneMeshes(),
                        in.gpuMeshes(),
                        in.iblIrradianceTexture(),
                        in.iblRadianceTexture(),
                        in.iblBrdfLutTexture(),
                        in.iblIrradiancePath(),
                        in.iblRadiancePath(),
                        in.iblBrdfLutPath(),
                        in.createTextureFromPath(),
                        in.resolveOrCreateTexture(),
                        in.textureCacheKey(),
                        in.refreshTextureDescriptorSets(),
                        in.markSceneStateDirty()
                )
        );
    }

    public static VulkanSceneMeshLifecycle.DestroyResult destroy(
            VkDevice device,
            List<VulkanGpuMesh> gpuMeshes,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            long textureDescriptorPool
    ) {
        return VulkanSceneMeshLifecycle.destroyMeshes(
                device,
                gpuMeshes,
                iblIrradianceTexture,
                iblRadianceTexture,
                iblBrdfLutTexture,
                textureDescriptorPool
        );
    }

    public record UploadInvocation(
            long meshBufferRebuildCount,
            VulkanSceneUploadCoordinator.DestroySceneMeshesAction destroySceneMeshes,
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            long commandPool,
            VkQueue graphicsQueue,
            MemoryStack stack,
            List<VulkanSceneMeshData> sceneMeshes,
            List<VulkanGpuMesh> gpuMeshes,
            Path iblIrradiancePath,
            Path iblRadiancePath,
            Path iblBrdfLutPath,
            int uniformFrameSpanBytes,
            int framesInFlight,
            VulkanSceneMeshLifecycle.TextureFactory createTextureFromPath,
            VulkanSceneMeshLifecycle.TextureResolver resolveOrCreateTexture,
            VulkanSceneMeshLifecycle.TextureKeyer textureCacheKey,
            VulkanSceneMeshLifecycle.DescriptorWriter createTextureDescriptorSets,
            VulkanSceneMeshLifecycle.FailureFactory vkFailure
    ) {
    }

    public record RebindInvocation(
            VkDevice device,
            List<VulkanSceneMeshData> sceneMeshes,
            List<VulkanGpuMesh> gpuMeshes,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            Path iblIrradiancePath,
            Path iblRadiancePath,
            Path iblBrdfLutPath,
            VulkanSceneMeshLifecycle.TextureFactory createTextureFromPath,
            VulkanSceneMeshLifecycle.TextureResolver resolveOrCreateTexture,
            VulkanSceneMeshLifecycle.TextureKeyer textureCacheKey,
            VulkanSceneUploadCoordinator.RefreshTextureDescriptorsAction refreshTextureDescriptorSets,
            VulkanSceneUploadCoordinator.MarkSceneDirtyAction markSceneStateDirty
    ) {
    }
}
