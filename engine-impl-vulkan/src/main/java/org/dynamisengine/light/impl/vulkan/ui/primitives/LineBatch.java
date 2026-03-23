package org.dynamisengine.light.impl.vulkan.ui.primitives;

import org.dynamisengine.light.impl.vulkan.ui.UiLineVertex;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * CPU-side accumulator for line vertices. Collected per frame, uploaded once.
 * Lines are rendered as line-list (2 vertices per segment).
 */
public final class LineBatch {

    private final List<UiLineVertex> vertices = new ArrayList<>(512);

    public void clear() {
        vertices.clear();
    }

    /** Add a line segment. */
    public void addLine(float x0, float y0, float x1, float y1, int color) {
        vertices.add(new UiLineVertex(x0, y0, color));
        vertices.add(new UiLineVertex(x1, y1, color));
    }

    public int vertexCount() { return vertices.size(); }

    public void writeTo(ByteBuffer buf) {
        for (var v : vertices) v.writeTo(buf);
    }
}
