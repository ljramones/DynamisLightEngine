package org.dynamislight.impl.vulkan.scene;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.memory.VulkanMemoryOps;
import org.dynamislight.impl.vulkan.model.VulkanBufferAlloc;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamislight.impl.vulkan.texture.VulkanTextureResourceOps;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;

public final class VulkanSceneMeshLifecycle {
    private VulkanSceneMeshLifecycle() {
    }

    @FunctionalInterface
    public interface TextureFactory {
        VulkanGpuTexture create(Path path, boolean normalMap) throws EngineException;
    }

    @FunctionalInterface
    public interface TextureResolver {
        VulkanGpuTexture resolve(Path path, Map<String, VulkanGpuTexture> cache, VulkanGpuTexture defaultTexture, boolean normalMap)
                throws EngineException;
    }

    @FunctionalInterface
    public interface TextureKeyer {
        String key(Path path, boolean normalMap);
    }

    @FunctionalInterface
    public interface DescriptorWriter {
        void write(MemoryStack stack) throws EngineException;
    }

    @FunctionalInterface
    public interface FailureFactory {
        EngineException failure(String operation, int result);
    }

    public static UploadResult uploadMeshes(
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
            TextureFactory textureFactory,
            TextureResolver textureResolver,
            TextureKeyer textureKeyer,
            DescriptorWriter descriptorWriter,
            FailureFactory vkFailure
    ) throws EngineException {
        Map<String, VulkanGpuTexture> textureCache = new HashMap<>();
        VulkanGpuTexture defaultAlbedo = textureFactory.create(null, false);
        VulkanGpuTexture defaultNormal = textureFactory.create(null, true);
        VulkanGpuTexture defaultMetallicRoughness = textureFactory.create(null, false);
        VulkanGpuTexture defaultOcclusion = textureFactory.create(null, false);
        VulkanGpuTexture iblIrradianceTexture = textureResolver.resolve(iblIrradiancePath, textureCache, defaultAlbedo, false);
        VulkanGpuTexture iblRadianceTexture = textureResolver.resolve(iblRadiancePath, textureCache, defaultAlbedo, false);
        VulkanGpuTexture iblBrdfLutTexture = textureResolver.resolve(iblBrdfLutPath, textureCache, defaultAlbedo, false);

        for (VulkanSceneMeshData mesh : sceneMeshes) {
            float[] vertices = mesh.vertices();
            int[] indices = mesh.indices();
            ByteBuffer vertexData = ByteBuffer.allocateDirect(vertices.length * Float.BYTES).order(ByteOrder.nativeOrder());
            FloatBuffer vb = vertexData.asFloatBuffer();
            vb.put(vertices);
            vertexData.limit(vertices.length * Float.BYTES);

            ByteBuffer indexData = ByteBuffer.allocateDirect(indices.length * Integer.BYTES).order(ByteOrder.nativeOrder());
            IntBuffer ib = indexData.asIntBuffer();
            ib.put(indices);
            indexData.limit(indices.length * Integer.BYTES);

            VulkanBufferAlloc vertexAlloc = VulkanMemoryOps.createDeviceLocalBufferWithStaging(
                    device,
                    physicalDevice,
                    commandPool,
                    graphicsQueue,
                    stack,
                    vertexData,
                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    vkFailure::failure
            );
            VulkanBufferAlloc indexAlloc = VulkanMemoryOps.createDeviceLocalBufferWithStaging(
                    device,
                    physicalDevice,
                    commandPool,
                    graphicsQueue,
                    stack,
                    indexData,
                    VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    vkFailure::failure
            );
            int vertexHash = Arrays.hashCode(vertices);
            int indexHash = Arrays.hashCode(indices);
            String albedoKey = textureKeyer.key(mesh.albedoTexturePath(), false);
            String normalKey = textureKeyer.key(mesh.normalTexturePath(), true);
            String metallicRoughnessKey = textureKeyer.key(mesh.metallicRoughnessTexturePath(), false);
            String occlusionKey = textureKeyer.key(mesh.occlusionTexturePath(), false);
            VulkanGpuTexture albedoTexture = textureResolver.resolve(mesh.albedoTexturePath(), textureCache, defaultAlbedo, false);
            VulkanGpuTexture normalTexture = textureResolver.resolve(mesh.normalTexturePath(), textureCache, defaultNormal, true);
            VulkanGpuTexture metallicRoughnessTexture = textureResolver.resolve(
                    mesh.metallicRoughnessTexturePath(),
                    textureCache,
                    defaultMetallicRoughness,
                    false
            );
            VulkanGpuTexture occlusionTexture = textureResolver.resolve(mesh.occlusionTexturePath(), textureCache, defaultOcclusion, false);

            gpuMeshes.add(new VulkanGpuMesh(
                    vertexAlloc.buffer(),
                    vertexAlloc.memory(),
                    indexAlloc.buffer(),
                    indexAlloc.memory(),
                    indices.length,
                    vertexData.remaining(),
                    indexData.remaining(),
                    mesh.modelMatrix().clone(),
                    mesh.modelMatrix().clone(),
                    mesh.color()[0],
                    mesh.color()[1],
                    mesh.color()[2],
                    mesh.metallic(),
                    mesh.roughness(),
                    mesh.reactiveStrength(),
                    mesh.alphaTested(),
                    mesh.foliage(),
                    mesh.reflectionOverrideMode(),
                    mesh.reactiveBoost(),
                    mesh.taaHistoryClamp(),
                    mesh.emissiveReactiveBoost(),
                    mesh.reactivePreset(),
                    albedoTexture,
                    normalTexture,
                    metallicRoughnessTexture,
                    occlusionTexture,
                    mesh.meshId(),
                    vertexHash,
                    indexHash,
                    albedoKey,
                    normalKey,
                    metallicRoughnessKey,
                    occlusionKey
            ));
        }

        descriptorWriter.write(stack);

        long meshBytes = 0;
        long textureBytes = 0;
        Set<VulkanGpuTexture> uniqueTextures = new HashSet<>();
        for (VulkanGpuMesh mesh : gpuMeshes) {
            meshBytes += mesh.vertexBytes + mesh.indexBytes;
            if (uniqueTextures.add(mesh.albedoTexture)) {
                textureBytes += mesh.albedoTexture.bytes();
            }
            if (uniqueTextures.add(mesh.normalTexture)) {
                textureBytes += mesh.normalTexture.bytes();
            }
            if (uniqueTextures.add(mesh.metallicRoughnessTexture)) {
                textureBytes += mesh.metallicRoughnessTexture.bytes();
            }
            if (uniqueTextures.add(mesh.occlusionTexture)) {
                textureBytes += mesh.occlusionTexture.bytes();
            }
        }
        if (uniqueTextures.add(iblIrradianceTexture)) {
            textureBytes += iblIrradianceTexture.bytes();
        }
        if (uniqueTextures.add(iblRadianceTexture)) {
            textureBytes += iblRadianceTexture.bytes();
        }
        if (uniqueTextures.add(iblBrdfLutTexture)) {
            textureBytes += iblBrdfLutTexture.bytes();
        }
        long uniformBytes = (long) uniformFrameSpanBytes * framesInFlight * 2L;
        long estimatedGpuMemoryBytes = uniformBytes + meshBytes + textureBytes;
        return new UploadResult(iblIrradianceTexture, iblRadianceTexture, iblBrdfLutTexture, estimatedGpuMemoryBytes);
    }

