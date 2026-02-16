package org.dynamislight.impl.common.framegraph;

import java.util.Set;

/**
 * A render pass node with optional dependencies.
 */
public interface FrameGraphPass {
    String id();

    Set<String> dependsOn();

    default Set<String> reads() {
        return Set.of();
    }

    default Set<String> writes() {
        return Set.of();
    }

    void execute();
}
