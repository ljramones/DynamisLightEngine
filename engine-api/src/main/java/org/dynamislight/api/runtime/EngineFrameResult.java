package org.dynamislight.api.runtime;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;

/**
 * Represents the result of rendering or updating a single frame in the engine runtime.
 *
 * This record is used to capture detailed information about a frame, including:
 * - The index of the frame in the sequence of frames processed by the engine.
 * - Timing data for the CPU and GPU processing times of the frame, measured in milliseconds.
 * - A handle to the frame, which is an opaque object that can be used internally by the engine.
 * - A list of warnings that occurred during the processing of the frame, providing diagnostic
 *   or informational details.
 *
 * Instances of this record are immutable and are intended to be thread-safe for use across
 * different components managing engine states and frame-related operations.
 *
 * @param frameIndex   The sequential index of the frame, indicating its order in the rendering process.
 * @param cpuFrameMs   The time, in milliseconds, taken to process the frame on the CPU.
 * @param gpuFrameMs   The time, in milliseconds, taken to process the frame on the GPU.
 * @param frameHandle  An opaque handle representing the processed frame and its associated resources.
 * @param warnings     A list of engine warnings associated with this frame. An empty list is used if
 *                     no warnings were generated.
 */
public record EngineFrameResult(
        long frameIndex,
        double cpuFrameMs,
        double gpuFrameMs,
        FrameHandle frameHandle,
        List<EngineWarning> warnings
) {
    /**
     * Constructs an instance of the {@code EngineFrameResult} record, ensuring immutability and safe handling of
     * the warnings list. If the provided warnings list is {@code null}, an empty immutable list is assigned
     * instead. Otherwise, a copy of the provided list is used to maintain immutability.
     *
     * @param frameIndex   The sequential index of the frame, indicating its order in the rendering process.
     * @param cpuFrameMs   The time, in milliseconds, taken to process the frame on the CPU.
     * @param gpuFrameMs   The time, in milliseconds, taken to process the frame on the GPU.
     * @param frameHandle  An opaque handle representing the processed frame and its associated resources.
     * @param warnings     A list of engine warnings associated with this frame. If the input is {@code null},
     *                     it is replaced with an empty, immutable list.
     */
    public EngineFrameResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
