package org.dynamisengine.light.impl.vulkan.ui.primitives;

import org.dynamisengine.light.impl.vulkan.ui.VulkanFontAtlas;

/**
 * Converts text strings into glyph quads added to a {@link QuadBatch}.
 * Uses a bitmap {@link VulkanFontAtlas} for glyph UV lookup.
 */
public final class TextBatch {

    private final VulkanFontAtlas fontAtlas;

    public TextBatch(VulkanFontAtlas fontAtlas) {
        this.fontAtlas = fontAtlas;
    }

    /**
     * Render text as glyph quads into the given quad batch.
     *
     * @param batch  quad batch to accumulate into
     * @param text   the string to render
     * @param x      screen x position
     * @param y      screen y position
     * @param scale  character size multiplier
     * @param color  packed ABGR color
     */
    public void addText(QuadBatch batch, String text, float x, float y, float scale, int color) {
        if (text == null || text.isEmpty()) return;

        float cursorX = x;
        float charW = fontAtlas.charWidth() * scale;
        float charH = fontAtlas.charHeight() * scale;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 || c > 126) c = '?'; // ASCII only

            var uv = fontAtlas.glyphUV(c);
            batch.addQuad(cursorX, y, charW, charH,
                uv.u0(), uv.v0(), uv.u1(), uv.v1(), color);
            cursorX += charW;
        }
    }
}
