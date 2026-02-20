package org.dynamislight.impl.vulkan.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamislight.spi.render.RenderDescriptorType;
import org.junit.jupiter.api.Test;

class VulkanPipelineProfileCompilerTest {
    @Test
    void compileDefaultsProducesComposedSourcesAndPlans() {
        VulkanPipelineProfileCompilation compilation =
                VulkanPipelineProfileCompiler.compile(VulkanPipelineProfileKey.defaults());

        assertNotNull(compilation);
        assertNotNull(compilation.mainFragmentSource());
        assertNotNull(compilation.postFragmentSource());
        assertFalse(compilation.mainFragmentSource().isBlank());
        assertFalse(compilation.postFragmentSource().isBlank());
        assertTrue(compilation.mainFragmentSource().contains("void main"));
        assertTrue(compilation.postFragmentSource().contains("void main"));
        assertTrue(compilation.mainGeometryDescriptorPlan().allBindingsSorted().stream().anyMatch(
                binding -> binding.setIndex() == 0
                        && binding.bindingIndex() == 0
                        && binding.type() == RenderDescriptorType.UNIFORM_BUFFER
        ));
        assertTrue(compilation.mainGeometryDescriptorPlan().allBindingsSorted().stream().anyMatch(
                binding -> binding.setIndex() == 1
                        && binding.bindingIndex() == 9
                        && binding.type() == RenderDescriptorType.COMBINED_IMAGE_SAMPLER
        ));
    }
}
