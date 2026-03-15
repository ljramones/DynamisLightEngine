package org.dynamisengine.light.impl.common.framegraph;

import java.util.List;

/**
 * Immutable frame graph execution plan.
 */
public record FrameGraph(List<FrameGraphPass> orderedPasses) {
}
