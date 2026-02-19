package org.dynamislight.spi.render;

import java.util.List;

/**
 * Pass-level capability contribution declaration.
 *
 * @param passId stable pass identifier
 * @param phase pass execution phase
 * @param reads named resources read by this pass
 * @param writes named resources written by this pass
 * @param optional whether pass is optional (profile/config dependent)
 */
public record RenderPassContribution(
        String passId,
        RenderPassPhase phase,
        List<String> reads,
        List<String> writes,
        boolean optional
) {
    public RenderPassContribution {
        passId = passId == null ? "" : passId.trim();
        phase = phase == null ? RenderPassPhase.AUXILIARY : phase;
        reads = reads == null ? List.of() : List.copyOf(reads);
        writes = writes == null ? List.of() : List.copyOf(writes);
    }
}
