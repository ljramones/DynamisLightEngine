package org.dynamisengine.light.impl.vulkan.ui;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Owns the Vulkan pipelines, descriptor set layout, and pipeline layout
 * for UI rendering (quads + lines).
 *
 * <p>Two graphics pipelines:
 * <ul>
 *   <li><b>quad</b> — triangle list with font atlas sampler, alpha blending</li>
 *   <li><b>line</b> — line list, color only, alpha blending</li>
 * </ul>
 */
public final class VulkanUiPipeline {

    private static final Logger LOG = Logger.getLogger(VulkanUiPipeline.class.getName());

    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;
    private long quadPipeline = VK_NULL_HANDLE;
    private long linePipeline = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;
    private long renderPass = VK_NULL_HANDLE;

    public long quadPipeline() { return quadPipeline; }
    public long linePipeline() { return linePipeline; }
    public long pipelineLayout() { return pipelineLayout; }
    public long descriptorSet() { return descriptorSet; }
    public long renderPass() { return renderPass; }

    /**
     * Initialize all Vulkan resources for UI rendering.
     */
    public void initialize(VkDevice device, int swapchainFormat, VulkanFontAtlas fontAtlas) {
        createRenderPass(device, swapchainFormat);
        createDescriptorSetLayout(device);
        createPipelineLayout(device);
        createDescriptorPoolAndSet(device, fontAtlas);
        createQuadPipeline(device);
        createLinePipeline(device);
        LOG.info("VulkanUiPipeline initialized");
    }

    public void destroy(VkDevice device) {
        if (quadPipeline != VK_NULL_HANDLE) vkDestroyPipeline(device, quadPipeline, null);
        if (linePipeline != VK_NULL_HANDLE) vkDestroyPipeline(device, linePipeline, null);
        if (pipelineLayout != VK_NULL_HANDLE) vkDestroyPipelineLayout(device, pipelineLayout, null);
        if (descriptorPool != VK_NULL_HANDLE) vkDestroyDescriptorPool(device, descriptorPool, null);
        if (descriptorSetLayout != VK_NULL_HANDLE) vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        if (renderPass != VK_NULL_HANDLE) vkDestroyRenderPass(device, renderPass, null);
        quadPipeline = linePipeline = pipelineLayout = descriptorPool = descriptorSetLayout = renderPass = VK_NULL_HANDLE;
    }

    // --- Render pass (color-only, LOAD existing content) ---

    private void createRenderPass(VkDevice device, int swapchainFormat) {
        try (var stack = stackPush()) {
            var attachment = VkAttachmentDescription.calloc(1, stack)
                .format(swapchainFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            var colorRef = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            var subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorRef);

            var dependency = VkSubpassDependency.calloc(1, stack)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            var renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType$Default()
                .pAttachments(attachment)
                .pSubpasses(subpass)
                .pDependencies(dependency);

            var pRenderPass = stack.mallocLong(1);
            check(vkCreateRenderPass(device, renderPassInfo, null, pRenderPass), "createRenderPass");
            renderPass = pRenderPass.get(0);
        }
    }

    // --- Descriptor set layout (single combined image sampler for font atlas) ---

