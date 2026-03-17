package org.dynamisengine.light.impl.vulkan.pipeline;

import java.nio.ByteBuffer;
import org.dynamisengine.light.api.error.EngineErrorCode;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.impl.vulkan.shader.VulkanBindlessStaticVertexShaderSource;
import org.dynamisengine.light.impl.vulkan.shader.VulkanBindlessSkinnedVertexShaderSource;
import org.dynamisengine.light.impl.vulkan.shader.VulkanBindlessMorphVertexShaderSource;
import org.dynamisengine.light.impl.vulkan.shader.VulkanBindlessSkinnedMorphVertexShaderSource;
import org.dynamisengine.light.impl.vulkan.shader.VulkanBindlessInstancedVertexShaderSource;
import org.dynamisengine.light.impl.vulkan.shader.VulkanMorphVertexShaderSource;
import org.dynamisengine.light.impl.vulkan.shader.VulkanInstancedVertexShaderSource;
import org.dynamisengine.light.impl.vulkan.shader.VulkanSkinnedMorphVertexShaderSource;
import org.dynamisengine.light.impl.vulkan.shader.VulkanSkinnedVertexShaderSource;
import org.dynamisengine.light.impl.vulkan.shader.VulkanShaderCompiler;
import org.dynamisengine.light.impl.vulkan.shader.VulkanShaderSources;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
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
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
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
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

