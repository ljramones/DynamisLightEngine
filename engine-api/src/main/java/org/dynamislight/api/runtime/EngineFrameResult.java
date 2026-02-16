package org.dynamislight.api.runtime;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;

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
