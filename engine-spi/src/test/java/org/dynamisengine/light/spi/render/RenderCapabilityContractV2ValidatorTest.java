package org.dynamisengine.light.spi.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamisengine.light.api.config.QualityTier;
import org.junit.jupiter.api.Test;

class RenderCapabilityContractV2ValidatorTest {
    @Test
    void brokenDescriptorsTriggerAllExpectedIssueTypes() {
        RenderFeatureCapabilityV2 featureA = new BrokenCapability("feature.a", "resource.shared", "missing.scope");
        RenderFeatureCapabilityV2 featureB = new BrokenCapability("feature.b", "resource.shared", "missing.scope");

        List<RenderCapabilityValidationIssue> issues = RenderCapabilityContractV2Validator.validate(
                List.of(featureA, featureB),
                QualityTier.HIGH
        );

        assertIssue(issues, "DESCRIPTOR_BINDING_COLLISION", RenderCapabilityValidationIssue.Severity.ERROR);
        assertIssue(issues, "RESOURCE_NAME_COLLISION", RenderCapabilityValidationIssue.Severity.ERROR);
        assertIssue(issues, "PUSH_CONSTANT_LAYOUT_CONFLICT", RenderCapabilityValidationIssue.Severity.ERROR);
        assertIssue(issues, "UNRESOLVED_FEATURE_SCOPE_DEPENDENCY", RenderCapabilityValidationIssue.Severity.ERROR);
        assertIssue(issues, "SHADER_INJECTION_POINT_CONFLICT", RenderCapabilityValidationIssue.Severity.WARNING);
        assertTrue(RenderCapabilityContractV2Validator.hasErrors(issues));
    }

    @Test
    void phaseParticipationContractIsAuthoritativeForPhaseInterpretation() {
        RenderFeatureCapabilityV2 feature = new PhaseCapability("feature.sky", RenderPassPhase.MAIN);

        RenderPhaseContract phaseContract = new RenderPhaseContract(
                List.of(RenderPassPhase.PRE_MAIN, RenderPassPhase.MAIN, RenderPassPhase.POST_MAIN),
                List.of(new RenderPhaseParticipation("feature.sky", RenderPassPhase.POST_MAIN, List.of(), List.of("feature.unknown")))
        );

        List<RenderCapabilityValidationIssue> issues = RenderCapabilityContractV2Validator.validate(
                List.of(feature),
                QualityTier.HIGH,
                phaseContract
        );

        assertIssue(issues, "FEATURE_PHASE_DECLARATION_MISMATCH", RenderCapabilityValidationIssue.Severity.WARNING);
        assertIssue(issues, "FEATURE_PHASE_ORDER_REFERENCE_UNRESOLVED", RenderCapabilityValidationIssue.Severity.WARNING);
    }

    private static void assertIssue(
            List<RenderCapabilityValidationIssue> issues,
            String code,
            RenderCapabilityValidationIssue.Severity severity
    ) {
        long matches = issues.stream()
                .filter(issue -> code.equals(issue.code()) && severity == issue.severity())
                .count();
        assertTrue(matches >= 1L, "expected at least one issue for code=" + code + ", got issues=" + issues);
    }

    private record BrokenCapability(String id, String ownedResourceName, String missingScope)
            implements RenderFeatureCapabilityV2 {

        @Override
        public String featureId() {
            return id;
        }

        @Override
        public List<RenderFeatureMode> supportedModes() {
            return List.of(new RenderFeatureMode("broken"));
        }

        @Override
        public RenderFeatureMode activeMode() {
            return new RenderFeatureMode("broken");
        }

        @Override
        public List<RenderPassDeclaration> declarePasses(QualityTier tier, RenderFeatureMode mode) {
            return List.of(new RenderPassDeclaration(
                    "main",
                    RenderPassPhase.MAIN,
                    List.of(),
                    List.of("color"),
                    false,
                    false,
                    false,
                    List.of(missingScope)
            ));
        }

        @Override
        public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
            return List.of(new RenderShaderContribution(
                    "main",
                    RenderShaderInjectionPoint.LIGHTING_EVAL,
                    RenderShaderStage.FRAGMENT,
                    "impl_" + id,
                    descriptorRequirements(mode),
                    uniformRequirements(mode),
                    pushConstantRequirements(mode),
                    false,
                    false,
                    -1,
                    false
            ));
        }

        @Override
        public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
            return List.of(new RenderDescriptorRequirement(
                    "main",
                    0,
                    0,
                    RenderDescriptorType.UNIFORM_BUFFER,
                    RenderBindingFrequency.PER_FRAME,
                    false
            ));
        }

        @Override
        public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
            return List.of(new RenderUniformRequirement("global", "x", 0, 16));
        }

        @Override
        public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
            int start = "feature.a".equals(id) ? 0 : 8;
            return List.of(new RenderPushConstantRequirement(
                    "main",
                    List.of(RenderShaderStage.FRAGMENT),
                    start,
                    16
            ));
        }

        @Override
        public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
            return List.of(new RenderResourceDeclaration(
                    ownedResourceName,
                    RenderResourceType.SAMPLED_IMAGE,
                    RenderResourceLifecycle.PERSISTENT,
                    false,
                    List.of()
            ));
        }

        @Override
        public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
            return List.of();
        }

        @Override
        public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
            return new RenderTelemetryDeclaration(List.of(), List.of(), List.of(), List.of());
        }
    }

    private record PhaseCapability(String id, RenderPassPhase phase) implements RenderFeatureCapabilityV2 {

        @Override
        public String featureId() {
            return id;
        }

        @Override
        public List<RenderFeatureMode> supportedModes() {
            return List.of(new RenderFeatureMode("phase-check"));
        }

        @Override
        public RenderFeatureMode activeMode() {
            return new RenderFeatureMode("phase-check");
        }

        @Override
        public List<RenderPassDeclaration> declarePasses(QualityTier tier, RenderFeatureMode mode) {
            return List.of(new RenderPassDeclaration(
                    "phase-pass",
                    phase,
                    List.of(),
                    List.of("color"),
                    false,
                    false,
                    false,
                    List.of()
            ));
        }

        @Override
        public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
            return List.of();
        }

        @Override
        public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
            return List.of();
        }

        @Override
        public List<RenderUniformRequirement> uniformRequirements(RenderFeatureMode mode) {
            return List.of();
        }

        @Override
        public List<RenderPushConstantRequirement> pushConstantRequirements(RenderFeatureMode mode) {
            return List.of();
        }

        @Override
        public List<RenderResourceDeclaration> ownedResources(RenderFeatureMode mode) {
            return List.of();
        }

        @Override
        public List<RenderSchedulerDeclaration> schedulers(RenderFeatureMode mode) {
            return List.of();
        }

        @Override
        public RenderTelemetryDeclaration telemetry(RenderFeatureMode mode) {
            return new RenderTelemetryDeclaration(List.of(), List.of(), List.of(), List.of());
        }
    }
}
