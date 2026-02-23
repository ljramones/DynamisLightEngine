package org.dynamislight.impl.vulkan.pipeline;

import java.nio.ByteBuffer;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.shader.VulkanBindlessStaticVertexShaderSource;
import org.dynamislight.impl.vulkan.shader.VulkanBindlessSkinnedVertexShaderSource;
import org.dynamislight.impl.vulkan.shader.VulkanMorphVertexShaderSource;
import org.dynamislight.impl.vulkan.shader.VulkanInstancedVertexShaderSource;
import org.dynamislight.impl.vulkan.shader.VulkanSkinnedMorphVertexShaderSource;
import org.dynamislight.impl.vulkan.shader.VulkanSkinnedVertexShaderSource;
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
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
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

public final class VulkanMainPipelineBuilder {
    static final int STATIC_STRIDE_BYTES = 11 * Float.BYTES;
    static final int SKINNED_STRIDE_BYTES = (11 * Float.BYTES) + (4 * Float.BYTES) + (4 * Byte.BYTES);

    private VulkanMainPipelineBuilder() {
    }

    public static Result create(
            VkDevice device,
            MemoryStack stack,
            int swapchainImageFormat,
            int depthFormat,
            int swapchainWidth,
            int swapchainHeight,
            int vertexStrideBytes,
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            long skinnedDescriptorSetLayout,
            long bindlessDescriptorSetLayout,
            String mainFragmentSource
    ) throws EngineException {
        if (vertexStrideBytes != STATIC_STRIDE_BYTES) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Unexpected static vertex stride: " + vertexStrideBytes + " (expected " + STATIC_STRIDE_BYTES + ")",
                    false
            );
        }
        long renderPass = createRenderPass(device, stack, swapchainImageFormat, depthFormat);
        long pipelineLayout = VK_NULL_HANDLE;
        long graphicsPipeline = VK_NULL_HANDLE;
        long bindlessStaticPipelineLayout = VK_NULL_HANDLE;
        long bindlessStaticGraphicsPipeline = VK_NULL_HANDLE;
        long bindlessSkinnedPipelineLayout = VK_NULL_HANDLE;
        long bindlessSkinnedGraphicsPipeline = VK_NULL_HANDLE;
        long morphPipelineLayout = VK_NULL_HANDLE;
        long morphGraphicsPipeline = VK_NULL_HANDLE;
        long skinnedPipelineLayout = VK_NULL_HANDLE;
        long skinnedGraphicsPipeline = VK_NULL_HANDLE;
        long skinnedMorphPipelineLayout = VK_NULL_HANDLE;
        long skinnedMorphGraphicsPipeline = VK_NULL_HANDLE;
        long instancedPipelineLayout = VK_NULL_HANDLE;
        long instancedGraphicsPipeline = VK_NULL_HANDLE;
        try {
            String vertexShaderSource = VulkanShaderSources.mainVertex();
            String fragmentShaderSource = (mainFragmentSource == null || mainFragmentSource.isBlank())
                    ? VulkanShaderSources.mainFragment()
                    : mainFragmentSource;

            ByteBuffer vertSpv = VulkanShaderCompiler.compileGlslToSpv(vertexShaderSource, shaderc_glsl_vertex_shader, "triangle.vert");
            ByteBuffer fragSpv = VulkanShaderCompiler.compileGlslToSpv(fragmentShaderSource, shaderc_fragment_shader, "triangle.frag");

            long vertModule = VK_NULL_HANDLE;
            long fragModule = VK_NULL_HANDLE;
            try {
                vertModule = VulkanShaderCompiler.createShaderModule(device, stack, vertSpv);
                fragModule = VulkanShaderCompiler.createShaderModule(device, stack, fragSpv);

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

                VkPipelineVertexInputStateCreateInfo vertexInput = staticVertexInputState(stack);
                VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                        .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                        .primitiveRestartEnable(false);

                VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                        .x(0f)
                        .y(0f)
                        .width((float) swapchainWidth)
                        .height((float) swapchainHeight)
                        .minDepth(0f)
                        .maxDepth(1f);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.get(0).offset(it -> it.set(0, 0));
                scissor.get(0).extent(VkExtent2D.calloc(stack).set(swapchainWidth, swapchainHeight));

                VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                        .viewportCount(1)
                        .pViewports(viewport)
                        .scissorCount(1)
                        .pScissors(scissor);

                VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                        .depthClampEnable(false)
                        .rasterizerDiscardEnable(false)
                        .polygonMode(VK_POLYGON_MODE_FILL)
                        .lineWidth(1.0f)
                        .cullMode(VK_CULL_MODE_NONE)
                        .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                        .depthBiasEnable(false);

                VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                        .sampleShadingEnable(false)
                        .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
                VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                        .depthTestEnable(true)
                        .depthWriteEnable(true)
                        .depthCompareOp(VK10.VK_COMPARE_OP_LESS)
                        .depthBoundsTestEnable(false)
                        .stencilTestEnable(false);

                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(2, stack);
                colorBlendAttachment.get(0)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                        .blendEnable(false)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                        .colorBlendOp(VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                        .alphaBlendOp(VK_BLEND_OP_ADD);
                colorBlendAttachment.get(1)
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

                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                        .pSetLayouts(stack.longs(descriptorSetLayout, textureDescriptorSetLayout))
                        .pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                                .offset(0)
                                .size(4 * Float.BYTES));
                var pPipelineLayout = stack.longs(VK_NULL_HANDLE);
                int layoutResult = vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
                if (layoutResult != VK_SUCCESS || pPipelineLayout.get(0) == VK_NULL_HANDLE) {
                    throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreatePipelineLayout failed: " + layoutResult, false);
                }
                pipelineLayout = pPipelineLayout.get(0);

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
                        .layout(pipelineLayout)
                        .renderPass(renderPass)
                        .subpass(0)
                        .basePipelineHandle(VK_NULL_HANDLE);

                var pPipeline = stack.longs(VK_NULL_HANDLE);
                int pipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
                if (pipelineResult != VK_SUCCESS || pPipeline.get(0) == VK_NULL_HANDLE) {
                    throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateGraphicsPipelines failed: " + pipelineResult, false);
                }
                graphicsPipeline = pPipeline.get(0);
                if (bindlessDescriptorSetLayout != VK_NULL_HANDLE) {
                    PipelineHandles bindlessStaticPipeline = buildBindlessStaticPipeline(
                            device,
                            stack,
                            fragModule,
                            inputAssembly,
                            viewportState,
                            rasterizer,
                            multisampling,
                            depthStencil,
                            colorBlending,
                            renderPass,
                            descriptorSetLayout,
                            textureDescriptorSetLayout,
                            skinnedDescriptorSetLayout,
                            bindlessDescriptorSetLayout
                    );
                    bindlessStaticPipelineLayout = bindlessStaticPipeline.pipelineLayout();
                    bindlessStaticGraphicsPipeline = bindlessStaticPipeline.graphicsPipeline();
                    PipelineHandles bindlessSkinnedPipeline = buildBindlessSkinnedPipeline(
                            device,
                            stack,
                            fragModule,
                            inputAssembly,
                            viewportState,
                            rasterizer,
                            multisampling,
                            depthStencil,
                            colorBlending,
                            renderPass,
                            descriptorSetLayout,
                            textureDescriptorSetLayout,
                            skinnedDescriptorSetLayout,
                            bindlessDescriptorSetLayout
                    );
                    bindlessSkinnedPipelineLayout = bindlessSkinnedPipeline.pipelineLayout();
                    bindlessSkinnedGraphicsPipeline = bindlessSkinnedPipeline.graphicsPipeline();
                }
                PipelineHandles morphPipeline = buildMorphPipeline(
                        device,
                        stack,
                        fragModule,
                        inputAssembly,
                        viewportState,
                        rasterizer,
                        multisampling,
                        depthStencil,
                        colorBlending,
                        renderPass,
                        descriptorSetLayout,
                        textureDescriptorSetLayout,
                        skinnedDescriptorSetLayout
                );
                morphPipelineLayout = morphPipeline.pipelineLayout();
                morphGraphicsPipeline = morphPipeline.graphicsPipeline();
                PipelineHandles skinnedPipeline = buildSkinnedPipeline(
                        device,
                        stack,
                        fragModule,
                        inputAssembly,
                        viewportState,
                        rasterizer,
                        multisampling,
                        depthStencil,
                        colorBlending,
                        renderPass,
                        descriptorSetLayout,
                        textureDescriptorSetLayout,
                        skinnedDescriptorSetLayout
                );
                skinnedPipelineLayout = skinnedPipeline.pipelineLayout();
                skinnedGraphicsPipeline = skinnedPipeline.graphicsPipeline();
                PipelineHandles skinnedMorphPipeline = buildSkinnedMorphPipeline(
                        device,
                        stack,
                        fragModule,
                        inputAssembly,
                        viewportState,
                        rasterizer,
                        multisampling,
                        depthStencil,
                        colorBlending,
                        renderPass,
                        descriptorSetLayout,
                        textureDescriptorSetLayout,
                        skinnedDescriptorSetLayout
                );
                skinnedMorphPipelineLayout = skinnedMorphPipeline.pipelineLayout();
                skinnedMorphGraphicsPipeline = skinnedMorphPipeline.graphicsPipeline();
                PipelineHandles instancedPipeline = buildInstancedPipeline(
                        device,
                        stack,
                        fragModule,
                        inputAssembly,
                        viewportState,
                        rasterizer,
                        multisampling,
                        depthStencil,
                        colorBlending,
                        renderPass,
                        descriptorSetLayout,
                        textureDescriptorSetLayout,
                        skinnedDescriptorSetLayout
                );
                instancedPipelineLayout = instancedPipeline.pipelineLayout();
                instancedGraphicsPipeline = instancedPipeline.graphicsPipeline();
            } finally {
                if (vertModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(device, vertModule, null);
                }
                if (fragModule != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(device, fragModule, null);
                }
            }
        } catch (EngineException ex) {
            if (bindlessStaticGraphicsPipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, bindlessStaticGraphicsPipeline, null);
            }
            if (bindlessStaticPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, bindlessStaticPipelineLayout, null);
            }
            if (bindlessSkinnedGraphicsPipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, bindlessSkinnedGraphicsPipeline, null);
            }
            if (bindlessSkinnedPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, bindlessSkinnedPipelineLayout, null);
            }
            if (morphGraphicsPipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, morphGraphicsPipeline, null);
            }
            if (morphPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, morphPipelineLayout, null);
            }
            if (skinnedGraphicsPipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, skinnedGraphicsPipeline, null);
            }
            if (skinnedPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, skinnedPipelineLayout, null);
            }
            if (skinnedMorphGraphicsPipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, skinnedMorphGraphicsPipeline, null);
            }
            if (skinnedMorphPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, skinnedMorphPipelineLayout, null);
            }
            if (instancedGraphicsPipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, instancedGraphicsPipeline, null);
            }
            if (instancedPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, instancedPipelineLayout, null);
            }
            if (pipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
            }
            if (renderPass != VK_NULL_HANDLE) {
                VK10.vkDestroyRenderPass(device, renderPass, null);
            }
            throw ex;
        }
        return new Result(
                renderPass,
                pipelineLayout,
                graphicsPipeline,
                bindlessStaticPipelineLayout,
                bindlessStaticGraphicsPipeline,
                bindlessSkinnedPipelineLayout,
                bindlessSkinnedGraphicsPipeline,
                morphPipelineLayout,
                morphGraphicsPipeline,
                skinnedPipelineLayout,
                skinnedGraphicsPipeline,
                skinnedMorphPipelineLayout,
                skinnedMorphGraphicsPipeline,
                instancedPipelineLayout,
                instancedGraphicsPipeline
        );
    }

    private static PipelineHandles buildInstancedPipeline(
            VkDevice device,
            MemoryStack stack,
            long fragModule,
            VkPipelineInputAssemblyStateCreateInfo inputAssembly,
            VkPipelineViewportStateCreateInfo viewportState,
            VkPipelineRasterizationStateCreateInfo rasterizer,
            VkPipelineMultisampleStateCreateInfo multisampling,
            VkPipelineDepthStencilStateCreateInfo depthStencil,
            VkPipelineColorBlendStateCreateInfo colorBlending,
            long renderPass,
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            long skinnedDescriptorSetLayout
    ) throws EngineException {
        String instancedVertexShaderSource = VulkanInstancedVertexShaderSource.mainVertex();
        ByteBuffer instancedVertSpv = VulkanShaderCompiler.compileGlslToSpv(
                instancedVertexShaderSource,
                shaderc_glsl_vertex_shader,
                "main_instanced.vert"
        );
        long instancedVertModule = VulkanShaderCompiler.createShaderModule(device, stack, instancedVertSpv);
        VkPipelineLayoutCreateInfo instancedLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(descriptorSetLayout, textureDescriptorSetLayout, skinnedDescriptorSetLayout))
                .pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                        .offset(0)
                        .size(4 * Float.BYTES));
        var pInstancedLayout = stack.longs(VK_NULL_HANDLE);
        int instancedLayoutResult = vkCreatePipelineLayout(device, instancedLayoutInfo, null, pInstancedLayout);
        if (instancedLayoutResult != VK_SUCCESS || pInstancedLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreatePipelineLayout(instanced) failed: " + instancedLayoutResult,
                    false
            );
        }
        long instancedPipelineLayout = pInstancedLayout.get(0);
        try {
            VkPipelineShaderStageCreateInfo.Buffer instancedShaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            instancedShaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(instancedVertModule)
                    .pName(stack.UTF8("main"));
            instancedShaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));
            VkGraphicsPipelineCreateInfo.Buffer instancedPipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(instancedShaderStages)
                    .pVertexInputState(staticVertexInputState(stack))
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(instancedPipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE);
            var pInstancedPipeline = stack.longs(VK_NULL_HANDLE);
            int instancedPipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, instancedPipelineInfo, null, pInstancedPipeline);
            if (instancedPipelineResult != VK_SUCCESS || pInstancedPipeline.get(0) == VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, instancedPipelineLayout, null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateGraphicsPipelines(instanced) failed: " + instancedPipelineResult,
                        false
                );
            }
            return new PipelineHandles(instancedPipelineLayout, pInstancedPipeline.get(0));
        } finally {
            vkDestroyShaderModule(device, instancedVertModule, null);
        }
    }

    private static PipelineHandles buildBindlessStaticPipeline(
            VkDevice device,
            MemoryStack stack,
            long fragModule,
            VkPipelineInputAssemblyStateCreateInfo inputAssembly,
            VkPipelineViewportStateCreateInfo viewportState,
            VkPipelineRasterizationStateCreateInfo rasterizer,
            VkPipelineMultisampleStateCreateInfo multisampling,
            VkPipelineDepthStencilStateCreateInfo depthStencil,
            VkPipelineColorBlendStateCreateInfo colorBlending,
            long renderPass,
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            long skinnedDescriptorSetLayout,
            long bindlessDescriptorSetLayout
    ) throws EngineException {
        String bindlessVertexShaderSource = VulkanBindlessStaticVertexShaderSource.mainVertex();
        ByteBuffer bindlessVertSpv = VulkanShaderCompiler.compileGlslToSpv(
                bindlessVertexShaderSource,
                shaderc_glsl_vertex_shader,
                "main_static_bindless.vert"
        );
        long bindlessVertModule = VulkanShaderCompiler.createShaderModule(device, stack, bindlessVertSpv);
        VkPipelineLayoutCreateInfo bindlessLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(
                        descriptorSetLayout,
                        textureDescriptorSetLayout,
                        skinnedDescriptorSetLayout,
                        bindlessDescriptorSetLayout
                ))
                .pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                        .offset(0)
                        .size(4 * Float.BYTES));
        var pBindlessLayout = stack.longs(VK_NULL_HANDLE);
        int bindlessLayoutResult = vkCreatePipelineLayout(device, bindlessLayoutInfo, null, pBindlessLayout);
        if (bindlessLayoutResult != VK_SUCCESS || pBindlessLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreatePipelineLayout(bindlessStatic) failed: " + bindlessLayoutResult,
                    false
            );
        }
        long bindlessPipelineLayout = pBindlessLayout.get(0);
        try {
            VkPipelineShaderStageCreateInfo.Buffer bindlessStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            bindlessStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(bindlessVertModule)
                    .pName(stack.UTF8("main"));
            bindlessStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));
            VkGraphicsPipelineCreateInfo.Buffer bindlessPipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(bindlessStages)
                    .pVertexInputState(staticVertexInputState(stack))
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(bindlessPipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE);
            var pBindlessPipeline = stack.longs(VK_NULL_HANDLE);
            int bindlessPipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, bindlessPipelineInfo, null, pBindlessPipeline);
            if (bindlessPipelineResult != VK_SUCCESS || pBindlessPipeline.get(0) == VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, bindlessPipelineLayout, null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateGraphicsPipelines(bindlessStatic) failed: " + bindlessPipelineResult,
                        false
                );
            }
            return new PipelineHandles(bindlessPipelineLayout, pBindlessPipeline.get(0));
        } finally {
            vkDestroyShaderModule(device, bindlessVertModule, null);
        }
    }

    private static PipelineHandles buildBindlessSkinnedPipeline(
            VkDevice device,
            MemoryStack stack,
            long fragModule,
            VkPipelineInputAssemblyStateCreateInfo inputAssembly,
            VkPipelineViewportStateCreateInfo viewportState,
            VkPipelineRasterizationStateCreateInfo rasterizer,
            VkPipelineMultisampleStateCreateInfo multisampling,
            VkPipelineDepthStencilStateCreateInfo depthStencil,
            VkPipelineColorBlendStateCreateInfo colorBlending,
            long renderPass,
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            long skinnedDescriptorSetLayout,
            long bindlessDescriptorSetLayout
    ) throws EngineException {
        String bindlessSkinnedVertexShaderSource = VulkanBindlessSkinnedVertexShaderSource.mainVertex();
        ByteBuffer bindlessSkinnedVertSpv = VulkanShaderCompiler.compileGlslToSpv(
                bindlessSkinnedVertexShaderSource,
                shaderc_glsl_vertex_shader,
                "main_skinned_bindless.vert"
        );
        long bindlessSkinnedVertModule = VulkanShaderCompiler.createShaderModule(device, stack, bindlessSkinnedVertSpv);
        VkPipelineLayoutCreateInfo bindlessSkinnedLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(
                        descriptorSetLayout,
                        textureDescriptorSetLayout,
                        skinnedDescriptorSetLayout,
                        bindlessDescriptorSetLayout
                ))
                .pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                        .offset(0)
                        .size(4 * Float.BYTES));
        var pLayout = stack.longs(VK_NULL_HANDLE);
        int layoutResult = vkCreatePipelineLayout(device, bindlessSkinnedLayoutInfo, null, pLayout);
        if (layoutResult != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreatePipelineLayout(bindlessSkinned) failed: " + layoutResult,
                    false
            );
        }
        long pipelineLayout = pLayout.get(0);
        try {
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(bindlessSkinnedVertModule)
                    .pName(stack.UTF8("main"));
            stages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));
            VkGraphicsPipelineCreateInfo.Buffer info = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(stages)
                    .pVertexInputState(skinnedVertexInputState(stack))
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
            int result = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, info, null, pPipeline);
            if (result != VK_SUCCESS || pPipeline.get(0) == VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateGraphicsPipelines(bindlessSkinned) failed: " + result,
                        false
                );
            }
            return new PipelineHandles(pipelineLayout, pPipeline.get(0));
        } finally {
            vkDestroyShaderModule(device, bindlessSkinnedVertModule, null);
        }
    }

    private static PipelineHandles buildSkinnedMorphPipeline(
            VkDevice device,
            MemoryStack stack,
            long fragModule,
            VkPipelineInputAssemblyStateCreateInfo inputAssembly,
            VkPipelineViewportStateCreateInfo viewportState,
            VkPipelineRasterizationStateCreateInfo rasterizer,
            VkPipelineMultisampleStateCreateInfo multisampling,
            VkPipelineDepthStencilStateCreateInfo depthStencil,
            VkPipelineColorBlendStateCreateInfo colorBlending,
            long renderPass,
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            long skinnedDescriptorSetLayout
    ) throws EngineException {
        String skinnedMorphVertexShaderSource = VulkanSkinnedMorphVertexShaderSource.mainVertex();
        ByteBuffer skinnedMorphVertSpv = VulkanShaderCompiler.compileGlslToSpv(
                skinnedMorphVertexShaderSource,
                shaderc_glsl_vertex_shader,
                "main_skinned_morph.vert"
        );
        long skinnedMorphVertModule = VulkanShaderCompiler.createShaderModule(device, stack, skinnedMorphVertSpv);
        VkPipelineLayoutCreateInfo skinnedMorphLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(
                        descriptorSetLayout,
                        textureDescriptorSetLayout,
                        skinnedDescriptorSetLayout,
                        skinnedDescriptorSetLayout
                ))
                .pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                        .offset(0)
                        .size(4 * Float.BYTES));
        long skinnedMorphPipelineLayout = VK_NULL_HANDLE;
        var pSkinnedMorphLayout = stack.longs(VK_NULL_HANDLE);
        int skinnedMorphLayoutResult = vkCreatePipelineLayout(device, skinnedMorphLayoutInfo, null, pSkinnedMorphLayout);
        if (skinnedMorphLayoutResult != VK_SUCCESS || pSkinnedMorphLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreatePipelineLayout(skinnedMorph) failed: " + skinnedMorphLayoutResult,
                    false
            );
        }
        skinnedMorphPipelineLayout = pSkinnedMorphLayout.get(0);
        try {
            VkPipelineShaderStageCreateInfo.Buffer skinnedMorphShaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            skinnedMorphShaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(skinnedMorphVertModule)
                    .pName(stack.UTF8("main"));
            skinnedMorphShaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));
            VkGraphicsPipelineCreateInfo.Buffer skinnedMorphPipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(skinnedMorphShaderStages)
                    .pVertexInputState(skinnedVertexInputState(stack))
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(skinnedMorphPipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE);
            var pSkinnedMorphPipeline = stack.longs(VK_NULL_HANDLE);
            int skinnedMorphPipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, skinnedMorphPipelineInfo, null, pSkinnedMorphPipeline);
            if (skinnedMorphPipelineResult != VK_SUCCESS || pSkinnedMorphPipeline.get(0) == VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, skinnedMorphPipelineLayout, null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateGraphicsPipelines(skinnedMorph) failed: " + skinnedMorphPipelineResult,
                        false
                );
            }
            return new PipelineHandles(skinnedMorphPipelineLayout, pSkinnedMorphPipeline.get(0));
        } finally {
            vkDestroyShaderModule(device, skinnedMorphVertModule, null);
        }
    }

    private static PipelineHandles buildSkinnedPipeline(
            VkDevice device,
            MemoryStack stack,
            long fragModule,
            VkPipelineInputAssemblyStateCreateInfo inputAssembly,
            VkPipelineViewportStateCreateInfo viewportState,
            VkPipelineRasterizationStateCreateInfo rasterizer,
            VkPipelineMultisampleStateCreateInfo multisampling,
            VkPipelineDepthStencilStateCreateInfo depthStencil,
            VkPipelineColorBlendStateCreateInfo colorBlending,
            long renderPass,
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            long skinnedDescriptorSetLayout
    ) throws EngineException {
        String skinnedVertexShaderSource = VulkanSkinnedVertexShaderSource.mainVertex();
        ByteBuffer skinnedVertSpv = VulkanShaderCompiler.compileGlslToSpv(
                skinnedVertexShaderSource,
                shaderc_glsl_vertex_shader,
                "main_skinned.vert"
        );
        long skinnedVertModule = VulkanShaderCompiler.createShaderModule(device, stack, skinnedVertSpv);
        VkPipelineLayoutCreateInfo skinnedLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(descriptorSetLayout, textureDescriptorSetLayout, skinnedDescriptorSetLayout))
                .pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                        .offset(0)
                        .size(4 * Float.BYTES));
        long skinnedPipelineLayout = VK_NULL_HANDLE;
        long skinnedGraphicsPipeline = VK_NULL_HANDLE;
        var pSkinnedLayout = stack.longs(VK_NULL_HANDLE);
        int skinnedLayoutResult = vkCreatePipelineLayout(device, skinnedLayoutInfo, null, pSkinnedLayout);
        if (skinnedLayoutResult != VK_SUCCESS || pSkinnedLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreatePipelineLayout(skinned) failed: " + skinnedLayoutResult,
                    false
            );
        }
        skinnedPipelineLayout = pSkinnedLayout.get(0);
        try {
            VkPipelineShaderStageCreateInfo.Buffer skinnedShaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            skinnedShaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(skinnedVertModule)
                    .pName(stack.UTF8("main"));
            skinnedShaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));

            VkGraphicsPipelineCreateInfo.Buffer skinnedPipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(skinnedShaderStages)
                    .pVertexInputState(skinnedVertexInputState(stack))
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(skinnedPipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE);
            var pSkinnedPipeline = stack.longs(VK_NULL_HANDLE);
            int skinnedPipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, skinnedPipelineInfo, null, pSkinnedPipeline);
            if (skinnedPipelineResult != VK_SUCCESS || pSkinnedPipeline.get(0) == VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, skinnedPipelineLayout, null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateGraphicsPipelines(skinned) failed: " + skinnedPipelineResult,
                        false
                );
            }
            skinnedGraphicsPipeline = pSkinnedPipeline.get(0);
            return new PipelineHandles(skinnedPipelineLayout, skinnedGraphicsPipeline);
        } finally {
            vkDestroyShaderModule(device, skinnedVertModule, null);
        }
    }

    private static PipelineHandles buildMorphPipeline(
            VkDevice device,
            MemoryStack stack,
            long fragModule,
            VkPipelineInputAssemblyStateCreateInfo inputAssembly,
            VkPipelineViewportStateCreateInfo viewportState,
            VkPipelineRasterizationStateCreateInfo rasterizer,
            VkPipelineMultisampleStateCreateInfo multisampling,
            VkPipelineDepthStencilStateCreateInfo depthStencil,
            VkPipelineColorBlendStateCreateInfo colorBlending,
            long renderPass,
            long descriptorSetLayout,
            long textureDescriptorSetLayout,
            long skinnedDescriptorSetLayout
    ) throws EngineException {
        String morphVertexShaderSource = VulkanMorphVertexShaderSource.mainVertex();
        ByteBuffer morphVertSpv = VulkanShaderCompiler.compileGlslToSpv(
                morphVertexShaderSource,
                shaderc_glsl_vertex_shader,
                "main_morph.vert"
        );
        long morphVertModule = VulkanShaderCompiler.createShaderModule(device, stack, morphVertSpv);
        VkPipelineLayoutCreateInfo morphLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(descriptorSetLayout, textureDescriptorSetLayout, skinnedDescriptorSetLayout))
                .pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                        .offset(0)
                        .size(4 * Float.BYTES));
        long morphPipelineLayout = VK_NULL_HANDLE;
        var pMorphLayout = stack.longs(VK_NULL_HANDLE);
        int morphLayoutResult = vkCreatePipelineLayout(device, morphLayoutInfo, null, pMorphLayout);
        if (morphLayoutResult != VK_SUCCESS || pMorphLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreatePipelineLayout(morph) failed: " + morphLayoutResult,
                    false
            );
        }
        morphPipelineLayout = pMorphLayout.get(0);
        try {
            VkPipelineShaderStageCreateInfo.Buffer morphShaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            morphShaderStages.get(0)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(morphVertModule)
                    .pName(stack.UTF8("main"));
            morphShaderStages.get(1)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));

            VkGraphicsPipelineCreateInfo.Buffer morphPipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(morphShaderStages)
                    .pVertexInputState(staticVertexInputState(stack))
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .layout(morphPipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE);
            var pMorphPipeline = stack.longs(VK_NULL_HANDLE);
            int morphPipelineResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, morphPipelineInfo, null, pMorphPipeline);
            if (morphPipelineResult != VK_SUCCESS || pMorphPipeline.get(0) == VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, morphPipelineLayout, null);
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateGraphicsPipelines(morph) failed: " + morphPipelineResult,
                        false
                );
            }
            return new PipelineHandles(morphPipelineLayout, pMorphPipeline.get(0));
        } finally {
            vkDestroyShaderModule(device, morphVertModule, null);
        }
    }

    private static VkPipelineVertexInputStateCreateInfo staticVertexInputState(MemoryStack stack) {
        var bindingDesc = org.lwjgl.vulkan.VkVertexInputBindingDescription.calloc(1, stack);
        bindingDesc.get(0)
                .binding(0)
                .stride(STATIC_STRIDE_BYTES)
                .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        var attrDesc = org.lwjgl.vulkan.VkVertexInputAttributeDescription.calloc(4, stack);
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

    private static VkPipelineVertexInputStateCreateInfo skinnedVertexInputState(MemoryStack stack) {
        var bindingDesc = org.lwjgl.vulkan.VkVertexInputBindingDescription.calloc(1, stack);
        bindingDesc.get(0)
                .binding(0)
                .stride(SKINNED_STRIDE_BYTES)
                .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        var attrDesc = org.lwjgl.vulkan.VkVertexInputAttributeDescription.calloc(6, stack);
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

    private static long createRenderPass(VkDevice device, MemoryStack stack, int swapchainImageFormat, int depthFormat) throws EngineException {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(3, stack);
        attachments.get(0)
                .format(swapchainImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        attachments.get(1)
                .format(swapchainImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        attachments.get(2)
                .format(depthFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

        VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(2, stack);
        colorRef.get(0)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        colorRef.get(1)
                .attachment(1)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VkAttachmentReference.Buffer depthRef = VkAttachmentReference.calloc(1, stack)
                .attachment(2)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

        VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(2)
                .pColorAttachments(colorRef)
                .pDepthStencilAttachment(depthRef.get(0));

        VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

        VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subpass)
                .pDependencies(dependency);

        var pRenderPass = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
        if (result != VK_SUCCESS || pRenderPass.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "vkCreateRenderPass failed: " + result, false);
        }
        return pRenderPass.get(0);
    }

    private record PipelineHandles(long pipelineLayout, long graphicsPipeline) {
    }

    public record Result(
            long renderPass,
            long pipelineLayout,
            long graphicsPipeline,
            long bindlessStaticPipelineLayout,
            long bindlessStaticGraphicsPipeline,
            long bindlessSkinnedPipelineLayout,
            long bindlessSkinnedGraphicsPipeline,
            long morphPipelineLayout,
            long morphGraphicsPipeline,
            long skinnedPipelineLayout,
            long skinnedGraphicsPipeline,
            long skinnedMorphPipelineLayout,
            long skinnedMorphGraphicsPipeline,
            long instancedPipelineLayout,
            long instancedGraphicsPipeline
    ) {
    }
}
