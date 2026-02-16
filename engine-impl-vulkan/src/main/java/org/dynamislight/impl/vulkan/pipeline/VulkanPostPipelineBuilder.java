package org.dynamislight.impl.vulkan.pipeline;

import java.nio.ByteBuffer;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.shader.VulkanShaderCompiler;
import org.dynamislight.impl.vulkan.shader.VulkanShaderSources;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
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
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkViewport;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateRenderPass;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

public final class VulkanPostPipelineBuilder {
    private VulkanPostPipelineBuilder() {
    }

    public static Result create(
            VkDevice device,
            MemoryStack stack,
            int swapchainImageFormat,
            int swapchainWidth,
            int swapchainHeight,
            long postDescriptorSetLayout
    ) throws EngineException {
        long postRenderPass = createRenderPass(device, stack, swapchainImageFormat);
        long postPipelineLayout = VK_NULL_HANDLE;
        long postGraphicsPipeline = VK_NULL_HANDLE;
        try {
            String vertexShaderSource = VulkanShaderSources.postVertex();
            String fragmentShaderSource = VulkanShaderSources.postFragment();
            ByteBuffer vertSpv = VulkanShaderCompiler.compileGlslToSpv(vertexShaderSource, shaderc_glsl_vertex_shader, "post.vert");
            ByteBuffer fragSpv = VulkanShaderCompiler.compileGlslToSpv(fragmentShaderSource, shaderc_fragment_shader, "post.frag");
            long vertModule = VK_NULL_HANDLE;
            long fragModule = VK_NULL_HANDLE;
            try {
                vertModule = VulkanShaderCompiler.createShaderModule(device, stack, vertSpv);
                fragModule = VulkanShaderCompiler.createShaderModule(device, stack, fragSpv);
                VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                shaderStages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertModule).pName(stack.UTF8("main"));
                shaderStages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragModule).pName(stack.UTF8("main"));

                VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
                VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                        .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                        .primitiveRestartEnable(false);
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                        .x(0f).y(0f).width((float) swapchainWidth).height((float) swapchainHeight).minDepth(0f).maxDepth(1f);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.get(0).offset(it -> it.set(0, 0));
                scissor.get(0).extent(VkExtent2D.calloc(stack).set(swapchainWidth, swapchainHeight));
                VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                        .viewportCount(1).pViewports(viewport).scissorCount(1).pScissors(scissor);
                VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                        .depthClampEnable(false).rasterizerDiscardEnable(false).polygonMode(VK_POLYGON_MODE_FILL)
                        .lineWidth(1.0f).cullMode(VK_CULL_MODE_NONE).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).depthBiasEnable(false);
                VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                        .sampleShadingEnable(false).rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                        .depthTestEnable(false).depthWriteEnable(false).depthBoundsTestEnable(false).stencilTestEnable(false);
                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
                colorBlendAttachment.get(0)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                        .blendEnable(false)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                        .colorBlendOp(VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                        .alphaBlendOp(VK_BLEND_OP_ADD);
                VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                        .logicOpEnable(false)
                        .pAttachments(colorBlendAttachment);

                VkPushConstantRange.Buffer pushRanges = VkPushConstantRange.calloc(1, stack);
                pushRanges.get(0)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                        .offset(0)
                        .size(16 * Float.BYTES);
                VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                        .pSetLayouts(stack.longs(postDescriptorSetLayout))
                        .pPushConstantRanges(pushRanges);
                var pLayout = stack.longs(VK_NULL_HANDLE);
                int layoutResult = vkCreatePipelineLayout(device, layoutInfo, null, pLayout);
                if (layoutResult != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
                    throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreatePipelineLayout(post) failed: " + layoutResult, false);
                }
                postPipelineLayout = pLayout.get(0);

                VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                        .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                        .pStages(shaderStages)
                        .pVertexInputState(vertexInput)
                        .pInputAssemblyState(inputAssembly)
                        .pViewportState(viewportState)
                        .pRasterizationState(rasterizer)
                        .pMultisampleState(multisampling)
                        .pDepthStencilState(depthStencil)
                        .pColorBlendState(colorBlending)
                        .layout(postPipelineLayout)
                        .renderPass(postRenderPass)
                        .subpass(0)
                        .basePipelineHandle(VK_NULL_HANDLE);
                var pPipeline = stack.longs(VK_NULL_HANDLE);
                int pipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
                if (pipelineResult != VK_SUCCESS || pPipeline.get(0) == VK_NULL_HANDLE) {
                    throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateGraphicsPipelines(post) failed: " + pipelineResult, false);
                }
                postGraphicsPipeline = pPipeline.get(0);
            } finally {
                if (vertModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(device, vertModule, null);
                }
                if (fragModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(device, fragModule, null);
                }
            }
        } catch (EngineException ex) {
            if (postPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, postPipelineLayout, null);
            }
            if (postRenderPass != VK_NULL_HANDLE) {
                VK10.vkDestroyRenderPass(device, postRenderPass, null);
            }
            throw ex;
        }
        return new Result(postRenderPass, postPipelineLayout, postGraphicsPipeline);
    }

    private static long createRenderPass(VkDevice device, MemoryStack stack, int swapchainImageFormat) throws EngineException {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack);
        attachments.get(0)
                .format(swapchainImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorRef);
        VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subpass)
                .pDependencies(dependency);
        var pRenderPass = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
        if (result != VK_SUCCESS || pRenderPass.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateRenderPass(post) failed: " + result, false);
        }
        return pRenderPass.get(0);
    }

    public record Result(long renderPass, long pipelineLayout, long graphicsPipeline) {
    }
}
