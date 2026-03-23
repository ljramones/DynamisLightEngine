package org.dynamisengine.light.impl.vulkan.ui;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDevice;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Per-frame GPU buffer management for the UI subsystem.
 *
 * <p>Owns vertex and index buffers that are rebuilt each frame from
 * {@link VulkanUiBatch} data. Buffers are created once and resized
 * only when capacity is exceeded.
 */
public final class VulkanUiFrameContext {

    private static final Logger LOG = Logger.getLogger(VulkanUiFrameContext.class.getName());
    private static final int INITIAL_VERTEX_CAPACITY = 32768; // bytes
    private static final int INITIAL_INDEX_CAPACITY = 16384;  // bytes

    private final VkDevice device;
    private long quadVertexBuffer = VK_NULL_HANDLE;
    private long quadVertexMemory = VK_NULL_HANDLE;
    private int quadVertexCapacity = INITIAL_VERTEX_CAPACITY;

    private long quadIndexBuffer = VK_NULL_HANDLE;
    private long quadIndexMemory = VK_NULL_HANDLE;
    private int quadIndexCapacity = INITIAL_INDEX_CAPACITY;

    private long lineVertexBuffer = VK_NULL_HANDLE;
    private long lineVertexMemory = VK_NULL_HANDLE;
    private int lineVertexCapacity = INITIAL_VERTEX_CAPACITY;

    public VulkanUiFrameContext(VkDevice device) {
        this.device = device;
    }

    public void initialize() {
        quadVertexBuffer = createBuffer(quadVertexCapacity, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        quadVertexMemory = allocateAndBind(quadVertexBuffer, quadVertexCapacity);
        quadIndexBuffer = createBuffer(quadIndexCapacity, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        quadIndexMemory = allocateAndBind(quadIndexBuffer, quadIndexCapacity);
        lineVertexBuffer = createBuffer(lineVertexCapacity, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        lineVertexMemory = allocateAndBind(lineVertexBuffer, lineVertexCapacity);
    }

    /**
     * Upload batch data to GPU buffers. Resizes if needed.
     */
    public void upload(VulkanUiBatch batch) {
        // Quad vertices
        int quadVertBytes = batch.quads().vertexCount() * UiQuadVertex.BYTES;
        if (quadVertBytes > quadVertexCapacity) {
            destroyBuffer(quadVertexBuffer, quadVertexMemory);
            quadVertexCapacity = quadVertBytes * 2;
            quadVertexBuffer = createBuffer(quadVertexCapacity, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
            quadVertexMemory = allocateAndBind(quadVertexBuffer, quadVertexCapacity);
        }
        if (quadVertBytes > 0) {
            mapAndWrite(quadVertexMemory, quadVertBytes, buf -> batch.quads().writeTo(buf));
        }

        // Quad indices
        int quadIdxBytes = batch.quads().indexCount() * 4;
        if (quadIdxBytes > quadIndexCapacity) {
            destroyBuffer(quadIndexBuffer, quadIndexMemory);
            quadIndexCapacity = quadIdxBytes * 2;
            quadIndexBuffer = createBuffer(quadIndexCapacity, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
            quadIndexMemory = allocateAndBind(quadIndexBuffer, quadIndexCapacity);
        }
        if (quadIdxBytes > 0) {
            mapAndWrite(quadIndexMemory, quadIdxBytes, buf -> batch.quads().writeIndicesTo(buf));
        }

        // Line vertices
        int lineVertBytes = batch.lines().vertexCount() * UiLineVertex.BYTES;
        if (lineVertBytes > lineVertexCapacity) {
            destroyBuffer(lineVertexBuffer, lineVertexMemory);
            lineVertexCapacity = lineVertBytes * 2;
            lineVertexBuffer = createBuffer(lineVertexCapacity, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
            lineVertexMemory = allocateAndBind(lineVertexBuffer, lineVertexCapacity);
        }
        if (lineVertBytes > 0) {
            mapAndWrite(lineVertexMemory, lineVertBytes, buf -> batch.lines().writeTo(buf));
        }
    }

    public long quadVertexBuffer() { return quadVertexBuffer; }
    public long quadIndexBuffer() { return quadIndexBuffer; }
    public long lineVertexBuffer() { return lineVertexBuffer; }

    public void destroy() {
        destroyBuffer(quadVertexBuffer, quadVertexMemory);
        destroyBuffer(quadIndexBuffer, quadIndexMemory);
        destroyBuffer(lineVertexBuffer, lineVertexMemory);
        quadVertexBuffer = quadIndexBuffer = lineVertexBuffer = VK_NULL_HANDLE;
    }

    // --- Vulkan helpers ---

    private long createBuffer(int size, int usage) {
        try (var stack = MemoryStack.stackPush()) {
            var bufInfo = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                .sType$Default()
                .size(size)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            var pBuf = stack.mallocLong(1);
            vkCreateBuffer(device, bufInfo, null, pBuf);
            return pBuf.get(0);
        }
    }

    private long allocateAndBind(long buffer, int size) {
        try (var stack = MemoryStack.stackPush()) {
            var memReqs = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, memReqs);

            var allocInfo = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .allocationSize(memReqs.size())
                .memoryTypeIndex(findHostVisibleMemoryType(memReqs.memoryTypeBits()));

            var pMem = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMem);
            long memory = pMem.get(0);
            vkBindBufferMemory(device, buffer, memory, 0);
            return memory;
        }
    }

    private void mapAndWrite(long memory, int size, java.util.function.Consumer<ByteBuffer> writer) {
        try (var stack = MemoryStack.stackPush()) {
            var ppData = stack.mallocPointer(1);
            vkMapMemory(device, memory, 0, size, 0, ppData);
            ByteBuffer mapped = MemoryUtil.memByteBuffer(ppData.get(0), size);
            writer.accept(mapped);
            vkUnmapMemory(device, memory);
        }
    }

    private void destroyBuffer(long buffer, long memory) {
        if (buffer != VK_NULL_HANDLE) vkDestroyBuffer(device, buffer, null);
        if (memory != VK_NULL_HANDLE) vkFreeMemory(device, memory, null);
    }

    private int findHostVisibleMemoryType(int typeFilter) {
        try (var stack = MemoryStack.stackPush()) {
            var memProps = org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(device.getPhysicalDevice(), memProps);

            int required = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            for (int i = 0; i < memProps.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0 &&
                    (memProps.memoryTypes(i).propertyFlags() & required) == required) {
                    return i;
                }
            }
            throw new RuntimeException("Failed to find host-visible memory type");
        }
    }
}
