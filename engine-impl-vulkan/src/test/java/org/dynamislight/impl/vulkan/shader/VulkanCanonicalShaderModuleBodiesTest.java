package org.dynamislight.impl.vulkan.shader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanReflectionCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanShadowCapabilityDescriptorV2;
import org.junit.jupiter.api.Test;

class VulkanCanonicalShaderModuleBodiesTest {
    @Test
    void shadowBodyExtractsCanonicalFunction() {
        String body = VulkanCanonicalShaderModuleBodies.shadowMainBody(VulkanShadowCapabilityDescriptorV2.MODE_PCF);
        assertFalse(body.isBlank());
        assertTrue(body.contains("float finalizeShadowVisibility("));
    }

    @Test
    void reflectionBodiesExtractCanonicalFunctions() {
        String mainBody = VulkanCanonicalShaderModuleBodies.reflectionMainBody(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID);
        String postBody = VulkanCanonicalShaderModuleBodies.reflectionPostBody(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID);
        assertFalse(mainBody.isBlank());
        assertFalse(postBody.isBlank());
        assertTrue(mainBody.contains("vec3 sampleProbeRadiance("));
        assertTrue(postBody.contains("vec3 applyReflections("));
    }

    @Test
    void aaBodyIncludesTemporalFunctionsForTemporalModes() {
        String temporalBody = VulkanCanonicalShaderModuleBodies.aaPostBody(VulkanAaCapabilityDescriptorV2.MODE_TAA);
        String fxaaBody = VulkanCanonicalShaderModuleBodies.aaPostBody(VulkanAaCapabilityDescriptorV2.MODE_FXAA_LOW);
        assertTrue(temporalBody.contains("vec3 taaSharpen("));
        assertFalse(fxaaBody.contains("vec3 taaSharpen("));
        assertTrue(fxaaBody.contains("vec3 smaaFull("));
    }
}

