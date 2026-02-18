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
        assertTrue(shader.contains("vec2 sampleMomentsRingBilateral(vec2 uv, int layer, float lod, float texel, float depthRef, float blendStrength)"));
        assertTrue(shader.contains("vec4 sampleMomentBounds(vec2 uv, int layer, float lod, float texel)"));
        assertTrue(shader.contains("vec2 clampMomentsToBounds(vec2 moments, vec4 bounds, float edgeFactor)"));
        assertTrue(shader.contains("float momentVarianceConfidence(vec2 moments, float edgeFactor, float blendStrength)"));
        assertTrue(shader.contains("vec2 wideMoments = sampleMomentsWeighted("));
        assertTrue(shader.contains("vec2 deepMoments = sampleMomentsWideBilateral("));
        assertTrue(shader.contains("vec2 ultraMoments = sampleMomentsWideBilateral("));
        assertTrue(shader.contains("vec2 ringMoments = sampleMomentsRingBilateral("));
        assertTrue(shader.contains("vec4 momentBounds = sampleMomentBounds("));
        assertTrue(shader.contains("float productionChainBoost = clamp((momentBlend - 0.75) * 1.25, 0.0, 1.0);"));
        assertTrue(shader.contains("float ringWeight = clamp((0.05 + 0.16 * denoiseStability) * productionChainBoost, 0.0, 0.24);"));
        assertTrue(shader.contains("float varianceConfidence = momentVarianceConfidence(moments, denoiseEdgeFactor, momentBlend);"));
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
        assertTrue(shader.contains("int farRefineRadius = clamp(refineRadius + int(clamp(blockerSeparation * 2.0 + (1.0 - ndl) * 1.5, 0.0, 3.0)), 2, 7);"));
        assertTrue(shader.contains("float farRefinedDepth = farRefineDepthWeight > 0.0"));
        assertTrue(shader.contains("float farBlend = clamp(0.22 + blockerSeparation * 0.46 + (1.0 - ndl) * 0.18, 0.10, 0.70);"));
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
        assertTrue(shader.contains("float bvhProductionTraversalVisibility("));
        assertTrue(shader.contains("float rtDedicatedDenoiseStack("));
        assertTrue(shader.contains("float rtProductionDenoiseStack("));
        assertTrue(shader.contains("float rtNativeTraversalVisibility("));
        assertTrue(shader.contains("float rtNativeDenoiseStack("));
        assertTrue(shader.contains("if (shadowRtMode > 6) {"));
        assertTrue(shader.contains("if (shadowRtMode > 5) {"));
        assertTrue(shader.contains("float nativeVis = rtNativeTraversalVisibility("));
        assertTrue(shader.contains("float nativeDenoised = rtNativeDenoiseStack("));
        assertTrue(shader.contains("} else if (shadowRtMode > 4) {"));
        assertTrue(shader.contains("float bvhProductionVis = bvhProductionTraversalVisibility("));
        assertTrue(shader.contains("float bvhProductionDenoised = rtProductionDenoiseStack("));
        assertTrue(shader.contains("if (shadowRtMode > 2) {"));
        assertTrue(shader.contains("float bvhVis = bvhTraversalVisibilityApprox("));
        assertTrue(shader.contains("if (shadowRtMode > 3) {"));
        assertTrue(shader.contains("float bvhDedicatedVis = bvhDedicatedTraversalVisibilityApprox("));
        assertTrue(shader.contains("float bvhDedicatedDenoised = rtDedicatedDenoiseStack("));
        assertTrue(shader.contains("int shadowModePacked = max(int(gbo.uLocalLightMeta.z + 0.5), 0);"));
        assertTrue(shader.contains("soft = mix(soft, neigh, clamp(0.32 * penumbra, 0.0, 0.45));"));
        assertTrue(shader.contains("float contactTemporalStability = mix("));
        assertTrue(shader.contains("vec3 prevWorldPos = (obj.uPrevModel * vec4(vLocalPos, 1.0)).xyz;"));
        assertTrue(shader.contains("float contactDepthGrad = clamp(length(vec2(dFdx(vWorldPos.z), dFdy(vWorldPos.z)))"));
        assertTrue(shader.contains("float contactReject = clamp(max(contactMotionMag * (1.05 + taaBlend), max(contactDepthGrad, contactNormalGrad)), 0.0, 1.0);"));
        assertTrue(shader.contains("float contactTemporalHistoryWeight = mix(contactHistoryProxy, 1.0 - contactReject, 0.58);"));
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