    public static RebindResult rebindSceneTextures(
            List<VulkanSceneMeshData> sceneMeshes,
            List<VulkanGpuMesh> gpuMeshes,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            Path iblIrradiancePath,
            Path iblRadiancePath,
            Path iblBrdfLutPath,
            TextureFactory textureFactory,
            TextureResolver textureResolver,
            TextureKeyer textureKeyer
    ) throws EngineException {
        Map<String, VulkanGpuMesh> byId = new HashMap<>();
        for (VulkanGpuMesh mesh : gpuMeshes) {
            byId.put(mesh.meshId, mesh);
        }
        Set<VulkanGpuTexture> oldTextures = VulkanTextureResourceOps.collectLiveTextures(
                gpuMeshes,
                iblIrradianceTexture,
                iblRadianceTexture,
                iblBrdfLutTexture
        );
        List<VulkanGpuMesh> rebound = new java.util.ArrayList<>(sceneMeshes.size());
        Map<String, VulkanGpuTexture> textureCache = new HashMap<>();
        VulkanGpuTexture defaultAlbedo = textureFactory.create(null, false);
        VulkanGpuTexture defaultNormal = textureFactory.create(null, true);
        VulkanGpuTexture defaultMetallicRoughness = textureFactory.create(null, false);
        VulkanGpuTexture defaultOcclusion = textureFactory.create(null, false);
        VulkanGpuTexture newIblIrradiance = textureResolver.resolve(iblIrradiancePath, textureCache, defaultAlbedo, false);
        VulkanGpuTexture newIblRadiance = textureResolver.resolve(iblRadiancePath, textureCache, defaultAlbedo, false);
        VulkanGpuTexture newIblBrdfLut = textureResolver.resolve(iblBrdfLutPath, textureCache, defaultAlbedo, false);
        for (VulkanSceneMeshData sceneMesh : sceneMeshes) {
            VulkanGpuMesh mesh = byId.get(sceneMesh.meshId());
            if (mesh == null) {
                throw new EngineException(
                        EngineErrorCode.RESOURCE_CREATION_FAILED,
                        "Unable to resolve reusable mesh '" + sceneMesh.meshId() + "' for texture rebind path",
                        false
                );
            }
            String albedoKey = textureKeyer.key(sceneMesh.albedoTexturePath(), false);
            String normalKey = textureKeyer.key(sceneMesh.normalTexturePath(), true);
            String metallicRoughnessKey = textureKeyer.key(sceneMesh.metallicRoughnessTexturePath(), false);
            String occlusionKey = textureKeyer.key(sceneMesh.occlusionTexturePath(), false);
            VulkanGpuTexture albedoTexture = textureResolver.resolve(sceneMesh.albedoTexturePath(), textureCache, defaultAlbedo, false);
            VulkanGpuTexture normalTexture = textureResolver.resolve(sceneMesh.normalTexturePath(), textureCache, defaultNormal, true);
            VulkanGpuTexture metallicRoughnessTexture = textureResolver.resolve(
                    sceneMesh.metallicRoughnessTexturePath(),
                    textureCache,
                    defaultMetallicRoughness,
                    false
            );
            VulkanGpuTexture occlusionTexture = textureResolver.resolve(sceneMesh.occlusionTexturePath(), textureCache, defaultOcclusion, false);
            rebound.add(new VulkanGpuMesh(
                    mesh.vertexBuffer,
                    mesh.vertexMemory,
                    mesh.indexBuffer,
                    mesh.indexMemory,
                    mesh.indexCount,
                    mesh.vertexBytes,
                    mesh.indexBytes,
                    sceneMesh.modelMatrix().clone(),
                    mesh.modelMatrix.clone(),
                    sceneMesh.color()[0],
                    sceneMesh.color()[1],
                    sceneMesh.color()[2],
                    sceneMesh.metallic(),
                    sceneMesh.roughness(),
                    sceneMesh.reactiveStrength(),
                    sceneMesh.alphaTested(),
                    sceneMesh.foliage(),
                    sceneMesh.reflectionOverrideMode(),
                    sceneMesh.reactiveBoost(),
                    sceneMesh.taaHistoryClamp(),
                    sceneMesh.emissiveReactiveBoost(),
                    sceneMesh.reactivePreset(),
                    albedoTexture,
                    normalTexture,
                    metallicRoughnessTexture,
                    occlusionTexture,
                    mesh.meshId,
                    mesh.vertexHash,
                    mesh.indexHash,
                    albedoKey,
                    normalKey,
                    metallicRoughnessKey,
                    occlusionKey
            ));
        }
        gpuMeshes.clear();
        gpuMeshes.addAll(rebound);
        Set<VulkanGpuTexture> newTextures = VulkanTextureResourceOps.collectLiveTextures(gpuMeshes, newIblIrradiance, newIblRadiance, newIblBrdfLut);
        oldTextures.removeAll(newTextures);
        return new RebindResult(newIblIrradiance, newIblRadiance, newIblBrdfLut, oldTextures);
    }

