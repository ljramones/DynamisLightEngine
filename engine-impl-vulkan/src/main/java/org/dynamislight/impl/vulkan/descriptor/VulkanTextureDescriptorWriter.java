package org.dynamislight.impl.vulkan.descriptor;

import java.util.List;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanGpuTexture;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;

public final class VulkanTextureDescriptorWriter {
    private VulkanTextureDescriptorWriter() {
    }

    public static void allocateAndWrite(
            VkDevice device,
            MemoryStack stack,
            long textureDescriptorPool,
            long textureDescriptorSetLayout,
            List<VulkanGpuMesh> meshes,
            long shadowDepthImageView,
            long shadowSampler,
            long shadowMomentImageView,
            long shadowMomentSampler,
            VulkanGpuTexture iblIrradianceTexture,
            VulkanGpuTexture iblRadianceTexture,
            VulkanGpuTexture iblBrdfLutTexture,
            VulkanGpuTexture probeRadianceTexture
    ) throws EngineException {
        int requiredSetCount = meshes.size();
        if (requiredSetCount <= 0 || textureDescriptorSetLayout == VK_NULL_HANDLE || textureDescriptorPool == VK_NULL_HANDLE) {
            return;
        }

        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(textureDescriptorPool);
        var setLayouts = stack.mallocLong(requiredSetCount);
        for (int i = 0; i < requiredSetCount; i++) {
            setLayouts.put(i, textureDescriptorSetLayout);
        }
        allocInfo.pSetLayouts(setLayouts);

        var allocatedSets = stack.mallocLong(requiredSetCount);
        int setResult = vkAllocateDescriptorSets(device, allocInfo, allocatedSets);
        if (setResult != VK_SUCCESS) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkAllocateDescriptorSets(texture) failed: " + setResult,
                    false
            );
        }

        for (int i = 0; i < requiredSetCount; i++) {
            VulkanGpuMesh mesh = meshes.get(i);
            mesh.textureDescriptorSet = allocatedSets.get(i);

            VkDescriptorImageInfo.Buffer albedoInfo = imageInfo(stack, mesh.albedoTexture.view(), mesh.albedoTexture.sampler());
            VkDescriptorImageInfo.Buffer normalInfo = imageInfo(stack, mesh.normalTexture.view(), mesh.normalTexture.sampler());
            VkDescriptorImageInfo.Buffer metallicRoughnessInfo = imageInfo(stack, mesh.metallicRoughnessTexture.view(), mesh.metallicRoughnessTexture.sampler());
            VkDescriptorImageInfo.Buffer occlusionInfo = imageInfo(stack, mesh.occlusionTexture.view(), mesh.occlusionTexture.sampler());
            VkDescriptorImageInfo.Buffer shadowInfo = VkDescriptorImageInfo.calloc(1, stack);
            shadowInfo.get(0)
                    .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                    .imageView(shadowDepthImageView)
                    .sampler(shadowSampler);
            VkDescriptorImageInfo.Buffer iblIrradianceInfo = imageInfo(stack, iblIrradianceTexture.view(), iblIrradianceTexture.sampler());
            VkDescriptorImageInfo.Buffer iblRadianceInfo = imageInfo(stack, iblRadianceTexture.view(), iblRadianceTexture.sampler());
            VkDescriptorImageInfo.Buffer iblBrdfLutInfo = imageInfo(stack, iblBrdfLutTexture.view(), iblBrdfLutTexture.sampler());
            VkDescriptorImageInfo.Buffer shadowMomentInfo = imageInfo(stack, shadowMomentImageView, shadowMomentSampler);
            VkDescriptorImageInfo.Buffer probeRadianceInfo = imageInfo(stack, probeRadianceTexture.view(), probeRadianceTexture.sampler());

            // Binding 9 is a dedicated probe-radiance sampler lane (currently aliased to IBL radiance).
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(10, stack);
            writes.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(0)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(albedoInfo);
            writes.get(1)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(1)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(normalInfo);
            writes.get(2)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(2)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(metallicRoughnessInfo);
            writes.get(3)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(3)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(occlusionInfo);
            writes.get(4)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(4)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(shadowInfo);
            writes.get(5)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(5)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(iblIrradianceInfo);
            writes.get(6)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(6)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(iblRadianceInfo);
            writes.get(7)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(7)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(iblBrdfLutInfo);
            writes.get(8)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(8)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(shadowMomentInfo);
            writes.get(9)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(mesh.textureDescriptorSet)
                    .dstBinding(9)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(probeRadianceInfo);
            vkUpdateDescriptorSets(device, writes, null);
        }
    }

    private static VkDescriptorImageInfo.Buffer imageInfo(MemoryStack stack, long view, long sampler) {
        VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack);
        info.get(0)
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(view)
                .sampler(sampler);
        return info;
    }
}
