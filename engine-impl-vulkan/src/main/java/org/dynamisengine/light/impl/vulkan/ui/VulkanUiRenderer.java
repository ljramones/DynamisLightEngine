package org.dynamisengine.light.impl.vulkan.ui;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.logging.Logger;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Vulkan-first UI rendering subsystem.
 *
 * <p>This is the canonical UI rendering path for the Dynamis engine.
 * It provides batched 2D rendering of quads (panels, glyphs), lines
 * (sparklines, borders), and text (bitmap font atlas).
 *
 * <p>The debug overlay is the first client; future clients include
 * the game UI system, editor overlays, and tooling.
 *
 * <h3>Frame flow:</h3>
 * <pre>
 *   renderer.beginFrame(cmd, width, height, swapchainImageView)
 *   // ... accumulate draw calls via batch ...
 *   renderer.drawQuad(...)
 *   renderer.drawLine(...)
 *   renderer.drawText(...)
 *   renderer.endFrame()
 * </pre>
 *
 * <p>All draw data is accumulated CPU-side in {@link VulkanUiBatch},
 * uploaded once, and drawn with minimal Vulkan calls.
 */
public final class VulkanUiRenderer {

    private static final Logger LOG = Logger.getLogger(VulkanUiRenderer.class.getName());

    private final VulkanFontAtlas fontAtlas = new VulkanFontAtlas();
    private final VulkanUiPipeline pipeline = new VulkanUiPipeline();
    private VulkanUiFrameContext frameContext;
    private VulkanUiBatch batch;

    // Per-frame state
    private VkCommandBuffer currentCmd;
    private int screenWidth, screenHeight;
    private long currentFramebuffer = VK_NULL_HANDLE;
    private boolean initialized;

    // Reusable framebuffers per swapchain image
    private long[] framebuffers;

    /**
     * Initialize the UI subsystem. Call once after Vulkan device is ready.
     */
    public void initialize(VkDevice device, long physicalDevice, long commandPool,
                            long graphicsQueue, int swapchainFormat, long[] swapchainImageViews) {
        fontAtlas.initialize(device, physicalDevice, commandPool, graphicsQueue);
        pipeline.initialize(device, swapchainFormat, fontAtlas);
        frameContext = new VulkanUiFrameContext(device);
        frameContext.initialize();
        batch = new VulkanUiBatch(fontAtlas);

        // Create framebuffers for each swapchain image
        framebuffers = new long[swapchainImageViews.length];
        for (int i = 0; i < swapchainImageViews.length; i++) {
            framebuffers[i] = createFramebuffer(device, swapchainImageViews[i], 1, 1);
        }

        initialized = true;
        LOG.info("VulkanUiRenderer initialized with " + swapchainImageViews.length + " swapchain images");
    }

    /**
     * Recreate framebuffers after swapchain resize.
     */
    public void recreateFramebuffers(VkDevice device, long[] swapchainImageViews,
                                      int width, int height) {
        destroyFramebuffers(device);
        framebuffers = new long[swapchainImageViews.length];
        for (int i = 0; i < swapchainImageViews.length; i++) {
            framebuffers[i] = createFramebuffer(device, swapchainImageViews[i], width, height);
        }
    }

    /**
     * Begin a new UI frame. Call after the main scene has been rendered.
     *
     * @param cmd         active command buffer
     * @param width       framebuffer width
     * @param height      framebuffer height
     * @param imageIndex  swapchain image index
     */
    public void beginFrame(VkCommandBuffer cmd, int width, int height, int imageIndex) {
        if (!initialized) return;

        currentCmd = cmd;
        screenWidth = width;
        screenHeight = height;
        batch.clear();

        if (imageIndex >= 0 && imageIndex < framebuffers.length) {
            currentFramebuffer = framebuffers[imageIndex];
        }
    }

    // --- Draw API ---

    /** Draw a solid colored rectangle. */
    public void drawQuad(float x, float y, float w, float h, int color) {
        batch.addSolidQuad(x, y, w, h, color);
    }

    /** Draw a textured quad. */
    public void drawTexturedQuad(float x, float y, float w, float h,
                                  float u0, float v0, float u1, float v1, int color) {
        batch.addTexturedQuad(x, y, w, h, u0, v0, u1, v1, color);
    }

    /** Draw a line segment. */
    public void drawLine(float x0, float y0, float x1, float y1, int color) {
        batch.addLine(x0, y0, x1, y1, color);
    }

    /** Draw text at screen coordinates. */
    public void drawText(String text, float x, float y, float scale, int color) {
        batch.addText(text, x, y, scale, color);
    }

