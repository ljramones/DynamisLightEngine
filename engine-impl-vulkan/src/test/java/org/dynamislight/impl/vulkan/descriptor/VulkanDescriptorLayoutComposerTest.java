package org.dynamislight.impl.vulkan.descriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanPostCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanReflectionCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanShadowCapabilityDescriptorV2;
import org.dynamislight.spi.render.RenderBindingFrequency;
import org.dynamislight.spi.render.RenderDescriptorRequirement;
import org.dynamislight.spi.render.RenderDescriptorType;
import org.dynamislight.spi.render.RenderFeatureCapabilityV2;
import org.dynamislight.spi.render.RenderFeatureMode;
import org.dynamislight.spi.render.RenderPassDeclaration;
import org.dynamislight.spi.render.RenderPassPhase;
import org.dynamislight.spi.render.RenderPushConstantRequirement;
import org.dynamislight.spi.render.RenderResourceDeclaration;
import org.dynamislight.spi.render.RenderSchedulerDeclaration;
import org.dynamislight.spi.render.RenderShaderContribution;
import org.dynamislight.spi.render.RenderTelemetryDeclaration;
import org.dynamislight.spi.render.RenderUniformRequirement;
import org.junit.jupiter.api.Test;

class VulkanDescriptorLayoutComposerTest {
    @Test
    void composeMainGeometryMergesShadowAndReflectionRequirementsDeterministically() {
        List<RenderFeatureCapabilityV2> capabilities = List.of(
                VulkanShadowCapabilityDescriptorV2.withMode(VulkanShadowCapabilityDescriptorV2.MODE_EVSM),
                VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID)
        );

        VulkanComposedDescriptorLayoutPlan plan = VulkanDescriptorLayoutComposer.composeForPass(
                "main_geometry",
                QualityTier.HIGH,
                capabilities
        );

        assertEquals("main_geometry", plan.targetPassId());
        assertFalse(plan.allBindingsSorted().isEmpty());
        assertTrue(plan.bindingsBySet().containsKey(0));
        assertTrue(plan.bindingsBySet().containsKey(1));
        assertTrue(plan.allBindingsSorted().stream().anyMatch(binding ->
                binding.setIndex() == 0 && binding.bindingIndex() == 2 && binding.type() == RenderDescriptorType.STORAGE_BUFFER));
        assertTrue(plan.allBindingsSorted().stream().anyMatch(binding ->
                binding.setIndex() == 1 && binding.bindingIndex() == 4 && binding.type() == RenderDescriptorType.COMBINED_IMAGE_SAMPLER));
        assertTrue(plan.allBindingsSorted().stream().anyMatch(binding ->
                binding.setIndex() == 1 && binding.bindingIndex() == 9 && binding.type() == RenderDescriptorType.COMBINED_IMAGE_SAMPLER));
    }

    @Test
    void composePostCompositeMergesAaReflectionAndPostRequirements() {
        List<RenderFeatureCapabilityV2> capabilities = List.of(
                VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID),
                VulkanAaCapabilityDescriptorV2.withMode(VulkanAaCapabilityDescriptorV2.MODE_TAA),
                VulkanPostCapabilityDescriptorV2.withMode(VulkanPostCapabilityDescriptorV2.MODE_TAA_RESOLVE)
        );

        VulkanComposedDescriptorLayoutPlan plan = VulkanDescriptorLayoutComposer.composeForPass(
                "post_composite",
                QualityTier.ULTRA,
                capabilities
        );

        List<VulkanComposedDescriptorBinding> bindings = plan.allBindingsSorted();
        assertFalse(bindings.isEmpty());
        assertTrue(bindings.stream().anyMatch(binding -> binding.bindingIndex() == 0));
        assertTrue(bindings.stream().anyMatch(binding -> binding.bindingIndex() == 20));
        assertTrue(bindings.stream().anyMatch(binding -> binding.bindingIndex() == 30));
        assertTrue(bindings.stream().anyMatch(binding -> binding.bindingIndex() == 34));
    }

    @Test
    void composeRejectsConflictingBindingTypeOrFrequency() {
        RenderFeatureCapabilityV2 a = new StubCapability(
                "a",
                List.of(new RenderDescriptorRequirement("main_geometry", 1, 4, RenderDescriptorType.COMBINED_IMAGE_SAMPLER, RenderBindingFrequency.PER_FRAME, false))
        );
        RenderFeatureCapabilityV2 b = new StubCapability(
                "b",
                List.of(new RenderDescriptorRequirement("main_geometry", 1, 4, RenderDescriptorType.UNIFORM_BUFFER, RenderBindingFrequency.PER_FRAME, false))
        );

        assertThrows(IllegalStateException.class, () ->
                VulkanDescriptorLayoutComposer.composeForPass(
                        "main_geometry",
                        QualityTier.HIGH,
                        List.of(a, b)
                ));
    }

    private record StubCapability(
            String featureId,
            List<RenderDescriptorRequirement> descriptorRequirements
    ) implements RenderFeatureCapabilityV2 {
        @Override
        public List<RenderFeatureMode> supportedModes() {
            return List.of(new RenderFeatureMode("stub"));
        }

        @Override
        public RenderFeatureMode activeMode() {
            return new RenderFeatureMode("stub");
        }

        @Override
        public List<RenderPassDeclaration> declarePasses(QualityTier tier, RenderFeatureMode mode) {
            return List.of(new RenderPassDeclaration("main_geometry", RenderPassPhase.MAIN, List.of(), List.of(), false, false, false, List.of()));
        }

        @Override
        public List<RenderShaderContribution> shaderContributions(RenderFeatureMode mode) {
            return List.of();
        }

        @Override
        public List<RenderDescriptorRequirement> descriptorRequirements(RenderFeatureMode mode) {
            return descriptorRequirements;
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
