package org.dynamislight.spi.render;

import java.util.Arrays;
import java.util.List;

/**
 * Minimal global phase contract declaration for render orchestration.
 *
 * This is intentionally contract-only for A1 and does not change scheduler behavior.
 *
 * @param phaseOrder declared global phase order
 * @param participations feature participation declarations
 */
public record RenderPhaseContract(
        List<RenderPassPhase> phaseOrder,
        List<RenderPhaseParticipation> participations
) {
    public RenderPhaseContract {
        phaseOrder = (phaseOrder == null || phaseOrder.isEmpty())
                ? List.copyOf(Arrays.asList(RenderPassPhase.values()))
                : List.copyOf(phaseOrder);
        participations = participations == null ? List.of() : List.copyOf(participations);
    }
}
