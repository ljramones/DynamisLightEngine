package org.dynamislight.impl.common.framegraph;

/**
 * Executes an immutable frame graph in dependency order.
 */
public final class FrameGraphExecutor {
    public void execute(FrameGraph graph) {
        for (FrameGraphPass pass : graph.orderedPasses()) {
            pass.execute();
        }
    }
}
