package org.dynamisengine.light.spi.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderPhaseContractTest {
    @Test
    void defaultsToDeclaredGlobalPhaseOrder() {
        RenderPhaseContract contract = new RenderPhaseContract(null, null);
        assertEquals(List.of(RenderPassPhase.values()), contract.phaseOrder());
        assertEquals(List.of(), contract.participations());
    }

    @Test
    void usesDefensiveCopiesForCollections() {
        List<RenderPassPhase> order = new ArrayList<>();
        order.add(RenderPassPhase.MAIN);

        List<RenderPhaseParticipation> participation = new ArrayList<>();
        participation.add(new RenderPhaseParticipation(
                "terrain",
                RenderPassPhase.MAIN,
                List.of("sky"),
                List.of()
        ));

        RenderPhaseContract contract = new RenderPhaseContract(order, participation);
        order.clear();
        participation.clear();

        assertEquals(1, contract.phaseOrder().size());
        assertEquals(1, contract.participations().size());
        assertThrows(UnsupportedOperationException.class, () -> contract.phaseOrder().add(RenderPassPhase.POST_MAIN));
        assertThrows(UnsupportedOperationException.class,
                () -> contract.participations().add(new RenderPhaseParticipation("vfx", RenderPassPhase.AUXILIARY, List.of(), List.of())));
    }

    @Test
    void interpretsFeaturePhaseUsingParticipationContract() {
        RenderPhaseContract contract = new RenderPhaseContract(
                List.of(RenderPassPhase.PRE_MAIN, RenderPassPhase.MAIN, RenderPassPhase.POST_MAIN),
                List.of(new RenderPhaseParticipation("sky", RenderPassPhase.POST_MAIN, List.of(), List.of()))
        );

        assertEquals(RenderPassPhase.POST_MAIN, contract.interpretedPhaseFor("sky", RenderPassPhase.MAIN));
        assertEquals(RenderPassPhase.MAIN, contract.interpretedPhaseFor("terrain", RenderPassPhase.MAIN));
    }
}
