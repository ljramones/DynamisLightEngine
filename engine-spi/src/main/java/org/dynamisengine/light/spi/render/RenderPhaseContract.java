package org.dynamisengine.light.spi.render;

import java.util.Arrays;
import java.util.List;

/**
 * Minimal global phase contract declaration for render orchestration.
 *
 * This is intentionally contract-only for tightening slices and does not
 * directly change scheduler behavior.
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

    /**
     * Interprets the effective phase for a feature using LightEngine-owned
     * phase participation declarations when available.
     */
    public RenderPassPhase interpretedPhaseFor(String featureId, RenderPassPhase declaredPhase) {
        String normalizedFeatureId = featureId == null ? "" : featureId.trim();
        RenderPassPhase safeDeclared = declaredPhase == null ? RenderPassPhase.AUXILIARY : declaredPhase;

        for (RenderPhaseParticipation participation : participations) {
            if (participation == null) {
                continue;
            }
            if (normalizedFeatureId.equals(participation.featureId())) {
                return participation.phase();
            }
        }
        return safeDeclared;
    }
}
