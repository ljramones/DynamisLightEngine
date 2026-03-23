package org.dynamisengine.light.impl.vulkan.ui;

import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDevice;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Bitmap font atlas for UI text rendering.
 *
 * <p>Generates a simple ASCII bitmap font atlas (characters 32-126) using
 * STB TrueType, uploads to a Vulkan image, and provides glyph UV lookups.
 *
 * <p>The atlas is created once at initialization and reused across frames.
 * No per-frame allocations.
 */
public final class VulkanFontAtlas {

    private static final Logger LOG = Logger.getLogger(VulkanFontAtlas.class.getName());

    // Simple monospace grid: 16 chars per row, 6 rows (chars 32-127)
    private static final int ATLAS_WIDTH = 256;
    private static final int ATLAS_HEIGHT = 256;
    private static final int CHARS_PER_ROW = 16;
    private static final int FIRST_CHAR = 32;
    private static final int CHAR_COUNT = 95; // 32-126
    private static final float CELL_W = ATLAS_WIDTH / (float) CHARS_PER_ROW;
    private static final float CELL_H = ATLAS_HEIGHT / 6f;

    private long image = VK_NULL_HANDLE;
    private long imageView = VK_NULL_HANDLE;
    private long imageMemory = VK_NULL_HANDLE;
    private long sampler = VK_NULL_HANDLE;

    private float charWidth = 8f;  // base char width in pixels
    private float charHeight = 14f; // base char height in pixels

    public float charWidth() { return charWidth; }
    public float charHeight() { return charHeight; }

    /** UV rectangle for a glyph. */
    public record GlyphUV(float u0, float v0, float u1, float v1) {}

    /** Look up the UV rectangle for an ASCII character. */
    public GlyphUV glyphUV(char c) {
        int idx = c - FIRST_CHAR;
        if (idx < 0 || idx >= CHAR_COUNT) idx = '?' - FIRST_CHAR;

        int col = idx % CHARS_PER_ROW;
        int row = idx / CHARS_PER_ROW;

        float u0 = col * CELL_W / ATLAS_WIDTH;
        float v0 = row * CELL_H / ATLAS_HEIGHT;
        float u1 = (col + 1) * CELL_W / ATLAS_WIDTH;
        float v1 = (row + 1) * CELL_H / ATLAS_HEIGHT;

        return new GlyphUV(u0, v0, u1, v1);
    }

    /**
     * Initialize the font atlas: generate bitmap, create Vulkan image + view + sampler.
     */
    public void initialize(VkDevice device, long physicalDevice, long commandPool, long graphicsQueue) {
        // Generate bitmap atlas (white-on-black, R8 format)
        ByteBuffer bitmap = MemoryUtil.memCalloc(ATLAS_WIDTH * ATLAS_HEIGHT);
        generateBitmapFont(bitmap);

        // Create Vulkan image
        image = createImage(device, physicalDevice);
        imageMemory = allocateAndBindMemory(device, physicalDevice, image);
        uploadBitmap(device, commandPool, graphicsQueue, bitmap);
        imageView = createImageView(device, image);
        sampler = createSampler(device);

        MemoryUtil.memFree(bitmap);
        LOG.info("VulkanFontAtlas initialized: " + ATLAS_WIDTH + "x" + ATLAS_HEIGHT);
    }

    /** Generate a simple pixel-based bitmap font. Each char is an 8x14 cell. */
    private void generateBitmapFont(ByteBuffer bitmap) {
        // Ultra-simple: draw each ASCII char as a recognizable pattern
        // This is a fallback bitmap font — just enough to render readable text
        for (int c = FIRST_CHAR; c < FIRST_CHAR + CHAR_COUNT; c++) {
            int idx = c - FIRST_CHAR;
            int col = idx % CHARS_PER_ROW;
            int row = idx / CHARS_PER_ROW;
            int baseX = (int)(col * CELL_W);
            int baseY = (int)(row * CELL_H);

            // Simple block pattern: fill most of the cell for visibility
            // A production system would use STB TrueType baking
            byte[] pattern = getCharPattern(c);
            for (int py = 0; py < Math.min(pattern.length, (int) CELL_H); py++) {
                for (int px = 0; px < 8; px++) {
                    if ((pattern[py] & (1 << (7 - px))) != 0) {
                        int fx = baseX + px;
                        int fy = baseY + py;
                        if (fx < ATLAS_WIDTH && fy < ATLAS_HEIGHT) {
                            bitmap.put(fy * ATLAS_WIDTH + fx, (byte) 0xFF);
                        }
                    }
                }
            }
        }
    }

