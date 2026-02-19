package org.dynamislight.impl.vulkan.capability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.spi.render.RenderCapabilityContractV2Validator;
import org.dynamislight.spi.render.RenderCapabilityValidationIssue;
import org.dynamislight.spi.render.RenderCapabilityContractV2;
import org.dynamislight.spi.render.RenderDescriptorRequirement;
import org.dynamislight.spi.render.RenderFeatureCapabilityV2;
import org.dynamislight.spi.render.RenderFeatureMode;
import org.junit.jupiter.api.Test;

class VulkanCapabilityContractV2DescriptorsTest {
    @Test
    void shadowDescriptorProducesCompleteContract() {
        VulkanShadowCapabilityDescriptorV2 descriptor = VulkanShadowCapabilityDescriptorV2.withMode(
                VulkanShadowCapabilityDescriptorV2.MODE_VSM
        );

        RenderCapabilityContractV2 contract = descriptor.contractV2(QualityTier.HIGH);

        assertEqualsNonBlank(contract.featureId());
        assertNotNull(contract.mode());
        assertFalse(contract.passes().isEmpty());
        assertFalse(contract.shaderContributions().isEmpty());
        assertFalse(contract.descriptorRequirements().isEmpty());
        assertFalse(contract.ownedResources().isEmpty());
    }

    @Test
    void reflectionDescriptorProducesCompleteContract() {
        VulkanReflectionCapabilityDescriptorV2 descriptor = VulkanReflectionCapabilityDescriptorV2.withMode(
                VulkanReflectionCapabilityDescriptorV2.MODE_RT_HYBRID
        );

        RenderCapabilityContractV2 contract = descriptor.contractV2(QualityTier.ULTRA);

        assertEqualsNonBlank(contract.featureId());
        assertNotNull(contract.mode());
        assertFalse(contract.passes().isEmpty());
        assertFalse(contract.shaderContributions().isEmpty());
        assertFalse(contract.descriptorRequirements().isEmpty());
        assertFalse(contract.ownedResources().isEmpty());

        boolean planarRecursiveDependencyPresent = contract.passes().stream()
                .filter(p -> "planar_capture".equals(p.passId()))
                .anyMatch(p -> p.requiredFeatureScopes().contains("vulkan.shadow"));
        assertTrue(planarRecursiveDependencyPresent);
    }

    @Test
    void aaDescriptorProducesCompleteContract() {
        VulkanAaCapabilityDescriptorV2 descriptor = VulkanAaCapabilityDescriptorV2.withMode(
                VulkanAaCapabilityDescriptorV2.MODE_TAA
        );

        RenderCapabilityContractV2 contract = descriptor.contractV2(QualityTier.HIGH);

        assertEqualsNonBlank(contract.featureId());
        assertNotNull(contract.mode());
        assertFalse(contract.passes().isEmpty());
        assertFalse(contract.shaderContributions().isEmpty());
        assertFalse(contract.descriptorRequirements().isEmpty());
        assertFalse(contract.ownedResources().isEmpty());
    }

    @Test
    void postDescriptorProducesCompleteContract() {
        VulkanPostCapabilityDescriptorV2 descriptor = VulkanPostCapabilityDescriptorV2.withMode(
                VulkanPostCapabilityDescriptorV2.MODE_TAA_RESOLVE
        );

        RenderCapabilityContractV2 contract = descriptor.contractV2(QualityTier.HIGH);

        assertEqualsNonBlank(contract.featureId());
        assertNotNull(contract.mode());
        assertFalse(contract.passes().isEmpty());
        assertFalse(contract.shaderContributions().isEmpty());
        assertFalse(contract.descriptorRequirements().isEmpty());
        assertFalse(contract.ownedResources().isEmpty());
    }

