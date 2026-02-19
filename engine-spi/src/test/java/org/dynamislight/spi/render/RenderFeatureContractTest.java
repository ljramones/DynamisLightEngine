package org.dynamislight.spi.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderFeatureContractTest {
    @Test
    void usesDefensiveCopiesForCollectionFields() {
        List<RenderPassContribution> passes = new ArrayList<>();
        passes.add(new RenderPassContribution("main", RenderPassPhase.MAIN, List.of("depth"), List.of("color"), false));
        RenderFeatureContract contract = new RenderFeatureContract(
                "reflections",
                "v1",
                passes,
                List.of(),
                List.of(),
                List.of()
        );

        passes.clear();

        assertEquals(1, contract.passContributions().size());
        assertThrows(UnsupportedOperationException.class,
                () -> contract.passContributions().add(new RenderPassContribution("", RenderPassPhase.AUXILIARY, List.of(), List.of(), true)));
    }

    @Test
    void capabilityFeatureIdDefaultsToContractId() {
        RenderFeatureCapability capability = () -> new RenderFeatureContract(
                "shadows",
                "v1",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertEquals("shadows", capability.featureId());
    }
}
