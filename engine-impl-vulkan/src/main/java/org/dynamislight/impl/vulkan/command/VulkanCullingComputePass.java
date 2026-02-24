package org.dynamislight.impl.vulkan.command;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamisgpu.api.gpu.ComputeCullingDispatch;
import org.dynamisgpu.api.error.GpuException;
import org.dynamisgpu.vulkan.memory.VulkanMemoryOps;
import org.dynamisgpu.vulkan.memory.VulkanBufferAlloc;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.shader.VulkanCullingComputeSource;
import org.dynamislight.impl.vulkan.shader.VulkanShaderCompiler;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_compute_shader;
import static org.lwjgl.vulkan.VK10.*;

public final class VulkanCullingComputePass implements ComputeCullingDispatch {
    private static final int LOCAL_SIZE_X = 64;
    private static final int DRAW_COUNT_BUFFER_BYTES = 8 * Integer.BYTES;
    private static final int PUSH_BYTES = (6 * 4 * Float.BYTES) + (8 * Integer.BYTES);

    private final VkDevice device;
    private final VulkanMeshBoundsBuffer boundsBuffer;
    private final VulkanIndirectDrawBuffer[] outputBuffers;
    private final long[] drawCountBuffers;
    private final long[] drawCountMemories;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long[] descriptorSets;
    private final long pipelineLayout;
    private final long pipeline;

    private VulkanCullingComputePass(
            VkDevice device,
            VulkanMeshBoundsBuffer boundsBuffer,
            VulkanIndirectDrawBuffer[] outputBuffers,
            long[] drawCountBuffers,
            long[] drawCountMemories,
            long descriptorSetLayout,
            long descriptorPool,
            long[] descriptorSets,
            long pipelineLayout,
            long pipeline
    ) {
        this.device = device;
        this.boundsBuffer = boundsBuffer;
        this.outputBuffers = outputBuffers;
        this.drawCountBuffers = drawCountBuffers;
        this.drawCountMemories = drawCountMemories;
        this.descriptorSetLayout = descriptorSetLayout;
        this.descriptorPool = descriptorPool;
        this.descriptorSets = descriptorSets;
        this.pipelineLayout = pipelineLayout;
        this.pipeline = pipeline;
    }

