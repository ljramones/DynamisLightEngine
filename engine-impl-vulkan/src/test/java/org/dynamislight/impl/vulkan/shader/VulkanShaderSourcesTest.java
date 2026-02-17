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
        assertTrue(shader.contains("vec2 sampleMomentsWideBilateral(vec2 uv, int layer, float lod, float texel, float depthRef, float blendStrength)"));
        assertTrue(shader.contains("vec4 sampleMomentBounds(vec2 uv, int layer, float lod, float texel)"));
        assertTrue(shader.contains("vec2 clampMomentsToBounds(vec2 moments, vec4 bounds, float edgeFactor)"));
        assertTrue(shader.contains("vec2 wideMoments = sampleMomentsWeighted("));
        assertTrue(shader.contains("vec2 deepMoments = sampleMomentsWideBilateral("));
        assertTrue(shader.contains("vec2 ultraMoments = sampleMomentsWideBilateral("));
        assertTrue(shader.contains("vec4 momentBounds = sampleMomentBounds("));
        assertTrue(shader.contains("moments = clampMomentsToBounds(moments, momentBounds, denoiseEdgeFactor);"));
        assertTrue(shader.contains("float consistency = clamp(abs(deepMoments.x - baseMoments.x)"));
        assertTrue(shader.contains("float leakRisk = clamp((compareDepth - mean)"));
        assertTrue(shader.contains("float antiBleedMix = clamp("));
        assertTrue(shader.contains("float denoiseEdgeFactor = clamp(length(vec2(dFdx(compareDepth), dFdy(compareDepth)))"));
        assertTrue(shader.contains("float momentVisibilityApprox(vec2 uv, float compareDepth, int layer)"));
        assertTrue(shader.contains("float evsmVisibilityApprox(vec2 uv, float compareDepth, int layer)"));
        assertTrue(shader.contains("float finalizeShadowVisibility("));
        assertTrue(shader.contains("int blockerRadius = clamp(int(mix(1.0, 6.0"));
        assertTrue(shader.contains("int refineRadius = clamp(int(mix(1.0, 4.0"));
        assertTrue(shader.contains("float refinedBlockerDepth = refineDepthWeight > 0.0"));
        assertTrue(shader.contains("float hasMoments = textureQueryLevels(uShadowMomentMap) > 0 ? 1.0 : 0.0;"));
        assertTrue(shader.contains("float blockerMeanDepth = blockerDepthWeight > 0.0"));
        assertTrue(shader.contains("float neighDiag = 0.0;"));
        assertTrue(shader.contains("float neighEdge = abs(neigh - neighDiag);"));
        assertTrue(shader.contains("if (shadowRtEnabled) {"));
        assertTrue(shader.contains("int shadowRtMode = (shadowModePacked >> 2) & 7;"));
        assertTrue(shader.contains("bool shadowRtActive = ((shadowModePacked >> 5) & 1) == 1;"));
        assertTrue(shader.contains("int shadowRtSampleCount = max((shadowModePacked >> 6) & 31, 1);"));
        assertTrue(shader.contains("float shadowRtDenoiseStrength = clamp(gbo.uLightIntensity.y, 0.0, 1.0);"));
        assertTrue(shader.contains("float shadowRtRayLength = clamp(gbo.uShadowCascadeExt.x, 1.0, 500.0);"));
        assertTrue(shader.contains("int rtSteps = clamp(shadowRtSampleCount * (shadowRtMode > 1 ? 2 : 1), 4, 24);"));
        assertTrue(shader.contains("float rtKernelBlend = mix(shadowRtMode > 1 ? 0.30 : 0.18, shadowRtMode > 1 ? 0.60 : 0.45, shadowRtDenoiseStrength);"));
        assertTrue(shader.contains("float bvhTraversalVisibilityApprox("));
        assertTrue(shader.contains("float bvhDedicatedTraversalVisibilityApprox("));
        assertTrue(shader.contains("if (shadowRtMode > 2) {"));
        assertTrue(shader.contains("float bvhVis = bvhTraversalVisibilityApprox("));
        assertTrue(shader.contains("if (shadowRtMode > 3) {"));
        assertTrue(shader.contains("float bvhDedicatedVis = bvhDedicatedTraversalVisibilityApprox("));
        assertTrue(shader.contains("int shadowModePacked = max(int(gbo.uLocalLightMeta.z + 0.5), 0);"));
        assertTrue(shader.contains("soft = mix(soft, neigh, clamp(0.32 * penumbra, 0.0, 0.45));"));
        assertTrue(shader.contains("float contactTemporalStability = mix("));
        assertTrue(shader.contains("vec3 prevWorldPos = (obj.uPrevModel * vec4(vLocalPos, 1.0)).xyz;"));
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