    /**
     * End the UI frame: upload batched data, record Vulkan draw commands.
     */
    public void endFrame() {
        if (!initialized || currentCmd == null) return;
        if (batch.quads().vertexCount() == 0 && batch.lines().vertexCount() == 0) return;
        if (currentFramebuffer == VK_NULL_HANDLE) return;

        // Upload batch data to GPU
        frameContext.upload(batch);

        try (var stack = stackPush()) {
            // Begin UI render pass
            var clearValues = VkClearValue.calloc(0, stack); // LOAD_OP_LOAD, no clear needed
            var renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType$Default()
                .renderPass(pipeline.renderPass())
                .framebuffer(currentFramebuffer)
                .renderArea(a -> a.offset(o -> o.set(0, 0)).extent(e -> e.set(screenWidth, screenHeight)));

            vkCmdBeginRenderPass(currentCmd, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

            // Set dynamic viewport and scissor
            var viewport = VkViewport.calloc(1, stack)
                .x(0).y(0)
                .width(screenWidth).height(screenHeight)
                .minDepth(0f).maxDepth(1f);
            vkCmdSetViewport(currentCmd, 0, viewport);

            var scissor = VkRect2D.calloc(1, stack)
                .offset(o -> o.set(0, 0))
                .extent(e -> e.set(screenWidth, screenHeight));
            vkCmdSetScissor(currentCmd, 0, scissor);

            // Push screen size constant
            var pushData = stack.mallocFloat(2);
            pushData.put(0, (float) screenWidth);
            pushData.put(1, (float) screenHeight);
            vkCmdPushConstants(currentCmd, pipeline.pipelineLayout(),
                VK_SHADER_STAGE_VERTEX_BIT, 0, pushData);

            // Draw quads (panels + text glyphs)
            if (batch.quads().vertexCount() > 0) {
                vkCmdBindPipeline(currentCmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.quadPipeline());

                var pDescSets = stack.mallocLong(1).put(0, pipeline.descriptorSet());
                vkCmdBindDescriptorSets(currentCmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.pipelineLayout(), 0, pDescSets, null);

                var pVertBuffers = stack.mallocLong(1).put(0, frameContext.quadVertexBuffer());
                var pOffsets = stack.mallocLong(1).put(0, 0L);
                vkCmdBindVertexBuffers(currentCmd, 0, pVertBuffers, pOffsets);
                vkCmdBindIndexBuffer(currentCmd, frameContext.quadIndexBuffer(), 0, VK_INDEX_TYPE_UINT32);

                vkCmdDrawIndexed(currentCmd, batch.quads().indexCount(), 1, 0, 0, 0);
            }

            // Draw lines (sparklines, borders)
            if (batch.lines().vertexCount() > 0) {
                vkCmdBindPipeline(currentCmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.linePipeline());

                // Push constants again for line pipeline
                vkCmdPushConstants(currentCmd, pipeline.pipelineLayout(),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushData);

                var pVertBuffers = stack.mallocLong(1).put(0, frameContext.lineVertexBuffer());
                var pOffsets = stack.mallocLong(1).put(0, 0L);
                vkCmdBindVertexBuffers(currentCmd, 0, pVertBuffers, pOffsets);

                vkCmdDraw(currentCmd, batch.lines().vertexCount(), 1, 0, 0);
            }

            vkCmdEndRenderPass(currentCmd);
        }

        currentCmd = null;
    }

    public void destroy(VkDevice device) {
        if (frameContext != null) frameContext.destroy();
        pipeline.destroy(device);
        fontAtlas.destroy(device);
        destroyFramebuffers(device);
        initialized = false;
    }

    public boolean isInitialized() { return initialized; }
    public VulkanFontAtlas fontAtlas() { return fontAtlas; }
    public int screenWidth() { return screenWidth; }
    public int screenHeight() { return screenHeight; }

    // --- Framebuffer management ---

    private long createFramebuffer(VkDevice device, long imageView, int width, int height) {
        try (var stack = stackPush()) {
            var pAttachments = stack.mallocLong(1).put(0, imageView);

            var fbInfo = VkFramebufferCreateInfo.calloc(stack)
                .sType$Default()
                .renderPass(pipeline.renderPass())
                .pAttachments(pAttachments)
                .width(width)
                .height(height)
                .layers(1);

            var pFb = stack.mallocLong(1);
            int result = vkCreateFramebuffer(device, fbInfo, null, pFb);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create UI framebuffer: " + result);
            return pFb.get(0);
        }
    }

    private void destroyFramebuffers(VkDevice device) {
        if (framebuffers != null) {
            for (long fb : framebuffers) {
                if (fb != VK_NULL_HANDLE) vkDestroyFramebuffer(device, fb, null);
            }
            framebuffers = null;
        }
    }
}
