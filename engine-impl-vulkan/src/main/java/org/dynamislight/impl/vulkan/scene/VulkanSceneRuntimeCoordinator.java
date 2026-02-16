package org.dynamislight.impl.vulkan.scene;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

public final class VulkanSceneRuntimeCoordinator {
    private VulkanSceneRuntimeCoordinator() {
    }

    public static VulkanDynamicSceneUpdater.Result updateDynamicState(
            List<VulkanGpuMesh> gpuMeshes,
            List<VulkanSceneMeshData> sceneMeshes
    ) {
        return VulkanSceneLifecycleCoordinator.updateDynamicSceneState(gpuMeshes, sceneMeshes);
    }

    public static UploadState upload(UploadRequest in) throws EngineException {
        var result = VulkanSceneLifecycleCoordinator.upload(
                new VulkanSceneLifecycleCoordinator.UploadInvocation(
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
        return new UploadState(
                result.meshBufferRebuildCount(),
                result.iblIrradianceTexture(),
                result.iblRadianceTexture(),
                result.iblBrdfLutTexture(),
                result.estimatedGpuMemoryBytes()
        );
    }

    public static RebindState rebind(RebindRequest in) throws EngineException {
        var result = VulkanSceneLifecycleCoordinator.rebind(
                new VulkanSceneLifecycleCoordinator.RebindInvocation(
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
        return new RebindState(
                result.iblIrradianceTexture(),
                result.iblRadianceTexture(),
                result.iblBrdfLutTexture()
        );
    }

    public static DestroyState destroy(
            VkDevice device,
            List<VulkanGpuMesh> gpuMeshes,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            long textureDescriptorPool
    ) {
        var result = VulkanSceneLifecycleCoordinator.destroy(
                device,
                gpuMeshes,
                iblIrradianceTexture,
                iblRadianceTexture,
                iblBrdfLutTexture,
                textureDescriptorPool
        );
        return new DestroyState(result.textureDescriptorPool());
    }

    public record UploadRequest(
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

    public record RebindRequest(
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

    public record UploadState(
            long meshBufferRebuildCount,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            long estimatedGpuMemoryBytes
    ) {
    }

    public record RebindState(
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture
    ) {
    }

    public record DestroyState(long textureDescriptorPool) {
    }
}