    /** Get a simple 8-wide bitmap pattern for a character (up to 14 rows). */
    private static byte[] getCharPattern(int c) {
        // Minimal 8x14 font patterns for essential ASCII chars
        // Only a subset is hand-coded; others get a default block
        return switch (c) {
            case ' ' -> new byte[14]; // blank
            case '0' -> new byte[]{0x3C,0x66,0x6E,0x76,0x66,0x66,0x3C,0,0,0,0,0,0,0};
            case '1' -> new byte[]{0x18,0x38,0x18,0x18,0x18,0x18,0x7E,0,0,0,0,0,0,0};
            case '2' -> new byte[]{0x3C,0x66,0x06,0x0C,0x18,0x30,0x7E,0,0,0,0,0,0,0};
            case '3' -> new byte[]{0x3C,0x66,0x06,0x1C,0x06,0x66,0x3C,0,0,0,0,0,0,0};
            case '4' -> new byte[]{0x0C,0x1C,0x2C,0x4C,0x7E,0x0C,0x0C,0,0,0,0,0,0,0};
            case '5' -> new byte[]{0x7E,0x60,0x7C,0x06,0x06,0x66,0x3C,0,0,0,0,0,0,0};
            case '6' -> new byte[]{0x3C,0x60,0x7C,0x66,0x66,0x66,0x3C,0,0,0,0,0,0,0};
            case '7' -> new byte[]{0x7E,0x06,0x0C,0x18,0x18,0x18,0x18,0,0,0,0,0,0,0};
            case '8' -> new byte[]{0x3C,0x66,0x66,0x3C,0x66,0x66,0x3C,0,0,0,0,0,0,0};
            case '9' -> new byte[]{0x3C,0x66,0x66,0x3E,0x06,0x06,0x3C,0,0,0,0,0,0,0};
            case 'A','a' -> new byte[]{0x18,0x3C,0x66,0x66,0x7E,0x66,0x66,0,0,0,0,0,0,0};
            case 'B','b' -> new byte[]{0x7C,0x66,0x66,0x7C,0x66,0x66,0x7C,0,0,0,0,0,0,0};
            case 'C','c' -> new byte[]{0x3C,0x66,0x60,0x60,0x60,0x66,0x3C,0,0,0,0,0,0,0};
            case 'D','d' -> new byte[]{0x78,0x6C,0x66,0x66,0x66,0x6C,0x78,0,0,0,0,0,0,0};
            case 'E','e' -> new byte[]{0x7E,0x60,0x60,0x7C,0x60,0x60,0x7E,0,0,0,0,0,0,0};
            case 'F','f' -> new byte[]{0x7E,0x60,0x60,0x7C,0x60,0x60,0x60,0,0,0,0,0,0,0};
            case 'G','g' -> new byte[]{0x3C,0x66,0x60,0x6E,0x66,0x66,0x3E,0,0,0,0,0,0,0};
            case 'H','h' -> new byte[]{0x66,0x66,0x66,0x7E,0x66,0x66,0x66,0,0,0,0,0,0,0};
            case 'I','i' -> new byte[]{0x7E,0x18,0x18,0x18,0x18,0x18,0x7E,0,0,0,0,0,0,0};
            case 'J','j' -> new byte[]{0x06,0x06,0x06,0x06,0x06,0x66,0x3C,0,0,0,0,0,0,0};
            case 'K','k' -> new byte[]{0x66,0x6C,0x78,0x70,0x78,0x6C,0x66,0,0,0,0,0,0,0};
            case 'L','l' -> new byte[]{0x60,0x60,0x60,0x60,0x60,0x60,0x7E,0,0,0,0,0,0,0};
            case 'M','m' -> new byte[]{0x63,0x77,0x7F,0x6B,0x63,0x63,0x63,0,0,0,0,0,0,0};
            case 'N','n' -> new byte[]{0x66,0x76,0x7E,0x7E,0x6E,0x66,0x66,0,0,0,0,0,0,0};
            case 'O','o' -> new byte[]{0x3C,0x66,0x66,0x66,0x66,0x66,0x3C,0,0,0,0,0,0,0};
            case 'P','p' -> new byte[]{0x7C,0x66,0x66,0x7C,0x60,0x60,0x60,0,0,0,0,0,0,0};
            case 'Q','q' -> new byte[]{0x3C,0x66,0x66,0x66,0x6A,0x6C,0x36,0,0,0,0,0,0,0};
            case 'R','r' -> new byte[]{0x7C,0x66,0x66,0x7C,0x6C,0x66,0x66,0,0,0,0,0,0,0};
            case 'S','s' -> new byte[]{0x3C,0x66,0x60,0x3C,0x06,0x66,0x3C,0,0,0,0,0,0,0};
            case 'T','t' -> new byte[]{0x7E,0x18,0x18,0x18,0x18,0x18,0x18,0,0,0,0,0,0,0};
            case 'U','u' -> new byte[]{0x66,0x66,0x66,0x66,0x66,0x66,0x3C,0,0,0,0,0,0,0};
            case 'V','v' -> new byte[]{0x66,0x66,0x66,0x66,0x66,0x3C,0x18,0,0,0,0,0,0,0};
            case 'W','w' -> new byte[]{0x63,0x63,0x63,0x6B,0x7F,0x77,0x63,0,0,0,0,0,0,0};
            case 'X','x' -> new byte[]{0x66,0x66,0x3C,0x18,0x3C,0x66,0x66,0,0,0,0,0,0,0};
            case 'Y','y' -> new byte[]{0x66,0x66,0x66,0x3C,0x18,0x18,0x18,0,0,0,0,0,0,0};
            case 'Z','z' -> new byte[]{0x7E,0x06,0x0C,0x18,0x30,0x60,0x7E,0,0,0,0,0,0,0};
            case '.' -> new byte[]{0,0,0,0,0,0x18,0x18,0,0,0,0,0,0,0};
            case ',' -> new byte[]{0,0,0,0,0,0x18,0x18,0x30,0,0,0,0,0,0};
            case ':' -> new byte[]{0,0,0x18,0x18,0,0x18,0x18,0,0,0,0,0,0,0};
            case ';' -> new byte[]{0,0,0x18,0x18,0,0x18,0x18,0x30,0,0,0,0,0,0};
            case '-' -> new byte[]{0,0,0,0x7E,0,0,0,0,0,0,0,0,0,0};
            case '+' -> new byte[]{0,0,0x18,0x18,0x7E,0x18,0x18,0,0,0,0,0,0,0};
            case '=' -> new byte[]{0,0,0x7E,0,0x7E,0,0,0,0,0,0,0,0,0};
            case '(' -> new byte[]{0x0C,0x18,0x30,0x30,0x30,0x18,0x0C,0,0,0,0,0,0,0};
            case ')' -> new byte[]{0x30,0x18,0x0C,0x0C,0x0C,0x18,0x30,0,0,0,0,0,0,0};
            case '[' -> new byte[]{0x3C,0x30,0x30,0x30,0x30,0x30,0x3C,0,0,0,0,0,0,0};
            case ']' -> new byte[]{0x3C,0x0C,0x0C,0x0C,0x0C,0x0C,0x3C,0,0,0,0,0,0,0};
            case '/' -> new byte[]{0x02,0x06,0x0C,0x18,0x30,0x60,0x40,0,0,0,0,0,0,0};
            case '>' -> new byte[]{0x60,0x30,0x18,0x0C,0x18,0x30,0x60,0,0,0,0,0,0,0};
            case '<' -> new byte[]{0x06,0x0C,0x18,0x30,0x18,0x0C,0x06,0,0,0,0,0,0,0};
            case '!' -> new byte[]{0x18,0x18,0x18,0x18,0x18,0,0x18,0,0,0,0,0,0,0};
            case '?' -> new byte[]{0x3C,0x66,0x06,0x0C,0x18,0,0x18,0,0,0,0,0,0,0};
            case '%' -> new byte[]{0x62,0x64,0x08,0x10,0x20,0x4C,(byte)0x8C,0,0,0,0,0,0,0};
            case '*' -> new byte[]{0,0x66,0x3C,0x7E,0x3C,0x66,0,0,0,0,0,0,0,0};
            case '_' -> new byte[]{0,0,0,0,0,0,0x7E,0,0,0,0,0,0,0};
            case '#' -> new byte[]{0x24,0x24,0x7E,0x24,0x7E,0x24,0x24,0,0,0,0,0,0,0};
            case '@' -> new byte[]{0x3C,0x66,0x6E,0x6E,0x60,0x62,0x3C,0,0,0,0,0,0,0};
            case '|' -> new byte[]{0x18,0x18,0x18,0x18,0x18,0x18,0x18,0,0,0,0,0,0,0};
            default -> new byte[]{0x7E,0x42,0x42,0x42,0x42,0x42,0x7E,0,0,0,0,0,0,0}; // box
        };
    }

