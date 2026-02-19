package org.dynamislight.impl.vulkan.shader;

final class VulkanMainFragmentShaderPart1 {
    private VulkanMainFragmentShaderPart1() {
    }
    static final String TEXT = """
                float reduceLightBleed(float visibility, float amount) {
                    return clamp((visibility - amount) / max(1.0 - amount, 0.0001), 0.0, 1.0);
                }
                vec2 sampleMomentsWeighted(vec2 uv, int layer, float lod, float texel, float depthRef, float blendStrength) {
                    vec2 accum = vec2(0.0);
                    float weightSum = 0.0;
                    vec2 taps[9] = vec2[](
                            vec2(0.0, 0.0),
                            vec2(1.0, 0.0), vec2(-1.0, 0.0),
                            vec2(0.0, 1.0), vec2(0.0, -1.0),
                            vec2(1.0, 1.0), vec2(-1.0, 1.0),
                            vec2(1.0, -1.0), vec2(-1.0, -1.0)
                    );
                    for (int i = 0; i < 9; i++) {
                        vec2 offset = taps[i] * texel;
                        vec3 coord = vec3(clamp(uv + offset, vec2(0.0), vec2(1.0)), float(clamp(layer, 0, 23)));
                        vec2 moments = textureLod(uShadowMomentMap, coord, lod).rg;
                        float depthDelta = abs(moments.x - depthRef);
                        float bilateral = exp(-depthDelta * (24.0 + blendStrength * 20.0));
                        float radial = 1.0 / (1.0 + dot(taps[i], taps[i]) * 0.60);
                        float w = bilateral * radial;
                        accum += moments * w;
                        weightSum += w;
                    }
                    return accum / max(weightSum, 0.0001);
                }
                vec2 sampleMomentsWideBilateral(vec2 uv, int layer, float lod, float texel, float depthRef, float blendStrength) {
                    vec2 accum = vec2(0.0);
                    float weightSum = 0.0;
                    for (int y = -2; y <= 2; y++) {
                        for (int x = -2; x <= 2; x++) {
                            vec2 tap = vec2(float(x), float(y));
                            vec2 offset = tap * texel;
                            vec3 coord = vec3(clamp(uv + offset, vec2(0.0), vec2(1.0)), float(clamp(layer, 0, 23)));
                            vec2 moments = textureLod(uShadowMomentMap, coord, lod).rg;
                            float depthDelta = abs(moments.x - depthRef);
                            float radial = exp(-(dot(tap, tap)) * 0.42);
                            float bilateral = exp(-depthDelta * (28.0 + blendStrength * 24.0));
                            float w = radial * bilateral;
                            accum += moments * w;
                            weightSum += w;
                        }
                    }
                    return accum / max(weightSum, 0.0001);
                }
                vec2 sampleMomentsRingBilateral(vec2 uv, int layer, float lod, float texel, float depthRef, float blendStrength) {
                    vec2 accum = vec2(0.0);
                    float weightSum = 0.0;
                    for (int i = 0; i < 12; i++) {
                        float a = (6.2831853 * float(i)) / 12.0;
                        vec2 dir = vec2(cos(a), sin(a));
                        float radius = mix(2.0, 4.8, float(i & 1));
                        vec2 tap = dir * radius;
                        vec3 coord = vec3(clamp(uv + tap * texel, vec2(0.0), vec2(1.0)), float(clamp(layer, 0, 23)));
                        vec2 moments = textureLod(uShadowMomentMap, coord, lod).rg;
                        float depthDelta = abs(moments.x - depthRef);
                        float bilateral = exp(-depthDelta * (30.0 + blendStrength * 26.0));
                        float radial = 1.0 / (1.0 + radius * radius * 0.12);
                        float w = bilateral * radial;
                        accum += moments * w;
                        weightSum += w;
                    }
                    return accum / max(weightSum, 0.0001);
                }
                vec4 sampleMomentBounds(vec2 uv, int layer, float lod, float texel) {
                    float minMean = 1.0;
                    float maxMean = 0.0;
                    float minSecond = 1.0;
                    float maxSecond = 0.0;
                    for (int y = -1; y <= 1; y++) {
                        for (int x = -1; x <= 1; x++) {
                            vec2 tap = vec2(float(x), float(y));
                            vec3 coord = vec3(clamp(uv + tap * texel, vec2(0.0), vec2(1.0)), float(clamp(layer, 0, 23)));
                            vec2 m = textureLod(uShadowMomentMap, coord, lod).rg;
                            minMean = min(minMean, m.x);
                            maxMean = max(maxMean, m.x);
                            minSecond = min(minSecond, m.y);
                            maxSecond = max(maxSecond, m.y);
                        }
                    }
                    return vec4(minMean, maxMean, minSecond, maxSecond);
                }
                vec2 clampMomentsToBounds(vec2 moments, vec4 bounds, float edgeFactor) {
                    float meanSlack = mix(0.020, 0.006, edgeFactor);
                    float meanMin = max(bounds.x - meanSlack, 0.0);
                    float meanMax = min(bounds.y + meanSlack, 1.0);
                    float mean = clamp(moments.x, meanMin, max(meanMin, meanMax));
                    float secondSlack = mix(0.00014, 0.00004, edgeFactor);
                    float secondMin = max(mean * mean, bounds.z - secondSlack);
                    float secondMax = max(secondMin, bounds.w + secondSlack);
                    float second = clamp(moments.y, secondMin, secondMax);
                    return vec2(mean, second);
                }
                float momentVarianceConfidence(vec2 moments, float edgeFactor, float blendStrength) {
                    float mean = clamp(moments.x, 0.0, 1.0);
                    float second = max(moments.y, mean * mean);
                    float variance = max(second - (mean * mean), 0.000001);
                    float shaped = variance / max(0.00008 + (1.0 - mean) * 0.00018, 0.00001);
                    float conf = 1.0 - exp(-shaped * (0.65 + 0.45 * blendStrength));
                    conf *= (1.0 - edgeFactor * 0.32);
                    return clamp(conf, 0.0, 1.0);
                }
                float momentVisibilityApprox(vec2 uv, float compareDepth, int layer) {
                    float momentBlend = clamp(gbo.uDirLightColor.w, 0.25, 1.5);
                    float momentBleedReduction = clamp(gbo.uPointLightColor.w, 0.25, 1.5);
                    vec3 momentUv = vec3(clamp(uv, vec2(0.0), vec2(1.0)), float(clamp(layer, 0, 23)));
                    float maxLod = float(max(textureQueryLevels(uShadowMomentMap) - 1, 0));
                    float lod = min(1.5, maxLod);
                    float texel = 1.0 / max(gbo.uShadowCascade.y, 1.0);
                    float denoiseEdgeFactor = clamp(length(vec2(dFdx(compareDepth), dFdy(compareDepth))) * 240.0, 0.0, 1.0);
                    float denoiseStability = 1.0 - denoiseEdgeFactor;
                    vec2 baseMoments = sampleMomentsWeighted(momentUv.xy, layer, 0.0, texel, compareDepth, momentBlend);
                    vec2 filteredMoments = sampleMomentsWeighted(momentUv.xy, layer, lod, texel * 1.5, compareDepth, momentBlend);
                    vec2 wideMoments = sampleMomentsWeighted(
                            momentUv.xy,
                            layer,
                            min(lod + 1.0, maxLod),
                            texel * 2.5,
                            compareDepth,
                            momentBlend
                    );
                    vec2 deepMoments = sampleMomentsWideBilateral(
                            momentUv.xy,
                            layer,
                            min(lod + 1.5, maxLod),
                            texel * 2.2,
                            compareDepth,
                            momentBlend
                    );
                    vec2 ultraMoments = sampleMomentsWideBilateral(
                            momentUv.xy,
                            layer,
                            min(lod + 2.0, maxLod),
                            texel * 3.4,
                            compareDepth,
                            momentBlend
                    );
                    vec2 ringMoments = sampleMomentsRingBilateral(
                            momentUv.xy,
                            layer,
                            min(lod + 2.5, maxLod),
                            texel * 3.8,
                            compareDepth,
                            momentBlend
                    );
                    vec4 momentBounds = sampleMomentBounds(
                            momentUv.xy,
                            layer,
                            min(lod + 1.0, maxLod),
                            texel * 2.0
                    );
                    float productionChainBoost = clamp((momentBlend - 0.75) * 1.25, 0.0, 1.0);
                    float filteredWeight = clamp(mix(0.68 * momentBlend, 0.34 * momentBlend, denoiseEdgeFactor), 0.20, 0.95);
                    float wideWeight = clamp(0.20 * momentBlend * denoiseStability, 0.02, 0.35);
                    vec2 moments = mix(baseMoments, filteredMoments, filteredWeight);
                    moments = mix(moments, wideMoments, wideWeight);
                    float deepWeight = clamp(0.10 + 0.22 * denoiseStability * momentBlend, 0.04, 0.32);
                    moments = mix(moments, deepMoments, deepWeight);
                    float ultraWeight = clamp((1.0 - denoiseEdgeFactor) * (0.06 + 0.12 * momentBlend), 0.02, 0.16);
                    moments = mix(moments, ultraMoments, ultraWeight);
                    float ringWeight = clamp((0.05 + 0.16 * denoiseStability) * productionChainBoost, 0.0, 0.24);
                    moments = mix(moments, ringMoments, ringWeight);
                    float consistency = clamp(abs(deepMoments.x - baseMoments.x) * 18.0, 0.0, 1.0);
                    moments = mix(moments, baseMoments, consistency * 0.35);
                    moments = clampMomentsToBounds(moments, momentBounds, denoiseEdgeFactor);
                    float varianceConfidence = momentVarianceConfidence(moments, denoiseEdgeFactor, momentBlend);
                    moments = mix(baseMoments, moments, clamp(0.58 + 0.42 * varianceConfidence, 0.45, 1.0));
                    // Neutral fallback for uninitialized/provisional moment data.
                    if (moments.y <= 0.000001) {
                        return 1.0;
                    }
                    float mean = clamp(moments.x, 0.0, 1.0);
                    if (compareDepth <= mean) {
                        return 1.0;
                    }
                    float second = max(moments.y, mean * mean);
                    float varianceFloor = mix(0.00003 + (1.0 - mean) * 0.00006, 0.00005 + (1.0 - mean) * 0.00008, denoiseEdgeFactor);
                    float variance = max(second - (mean * mean), varianceFloor);
                    float diff = compareDepth - mean;
                    float pMax = variance / (variance + diff * diff);
                    float antiBleed = reduceLightBleed(clamp(pMax, 0.0, 1.0), clamp(0.22 * momentBleedReduction, 0.08, 0.45));
                    float leakRisk = clamp((compareDepth - mean) * (15.0 + 4.0 * denoiseStability), 0.0, 1.0);
                    float antiBleedMix = clamp(0.74 + 0.20 * leakRisk, 0.70, 0.96);
                    return clamp(mix(pMax, antiBleed, antiBleedMix), 0.0, 1.0);
                }
                float evsmVisibilityApprox(vec2 uv, float compareDepth, int layer) {
                    float momentBlend = clamp(gbo.uDirLightColor.w, 0.25, 1.5);
                    float momentBleedReduction = clamp(gbo.uPointLightColor.w, 0.25, 1.5);
                    vec3 momentUv = vec3(clamp(uv, vec2(0.0), vec2(1.0)), float(clamp(layer, 0, 23)));
                    float maxLod = float(max(textureQueryLevels(uShadowMomentMap) - 1, 0));
                    float lod = min(2.0, maxLod);
                    float texel = 1.0 / max(gbo.uShadowCascade.y, 1.0);
                    float denoiseEdgeFactor = clamp(length(vec2(dFdx(compareDepth), dFdy(compareDepth))) * 220.0, 0.0, 1.0);
                    float denoiseStability = 1.0 - denoiseEdgeFactor;
                    vec2 baseMoments = sampleMomentsWeighted(momentUv.xy, layer, 0.0, texel, compareDepth, momentBlend);
                    vec2 filteredMoments = sampleMomentsWeighted(momentUv.xy, layer, lod, texel * 2.0, compareDepth, momentBlend);
                    vec2 wideMoments = sampleMomentsWeighted(
                            momentUv.xy,
                            layer,
                            min(lod + 1.0, maxLod),
                            texel * 3.0,
                            compareDepth,
                            momentBlend
                    );
                    vec2 deepMoments = sampleMomentsWideBilateral(
                            momentUv.xy,
                            layer,
                            min(lod + 1.5, maxLod),
                            texel * 2.8,
                            compareDepth,
                            momentBlend
                    );
                    vec2 ultraMoments = sampleMomentsWideBilateral(
                            momentUv.xy,
                            layer,
                            min(lod + 2.2, maxLod),
                            texel * 4.0,
                            compareDepth,
                            momentBlend
                    );
                    vec2 ringMoments = sampleMomentsRingBilateral(
                            momentUv.xy,
                            layer,
                            min(lod + 2.8, maxLod),
                            texel * 4.4,
                            compareDepth,
                            momentBlend
                    );
                    vec4 momentBounds = sampleMomentBounds(
                            momentUv.xy,
                            layer,
                            min(lod + 1.2, maxLod),
                            texel * 2.4
                    );
                    float productionChainBoost = clamp((momentBlend - 0.75) * 1.25, 0.0, 1.0);
                    float filteredWeight = clamp(mix(0.75 * momentBlend, 0.40 * momentBlend, denoiseEdgeFactor), 0.25, 0.97);
                    float wideWeight = clamp(0.28 * momentBlend * denoiseStability, 0.03, 0.42);
                    vec2 moments = mix(baseMoments, filteredMoments, filteredWeight);
                    moments = mix(moments, wideMoments, wideWeight);
                    float deepWeight = clamp(0.14 + 0.24 * denoiseStability * momentBlend, 0.05, 0.36);
                    moments = mix(moments, deepMoments, deepWeight);
                    float ultraWeight = clamp((1.0 - denoiseEdgeFactor) * (0.08 + 0.14 * momentBlend), 0.03, 0.20);
                    moments = mix(moments, ultraMoments, ultraWeight);
                    float ringWeight = clamp((0.07 + 0.18 * denoiseStability) * productionChainBoost, 0.0, 0.30);
                    moments = mix(moments, ringMoments, ringWeight);
                    float consistency = clamp(abs(deepMoments.x - baseMoments.x) * 16.0, 0.0, 1.0);
                    moments = mix(moments, baseMoments, consistency * 0.32);
                    moments = clampMomentsToBounds(moments, momentBounds, denoiseEdgeFactor);
                    float varianceConfidence = momentVarianceConfidence(moments, denoiseEdgeFactor, momentBlend);
                    moments = mix(baseMoments, moments, clamp(0.55 + 0.45 * varianceConfidence, 0.42, 1.0));
                    if (moments.y <= 0.000001) {
                        return 1.0;
                    }
                    float warp = 40.0;
                    float mean = clamp(moments.x, 0.0, 1.0);
                    float second = max(moments.y, mean * mean);
                    float varianceFloor = mix(0.00008 + (1.0 - mean) * 0.00010, 0.00011 + (1.0 - mean) * 0.00013, denoiseEdgeFactor);
                    float variance = max(second - (mean * mean), varianceFloor);
                    float warpedCompare = exp(warp * clamp(compareDepth, 0.0, 1.0));
                    float warpedMean = exp(warp * clamp(mean, 0.0, 1.0));
                    float warpedVariance = variance * (1.0 + 0.45 * warp);
                    float diff = max(warpedCompare - warpedMean, 0.0);
                    float pMax = warpedVariance / (warpedVariance + diff * diff);
                    float momentBase = momentVisibilityApprox(uv, compareDepth, layer);
                    float antiBleed = reduceLightBleed(clamp(pMax, 0.0, 1.0), clamp(0.30 * momentBleedReduction, 0.12, 0.50));
                    float leakRisk = clamp((compareDepth - mean) * (12.0 + 3.0 * denoiseStability), 0.0, 1.0);
                    float antiBleedMix = clamp(0.64 + 0.24 * leakRisk, 0.60, 0.90);
                    return clamp(mix(momentBase, antiBleed, antiBleedMix), 0.0, 1.0);
                }
            """;
}