    private void createDescriptorSetLayout(VkDevice device) {
        try (var stack = stackPush()) {
            var binding = VkDescriptorSetLayoutBinding.calloc(1, stack)
                .binding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            var layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pBindings(binding);

            var pLayout = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout), "createDescriptorSetLayout");
            descriptorSetLayout = pLayout.get(0);
        }
    }

    // --- Pipeline layout (push constant for screen size + descriptor set) ---

    private void createPipelineLayout(VkDevice device) {
        try (var stack = stackPush()) {
            var pushRange = VkPushConstantRange.calloc(1, stack)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                .offset(0)
                .size(8); // 2 floats: screenWidth, screenHeight

            var pSetLayouts = stack.mallocLong(1).put(0, descriptorSetLayout);

            var layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pSetLayouts(pSetLayouts)
                .pPushConstantRanges(pushRange);

            var pLayout = stack.mallocLong(1);
            check(vkCreatePipelineLayout(device, layoutInfo, null, pLayout), "createPipelineLayout");
            pipelineLayout = pLayout.get(0);
        }
    }

    // --- Descriptor pool and set for font atlas ---

    private void createDescriptorPoolAndSet(VkDevice device, VulkanFontAtlas atlas) {
        try (var stack = stackPush()) {
            var poolSize = VkDescriptorPoolSize.calloc(1, stack)
                .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1);

            var poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType$Default()
                .maxSets(1)
                .pPoolSizes(poolSize);

            var pPool = stack.mallocLong(1);
            check(vkCreateDescriptorPool(device, poolInfo, null, pPool), "createDescriptorPool");
            descriptorPool = pPool.get(0);

            var pSetLayouts = stack.mallocLong(1).put(0, descriptorSetLayout);
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType$Default()
                .descriptorPool(descriptorPool)
                .pSetLayouts(pSetLayouts);

            var pSet = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(device, allocInfo, pSet), "allocateDescriptorSets");
            descriptorSet = pSet.get(0);

            // Write font atlas to descriptor set
            var imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(atlas.imageView())
                .sampler(atlas.sampler());

            var write = VkWriteDescriptorSet.calloc(1, stack)
                .sType$Default()
                .dstSet(descriptorSet)
                .dstBinding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(device, write, null);
        }
    }

    // --- Quad pipeline ---

    private void createQuadPipeline(VkDevice device) {
        try (var stack = stackPush()) {
            long vertModule = createShaderModule(device, "/shaders/ui/ui_quad.vert", shaderc_vertex_shader);
            long fragModule = createShaderModule(device, "/shaders/ui/ui_quad.frag", shaderc_fragment_shader);

            var stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertModule)
                .pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragModule)
                .pName(stack.UTF8("main"));

            // Vertex input: pos(2f), uv(2f), color(1u)
            var bindingDesc = VkVertexInputBindingDescription.calloc(1, stack)
                .binding(0).stride(UiQuadVertex.BYTES).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            var attrDescs = VkVertexInputAttributeDescription.calloc(3, stack);
            attrDescs.get(0).location(0).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(0);
            attrDescs.get(1).location(1).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(8);
            attrDescs.get(2).location(2).binding(0).format(VK_FORMAT_R32_UINT).offset(16);

            quadPipeline = buildPipeline(device, stack, stages, bindingDesc, attrDescs,
                VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            vkDestroyShaderModule(device, vertModule, null);
            vkDestroyShaderModule(device, fragModule, null);
        }
    }

    // --- Line pipeline ---

    private void createLinePipeline(VkDevice device) {
        try (var stack = stackPush()) {
            long vertModule = createShaderModule(device, "/shaders/ui/ui_line.vert", shaderc_vertex_shader);
            long fragModule = createShaderModule(device, "/shaders/ui/ui_line.frag", shaderc_fragment_shader);

            var stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertModule)
                .pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragModule)
                .pName(stack.UTF8("main"));

            var bindingDesc = VkVertexInputBindingDescription.calloc(1, stack)
                .binding(0).stride(UiLineVertex.BYTES).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            var attrDescs = VkVertexInputAttributeDescription.calloc(2, stack);
            attrDescs.get(0).location(0).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(0);
            attrDescs.get(1).location(1).binding(0).format(VK_FORMAT_R32_UINT).offset(8);

            linePipeline = buildPipeline(device, stack, stages, bindingDesc, attrDescs,
                VK_PRIMITIVE_TOPOLOGY_LINE_LIST);

            vkDestroyShaderModule(device, vertModule, null);
            vkDestroyShaderModule(device, fragModule, null);
        }
    }

    // --- Shared pipeline builder ---

    private long buildPipeline(VkDevice device, MemoryStack stack,
                                VkPipelineShaderStageCreateInfo.Buffer stages,
                                VkVertexInputBindingDescription.Buffer bindings,
                                VkVertexInputAttributeDescription.Buffer attrs,
                                int topology) {

        var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
            .sType$Default()
            .pVertexBindingDescriptions(bindings)
            .pVertexAttributeDescriptions(attrs);

        var inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
            .sType$Default()
            .topology(topology)
            .primitiveRestartEnable(false);

        var viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
            .sType$Default()
            .viewportCount(1)
            .scissorCount(1);

        var rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
            .sType$Default()
            .depthClampEnable(false)
            .rasterizerDiscardEnable(false)
            .polygonMode(VK_POLYGON_MODE_FILL)
            .lineWidth(1f)
            .cullMode(VK_CULL_MODE_NONE)
            .frontFace(VK_FRONT_FACE_CLOCKWISE)
            .depthBiasEnable(false);

        var multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
            .sType$Default()
            .sampleShadingEnable(false)
            .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

        var colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
            .blendEnable(true)
            .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
            .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
            .colorBlendOp(VK_BLEND_OP_ADD)
            .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
            .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
            .alphaBlendOp(VK_BLEND_OP_ADD)
            .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);

        var colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
            .sType$Default()
            .logicOpEnable(false)
            .pAttachments(colorBlendAttachment);

        var depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
            .sType$Default()
            .depthTestEnable(false)
            .depthWriteEnable(false);

        var dynamicStates = stack.mallocInt(2);
        dynamicStates.put(0, VK_DYNAMIC_STATE_VIEWPORT);
        dynamicStates.put(1, VK_DYNAMIC_STATE_SCISSOR);

        var dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
            .sType$Default()
            .pDynamicStates(dynamicStates);

        var pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
            .sType$Default()
            .pStages(stages)
            .pVertexInputState(vertexInput)
            .pInputAssemblyState(inputAssembly)
            .pViewportState(viewport)
            .pRasterizationState(rasterizer)
            .pMultisampleState(multisampling)
            .pColorBlendState(colorBlending)
            .pDepthStencilState(depthStencil)
            .pDynamicState(dynamicState)
            .layout(pipelineLayout)
            .renderPass(renderPass)
            .subpass(0);

        var pPipeline = stack.mallocLong(1);
        check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline), "createGraphicsPipelines");
        return pPipeline.get(0);
    }

    // --- Shader compilation ---

    private long createShaderModule(VkDevice device, String resourcePath, int shaderKind) {
        String source = loadShaderSource(resourcePath);
        ByteBuffer spirv = compileGlslToSpirv(source, resourcePath, shaderKind);

        try (var stack = stackPush()) {
            var createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType$Default()
                .pCode(spirv);

            var pModule = stack.mallocLong(1);
            check(vkCreateShaderModule(device, createInfo, null, pModule), "createShaderModule");
            long module = pModule.get(0);

            org.lwjgl.system.MemoryUtil.memFree(spirv);
            return module;
        }
    }

    private String loadShaderSource(String resourcePath) {
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) throw new RuntimeException("Shader not found: " + resourcePath);
            return new String(is.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + resourcePath, e);
        }
    }

    private ByteBuffer compileGlslToSpirv(String source, String name, int shaderKind) {
        long compiler = shaderc_compiler_initialize();
        if (compiler == 0) throw new RuntimeException("Failed to initialize shaderc compiler");

        long result = shaderc_compile_into_spv(compiler, source, shaderKind, name, "main", 0);
        if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            String error = shaderc_result_get_error_message(result);
            shaderc_result_release(result);
            shaderc_compiler_release(compiler);
            throw new RuntimeException("Shader compilation failed: " + name + "\n" + error);
        }

        ByteBuffer spirv = shaderc_result_get_bytes(result);
        // Copy to a standalone buffer before releasing the result
        ByteBuffer copy = org.lwjgl.system.MemoryUtil.memAlloc(spirv.remaining());
        copy.put(spirv).flip();

        shaderc_result_release(result);
        shaderc_compiler_release(compiler);
        return copy;
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) throw new RuntimeException("Vulkan " + operation + " failed: " + result);
    }
}
