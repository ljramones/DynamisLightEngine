package org.dynamislight.impl.vulkan.shader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VulkanShaderSourcesTest {
    @Test
    void mainFragmentIncludesMomentPipelineAndQualityPaths() {
        String shader = VulkanShaderSources.mainFragment();
        assertTrue(shader.contains("uniform sampler2DArray uShadowMomentMap;"));
        assertTrue(shader.contains("float reduceLightBleed(float visibility, float amount)"));
        assertTrue(shader.contains("vec2 sampleMomentsWeighted(vec2 uv, int layer, float lod, float texel, float depthRef, float blendStrength)"));
        assertTrue(shader.contains("vec2 wideMoments = sampleMomentsWeighted("));
        assertTrue(shader.contains("float denoiseEdgeFactor = clamp(length(vec2(dFdx(compareDepth), dFdy(compareDepth)))"));
        assertTrue(shader.contains("float momentVisibilityApprox(vec2 uv, float compareDepth, int layer)"));
        assertTrue(shader.contains("float evsmVisibilityApprox(vec2 uv, float compareDepth, int layer)"));
        assertTrue(shader.contains("float finalizeShadowVisibility("));
        assertTrue(shader.contains("soft = mix(soft, neigh, clamp(0.32 * penumbra, 0.0, 0.45));"));
        assertTrue(shader.contains("float contactTemporalStability = mix("));
        assertTrue(shader.contains("float contactTemporalMotionScale = clamp(gbo.uLightIntensity.z, 0.1, 3.0);"));
        assertTrue(shader.contains("float contactTemporalMinStability = clamp(gbo.uLightIntensity.w, 0.2, 1.0);"));
        assertTrue(shader.contains("* contactTemporalStability;"));
        assertTrue(shader.contains("float pcssSoftness = clamp(gbo.uDirLightDir.w, 0.25, 2.0);"));
        assertTrue(shader.contains("float momentBlend = clamp(gbo.uDirLightColor.w, 0.25, 1.5);"));
        assertTrue(shader.contains("float momentBleedReduction = clamp(gbo.uPointLightColor.w, 0.25, 1.5);"));
        assertTrue(shader.contains("float contactStrengthScale = clamp(gbo.uPointLightDir.w, 0.25, 2.0);"));
        assertTrue(shader.contains("if (contactShadows) {"));
    }
}
