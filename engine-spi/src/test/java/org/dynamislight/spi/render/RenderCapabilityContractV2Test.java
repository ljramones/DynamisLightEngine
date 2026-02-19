package org.dynamislight.spi.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.junit.jupiter.api.Test;

class RenderCapabilityContractV2Test {
    @Test
    void contractUsesDefensiveCopies() {
        List<RenderPassDeclaration> passes = new ArrayList<>();
        passes.add(new RenderPassDeclaration(
                "main",
                RenderPassPhase.MAIN,
                List.of("depth"),
                List.of("color"),
                false,
                false,
                false,
                List.of()
        ));
        RenderCapabilityContractV2 contract = new RenderCapabilityContractV2(
                "feature",
                new RenderFeatureMode("default"),
                passes,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new RenderTelemetryDeclaration(List.of(), List.of(), List.of(), List.of())
        );

        passes.clear();

        assertEquals(1, contract.passes().size());
        assertThrows(UnsupportedOperationException.class, () -> contract.passes().add(new RenderPassDeclaration(
                "x", RenderPassPhase.AUXILIARY, List.of(), List.of(), true, false, false, List.of()
        )));
    }

    @Test
    void defaultContractBuilderAggregatesFromInterface() {
        RenderFeatureCapabilityV2 capability = new RenderFeatureCapabilityV2() {
            @Override
            public String featureId() {
                return "test.feature";
            }

            @Override
            public List<RenderFeatureMode> supportedModes() {
                return List.of(new RenderFeatureMode("default"));
            }

            @Override
            public RenderFeatureMode activeMode() {
                return new RenderFeatureMode("default");
            }

            @Override
            public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
                return List.of(new RenderDescriptorRequirement("main", 0, 0, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false));
            }

            @Override
            public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
                return List.of(new RenderUniformRequirement("global", "x", 0, 16));
            }

            @Override
            public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
                return List.of(new RenderPushConstantRequirement("main", List.of(RenderShaderStage.FRAGMENT), 0, 16));
            }

            @Override
            public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
                return List.of(new RenderResourceDeclaration("history", RenderResourceType.SAMPLED_IMAGE, RenderResourceLifecycle.CROSS_FRAME_TEMPORAL, false, List.of("reset")));
            }

            @Override
            public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
                return List.of(new RenderSchedulerDeclaration("sched", List.of(), false, true));
            }

            @Override
            public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
                return new RenderTelemetryDeclaration(List.of("WARN"), List.of("diag"), List.of(), List.of("gate"));
            }

            @Override
            public List<RenderPassDeclaration> declarePasses(QualityTier tier, RenderFeatureMode mode) {
                return List.of(new RenderPassDeclaration("main", RenderPassPhase.MAIN, List.of(), List.of("color"), false, false, false, List.of()));
            }

            @Override
            public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
                return List.of(new RenderShaderContribution(
                        "main",
                        RenderShaderInjectionPoint.LIGHTING_EVAL,
                        RenderShaderStage.FRAGMENT,
                        "impl",
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        false,
                        0,
                        false
                ));
            }
        };

        RenderCapabilityContractV2 contract = capability.contractV2(QualityTier.HIGH);

        assertEquals("test.feature", contract.featureId());
        assertEquals("default", contract.mode().id());
        assertFalse(contract.passes().isEmpty());
        assertFalse(contract.shaderContributions().isEmpty());
        assertFalse(contract.descriptorRequirements().isEmpty());
    }
}
