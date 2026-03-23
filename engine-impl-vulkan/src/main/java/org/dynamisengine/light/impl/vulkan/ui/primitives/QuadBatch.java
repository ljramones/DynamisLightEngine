package org.dynamisengine.light.impl.vulkan.ui.primitives;

import org.dynamisengine.light.impl.vulkan.ui.UiQuadVertex;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * CPU-side accumulator for quad vertices. Collected per frame, uploaded once.
 * Each quad is 4 vertices + 6 indices (2 triangles).
 */
public final class QuadBatch {

    private final List<UiQuadVertex> vertices = new ArrayList<>(1024);
    private int quadCount = 0;

    public void clear() {
        vertices.clear();
        quadCount = 0;
    }

    /**
     * Add a textured/colored quad.
     * Vertices: top-left, top-right, bottom-right, bottom-left.
     */
    public void addQuad(float x, float y, float w, float h,
                         float u0, float v0, float u1, float v1,
                         int color) {
        vertices.add(new UiQuadVertex(x, y, u0, v0, color));         // TL
        vertices.add(new UiQuadVertex(x + w, y, u1, v0, color));     // TR
        vertices.add(new UiQuadVertex(x + w, y + h, u1, v1, color)); // BR
        vertices.add(new UiQuadVertex(x, y + h, u0, v1, color));     // BL
        quadCount++;
    }

    /** Add a solid-color quad (UV = 0,0 → white texel). */
    public void addSolidQuad(float x, float y, float w, float h, int color) {
        addQuad(x, y, w, h, 0f, 0f, 0f, 0f, color);
    }

    public int vertexCount() { return vertices.size(); }
    public int quadCount() { return quadCount; }
    public int indexCount() { return quadCount * 6; }

    /** Write all vertices to a direct ByteBuffer. Buffer must have capacity for vertexCount * BYTES. */
    public void writeTo(ByteBuffer buf) {
        for (var v : vertices) v.writeTo(buf);
    }

    /** Build index buffer for quad→triangle conversion. */
    public void writeIndicesTo(ByteBuffer buf) {
        for (int q = 0; q < quadCount; q++) {
            int base = q * 4;
            buf.putInt(base).putInt(base + 1).putInt(base + 2);
            buf.putInt(base).putInt(base + 2).putInt(base + 3);
        }
    }
}
