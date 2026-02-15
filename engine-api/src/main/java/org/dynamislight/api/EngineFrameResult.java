package org.dynamislight.api;

import java.util.List;

/**
 * EngineFrameResult API type.
 */
public record EngineFrameResult(
        long frameIndex,
        double cpuFrameMs,
        double gpuFrameMs,
        FrameHandle frameHandle,
        List<EngineWarning> warnings
) {
    public EngineFrameResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
