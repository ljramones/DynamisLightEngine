package org.dynamisengine.light.impl.vulkan.pipeline;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import org.dynamisengine.light.api.error.EngineErrorCode;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.impl.vulkan.shader.VulkanShaderCompiler;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

final class VulkanPipelineAssembler {

    private VulkanPipelineAssembler() {
    }

    record PipelineHandles(long pipelineLayout, long graphicsPipeline) {
    }

    /**
     * Assembles a complete graphics pipeline from variant-specific parameters.
     *
     * @param device              the Vulkan logical device
     * @param stack               the memory stack for allocations
     * @param fragModule          the pre-compiled fragment shader module (shared across all variants)
     * @param vertexShaderSource  GLSL source for the vertex shader
     * @param shaderFileName      debug name for the vertex shader (e.g. "main_skinned.vert")
     * @param vertexInputState    the vertex input state (static or skinned layout)
     * @param inputAssembly       shared input assembly state
     * @param viewportState       shared viewport state
     * @param rasterizer          shared rasterization state
     * @param multisampling       shared multisample state
     * @param depthStencil        shared depth/stencil state
     * @param colorBlending       shared color blend state
     * @param renderPass          the render pass handle
     * @param descriptorSetLayouts the descriptor set layouts for the pipeline layout
     * @param label               human-readable label for error messages
     * @return the pipeline and layout handles
     */
    static PipelineHandles assemble(
            VkDevice device,
            MemoryStack stack,
            long fragModule,
            String vertexShaderSource,
            String shaderFileName,
            VkPipelineVertexInputStateCreateInfo vertexInputState,
            VkPipelineInputAssemblyStateCreateInfo inputAssembly,
            VkPipelineViewportStateCreateInfo viewportState,
            VkPipelineRasterizationStateCreateInfo rasterizer,
            VkPipelineMultisampleStateCreateInfo multisampling,
            VkPipelineDepthStencilStateCreateInfo depthStencil,
            VkPipelineColorBlendStateCreateInfo colorBlending,
            long renderPass,
            LongBuffer descriptorSetLayouts,
            String label
    ) throws EngineException {
        ByteBuffer vertSpv = VulkanShaderCompiler.compileGlslToSpv(
                vertexShaderSource, shaderc_glsl_vertex_shader, shaderFileName);
        long vertModule = VulkanShaderCompiler.createShaderModule(device, stack, vertSpv);

        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(descriptorSetLayouts)
                .pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                        .offset(0)
                        .size(4 * Float.BYTES));

        var pLayout = stack.longs(VK_NULL_HANDLE);
        int layoutResult = vkCreatePipelineLayout(device, layoutInfo, null, pLayout);
        if (layoutResult != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreatePipelineLayout(" + label + ") failed: " + layoutResult,
                    false
            );
        }
        long pipelineLayout = pLayout.get(0);
        try {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertModule)
                    .pName(stack.UTF8("main"));
            shaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInputState)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE);

            var pPipeline = stack.longs(VK_NULL_HANDLE);
            int pipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            if (pipelineResult != VK_SUCCESS || pPipeline.get(0) == VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateGraphicsPipelines(" + label + ") failed: " + pipelineResult,
                        false
                );
            }
            return new PipelineHandles(pipelineLayout, pPipeline.get(0));
        } finally {
            vkDestroyShaderModule(device, vertModule, null);
        }
    }
}
