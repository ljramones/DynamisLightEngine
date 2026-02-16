package org.dynamislight.impl.vulkan.uniform;

import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_UNIFORM_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;

public final class VulkanUniformUploadRecorder {
    private VulkanUniformUploadRecorder() {
    }

    public static UploadStats recordUploads(VkCommandBuffer commandBuffer, UploadInputs in) {
        if (in.pendingGlobalUploadByteCount() > 0) {
            VkBufferCopy.Buffer globalCopy = VkBufferCopy.calloc(1)
                    .srcOffset(in.pendingGlobalUploadSrcOffset())
                    .dstOffset(in.pendingGlobalUploadDstOffset())
                    .size(in.pendingGlobalUploadByteCount());
            vkCmdCopyBuffer(commandBuffer, in.sceneGlobalUniformStagingBuffer(), in.sceneGlobalUniformBuffer(), globalCopy);
            globalCopy.free();

            VkBufferMemoryBarrier.Buffer globalBarrier = VkBufferMemoryBarrier.calloc(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_UNIFORM_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(in.sceneGlobalUniformBuffer())
                    .offset(in.pendingGlobalUploadDstOffset())
                    .size(in.pendingGlobalUploadByteCount());
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    globalBarrier,
                    null
            );
            globalBarrier.free();
        }

        if (in.pendingUploadRangeCount() <= 0) {
            return new UploadStats(in.pendingGlobalUploadByteCount(), 0, 0, 0, 0);
        }

        int totalByteCount = 0;
        for (int i = 0; i < in.pendingUploadRangeCount(); i++) {
            totalByteCount += in.pendingUploadByteCounts()[i];
        }

        for (int range = 0; range < in.pendingUploadRangeCount(); range++) {
            VkBufferCopy.Buffer copy = VkBufferCopy.calloc(1)
                    .srcOffset(in.pendingUploadSrcOffsets()[range])
                    .dstOffset(in.pendingUploadDstOffsets()[range])
                    .size(in.pendingUploadByteCounts()[range]);
            vkCmdCopyBuffer(commandBuffer, in.objectUniformStagingBuffer(), in.objectUniformBuffer(), copy);
            copy.free();

            VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_UNIFORM_READ_BIT)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(in.objectUniformBuffer())
                    .offset(in.pendingUploadDstOffsets()[range])
                    .size(in.pendingUploadByteCounts()[range]);
            vkCmdPipelineBarrier(
                    commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    barrier,
                    null
            );
            barrier.free();
        }

        return new UploadStats(
                in.pendingGlobalUploadByteCount(),
                totalByteCount,
                in.pendingUploadObjectCount(),
                in.pendingUploadRangeCount(),
                in.pendingUploadStartObject()
        );
    }

    public record UploadInputs(
            long sceneGlobalUniformStagingBuffer,
            long sceneGlobalUniformBuffer,
            long objectUniformStagingBuffer,
            long objectUniformBuffer,
            long pendingGlobalUploadSrcOffset,
            long pendingGlobalUploadDstOffset,
            int pendingGlobalUploadByteCount,
            int pendingUploadObjectCount,
            int pendingUploadStartObject,
            long[] pendingUploadSrcOffsets,
            long[] pendingUploadDstOffsets,
            int[] pendingUploadByteCounts,
            int pendingUploadRangeCount
    ) {
    }

    public record UploadStats(
            int globalUploadBytes,
            int uniformUploadBytes,
            int uniformObjectCount,
            int uniformUploadRanges,
            int uniformUploadStartObject
    ) {
    }
}