    @Test
    void combinedShadowReflectionContractsHaveNoDescriptorCollisionsPerPass() {
        List<RenderFeatureCapabilityV2> capabilities = List.of(
                VulkanShadowCapabilityDescriptorV2.withMode(VulkanShadowCapabilityDescriptorV2.MODE_EVSM),
                VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID)
        );

        Set<String> keys = new HashSet<>();
        for (RenderFeatureCapabilityV2 capability : capabilities) {
            RenderCapabilityContractV2 contract = capability.contractV2(QualityTier.HIGH);
            for (RenderDescriptorRequirement req : contract.descriptorRequirements()) {
                String key = req.targetPassId() + "|set=" + req.setIndex() + "|binding=" + req.bindingIndex();
                assertTrue(keys.add(key), "descriptor collision: " + key);
            }
        }
    }

    @Test
    void crossCapabilityValidatorReportsNoErrorsForShadowAndReflectionDescriptors() {
        List<RenderFeatureCapabilityV2> capabilities = List.of(
                VulkanShadowCapabilityDescriptorV2.withMode(VulkanShadowCapabilityDescriptorV2.MODE_EVSM),
                VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID)
        );

        List<RenderCapabilityValidationIssue> issues = RenderCapabilityContractV2Validator.validate(
                capabilities,
                QualityTier.HIGH
        );

        assertTrue(issues.stream().noneMatch(issue -> issue.severity() == RenderCapabilityValidationIssue.Severity.ERROR),
                "expected zero errors, got: " + issues);
    }

    @Test
    void crossCapabilityValidatorReportsNoErrorsForShadowReflectionAaAndPostDescriptors() {
        List<RenderFeatureCapabilityV2> capabilities = List.of(
                VulkanShadowCapabilityDescriptorV2.withMode(VulkanShadowCapabilityDescriptorV2.MODE_EVSM),
                VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID),
                VulkanAaCapabilityDescriptorV2.withMode(VulkanAaCapabilityDescriptorV2.MODE_TAA),
                VulkanPostCapabilityDescriptorV2.withMode(VulkanPostCapabilityDescriptorV2.MODE_TAA_RESOLVE)
        );

        List<RenderCapabilityValidationIssue> issues = RenderCapabilityContractV2Validator.validate(
                capabilities,
                QualityTier.HIGH
        );

        assertTrue(issues.stream().noneMatch(issue -> issue.severity() == RenderCapabilityValidationIssue.Severity.ERROR),
                "expected zero errors, got: " + issues);
    }

    @Test
    void everyShadowModeProducesCompleteContractAndValidatesWithReflection() {
        VulkanReflectionCapabilityDescriptorV2 reflection =
                VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID);
        for (RenderFeatureMode mode : VulkanShadowCapabilityDescriptorV2.withMode(null).supportedModes()) {
            VulkanShadowCapabilityDescriptorV2 shadow = VulkanShadowCapabilityDescriptorV2.withMode(mode);
            RenderCapabilityContractV2 contract = shadow.contractV2(QualityTier.ULTRA);
            assertEqualsNonBlank(contract.featureId());
            assertNotNull(contract.mode());
            assertFalse(contract.passes().isEmpty(), "mode " + mode.id() + " must declare passes");
            assertFalse(contract.shaderContributions().isEmpty(), "mode " + mode.id() + " must declare shader contributions");
            assertFalse(contract.descriptorRequirements().isEmpty(), "mode " + mode.id() + " must declare descriptor requirements");
            assertFalse(contract.ownedResources().isEmpty(), "mode " + mode.id() + " must declare owned resources");

            List<RenderCapabilityValidationIssue> issues = RenderCapabilityContractV2Validator.validate(
                    List.of(shadow, reflection),
                    QualityTier.ULTRA
            );
            assertTrue(issues.stream().noneMatch(issue -> issue.severity() == RenderCapabilityValidationIssue.Severity.ERROR),
                    "expected zero errors for mode " + mode.id() + ", got: " + issues);
        }
    }

    @Test
    void everyAaModeProducesCompleteContractAndValidatesWithShadowReflectionAndPost() {
        VulkanShadowCapabilityDescriptorV2 shadow =
                VulkanShadowCapabilityDescriptorV2.withMode(VulkanShadowCapabilityDescriptorV2.MODE_EVSM);
        VulkanReflectionCapabilityDescriptorV2 reflection =
                VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID);
        VulkanPostCapabilityDescriptorV2 post =
                VulkanPostCapabilityDescriptorV2.withMode(VulkanPostCapabilityDescriptorV2.MODE_TAA_RESOLVE);
        for (RenderFeatureMode mode : VulkanAaCapabilityDescriptorV2.withMode(null).supportedModes()) {
            VulkanAaCapabilityDescriptorV2 aa = VulkanAaCapabilityDescriptorV2.withMode(mode);
            RenderCapabilityContractV2 contract = aa.contractV2(QualityTier.ULTRA);
            assertEqualsNonBlank(contract.featureId());
            assertNotNull(contract.mode());
            assertFalse(contract.passes().isEmpty(), "mode " + mode.id() + " must declare passes");
            assertFalse(contract.shaderContributions().isEmpty(), "mode " + mode.id() + " must declare shader contributions");
            assertFalse(contract.descriptorRequirements().isEmpty(), "mode " + mode.id() + " must declare descriptor requirements");
            assertFalse(contract.ownedResources().isEmpty(), "mode " + mode.id() + " must declare owned resources");

            List<RenderCapabilityValidationIssue> issues = RenderCapabilityContractV2Validator.validate(
                    List.of(shadow, reflection, aa, post),
                    QualityTier.ULTRA
            );
            assertTrue(issues.stream().noneMatch(issue -> issue.severity() == RenderCapabilityValidationIssue.Severity.ERROR),
                    "expected zero errors for mode " + mode.id() + ", got: " + issues);
        }
    }

    @Test
    void everyPostModeProducesCompleteContractAndValidatesWithShadowReflectionAndAa() {
        VulkanShadowCapabilityDescriptorV2 shadow =
                VulkanShadowCapabilityDescriptorV2.withMode(VulkanShadowCapabilityDescriptorV2.MODE_EVSM);
        VulkanReflectionCapabilityDescriptorV2 reflection =
                VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID);
        VulkanAaCapabilityDescriptorV2 aa =
                VulkanAaCapabilityDescriptorV2.withMode(VulkanAaCapabilityDescriptorV2.MODE_TAA);
        for (RenderFeatureMode mode : VulkanPostCapabilityDescriptorV2.withMode(null).supportedModes()) {
            VulkanPostCapabilityDescriptorV2 post = VulkanPostCapabilityDescriptorV2.withMode(mode);
            RenderCapabilityContractV2 contract = post.contractV2(QualityTier.ULTRA);
            assertEqualsNonBlank(contract.featureId());
            assertNotNull(contract.mode());
            assertFalse(contract.passes().isEmpty(), "mode " + mode.id() + " must declare passes");
            assertFalse(contract.shaderContributions().isEmpty(), "mode " + mode.id() + " must declare shader contributions");
            assertFalse(contract.descriptorRequirements().isEmpty(), "mode " + mode.id() + " must declare descriptor requirements");
            assertFalse(contract.ownedResources().isEmpty(), "mode " + mode.id() + " must declare owned resources");

            List<RenderCapabilityValidationIssue> issues = RenderCapabilityContractV2Validator.validate(
                    List.of(shadow, reflection, aa, post),
                    QualityTier.ULTRA
            );
            assertTrue(issues.stream().noneMatch(issue -> issue.severity() == RenderCapabilityValidationIssue.Severity.ERROR),
                    "expected zero errors for mode " + mode.id() + ", got: " + issues);
        }
    }

    private static void assertEqualsNonBlank(String value) {
        assertNotNull(value);
        assertFalse(value.isBlank());
    }
}
