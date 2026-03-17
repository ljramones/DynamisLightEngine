package org.dynamisengine.light.impl.vulkan.pipeline;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;

final class VulkanVertexInputLayouts {

    static final int STATIC_STRIDE_BYTES = 11 * Float.BYTES;
    static final int SKINNED_STRIDE_BYTES = (11 * Float.BYTES) + (4 * Float.BYTES) + (4 * Byte.BYTES);

    private VulkanVertexInputLayouts() {
    }

    static VkPipelineVertexInputStateCreateInfo staticVertexInputState(MemoryStack stack) {
        var bindingDesc = VkVertexInputBindingDescription.calloc(1, stack);
        bindingDesc.get(0)
                .binding(0)
                .stride(STATIC_STRIDE_BYTES)
                .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        var attrDesc = VkVertexInputAttributeDescription.calloc(4, stack);
        attrDesc.get(0)
                .location(0)
                .binding(0)
                .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);
        attrDesc.get(1)
                .location(1)
                .binding(0)
                .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                .offset(3 * Float.BYTES);
        attrDesc.get(2)
                .location(2)
                .binding(0)
                .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                .offset(6 * Float.BYTES);
        attrDesc.get(3)
                .location(3)
                .binding(0)
                .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                .offset(8 * Float.BYTES);
        return VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(bindingDesc)
                .pVertexAttributeDescriptions(attrDesc);
    }

    static VkPipelineVertexInputStateCreateInfo skinnedVertexInputState(MemoryStack stack) {
        var bindingDesc = VkVertexInputBindingDescription.calloc(1, stack);
        bindingDesc.get(0)
                .binding(0)
                .stride(SKINNED_STRIDE_BYTES)
                .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        var attrDesc = VkVertexInputAttributeDescription.calloc(6, stack);
        attrDesc.get(0)
                .location(0)
                .binding(0)
                .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);
        attrDesc.get(1)
                .location(1)
                .binding(0)
                .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                .offset(3 * Float.BYTES);
        attrDesc.get(2)
                .location(2)
                .binding(0)
                .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                .offset(6 * Float.BYTES);
        attrDesc.get(3)
                .location(3)
                .binding(0)
                .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                .offset(8 * Float.BYTES);
        attrDesc.get(4)
                .location(4)
                .binding(0)
                .format(VK10.VK_FORMAT_R32G32B32A32_SFLOAT)
                .offset(11 * Float.BYTES);
        attrDesc.get(5)
                .location(5)
                .binding(0)
                .format(VK10.VK_FORMAT_R8G8B8A8_UINT)
                .offset((11 * Float.BYTES) + (4 * Float.BYTES));
        return VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(bindingDesc)
                .pVertexAttributeDescriptions(attrDesc);
    }
}