    public long image() { return image; }
    public long imageView() { return imageView; }
    public long sampler() { return sampler; }

    public void destroy(VkDevice device) {
        if (sampler != VK_NULL_HANDLE) vkDestroySampler(device, sampler, null);
        if (imageView != VK_NULL_HANDLE) vkDestroyImageView(device, imageView, null);
        if (image != VK_NULL_HANDLE) vkDestroyImage(device, image, null);
        if (imageMemory != VK_NULL_HANDLE) vkFreeMemory(device, imageMemory, null);
        sampler = imageView = image = imageMemory = VK_NULL_HANDLE;
    }

    // --- Vulkan resource creation helpers ---

    private long createImage(VkDevice device, long physicalDevice) {
        try (var stack = MemoryStack.stackPush()) {
            var imageInfo = org.lwjgl.vulkan.VkImageCreateInfo.calloc(stack)
                .sType$Default()
                .imageType(VK_IMAGE_TYPE_2D)
                .format(VK_FORMAT_R8_UNORM)
                .extent(e -> e.width(ATLAS_WIDTH).height(ATLAS_HEIGHT).depth(1))
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

            var pImage = stack.mallocLong(1);
            int result = vkCreateImage(device, imageInfo, null, pImage);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create font atlas image: " + result);
            return pImage.get(0);
        }
    }

