package org.dynamislight.spi.render;

import java.util.List;

/**
 * Minimal declaration for feature participation within a global render phase.
 *
 * @param featureId stable feature identifier
 * @param phase phase the feature participates in
 * @param runsBefore optional feature ids this feature should run before within the same phase
 * @param runsAfter optional feature ids this feature should run after within the same phase
 */
public record RenderPhaseParticipation(
        String featureId,
        RenderPassPhase phase,
        List<String> runsBefore,
        List<String> runsAfter
) {
    public RenderPhaseParticipation {
        featureId = featureId == null ? "" : featureId.trim();
        phase = phase == null ? RenderPassPhase.AUXILIARY : phase;
        runsBefore = runsBefore == null ? List.of() : List.copyOf(runsBefore);
        runsAfter = runsAfter == null ? List.of() : List.copyOf(runsAfter);
    }
}
