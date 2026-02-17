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
        assertTrue(shader.contains("float pcssSoftness = clamp(gbo.uDirLightDir.w, 0.25, 2.0);"));
        assertTrue(shader.contains("float momentBlend = clamp(gbo.uDirLightColor.w, 0.25, 1.5);"));
        assertTrue(shader.contains("float momentBleedReduction = clamp(gbo.uPointLightColor.w, 0.25, 1.5);"));
        assertTrue(shader.contains("float contactStrengthScale = clamp(gbo.uPointLightDir.w, 0.25, 2.0);"));
        assertTrue(shader.contains("if (contactShadows) {"));
    }
}
