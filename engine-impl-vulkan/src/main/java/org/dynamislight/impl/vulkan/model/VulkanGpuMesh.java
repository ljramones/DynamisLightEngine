package org.dynamislight.impl.vulkan.model;

import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public final class VulkanGpuMesh {
    public final long vertexBuffer;
    public final long vertexMemory;
    public final long indexBuffer;
    public final long indexMemory;
    public final int indexCount;
    public final long vertexBytes;
    public final long indexBytes;
    public final String meshId;
    public float[] modelMatrix;
    public float colorR;
    public float colorG;
    public float colorB;
    public float metallic;
    public float roughness;
    public float reactiveStrength;
    public boolean alphaTested;
    public boolean foliage;
    public float reactiveBoost;
    public float taaHistoryClamp;
    public final VulkanGpuTexture albedoTexture;
    public final VulkanGpuTexture normalTexture;
    public final VulkanGpuTexture metallicRoughnessTexture;
    public final VulkanGpuTexture occlusionTexture;
    public final int vertexHash;
    public final int indexHash;
    public final String albedoKey;
    public final String normalKey;
    public final String metallicRoughnessKey;
    public final String occlusionKey;
    public long textureDescriptorSet = VK_NULL_HANDLE;

    public VulkanGpuMesh(
            long vertexBuffer,
            long vertexMemory,
            long indexBuffer,
            long indexMemory,
            int indexCount,
            long vertexBytes,
            long indexBytes,
            float[] modelMatrix,
            float colorR,
            float colorG,
            float colorB,
            float metallic,
            float roughness,
            float reactiveStrength,
            boolean alphaTested,
            boolean foliage,
            float reactiveBoost,
            float taaHistoryClamp,
            VulkanGpuTexture albedoTexture,
            VulkanGpuTexture normalTexture,
            VulkanGpuTexture metallicRoughnessTexture,
            VulkanGpuTexture occlusionTexture,
            String meshId,
            int vertexHash,
            int indexHash,
            String albedoKey,
            String normalKey,
            String metallicRoughnessKey,
            String occlusionKey
    ) {
        this.vertexBuffer = vertexBuffer;
        this.vertexMemory = vertexMemory;
        this.indexBuffer = indexBuffer;
        this.indexMemory = indexMemory;
        this.indexCount = indexCount;
        this.vertexBytes = vertexBytes;
        this.indexBytes = indexBytes;
        this.meshId = meshId;
        this.modelMatrix = modelMatrix;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
        this.metallic = metallic;
        this.roughness = roughness;
        this.reactiveStrength = reactiveStrength;
        this.alphaTested = alphaTested;
        this.foliage = foliage;
        this.reactiveBoost = reactiveBoost;
        this.taaHistoryClamp = taaHistoryClamp;
        this.albedoTexture = albedoTexture;
        this.normalTexture = normalTexture;
        this.metallicRoughnessTexture = metallicRoughnessTexture;
        this.occlusionTexture = occlusionTexture;
        this.vertexHash = vertexHash;
        this.indexHash = indexHash;
        this.albedoKey = albedoKey;
        this.normalKey = normalKey;
        this.metallicRoughnessKey = metallicRoughnessKey;
        this.occlusionKey = occlusionKey;
    }

    public boolean updateDynamicState(
            float[] modelMatrix,
            float colorR,
            float colorG,
            float colorB,
            float metallic,
            float roughness,
            float reactiveStrength,
            boolean alphaTested,
            boolean foliage,
            float reactiveBoost,
            float taaHistoryClamp
    ) {
        boolean changed = !Arrays.equals(this.modelMatrix, modelMatrix)
                || this.colorR != colorR
                || this.colorG != colorG
                || this.colorB != colorB
                || this.metallic != metallic
                || this.roughness != roughness
                || this.reactiveStrength != reactiveStrength
                || this.alphaTested != alphaTested
                || this.foliage != foliage
                || this.reactiveBoost != reactiveBoost
                || this.taaHistoryClamp != taaHistoryClamp;
        this.modelMatrix = modelMatrix;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
        this.metallic = metallic;
        this.roughness = roughness;
        this.reactiveStrength = reactiveStrength;
        this.alphaTested = alphaTested;
        this.foliage = foliage;
        this.reactiveBoost = reactiveBoost;
        this.taaHistoryClamp = taaHistoryClamp;
        return changed;
    }
}