    private long allocateAndBindMemory(VkDevice device, long physicalDevice, long image) {
        try (var stack = MemoryStack.stackPush()) {
            var memReqs = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, memReqs);

            var allocInfo = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .allocationSize(memReqs.size())
                .memoryTypeIndex(findMemoryType(device, physicalDevice, memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            var pMemory = stack.mallocLong(1);
            int result = vkAllocateMemory(device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate font atlas memory: " + result);
            long memory = pMemory.get(0);
            vkBindImageMemory(device, image, memory, 0);
            return memory;
        }
    }

    private void uploadBitmap(VkDevice device, long commandPool, long graphicsQueue, ByteBuffer bitmap) {
        long bufferSize = (long) ATLAS_WIDTH * ATLAS_HEIGHT;

        // Create staging buffer
        try (var stack = MemoryStack.stackPush()) {
            var bufInfo = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                .sType$Default()
                .size(bufferSize)
                .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            var pBuf = stack.mallocLong(1);
            vkCreateBuffer(device, bufInfo, null, pBuf);
            long stagingBuffer = pBuf.get(0);

            var memReqs = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, stagingBuffer, memReqs);

            var allocInfo = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .allocationSize(memReqs.size())
                .memoryTypeIndex(findMemoryType(device, null, memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));

            var pMem = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMem);
            long stagingMemory = pMem.get(0);
            vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0);

            // Map and copy
            var ppData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, bufferSize, 0, ppData);
            MemoryUtil.memCopy(MemoryUtil.memAddress(bitmap), ppData.get(0), bufferSize);
            vkUnmapMemory(device, stagingMemory);

