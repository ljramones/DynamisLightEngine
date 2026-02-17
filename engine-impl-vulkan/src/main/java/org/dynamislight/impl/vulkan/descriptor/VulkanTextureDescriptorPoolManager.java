package org.dynamislight.impl.vulkan.descriptor;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDevice;

import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkResetDescriptorPool;

public final class VulkanTextureDescriptorPoolManager {
    private VulkanTextureDescriptorPoolManager() {
    }

    public static State createOrReuseAndWrite(
            VkDevice device,
            MemoryStack stack,
            List<VulkanGpuMesh> gpuMeshes,
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
            int targetSetCapacity,
            long shadowDepthImageView,
            long shadowSampler,
            long shadowMomentImageView,
            long shadowMomentSampler,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture
    ) throws EngineException {
        int requiredSetCount = gpuMeshes.size();
        boolean needsRebuild = textureDescriptorPool == VK_NULL_HANDLE || requiredSetCount > descriptorRingSetCapacity;
        if (!needsRebuild) {
            int resetResult = vkResetDescriptorPool(device, textureDescriptorPool, 0);
            if (resetResult == VK_SUCCESS) {
                descriptorRingPoolReuseCount++;
            } else {
                descriptorRingPoolResetFailureCount++;
                needsRebuild = true;
            }
        }

        if (needsRebuild) {
            if (textureDescriptorPool != VK_NULL_HANDLE) {
                vkDestroyDescriptorPool(device, textureDescriptorPool, null);
                textureDescriptorPool = VK_NULL_HANDLE;
                descriptorPoolRebuildCount++;
                if (requiredSetCount > descriptorRingSetCapacity) {
                    descriptorRingGrowthRebuildCount++;
                } else {
                    descriptorRingSteadyRebuildCount++;
                }
            }
            descriptorPoolBuildCount++;
            descriptorRingSetCapacity = targetSetCapacity;
            descriptorRingPeakSetCapacity = Math.max(descriptorRingPeakSetCapacity, descriptorRingSetCapacity);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0)
                    .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(descriptorRingSetCapacity * 9);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .maxSets(descriptorRingSetCapacity)
                    .pPoolSizes(poolSizes);
            var pPool = stack.longs(VK_NULL_HANDLE);
            int poolResult = vkCreateDescriptorPool(device, poolInfo, null, pPool);
            if (poolResult != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateDescriptorPool(texture) failed: " + poolResult,
                        false
                );
            }
            textureDescriptorPool = pPool.get(0);
        }
        int descriptorRingActiveSetCount = requiredSetCount;
        int descriptorRingWasteSetCount = Math.max(0, descriptorRingSetCapacity - requiredSetCount);
        descriptorRingPeakWasteSetCount = Math.max(descriptorRingPeakWasteSetCount, descriptorRingWasteSetCount);

        VulkanTextureDescriptorWriter.allocateAndWrite(
                device,
                stack,
                textureDescriptorPool,
                textureDescriptorSetLayout,
                gpuMeshes,
                shadowDepthImageView,
                shadowSampler,
                shadowMomentImageView,
                shadowMomentSampler,
                iblIrradianceTexture,
                iblRadianceTexture,
                iblBrdfLutTexture
        );

        return new State(
                textureDescriptorPool,
                descriptorPoolBuildCount,
                descriptorPoolRebuildCount,
                descriptorRingGrowthRebuildCount,
                descriptorRingSteadyRebuildCount,
                descriptorRingPoolReuseCount,
                descriptorRingPoolResetFailureCount,
                descriptorRingSetCapacity,
                descriptorRingPeakSetCapacity,
                descriptorRingActiveSetCount,
                descriptorRingWasteSetCount,
                descriptorRingPeakWasteSetCount
        );
    }

    public record State(
            long textureDescriptorPool,
            long descriptorPoolBuildCount,
            long descriptorPoolRebuildCount,
            long descriptorRingGrowthRebuildCount,
            long descriptorRingSteadyRebuildCount,
            long descriptorRingPoolReuseCount,
            long descriptorRingPoolResetFailureCount,
            int descriptorRingSetCapacity,
            int descriptorRingPeakSetCapacity,
            int descriptorRingActiveSetCount,
            int descriptorRingWasteSetCount,
            int descriptorRingPeakWasteSetCount
    ) {
    }
}
