package org.dynamisengine.light.impl.vulkan.scene;

import org.dynamisengine.light.api.error.EngineErrorCode;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.gpu.api.error.GpuException;
import org.dynamisengine.gpu.vulkan.descriptor.VulkanBindlessDescriptorHeap;
import org.dynamisengine.gpu.vulkan.memory.VulkanBufferOps;
import org.dynamisengine.gpu.vulkan.memory.VulkanBufferAlloc;
import org.dynamisengine.light.impl.vulkan.model.VulkanGpuMesh;
import org.dynamisengine.light.impl.vulkan.model.VulkanInstanceBatch;
import org.dynamisengine.light.impl.vulkan.model.VulkanInstanceBatchBuffer;
import org.dynamisengine.light.impl.vulkan.model.VulkanGpuTexture;
import org.dynamisengine.gpu.vulkan.buffer.VulkanMorphTargetBuffer;
import org.dynamisengine.light.impl.vulkan.model.VulkanMorphWeightUniforms;
import org.dynamisengine.light.impl.vulkan.model.VulkanSceneMeshData;
import org.dynamisengine.light.impl.vulkan.model.VulkanSkinnedMeshUniforms;
import org.dynamisengine.light.impl.vulkan.texture.VulkanTextureResourceOps;
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
            long skinnedDescriptorSetLayout,
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

            VulkanBufferAlloc vertexAlloc;
            VulkanBufferAlloc indexAlloc;
            try {
                vertexAlloc = VulkanBufferOps.createDeviceLocalBufferWithStaging(
                        device,
                        physicalDevice,
                        commandPool,
                        graphicsQueue,
                        stack,
                        vertexData,
                        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                        vkFailure::failure
                );
                indexAlloc = VulkanBufferOps.createDeviceLocalBufferWithStaging(
                        device,
                        physicalDevice,
                        commandPool,
                        graphicsQueue,
                        stack,
                        indexData,
                        VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                        vkFailure::failure
                );
            } catch (GpuException ex) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "Failed to upload mesh vertex/index buffers: " + ex.getMessage(),
                        false
                );
            }
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
            boolean skinned = mesh.skinned() && mesh.jointCount() > 0 && skinnedDescriptorSetLayout != VK_NULL_HANDLE;
            VulkanSkinnedMeshUniforms skinnedUniforms = null;
            if (skinned) {
                skinnedUniforms = VulkanSkinnedMeshUniforms.create(
                        device,
                        physicalDevice,
                        skinnedDescriptorSetLayout,
                        mesh.jointCount()
                );
            }
            int morphTargetCount = Math.max(0, mesh.morphTargetCount());
            int morphTargetHash = mesh.morphTargetDeltas() == null ? 0 : Arrays.hashCode(mesh.morphTargetDeltas());
            VulkanMorphTargetBuffer morphTargets = mesh.morphTargets();
            if (morphTargets == null && morphTargetCount > 0) {
                int morphVertexCount = mesh.morphTargetDeltas().length / (morphTargetCount * 6);
                try {
                    morphTargets = VulkanMorphTargetBuffer.create(
                            device,
                            physicalDevice,
                            commandPool,
                            graphicsQueue,
                            stack,
                            mesh.morphTargetDeltas(),
                            morphVertexCount,
                            morphTargetCount,
                            vkFailure::failure
                    );
                } catch (GpuException ex) {
                    throw new EngineException(
                            EngineErrorCode.BACKEND_INIT_FAILED,
                            "Failed to create morph target buffer: " + ex.getMessage(),
                            false
                    );
                }
            }
            VulkanMorphWeightUniforms morphWeightUniforms = null;
            if (morphTargets != null && morphTargetCount > 0 && skinnedDescriptorSetLayout != VK_NULL_HANDLE) {
                morphWeightUniforms = VulkanMorphWeightUniforms.create(
                        device,
                        physicalDevice,
                        skinnedDescriptorSetLayout,
                        morphTargets.bufferHandle(),
                        morphTargets.bytes()
                );
            }
            float[] localBounds = computeLocalBounds(mesh.vertices(), skinned ? 16 : 11);

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
                    localBounds[0],
                    localBounds[1],
                    localBounds[2],
                    localBounds[3],
                    albedoKey,
                    normalKey,
                    metallicRoughnessKey,
                    occlusionKey,
                    skinned,
                    mesh.jointCount(),
                    skinnedUniforms,
                    0L,
                    0L,
                    0L,
                    morphTargetCount,
                    morphTargetHash,
                    morphTargets,
                    morphWeightUniforms
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
                    mesh.localBoundsCenterX,
                    mesh.localBoundsCenterY,
                    mesh.localBoundsCenterZ,
                    mesh.localBoundsRadius,
                    albedoKey,
                    normalKey,
                    metallicRoughnessKey,
                    occlusionKey,
                    mesh.skinned,
                    mesh.jointCount,
                    mesh.skinnedUniforms,
                    mesh.bindlessJointHandle,
                    mesh.bindlessMorphDeltaHandle,
                    mesh.bindlessMorphWeightHandle,
                    mesh.morphTargetCount,
                    mesh.morphTargetHash,
                    mesh.morphTargets,
                    mesh.morphWeightUniforms
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
            if (mesh.skinnedUniforms != null) {
                mesh.skinnedUniforms.destroy();
            }
            if (mesh.morphTargets != null) {
                mesh.morphTargets.destroy(device);
            }
            if (mesh.morphWeightUniforms != null) {
                mesh.morphWeightUniforms.destroy();
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

    private static float[] computeLocalBounds(float[] vertices, int strideFloats) {
        if (vertices == null || vertices.length < strideFloats * 3 || strideFloats < 3) {
            return new float[]{0f, 0f, 0f, 1f};
        }
        int vertexCount = vertices.length / strideFloats;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vertexCount; i++) {
            int base = i * strideFloats;
            float x = vertices[base];
            float y = vertices[base + 1];
            float z = vertices[base + 2];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;
        float radiusSq = 0f;
        for (int i = 0; i < vertexCount; i++) {
            int base = i * strideFloats;
            float dx = vertices[base] - cx;
            float dy = vertices[base + 1] - cy;
            float dz = vertices[base + 2] - cz;
            radiusSq = Math.max(radiusSq, (dx * dx) + (dy * dy) + (dz * dz));
        }
        return new float[]{cx, cy, cz, (float) Math.sqrt(Math.max(1e-8f, radiusSq))};
    }

    public static void updateSkinnedMesh(
            List<VulkanGpuMesh> gpuMeshes,
            int meshHandle,
            float[] jointMatrices,
            VulkanBindlessDescriptorHeap bindlessDescriptorHeap,
            long bindlessFrameSerial
    ) throws EngineException {
        if (gpuMeshes == null || meshHandle < 0 || meshHandle >= gpuMeshes.size()) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "Invalid meshHandle for skinned mesh update: " + meshHandle,
                    true
            );
        }
        VulkanGpuMesh mesh = gpuMeshes.get(meshHandle);
        if (!mesh.skinned || mesh.skinnedUniforms == null) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "Mesh handle " + meshHandle + " is not a skinned mesh",
                    true
            );
        }
        int expectedFloats = mesh.jointCount * 16;
        if (mesh.jointCount <= 0 || jointMatrices == null || jointMatrices.length != expectedFloats) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "jointMatrices must contain exactly " + expectedFloats + " floats for mesh handle " + meshHandle,
                    true
            );
        }
        mesh.skinnedUniforms.upload(jointMatrices);
        if (bindlessDescriptorHeap != null && bindlessDescriptorHeap.active() && mesh.skinningBufferHandle != VK_NULL_HANDLE) {
            if (mesh.bindlessJointHandle == 0L) {
                mesh.bindlessJointHandle = bindlessDescriptorHeap.allocate(VulkanBindlessDescriptorHeap.HeapType.JOINT_PALETTE);
            }
            if (mesh.bindlessJointHandle != 0L) {
                bindlessDescriptorHeap.updateJointPaletteDescriptor(
                        mesh.bindlessJointHandle,
                        bindlessFrameSerial,
                        mesh.skinnedUniforms.bufferHandle(),
                        (long) mesh.jointCount * 64L
                );
            }
        }
    }

    public static void updateMorphWeights(
            List<VulkanGpuMesh> gpuMeshes,
            int meshHandle,
            float[] weights,
            VulkanBindlessDescriptorHeap bindlessDescriptorHeap,
            long bindlessFrameSerial
    ) throws EngineException {
        if (gpuMeshes == null || meshHandle < 0 || meshHandle >= gpuMeshes.size()) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "Invalid meshHandle for morph weight update: " + meshHandle,
                    true
            );
        }
        VulkanGpuMesh mesh = gpuMeshes.get(meshHandle);
        if (mesh.morphTargetCount <= 0 || mesh.morphWeightUniforms == null) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "Mesh handle " + meshHandle + " is not a morph-target mesh",
                    true
            );
        }
        if (weights == null || weights.length != mesh.morphTargetCount) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "weights must contain exactly " + mesh.morphTargetCount + " floats for mesh handle " + meshHandle,
                    true
            );
        }
        mesh.morphWeightUniforms.upload(weights);
        if (bindlessDescriptorHeap != null
                && bindlessDescriptorHeap.active()
                && mesh.bindlessMorphWeightHandle != 0L
                && mesh.morphWeightUniforms.bufferHandle() != VK_NULL_HANDLE) {
            bindlessDescriptorHeap.updateMorphWeightDescriptor(
                    mesh.bindlessMorphWeightHandle,
                    bindlessFrameSerial,
                    mesh.morphWeightUniforms.bufferHandle(),
                    (long) VulkanMorphWeightUniforms.MAX_WEIGHTS * Float.BYTES
            );
        }
    }

    public static void syncBindlessMorphDescriptors(
            List<VulkanGpuMesh> gpuMeshes,
            VulkanBindlessDescriptorHeap bindlessDescriptorHeap,
            long bindlessFrameSerial
    ) {
        if (gpuMeshes == null || bindlessDescriptorHeap == null || !bindlessDescriptorHeap.active()) {
            return;
        }
        for (VulkanGpuMesh mesh : gpuMeshes) {
            if (mesh == null || mesh.morphTargetCount <= 0 || mesh.morphTargets == null || mesh.morphWeightUniforms == null) {
                continue;
            }
            if (mesh.bindlessMorphDeltaHandle == 0L) {
                mesh.bindlessMorphDeltaHandle = bindlessDescriptorHeap.allocate(VulkanBindlessDescriptorHeap.HeapType.MORPH_DELTA);
            }
            if (mesh.bindlessMorphWeightHandle == 0L) {
                mesh.bindlessMorphWeightHandle = bindlessDescriptorHeap.allocate(VulkanBindlessDescriptorHeap.HeapType.MORPH_WEIGHT);
            }
            if (mesh.bindlessMorphDeltaHandle != 0L && mesh.morphTargets.bufferHandle() != VK_NULL_HANDLE) {
                bindlessDescriptorHeap.updateMorphDeltaDescriptor(
                        mesh.bindlessMorphDeltaHandle,
                        bindlessFrameSerial,
                        mesh.morphTargets.bufferHandle(),
                        mesh.morphTargets.bytes()
                );
            }
            if (mesh.bindlessMorphWeightHandle != 0L && mesh.morphWeightUniforms.bufferHandle() != VK_NULL_HANDLE) {
                bindlessDescriptorHeap.updateMorphWeightDescriptor(
                        mesh.bindlessMorphWeightHandle,
                        bindlessFrameSerial,
                        mesh.morphWeightUniforms.bufferHandle(),
                        (long) VulkanMorphWeightUniforms.MAX_WEIGHTS * Float.BYTES
                );
            }
        }
    }

    public static int registerInstanceBatch(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            long skinnedDescriptorSetLayout,
            VulkanBindlessDescriptorHeap bindlessDescriptorHeap,
            long bindlessFrameSerial,
            List<VulkanGpuMesh> gpuMeshes,
            Map<Integer, VulkanInstanceBatch> instanceBatches,
            int meshHandle,
            float[][] modelMatrices,
            int nextBatchHandle
    ) throws EngineException {
        if (device == null || physicalDevice == null || skinnedDescriptorSetLayout == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.INVALID_STATE,
                    "Cannot register instance batch before Vulkan resources are initialized",
                    false
            );
        }
        if (gpuMeshes == null || meshHandle < 0 || meshHandle >= gpuMeshes.size()) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "Invalid meshHandle for instance batch registration: " + meshHandle,
                    true
            );
        }
        if (modelMatrices == null || modelMatrices.length == 0) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "modelMatrices must contain at least one matrix",
                    true
            );
        }
        VulkanInstanceBatchBuffer buffer = VulkanInstanceBatchBuffer.create(
                device,
                physicalDevice,
                skinnedDescriptorSetLayout,
                modelMatrices.length
        );
        buffer.upload(modelMatrices, null, null);
        long bindlessHandle = 0L;
        if (bindlessDescriptorHeap != null && bindlessDescriptorHeap.active()) {
            bindlessHandle = bindlessDescriptorHeap.allocate(VulkanBindlessDescriptorHeap.HeapType.INSTANCE_DATA);
            if (bindlessHandle != 0L) {
                bindlessDescriptorHeap.updateInstanceDataDescriptor(
                        bindlessHandle,
                        bindlessFrameSerial,
                        buffer.bufferHandle(),
                        buffer.allocatedBytes()
                );
            }
        }
        int handle = Math.max(0, nextBatchHandle);
        instanceBatches.put(handle, new VulkanInstanceBatch(handle, meshHandle, buffer, bindlessHandle, modelMatrices.length));
        return handle;
    }

    public static void updateInstanceBatch(
            Map<Integer, VulkanInstanceBatch> instanceBatches,
            int batchHandle,
            float[][] modelMatrices,
            VulkanBindlessDescriptorHeap bindlessDescriptorHeap,
            long bindlessFrameSerial
    ) throws EngineException {
        VulkanInstanceBatch batch = instanceBatches == null ? null : instanceBatches.get(batchHandle);
        if (batch == null || batch.buffer == null) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "Invalid batchHandle for instance batch update: " + batchHandle,
                    true
            );
        }
        if (modelMatrices == null || modelMatrices.length == 0) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "modelMatrices must contain at least one matrix",
                    true
            );
        }
        if (modelMatrices.length > batch.buffer.capacity()) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "modelMatrices exceed registered capacity for batch " + batchHandle,
                    true
            );
        }
        batch.buffer.upload(modelMatrices, null, null);
        batch.instanceCount = modelMatrices.length;
        if (bindlessDescriptorHeap != null
                && bindlessDescriptorHeap.active()
                && batch.bindlessInstanceHandle != 0L) {
            bindlessDescriptorHeap.updateInstanceDataDescriptor(
                    batch.bindlessInstanceHandle,
                    bindlessFrameSerial,
                    batch.buffer.bufferHandle(),
                    batch.buffer.allocatedBytes()
            );
        }
    }

    public static void removeInstanceBatch(
            Map<Integer, VulkanInstanceBatch> instanceBatches,
            int batchHandle,
            VulkanBindlessDescriptorHeap bindlessDescriptorHeap,
            long bindlessFrameSerial
    ) throws EngineException {
        VulkanInstanceBatch batch = instanceBatches == null ? null : instanceBatches.remove(batchHandle);
        if (batch == null) {
            throw new EngineException(
                    EngineErrorCode.INVALID_ARGUMENT,
                    "Invalid batchHandle for instance batch removal: " + batchHandle,
                    true
            );
        }
        if (bindlessDescriptorHeap != null && bindlessDescriptorHeap.active() && batch.bindlessInstanceHandle != 0L) {
            bindlessDescriptorHeap.retire(batch.bindlessInstanceHandle, bindlessFrameSerial);
            batch.bindlessInstanceHandle = 0L;
        }
        if (batch.buffer != null) {
            batch.buffer.destroy();
        }
    }

    public static void clearInstanceBatches(
            Map<Integer, VulkanInstanceBatch> instanceBatches,
            VulkanBindlessDescriptorHeap bindlessDescriptorHeap,
            long bindlessFrameSerial
    ) {
        if (instanceBatches == null || instanceBatches.isEmpty()) {
            return;
        }
        for (VulkanInstanceBatch batch : instanceBatches.values()) {
            if (bindlessDescriptorHeap != null
                    && bindlessDescriptorHeap.active()
                    && batch != null
                    && batch.bindlessInstanceHandle != 0L) {
                bindlessDescriptorHeap.retire(batch.bindlessInstanceHandle, bindlessFrameSerial);
                batch.bindlessInstanceHandle = 0L;
            }
            if (batch != null && batch.buffer != null) {
                batch.buffer.destroy();
            }
        }
        instanceBatches.clear();
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
