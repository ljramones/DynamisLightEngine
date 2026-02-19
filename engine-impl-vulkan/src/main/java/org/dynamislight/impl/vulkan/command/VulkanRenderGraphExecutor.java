package org.dynamislight.impl.vulkan.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.graph.VulkanBufferResourceBinding;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphPlan;
import org.dynamislight.impl.vulkan.graph.VulkanImageResourceBinding;
import org.dynamislight.impl.vulkan.graph.VulkanRenderGraphBarrier;
import org.dynamislight.impl.vulkan.graph.VulkanRenderGraphNode;
import org.dynamislight.impl.vulkan.graph.VulkanResourceBinding;
import org.dynamislight.impl.vulkan.graph.VulkanResourceBindingTable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryBarrier;

import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;

/**
 * Executes compiled Vulkan graph pass callbacks and emits planned inter-pass barriers.
 */
public final class VulkanRenderGraphExecutor {
    private final boolean emitVulkanBarriers;

    public VulkanRenderGraphExecutor() {
        this(true);
    }

    VulkanRenderGraphExecutor(boolean emitVulkanBarriers) {
        this.emitVulkanBarriers = emitVulkanBarriers;
    }

    public void execute(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanExecutableRenderGraphPlan executablePlan,
            VulkanResourceBindingTable bindingTable
    ) throws EngineException {
        if (executablePlan == null || bindingTable == null) {
            return;
        }
        Map<String, List<VulkanRenderGraphBarrier>> barriersByDestinationNode = barriersByDestinationNodeId(executablePlan);
        for (VulkanRenderGraphNode node : executablePlan.metadataPlan().orderedNodes()) {
            if (node == null) {
                continue;
            }
            List<VulkanRenderGraphBarrier> before = barriersByDestinationNode.getOrDefault(node.nodeId(), List.of());
            for (VulkanRenderGraphBarrier barrier : before) {
                emitBarrier(stack, commandBuffer, barrier, bindingTable);
            }
            executablePlan.executeCallback(node.nodeId()).run();
        }
    }

    private void emitBarrier(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderGraphBarrier barrier,
            VulkanResourceBindingTable bindingTable
    ) throws EngineException {
        if (barrier == null) {
            return;
        }
        VulkanResourceBinding binding = bindingTable.resolve(barrier.resourceName());
        if (binding == null) {
            throw new EngineException(
                    EngineErrorCode.INTERNAL_ERROR,
                    "Render graph barrier references unbound resource '" + barrier.resourceName() + "'",
                    false
            );
        }
        if (binding instanceof VulkanImageResourceBinding image) {
            emitImageBarrier(stack, commandBuffer, barrier, image, bindingTable);
            return;
        }
        if (binding instanceof VulkanBufferResourceBinding buffer) {
            emitBufferBarrier(stack, commandBuffer, barrier, buffer);
            return;
        }
        throw new EngineException(
                EngineErrorCode.INTERNAL_ERROR,
                "Unsupported Vulkan graph resource binding type for '" + barrier.resourceName() + "'",
                false
        );
    }

    private void emitImageBarrier(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderGraphBarrier barrier,
            VulkanImageResourceBinding image,
            VulkanResourceBindingTable bindingTable
    ) throws EngineException {
        if (barrier.oldLayout() >= 0 && image.currentLayout() >= 0 && image.currentLayout() != barrier.oldLayout()) {
            throw new EngineException(
                    EngineErrorCode.INTERNAL_ERROR,
                    "Render graph layout mismatch for '" + image.resourceName()
                            + "': binding=" + image.currentLayout()
                            + " plannedOld=" + barrier.oldLayout(),
                    false
            );
        }
        if (!emitVulkanBarriers) {
            if (barrier.newLayout() >= 0) {
                bindingTable.updateLayout(image.resourceName(), barrier.newLayout());
            }
            return;
        }
        VkImageMemoryBarrier.Buffer imageBarrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(barrier.srcAccessMask())
                .dstAccessMask(barrier.dstAccessMask())
                .oldLayout(barrier.oldLayout() >= 0 ? barrier.oldLayout() : image.currentLayout())
                .newLayout(barrier.newLayout() >= 0 ? barrier.newLayout() : image.currentLayout())
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image.vkImageHandle());
        imageBarrier.get(0).subresourceRange()
                .aspectMask(image.aspectMask())
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);

        if (emitVulkanBarriers) {
            VulkanRenderCommandRecorder.vkCmdPipelineBarrier(
                    commandBuffer,
                    barrier.srcStageMask(),
                    barrier.dstStageMask(),
                    0,
                    (VkMemoryBarrier.Buffer) null,
                    null,
                    imageBarrier
            );
        }
        if (barrier.newLayout() >= 0) {
            bindingTable.updateLayout(image.resourceName(), barrier.newLayout());
        }
    }

    private void emitBufferBarrier(
            MemoryStack stack,
            VkCommandBuffer commandBuffer,
            VulkanRenderGraphBarrier barrier,
            VulkanBufferResourceBinding buffer
    ) {
        if (!emitVulkanBarriers) {
            return;
        }
        VkBufferMemoryBarrier.Buffer bufferBarrier = VkBufferMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER)
                .srcAccessMask(barrier.srcAccessMask())
                .dstAccessMask(barrier.dstAccessMask())
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .buffer(buffer.vkBufferHandle())
                .offset(0)
                .size(buffer.sizeBytes());
        VK10.vkCmdPipelineBarrier(
                commandBuffer,
                barrier.srcStageMask(),
                barrier.dstStageMask(),
                0,
                null,
                bufferBarrier,
                null
        );
    }

    private static Map<String, List<VulkanRenderGraphBarrier>> barriersByDestinationNodeId(
            VulkanExecutableRenderGraphPlan executablePlan
    ) {
        Map<String, List<VulkanRenderGraphBarrier>> out = new LinkedHashMap<>();
        for (VulkanRenderGraphBarrier barrier : executablePlan.barrierPlan().barriers()) {
            if (barrier == null) {
                continue;
            }
            String nodeId = destinationNodeId(barrier.destinationAccessId());
            if (nodeId.isBlank()) {
                continue;
            }
            out.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(barrier);
        }
        return out;
    }

    static String destinationNodeId(String accessId) {
        String id = accessId == null ? "" : accessId.trim();
        if (id.isBlank()) {
            return "";
        }
        int colon = id.lastIndexOf(':');
        String left = colon >= 0 ? id.substring(0, colon) : id;
        int hash = left.lastIndexOf('#');
        if (hash < 0) {
            return left;
        }
        String suffix = left.substring(hash + 1);
        if (isUnsignedInteger(suffix)) {
            return left.substring(0, hash);
        }
        return left;
    }

    private static boolean isUnsignedInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
