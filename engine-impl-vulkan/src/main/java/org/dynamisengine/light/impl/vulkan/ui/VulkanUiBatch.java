package org.dynamisengine.light.impl.vulkan.ui;

import org.dynamisengine.light.impl.vulkan.ui.primitives.LineBatch;
import org.dynamisengine.light.impl.vulkan.ui.primitives.QuadBatch;
import org.dynamisengine.light.impl.vulkan.ui.primitives.TextBatch;

/**
 * Per-frame UI draw data accumulator. Collects all quads, lines, and text
 * during a frame, then uploads and draws once in {@code endFrame()}.
 *
 * <p>No GPU interaction during accumulation — all data is CPU-side.
 */
public final class VulkanUiBatch {

    private final QuadBatch quads = new QuadBatch();
    private final LineBatch lines = new LineBatch();
    private final TextBatch text;

    public VulkanUiBatch(VulkanFontAtlas fontAtlas) {
        this.text = new TextBatch(fontAtlas);
    }

    public void clear() {
        quads.clear();
        lines.clear();
    }

    // --- Quad API ---

    public void addSolidQuad(float x, float y, float w, float h, int color) {
        quads.addSolidQuad(x, y, w, h, color);
    }

    public void addTexturedQuad(float x, float y, float w, float h,
                                 float u0, float v0, float u1, float v1, int color) {
        quads.addQuad(x, y, w, h, u0, v0, u1, v1, color);
    }

    // --- Line API ---

    public void addLine(float x0, float y0, float x1, float y1, int color) {
        lines.addLine(x0, y0, x1, y1, color);
    }

    // --- Text API ---

    public void addText(String text, float x, float y, float scale, int color) {
        this.text.addText(quads, text, x, y, scale, color);
    }

    // --- Accessors for upload ---

    public QuadBatch quads() { return quads; }
    public LineBatch lines() { return lines; }
}