    public static VulkanCullingComputePass create(
            VkDevice device,
            VkPhysicalDevice physicalDevice,
            int meshCapacity,
            VulkanIndirectDrawBuffer[] inputBuffers,
            VulkanIndirectDrawBuffer[] outputBuffers
    ) throws EngineException {
        if (device == null || physicalDevice == null || inputBuffers == null || outputBuffers == null
                || inputBuffers.length == 0 || inputBuffers.length != outputBuffers.length) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "Cannot create culling compute pass without valid Vulkan buffers",
                    false
            );
        }

        try (MemoryStack stack = stackPush()) {
            VulkanMeshBoundsBuffer bounds = VulkanMeshBoundsBuffer.create(device, physicalDevice, meshCapacity);
            int frameCount = inputBuffers.length;
            long[] drawCountBuffers = new long[frameCount];
            long[] drawCountMemories = new long[frameCount];
            for (int i = 0; i < frameCount; i++) {
                VulkanBufferAlloc alloc;
                try {
                    alloc = VulkanMemoryOps.createBuffer(
                            device,
                            physicalDevice,
                            stack,
                            DRAW_COUNT_BUFFER_BYTES,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                    );
                } catch (GpuException ex) {
                    throw new EngineException(
                            EngineErrorCode.BACKEND_INIT_FAILED,
                            "Failed to create culling draw-count buffer: " + ex.getMessage(),
                            false
                    );
                }
                drawCountBuffers[i] = alloc.buffer();
                drawCountMemories[i] = alloc.memory();
            }

            long descriptorSetLayout = createDescriptorSetLayout(device, stack);
            long descriptorPool = createDescriptorPool(device, stack, frameCount);
            long[] descriptorSets = allocateDescriptorSets(device, stack, descriptorPool, descriptorSetLayout, frameCount);
            for (int i = 0; i < frameCount; i++) {
                writeDescriptorSet(
                        device,
                        stack,
                        descriptorSets[i],
                        bounds.bufferHandle(),
                        inputBuffers[i].bufferHandle(),
                        outputBuffers[i].bufferHandle(),
                        drawCountBuffers[i]
                );
            }

            long pipelineLayout = createPipelineLayout(device, stack, descriptorSetLayout);
            long pipeline = createPipeline(device, stack, pipelineLayout);
            return new VulkanCullingComputePass(
                    device,
                    bounds,
                    outputBuffers,
                    drawCountBuffers,
                    drawCountMemories,
                    descriptorSetLayout,
                    descriptorPool,
                    descriptorSets,
                    pipelineLayout,
                    pipeline
            );
        }
    }

    public void uploadMeshBounds(List<VulkanGpuMesh> meshes) {
        boundsBuffer.upload(meshes);
    }

    public void dispatch(MemoryStack stack, VkCommandBuffer commandBuffer, int frameIdx, int drawCount, int boundsCount, float[] viewProjMatrix) {
        if (frameIdx < 0 || frameIdx >= descriptorSets.length || drawCount <= 0 || pipeline == VK_NULL_HANDLE) {
            return;
        }
        int safeBoundsCount = Math.max(0, Math.min(boundsCount, boundsBuffer.capacity()));
        resetDrawCount(commandBuffer, frameIdx);
        barrierTransferToCompute(commandBuffer, frameIdx);

        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_COMPUTE,
                pipelineLayout,
                0,
                stack.longs(descriptorSets[frameIdx]),
                null
        );

        ByteBuffer push = stack.malloc(PUSH_BYTES);
        float[] planes = extractFrustumPlanes(viewProjMatrix);
        for (float value : planes) {
            push.putFloat(value);
        }
        push.putInt(drawCount);
        push.putInt(safeBoundsCount);
        VulkanIndirectDrawBuffer.Layout layout = outputBuffers[frameIdx].layout();
        push.putInt(layout.staticOffsetCommands());
        push.putInt(layout.morphOffsetCommands());
        push.putInt(layout.skinnedOffsetCommands());
        push.putInt(layout.skinnedMorphOffsetCommands());
        push.putInt(layout.instancedOffsetCommands());
        push.putInt(0);
        push.flip();
        vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push);

        int groups = Math.max(1, (drawCount + LOCAL_SIZE_X - 1) / LOCAL_SIZE_X);
        vkCmdDispatch(commandBuffer, groups, 1, 1);
        barrierComputeToIndirect(commandBuffer, frameIdx);
    }

    @Override
    public void dispatch(org.dynamisgpu.api.gpu.VkCommandBuffer commandBuffer, int drawCount, float[] viewProjectionMatrix) {
        throw new UnsupportedOperationException("Use engine-specific dispatch with frame index and LWJGL command buffer");
    }

    public long culledIndirectBufferHandle(int frameIdx) {
        if (frameIdx < 0 || frameIdx >= outputBuffers.length) {
            return VK_NULL_HANDLE;
        }
        return outputBuffers[frameIdx].bufferHandle();
    }

    public VulkanIndirectDrawBuffer culledIndirectBuffer(int frameIdx) {
        if (frameIdx < 0 || frameIdx >= outputBuffers.length) {
            return null;
        }
        return outputBuffers[frameIdx];
    }

    public long drawCountBufferHandle(int frameIdx) {
        if (frameIdx < 0 || frameIdx >= drawCountBuffers.length) {
            return VK_NULL_HANDLE;
        }
        return drawCountBuffers[frameIdx];
    }

    public void destroy() {
        if (pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, pipeline, null);
        }
        if (pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, pipelineLayout, null);
        }
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null);
        }
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        }
        for (int i = 0; i < drawCountBuffers.length; i++) {
            if (drawCountBuffers[i] != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, drawCountBuffers[i], null);
            }
            if (drawCountMemories[i] != VK_NULL_HANDLE) {
                vkFreeMemory(device, drawCountMemories[i], null);
            }
        }
        boundsBuffer.destroy();
    }

    private void resetDrawCount(VkCommandBuffer commandBuffer, int frameIdx) {
        vkCmdFillBuffer(commandBuffer, drawCountBuffers[frameIdx], 0, DRAW_COUNT_BUFFER_BYTES, 0);
    }

    private void barrierTransferToCompute(VkCommandBuffer commandBuffer, int frameIdx) {
        try (MemoryStack stack = stackPush()) {
            VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(1, stack);
            barriers.get(0)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(drawCountBuffers[frameIdx])
                    .offset(0)
                    .size(VK_WHOLE_SIZE);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0,
                    null,
                    barriers,
                    null
            );
        }
    }

    private void barrierComputeToIndirect(VkCommandBuffer commandBuffer, int frameIdx) {
        try (MemoryStack stack = stackPush()) {
            VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(2, stack);
            barriers.get(0)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_INDIRECT_COMMAND_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(outputBuffers[frameIdx].bufferHandle())
                    .offset(0)
                    .size(VK_WHOLE_SIZE);
            barriers.get(1)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_INDIRECT_COMMAND_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(drawCountBuffers[frameIdx])
                    .offset(0)
                    .size(VK_WHOLE_SIZE);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                    0,
                    null,
                    barriers,
                    null
            );
        }
    }

    private static long createDescriptorSetLayout(VkDevice device, MemoryStack stack) throws EngineException {
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(4, stack);
        for (int i = 0; i < 4; i++) {
            bindings.get(i)
                    .binding(i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
        }
        VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings);
        var pLayout = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateDescriptorSetLayout(device, info, null, pLayout);
        if (result != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDescriptorSetLayout(culling) failed: " + result,
                    false
            );
        }
        return pLayout.get(0);
    }

    private static long createDescriptorPool(VkDevice device, MemoryStack stack, int frameCount) throws EngineException {
        VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
        sizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(Math.max(1, frameCount) * 4);
        VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(Math.max(1, frameCount))
                .pPoolSizes(sizes);
        var pPool = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateDescriptorPool(device, info, null, pPool);
        if (result != VK_SUCCESS || pPool.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreateDescriptorPool(culling) failed: " + result,
                    false
            );
        }
        return pPool.get(0);
    }

    private static long[] allocateDescriptorSets(
            VkDevice device,
            MemoryStack stack,
            long descriptorPool,
            long descriptorSetLayout,
            int frameCount
    ) throws EngineException {
        var layouts = stack.mallocLong(frameCount);
        for (int i = 0; i < frameCount; i++) {
            layouts.put(i, descriptorSetLayout);
        }
        VkDescriptorSetAllocateInfo alloc = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(layouts);
        var out = stack.mallocLong(frameCount);
        int result = vkAllocateDescriptorSets(device, alloc, out);
        if (result != VK_SUCCESS) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkAllocateDescriptorSets(culling) failed: " + result,
                    false
            );
        }
        long[] sets = new long[frameCount];
        for (int i = 0; i < frameCount; i++) {
            sets[i] = out.get(i);
        }
        return sets;
    }

    private static void writeDescriptorSet(
            VkDevice device,
            MemoryStack stack,
            long descriptorSet,
            long boundsBuffer,
            long inputDrawBuffer,
            long outputDrawBuffer,
            long drawCountBuffer
    ) {
        VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(4, stack);
        infos.get(0).buffer(boundsBuffer).offset(0).range(VK_WHOLE_SIZE);
        infos.get(1).buffer(inputDrawBuffer).offset(0).range(VK_WHOLE_SIZE);
        infos.get(2).buffer(outputDrawBuffer).offset(0).range(VK_WHOLE_SIZE);
        infos.get(3).buffer(drawCountBuffer).offset(0).range(DRAW_COUNT_BUFFER_BYTES);

        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(4, stack);
        for (int i = 0; i < 4; i++) {
            writes.get(i)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).put(0, infos.get(i)));
        }
        vkUpdateDescriptorSets(device, writes, null);
    }

    private static long createPipelineLayout(VkDevice device, MemoryStack stack, long descriptorSetLayout) throws EngineException {
        VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
        range.get(0)
                .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                .offset(0)
                .size(PUSH_BYTES);
        VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(descriptorSetLayout))
                .pPushConstantRanges(range);
        var pLayout = stack.longs(VK_NULL_HANDLE);
        int result = vkCreatePipelineLayout(device, info, null, pLayout);
        if (result != VK_SUCCESS || pLayout.get(0) == VK_NULL_HANDLE) {
            throw new EngineException(
                    EngineErrorCode.BACKEND_INIT_FAILED,
                    "vkCreatePipelineLayout(culling) failed: " + result,
                    false
            );
        }
        return pLayout.get(0);
    }

    private static long createPipeline(VkDevice device, MemoryStack stack, long pipelineLayout) throws EngineException {
        String source = VulkanCullingComputeSource.compute();
        ByteBuffer spv = VulkanShaderCompiler.compileGlslToSpv(source, shaderc_glsl_compute_shader, "culling.comp");
        long shaderModule = VK_NULL_HANDLE;
        try {
            shaderModule = VulkanShaderCompiler.createShaderModule(device, stack, spv);
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(shaderModule)
                    .pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer info = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .stage(stage)
                    .layout(pipelineLayout);
            var pPipeline = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateComputePipelines(device, VK_NULL_HANDLE, info, null, pPipeline);
            if (result != VK_SUCCESS || pPipeline.get(0) == VK_NULL_HANDLE) {
                throw new EngineException(
                        EngineErrorCode.BACKEND_INIT_FAILED,
                        "vkCreateComputePipelines(culling) failed: " + result,
                        false
                );
            }
            return pPipeline.get(0);
        } finally {
            if (shaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, shaderModule, null);
            }
        }
    }

    private static float[] extractFrustumPlanes(float[] viewProj) {
        float[] m = viewProj == null || viewProj.length != 16 ? identityMatrixArray() : viewProj;

        float r0x = m[0], r0y = m[4], r0z = m[8], r0w = m[12];
        float r1x = m[1], r1y = m[5], r1z = m[9], r1w = m[13];
        float r2x = m[2], r2y = m[6], r2z = m[10], r2w = m[14];
        float r3x = m[3], r3y = m[7], r3z = m[11], r3w = m[15];

        float[] out = new float[24];
        writePlane(out, 0, r3x + r0x, r3y + r0y, r3z + r0z, r3w + r0w); // left
        writePlane(out, 4, r3x - r0x, r3y - r0y, r3z - r0z, r3w - r0w); // right
        writePlane(out, 8, r3x + r1x, r3y + r1y, r3z + r1z, r3w + r1w); // bottom
        writePlane(out, 12, r3x - r1x, r3y - r1y, r3z - r1z, r3w - r1w); // top
        writePlane(out, 16, r3x + r2x, r3y + r2y, r3z + r2z, r3w + r2w); // near
        writePlane(out, 20, r3x - r2x, r3y - r2y, r3z - r2z, r3w - r2w); // far
        return out;
    }

    private static void writePlane(float[] out, int base, float x, float y, float z, float w) {
        float invLen = (float) (1.0 / Math.max(1e-6, Math.sqrt((x * x) + (y * y) + (z * z))));
        out[base] = x * invLen;
        out[base + 1] = y * invLen;
        out[base + 2] = z * invLen;
        out[base + 3] = w * invLen;
    }

    private static float[] identityMatrixArray() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }
}
