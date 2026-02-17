package org.dynamislight.impl.vulkan.shader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VulkanShaderSourcesTest {
    @Test
    void mainFragmentIncludesMomentPipelineAndQualityPaths() {
        String shader = VulkanShaderSources.mainFragment();
        assertTrue(shader.contains("uniform sampler2DArray uShadowMomentMap;"));
        assertTrue(shader.contains("float reduceLightBleed(float visibility, float amount)"));
        assertTrue(shader.contains("float momentVisibilityApprox(vec2 uv, float compareDepth, int layer)"));
        assertTrue(shader.contains("float evsmVisibilityApprox(vec2 uv, float compareDepth, int layer)"));
        assertTrue(shader.contains("float finalizeShadowVisibility("));
        assertTrue(shader.contains("if (contactShadows) {"));
    }
}