            // Record copy command
            copyBufferToImage(device, commandPool, graphicsQueue, stagingBuffer, image,
                ATLAS_WIDTH, ATLAS_HEIGHT);

            // Cleanup staging
            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingMemory, null);
        }
    }

    private void copyBufferToImage(VkDevice device, long commandPool, long graphicsQueue,
                                    long buffer, long image, int width, int height) {
        try (var stack = MemoryStack.stackPush()) {
            var allocInfo = org.lwjgl.vulkan.VkCommandBufferAllocateInfo.calloc(stack)
                .sType$Default()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);

            var pCmd = stack.mallocPointer(1);
            vkAllocateCommandBuffers(device, allocInfo, pCmd);
            var cmd = new org.lwjgl.vulkan.VkCommandBuffer(pCmd.get(0), device);

            var beginInfo = org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack)
                .sType$Default()
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            vkBeginCommandBuffer(cmd, beginInfo);

            // Transition to TRANSFER_DST
            var barrier = org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(r -> r.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1))
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);

            // Copy
            var region = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack)
                .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0)
                .imageSubresource(s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1))
                .imageOffset(o -> o.set(0, 0, 0))
                .imageExtent(e -> e.set(width, height, 1));
            vkCmdCopyBufferToImage(cmd, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

            // Transition to SHADER_READ_ONLY
            barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);

            vkEndCommandBuffer(cmd);

            var submitInfo = org.lwjgl.vulkan.VkSubmitInfo.calloc(stack)
                .sType$Default()
                .pCommandBuffers(pCmd);
            vkQueueSubmit(new org.lwjgl.vulkan.VkQueue(graphicsQueue, device), submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(new org.lwjgl.vulkan.VkQueue(graphicsQueue, device));

            vkFreeCommandBuffers(device, commandPool, pCmd);
        }
    }

    private long createImageView(VkDevice device, long image) {
        try (var stack = MemoryStack.stackPush()) {
            var viewInfo = org.lwjgl.vulkan.VkImageViewCreateInfo.calloc(stack)
                .sType$Default()
                .image(image)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(VK_FORMAT_R8_UNORM)
                .subresourceRange(r -> r.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1));

            var pView = stack.mallocLong(1);
            vkCreateImageView(device, viewInfo, null, pView);
            return pView.get(0);
        }
    }

    private long createSampler(VkDevice device) {
        try (var stack = MemoryStack.stackPush()) {
            var samplerInfo = org.lwjgl.vulkan.VkSamplerCreateInfo.calloc(stack)
                .sType$Default()
                .magFilter(VK_FILTER_NEAREST)
                .minFilter(VK_FILTER_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .anisotropyEnable(false)
                .maxAnisotropy(1f)
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false)
                .compareEnable(false)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .mipLodBias(0f)
                .minLod(0f)
                .maxLod(0f);

            var pSampler = stack.mallocLong(1);
            vkCreateSampler(device, samplerInfo, null, pSampler);
            return pSampler.get(0);
        }
    }

    private int findMemoryType(VkDevice device, Object physicalDeviceIgnored,
                                int typeFilter, int properties) {
        try (var stack = MemoryStack.stackPush()) {
            var memProps = org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(device.getPhysicalDevice(), memProps);

            for (int i = 0; i < memProps.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0 &&
                    (memProps.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }
            throw new RuntimeException("Failed to find suitable memory type");
        }
    }
}