    public static DestroyResult destroyMeshes(
            VkDevice device,
            List<VulkanGpuMesh> gpuMeshes,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            long textureDescriptorPool
    ) {
        if (device == null) {
            gpuMeshes.clear();
            return new DestroyResult(VK_NULL_HANDLE);
        }
        Set<VulkanGpuTexture> uniqueTextures = new HashSet<>();
        for (VulkanGpuMesh mesh : gpuMeshes) {
            if (mesh.vertexBuffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, mesh.vertexBuffer, null);
            }
            if (mesh.vertexMemory != VK_NULL_HANDLE) {
                vkFreeMemory(device, mesh.vertexMemory, null);
            }
            if (mesh.indexBuffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, mesh.indexBuffer, null);
            }
            if (mesh.indexMemory != VK_NULL_HANDLE) {
                vkFreeMemory(device, mesh.indexMemory, null);
            }
            uniqueTextures.add(mesh.albedoTexture);
            uniqueTextures.add(mesh.normalTexture);
            uniqueTextures.add(mesh.metallicRoughnessTexture);
            uniqueTextures.add(mesh.occlusionTexture);
        }
        uniqueTextures.add(iblIrradianceTexture);
        uniqueTextures.add(iblRadianceTexture);
        uniqueTextures.add(iblBrdfLutTexture);
        VulkanTextureResourceOps.destroyTextures(device, uniqueTextures);
        long nextTextureDescriptorPool = textureDescriptorPool;
        if (nextTextureDescriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, nextTextureDescriptorPool, null);
            nextTextureDescriptorPool = VK_NULL_HANDLE;
        }
        gpuMeshes.clear();
        return new DestroyResult(nextTextureDescriptorPool);
    }

    public record UploadResult(
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            long estimatedGpuMemoryBytes
    ) {
    }

    public record RebindResult(
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            Set<VulkanGpuTexture> staleTextures
    ) {
    }

    public record DestroyResult(long textureDescriptorPool) {
    }
}