public final class VulkanMainPipelineBuilder {

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
        if (vertexStrideBytes != VulkanVertexInputLayouts.STATIC_STRIDE_BYTES) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Unexpected static vertex stride: " + vertexStrideBytes + " (expected " + VulkanVertexInputLayouts.STATIC_STRIDE_BYTES + ")",
                    false
            );
        }
        long renderPass = VulkanRenderPassFactory.createRenderPass(device, stack, swapchainImageFormat, depthFormat);
        long pipelineLayout = VK_NULL_HANDLE;
        long graphicsPipeline = VK_NULL_HANDLE;
        long bindlessStaticPipelineLayout = VK_NULL_HANDLE;
        long bindlessStaticGraphicsPipeline = VK_NULL_HANDLE;
        long bindlessSkinnedPipelineLayout = VK_NULL_HANDLE;
        long bindlessSkinnedGraphicsPipeline = VK_NULL_HANDLE;
        long bindlessMorphPipelineLayout = VK_NULL_HANDLE;
        long bindlessMorphGraphicsPipeline = VK_NULL_HANDLE;
        long bindlessSkinnedMorphPipelineLayout = VK_NULL_HANDLE;
        long bindlessSkinnedMorphGraphicsPipeline = VK_NULL_HANDLE;
        long bindlessInstancedPipelineLayout = VK_NULL_HANDLE;
        long bindlessInstancedGraphicsPipeline = VK_NULL_HANDLE;
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

                // Build the base static pipeline (uses its own vertex shader, not the assembler)
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
                        .pVertexInputState(VulkanVertexInputLayouts.staticVertexInputState(stack))
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

                // Bindless variants (require bindlessDescriptorSetLayout)
                if (bindlessDescriptorSetLayout != VK_NULL_HANDLE) {
                    var bindless4Layouts = stack.longs(
                            descriptorSetLayout, textureDescriptorSetLayout,
                            skinnedDescriptorSetLayout, bindlessDescriptorSetLayout);

                    var h = VulkanPipelineAssembler.assemble(device, stack, fragModule,
                            VulkanBindlessStaticVertexShaderSource.mainVertex(), "main_static_bindless.vert",
                            VulkanVertexInputLayouts.staticVertexInputState(stack),
                            inputAssembly, viewportState, rasterizer, multisampling, depthStencil, colorBlending,
                            renderPass, bindless4Layouts, "bindlessStatic");
                    bindlessStaticPipelineLayout = h.pipelineLayout();
                    bindlessStaticGraphicsPipeline = h.graphicsPipeline();

                    h = VulkanPipelineAssembler.assemble(device, stack, fragModule,
                            VulkanBindlessSkinnedVertexShaderSource.mainVertex(), "main_skinned_bindless.vert",
                            VulkanVertexInputLayouts.skinnedVertexInputState(stack),
                            inputAssembly, viewportState, rasterizer, multisampling, depthStencil, colorBlending,
                            renderPass, bindless4Layouts, "bindlessSkinned");
                    bindlessSkinnedPipelineLayout = h.pipelineLayout();
                    bindlessSkinnedGraphicsPipeline = h.graphicsPipeline();

                    h = VulkanPipelineAssembler.assemble(device, stack, fragModule,
                            VulkanBindlessMorphVertexShaderSource.mainVertex(), "main_morph_bindless.vert",
                            VulkanVertexInputLayouts.staticVertexInputState(stack),
                            inputAssembly, viewportState, rasterizer, multisampling, depthStencil, colorBlending,
                            renderPass, bindless4Layouts, "bindlessMorph");
                    bindlessMorphPipelineLayout = h.pipelineLayout();
                    bindlessMorphGraphicsPipeline = h.graphicsPipeline();

                    h = VulkanPipelineAssembler.assemble(device, stack, fragModule,
                            VulkanBindlessSkinnedMorphVertexShaderSource.mainVertex(), "main_skinned_morph_bindless.vert",
                            VulkanVertexInputLayouts.skinnedVertexInputState(stack),
                            inputAssembly, viewportState, rasterizer, multisampling, depthStencil, colorBlending,
                            renderPass, bindless4Layouts, "bindlessSkinnedMorph");
                    bindlessSkinnedMorphPipelineLayout = h.pipelineLayout();
                    bindlessSkinnedMorphGraphicsPipeline = h.graphicsPipeline();

                    h = VulkanPipelineAssembler.assemble(device, stack, fragModule,
                            VulkanBindlessInstancedVertexShaderSource.mainVertex(), "main_instanced_bindless.vert",
                            VulkanVertexInputLayouts.staticVertexInputState(stack),
                            inputAssembly, viewportState, rasterizer, multisampling, depthStencil, colorBlending,
                            renderPass, bindless4Layouts, "bindlessInstanced");
                    bindlessInstancedPipelineLayout = h.pipelineLayout();
                    bindlessInstancedGraphicsPipeline = h.graphicsPipeline();
                }

                // Non-bindless variants (3 descriptor set layouts)
                var std3Layouts = stack.longs(descriptorSetLayout, textureDescriptorSetLayout, skinnedDescriptorSetLayout);

                var h = VulkanPipelineAssembler.assemble(device, stack, fragModule,
                        VulkanMorphVertexShaderSource.mainVertex(), "main_morph.vert",
                        VulkanVertexInputLayouts.staticVertexInputState(stack),
                        inputAssembly, viewportState, rasterizer, multisampling, depthStencil, colorBlending,
                        renderPass, std3Layouts, "morph");
                morphPipelineLayout = h.pipelineLayout();
                morphGraphicsPipeline = h.graphicsPipeline();

                h = VulkanPipelineAssembler.assemble(device, stack, fragModule,
                        VulkanSkinnedVertexShaderSource.mainVertex(), "main_skinned.vert",
                        VulkanVertexInputLayouts.skinnedVertexInputState(stack),
                        inputAssembly, viewportState, rasterizer, multisampling, depthStencil, colorBlending,
                        renderPass, std3Layouts, "skinned");
                skinnedPipelineLayout = h.pipelineLayout();
                skinnedGraphicsPipeline = h.graphicsPipeline();

                // skinnedMorph uses 4 layouts: desc, tex, skinned, skinned (duplicated)
                var skinnedMorph4Layouts = stack.longs(
                        descriptorSetLayout, textureDescriptorSetLayout,
                        skinnedDescriptorSetLayout, skinnedDescriptorSetLayout);

                h = VulkanPipelineAssembler.assemble(device, stack, fragModule,
                        VulkanSkinnedMorphVertexShaderSource.mainVertex(), "main_skinned_morph.vert",
                        VulkanVertexInputLayouts.skinnedVertexInputState(stack),
                        inputAssembly, viewportState, rasterizer, multisampling, depthStencil, colorBlending,
                        renderPass, skinnedMorph4Layouts, "skinnedMorph");
                skinnedMorphPipelineLayout = h.pipelineLayout();
                skinnedMorphGraphicsPipeline = h.graphicsPipeline();

                h = VulkanPipelineAssembler.assemble(device, stack, fragModule,
                        VulkanInstancedVertexShaderSource.mainVertex(), "main_instanced.vert",
                        VulkanVertexInputLayouts.staticVertexInputState(stack),
                        inputAssembly, viewportState, rasterizer, multisampling, depthStencil, colorBlending,
                        renderPass, std3Layouts, "instanced");
                instancedPipelineLayout = h.pipelineLayout();
                instancedGraphicsPipeline = h.graphicsPipeline();
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
            if (bindlessMorphGraphicsPipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, bindlessMorphGraphicsPipeline, null);
            }
            if (bindlessMorphPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, bindlessMorphPipelineLayout, null);
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
            if (bindlessSkinnedMorphGraphicsPipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, bindlessSkinnedMorphGraphicsPipeline, null);
            }
            if (bindlessSkinnedMorphPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, bindlessSkinnedMorphPipelineLayout, null);
            }
            if (bindlessInstancedGraphicsPipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, bindlessInstancedGraphicsPipeline, null);
            }
            if (bindlessInstancedPipelineLayout != VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineLayout(device, bindlessInstancedPipelineLayout, null);
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
                bindlessMorphPipelineLayout,
                bindlessMorphGraphicsPipeline,
                bindlessSkinnedMorphPipelineLayout,
                bindlessSkinnedMorphGraphicsPipeline,
                bindlessInstancedPipelineLayout,
                bindlessInstancedGraphicsPipeline,
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

    public record Result(
            long renderPass,
            long pipelineLayout,
            long graphicsPipeline,
            long bindlessStaticPipelineLayout,
            long bindlessStaticGraphicsPipeline,
            long bindlessSkinnedPipelineLayout,
            long bindlessSkinnedGraphicsPipeline,
            long bindlessMorphPipelineLayout,
            long bindlessMorphGraphicsPipeline,
            long bindlessSkinnedMorphPipelineLayout,
            long bindlessSkinnedMorphGraphicsPipeline,
            long bindlessInstancedPipelineLayout,
            long bindlessInstancedGraphicsPipeline,
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
