package org.dynamislight.impl.vulkan.scene;

import java.nio.file.Path;
import java.util.List;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.texture.VulkanTextureResourceOps;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

public final class VulkanSceneUploadCoordinator {
    private VulkanSceneUploadCoordinator() {
    }

    @FunctionalInterface
    public interface DestroySceneMeshesAction {
        void run();
    }

    @FunctionalInterface
    public interface RefreshTextureDescriptorsAction {
        void run() throws EngineException;
    }

    @FunctionalInterface
    public interface MarkSceneDirtyAction {
        void run(int dirtyStart, int dirtyEnd);
    }

    public static UploadResult upload(UploadInputs inputs) throws EngineException {
        long meshBufferRebuildCount = inputs.meshBufferRebuildCount() + 1;
        inputs.destroySceneMeshesAction().run();
        VulkanSceneMeshLifecycle.UploadResult result = VulkanSceneMeshLifecycle.uploadMeshes(
                inputs.device(),
                inputs.physicalDevice(),
                inputs.commandPool(),
                inputs.graphicsQueue(),
                inputs.stack(),
                inputs.sceneMeshes(),
                inputs.gpuMeshes(),
                inputs.iblIrradiancePath(),
                inputs.iblRadiancePath(),
                inputs.iblBrdfLutPath(),
                inputs.uniformFrameSpanBytes(),
                inputs.framesInFlight(),
                inputs.textureFactory(),
                inputs.textureResolver(),
                inputs.textureKeyer(),
                inputs.descriptorWriter(),
                inputs.vkFailure()
        );
        return new UploadResult(
                meshBufferRebuildCount,
                result.iblIrradianceTexture(),
                result.iblRadianceTexture(),
                result.iblBrdfLutTexture(),
                result.estimatedGpuMemoryBytes()
        );
    }

    public static RebindResult rebind(RebindInputs inputs) throws EngineException {
        VulkanSceneMeshLifecycle.RebindResult result = VulkanSceneMeshLifecycle.rebindSceneTextures(
                inputs.sceneMeshes(),
                inputs.gpuMeshes(),
                inputs.iblIrradianceTexture(),
                inputs.iblRadianceTexture(),
                inputs.iblBrdfLutTexture(),
                inputs.iblIrradiancePath(),
                inputs.iblRadiancePath(),
                inputs.iblBrdfLutPath(),
                inputs.textureFactory(),
                inputs.textureResolver(),
                inputs.textureKeyer()
        );
        inputs.refreshTextureDescriptorsAction().run();
        VulkanTextureResourceOps.destroyTextures(inputs.device(), result.staleTextures());
        inputs.markSceneDirtyAction().run(0, Math.max(0, inputs.sceneMeshes().size() - 1));
        return new RebindResult(
                result.iblIrradianceTexture(),
                result.iblRadianceTexture(),
                result.iblBrdfLutTexture()
        );
    }

    public record UploadInputs(
            long meshBufferRebuildCount,
            DestroySceneMeshesAction destroySceneMeshesAction,
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            long commandPool,
            VkQueue graphicsQueue,
            org.lwjgl.system.MemoryStack stack,
            List<VulkanSceneMeshData> sceneMeshes,
            List<VulkanGpuMesh> gpuMeshes,
            Path iblIrradiancePath,
            Path iblRadiancePath,
            Path iblBrdfLutPath,
            int uniformFrameSpanBytes,
            int framesInFlight,
            VulkanSceneMeshLifecycle.TextureFactory textureFactory,
            VulkanSceneMeshLifecycle.TextureResolver textureResolver,
            VulkanSceneMeshLifecycle.TextureKeyer textureKeyer,
            VulkanSceneMeshLifecycle.DescriptorWriter descriptorWriter,
            VulkanSceneMeshLifecycle.FailureFactory vkFailure
    ) {
    }

    public record RebindInputs(
            VkDevice device,
            List<VulkanSceneMeshData> sceneMeshes,
            List<VulkanGpuMesh> gpuMeshes,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            Path iblIrradiancePath,
            Path iblRadiancePath,
            Path iblBrdfLutPath,
            VulkanSceneMeshLifecycle.TextureFactory textureFactory,
            VulkanSceneMeshLifecycle.TextureResolver textureResolver,
            VulkanSceneMeshLifecycle.TextureKeyer textureKeyer,
            RefreshTextureDescriptorsAction refreshTextureDescriptorsAction,
            MarkSceneDirtyAction markSceneDirtyAction
    ) {
    }

    public record UploadResult(
            long meshBufferRebuildCount,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            long estimatedGpuMemoryBytes
    ) {
    }

    public record RebindResult(
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture
    ) {
    }
}
