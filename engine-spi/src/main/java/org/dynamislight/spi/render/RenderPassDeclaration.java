package org.dynamislight.spi.render;

import java.util.List;

/**
 * v2 pass declaration with conditional/dynamic and scoped dependency metadata.
 *
 * @param passId stable pass identifier
 * @param phase logical phase
 * @param reads resources read by pass
 * @param writes resources written by pass
 * @param conditional pass may be omitted based on active mode/runtime state
 * @param dynamicPassCount pass count can vary frame-to-frame
 * @param internalBarrierSequence pass owns internal GPU barriers hidden from graph
 * @param requiredFeatureScopes capabilities required within this pass scope
 */
public record RenderPassDeclaration(
        String passId,
        RenderPassPhase phase,
        List<String> reads,
        List<String> writes,
        boolean conditional,
        boolean dynamicPassCount,
        boolean internalBarrierSequence,
        List<String> requiredFeatureScopes
) {
    public RenderPassDeclaration {
        passId = passId == null ? "" : passId.trim();
        phase = phase == null ? RenderPassPhase.AUXILIARY : phase;
        reads = reads == null ? List.of() : List.copyOf(reads);
        writes = writes == null ? List.of() : List.copyOf(writes);
        requiredFeatureScopes = requiredFeatureScopes == null ? List.of() : List.copyOf(requiredFeatureScopes);
    }
}
