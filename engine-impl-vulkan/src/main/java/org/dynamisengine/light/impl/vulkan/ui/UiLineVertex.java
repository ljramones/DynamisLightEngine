package org.dynamisengine.light.impl.vulkan.ui;

import java.nio.ByteBuffer;

/**
 * Vertex for UI line rendering (sparklines, axes, borders).
 *
 * <p>Layout: x, y (screen-space), color (packed ABGR).
 * Total: 12 bytes per vertex.
 */
public record UiLineVertex(float x, float y, int color) {

    public static final int BYTES = 4 + 4 + 4; // 12 bytes

    public void writeTo(ByteBuffer buf) {
        buf.putFloat(x).putFloat(y).putInt(color);
    }
}
