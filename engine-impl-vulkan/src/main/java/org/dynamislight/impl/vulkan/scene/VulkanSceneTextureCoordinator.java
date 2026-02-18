package org.dynamislight.impl.vulkan.scene;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.descriptor.VulkanTextureDescriptorSetCoordinator;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.lwjgl.system.MemoryStack;

public final class VulkanSceneTextureCoordinator {
    private VulkanSceneTextureCoordinator() {
    }

    public static VulkanGpuTexture resolveOrCreateTexture(
            Path texturePath,
            Map<String, VulkanGpuTexture> cache,
            VulkanGpuTexture defaultTexture,
            boolean normalMap,
            TextureLoader textureLoader
    ) throws EngineException {
        if (texturePath == null || !Files.isRegularFile(texturePath)) {
            return defaultTexture;
        }
        String cacheKey = textureCacheKey(texturePath, normalMap);
        VulkanGpuTexture cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        VulkanGpuTexture created = textureLoader.createTexture(texturePath, normalMap);
        cache.put(cacheKey, created);
        return created;
    }

    public static String textureCacheKey(Path texturePath, boolean normalMap) {
        if (texturePath == null) {
            return normalMap ? "normal:__default__" : "albedo:__default__";
        }
        return (normalMap ? "normal:" : "albedo:") + texturePath.toAbsolutePath().normalize();
    }

    public static VulkanTextureDescriptorSetCoordinator.Result createTextureDescriptorSets(CreateInputs in)
            throws EngineException {
        if (in.gpuMeshes().isEmpty() || in.textureDescriptorSetLayout() == 0L) {
            return null;
        }
        return VulkanTextureDescriptorSetCoordinator.createOrReuse(
                new VulkanTextureDescriptorSetCoordinator.Inputs(
                        in.device(),
                        in.stack(),
                        in.gpuMeshes(),
                        in.textureDescriptorSetLayout(),
                        in.textureDescriptorPool(),
                        in.descriptorRingSetCapacity(),
                        in.descriptorRingPeakSetCapacity(),
                        in.descriptorRingPeakWasteSetCount(),
                        in.descriptorPoolBuildCount(),
                        in.descriptorPoolRebuildCount(),
                        in.descriptorRingGrowthRebuildCount(),
                        in.descriptorRingSteadyRebuildCount(),
                        in.descriptorRingPoolReuseCount(),
                        in.descriptorRingPoolResetFailureCount(),
                        in.descriptorRingMaxSetCapacity(),
                        in.shadowDepthImageView(),
                        in.shadowSampler(),
                        in.shadowMomentImageView(),
                        in.shadowMomentSampler(),
                        in.iblIrradianceTexture(),
                        in.iblRadianceTexture(),
                        in.iblBrdfLutTexture(),
                        in.probeRadianceTexture()
                )
        );
    }

    @FunctionalInterface
    public interface TextureLoader {
        VulkanGpuTexture createTexture(Path texturePath, boolean normalMap) throws EngineException;
    }

    public record CreateInputs(
            org.lwjgl.vulkan.VkDevice device,
            MemoryStack stack,
            java.util.List<VulkanGpuMesh> gpuMeshes,
            long textureDescriptorSetLayout,
            long textureDescriptorPool,
            int descriptorRingSetCapacity,
            int descriptorRingPeakSetCapacity,
            int descriptorRingPeakWasteSetCount,
            long descriptorPoolBuildCount,
            long descriptorPoolRebuildCount,
            long descriptorRingGrowthRebuildCount,
            long descriptorRingSteadyRebuildCount,
            long descriptorRingPoolReuseCount,
            long descriptorRingPoolResetFailureCount,
            int descriptorRingMaxSetCapacity,
            long shadowDepthImageView,
            long shadowSampler,
            long shadowMomentImageView,
            long shadowMomentSampler,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            VulkanGpuTexture probeRadianceTexture
    ) {
    }
}
