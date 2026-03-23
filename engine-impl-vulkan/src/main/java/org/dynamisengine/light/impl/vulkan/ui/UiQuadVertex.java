package org.dynamisengine.light.impl.vulkan.ui;

import java.nio.ByteBuffer;

/**
 * Vertex for UI quad rendering (panels, glyph quads).
 *
 * <p>Layout: x, y (screen-space), u, v (texture coords), color (packed ABGR).
 * Total: 20 bytes per vertex.
 */
public record UiQuadVertex(float x, float y, float u, float v, int color) {

    public static final int BYTES = 4 + 4 + 4 + 4 + 4; // 20 bytes

    /** Pack RGBA (0-255 each) into a single int (ABGR for Vulkan). */
    public static int packColor(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /** Pack float RGBA (0-1 each) into a single int. */
    public static int packColor(float r, float g, float b, float a) {
        return packColor(
            (int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));
    }

    public void writeTo(ByteBuffer buf) {
        buf.putFloat(x).putFloat(y).putFloat(u).putFloat(v).putInt(color);
    }
}
