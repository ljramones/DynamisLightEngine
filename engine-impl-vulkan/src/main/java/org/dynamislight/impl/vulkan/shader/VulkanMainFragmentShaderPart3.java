package org.dynamislight.impl.vulkan.shader;

final class VulkanMainFragmentShaderPart3 {
    private VulkanMainFragmentShaderPart3() {
    }
    static final String TEXT = """
                float bvhProductionTraversalVisibility(
                        vec2 uv,
                        float texel,
                        int layer,
                        float compareDepth,
                        float ndl,
                        float depthRatio,
                        int shadowRtSampleCount,
                        float shadowRtRayLength
                ) {
                    float rayScale = clamp(shadowRtRayLength / 120.0, 0.7, 6.0);
                    int rtSteps = clamp(shadowRtSampleCount * 6, 18, 72);
                    vec2 axisA = normalize(vec2(0.61 + (1.0 - ndl) * 0.60, 0.42 + depthRatio * 0.68));
                    vec2 axisB = vec2(-axisA.y, axisA.x);
                    float accum = 0.0;
                    float weightSum = 0.0;
                    for (int i = 0; i < rtSteps; i++) {
                        float t = (float(i) + 0.5) / float(rtSteps);
                        float stride = mix(0.45, 14.0, t * t) * rayScale;
                        float fan = mix(-1.0, 1.0, t);
                        vec2 fanDir = normalize(mix(axisA, axisB, fan * 0.80));
                        vec2 sampleUv = clamp(uv + fanDir * texel * stride, vec2(0.0), vec2(1.0));
                        float sampleVis = texture(uShadowMap, vec4(sampleUv, float(layer), compareDepth));
                        float w = mix(1.0, 0.10, t) * mix(1.0, 0.56, abs(fan));
                        accum += sampleVis * w;
                        weightSum += w;
                    }
                    float primary = weightSum > 0.0 ? (accum / weightSum) : 1.0;
                    float ringNear = 0.0;
                    float ringMid = 0.0;
                    float ringFar = 0.0;
                    float wNear = 0.0;
                    float wMid = 0.0;
                    float wFar = 0.0;
                    for (int i = 0; i < 12; i++) {
                        float a = (6.2831853 * float(i)) / 12.0;
                        vec2 dir = vec2(cos(a), sin(a));
                        vec2 uvNear = clamp(uv + dir * texel * 2.0, vec2(0.0), vec2(1.0));
                        vec2 uvMid = clamp(uv + dir * texel * 4.5, vec2(0.0), vec2(1.0));
                        vec2 uvFar = clamp(uv + dir * texel * 8.0, vec2(0.0), vec2(1.0));
                        float wn = mix(1.0, 0.70, float(i & 1));
                        float wm = mix(0.82, 0.56, float(i & 1));
                        float wf = mix(0.60, 0.38, float(i & 1));
                        ringNear += texture(uShadowMap, vec4(uvNear, float(layer), compareDepth)) * wn;
                        ringMid += texture(uShadowMap, vec4(uvMid, float(layer), compareDepth)) * wm;
                        ringFar += texture(uShadowMap, vec4(uvFar, float(layer), compareDepth)) * wf;
                        wNear += wn;
                        wMid += wm;
                        wFar += wf;
                    }
                    float nearAvg = ringNear / max(wNear, 0.0001);
                    float midAvg = ringMid / max(wMid, 0.0001);
                    float farAvg = ringFar / max(wFar, 0.0001);
                    float stageA = mix(primary, nearAvg, 0.34);
                    float stageB = mix(stageA, midAvg, 0.26);
                    float stageC = mix(stageB, farAvg, 0.16);
                    return clamp(stageC, 0.0, 1.0);
                }
                float rtProductionDenoiseStack(
                        float traversalVisibility,
                        vec2 uv,
                        float texel,
                        int layer,
                        float compareDepth,
                        float shadowRtDenoiseStrength,
                        float shadowTemporalStability
                ) {
                    float center = traversalVisibility;
                    float cross = 0.0;
                    float diag = 0.0;
                    float ring = 0.0;
                    float crossW = 0.0;
                    float diagW = 0.0;
                    float ringW = 0.0;
                    vec2 crossOffsets[4] = vec2[](vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0));
                    vec2 diagOffsets[4] = vec2[](vec2(1.0, 1.0), vec2(1.0, -1.0), vec2(-1.0, 1.0), vec2(-1.0, -1.0));
                    for (int i = 0; i < 4; i++) {
                        vec2 c = clamp(uv + crossOffsets[i] * texel * 1.6, vec2(0.0), vec2(1.0));
                        vec2 d = clamp(uv + diagOffsets[i] * texel * 2.2, vec2(0.0), vec2(1.0));
                        float cv = texture(uShadowMap, vec4(c, float(layer), compareDepth));
                        float dv = texture(uShadowMap, vec4(d, float(layer), compareDepth));
                        cross += cv;
                        diag += dv;
                        crossW += 1.0;
                        diagW += 1.0;
                    }
                    for (int i = 0; i < 8; i++) {
                        float a = (6.2831853 * float(i)) / 8.0;
                        vec2 dir = vec2(cos(a), sin(a));
                        vec2 r = clamp(uv + dir * texel * 5.5, vec2(0.0), vec2(1.0));
                        float rv = texture(uShadowMap, vec4(r, float(layer), compareDepth));
                        float rw = mix(0.75, 0.45, float(i & 1));
                        ring += rv * rw;
                        ringW += rw;
                    }
                    float crossAvg = cross / max(crossW, 0.0001);
                    float diagAvg = diag / max(diagW, 0.0001);
                    float ringAvg = ring / max(ringW, 0.0001);
                    float denoiseA = mix(center, crossAvg, clamp(0.36 + 0.30 * shadowRtDenoiseStrength, 0.12, 0.78));
                    float denoiseB = mix(denoiseA, diagAvg, clamp(0.24 + 0.30 * shadowRtDenoiseStrength, 0.08, 0.68));
                    float denoiseC = mix(denoiseB, ringAvg, clamp(0.10 + 0.28 * shadowRtDenoiseStrength * (1.0 - shadowTemporalStability), 0.04, 0.40));
                    return clamp(denoiseC, 0.0, 1.0);
                }
                float rtNativeTraversalVisibility(
                        vec2 uv,
                        float texel,
                        int layer,
                        float compareDepth,
                        float ndl,
                        float depthRatio,
                        int shadowRtSampleCount,
                        float shadowRtRayLength
                ) {
                    float rayScale = clamp(shadowRtRayLength / 130.0, 0.6, 7.0);
                    int rtSteps = clamp(shadowRtSampleCount * 7, 20, 96);
                    vec2 axisA = normalize(vec2(0.58 + (1.0 - ndl) * 0.72, 0.40 + depthRatio * 0.72));
                    vec2 axisB = vec2(-axisA.y, axisA.x);
                    float accum = 0.0;
                    float weightSum = 0.0;
                    for (int i = 0; i < rtSteps; i++) {
                        float t = (float(i) + 0.5) / float(rtSteps);
                        float stride = mix(0.35, 16.0, t * t) * rayScale;
                        float fan = mix(-1.0, 1.0, t);
                        vec2 fanDir = normalize(mix(axisA, axisB, fan * 0.92));
                        vec2 sampleUv = clamp(uv + fanDir * texel * stride, vec2(0.0), vec2(1.0));
                        float sampleVis = texture(uShadowMap, vec4(sampleUv, float(layer), compareDepth));
                        float w = mix(1.0, 0.08, t) * mix(1.0, 0.52, abs(fan));
                        accum += sampleVis * w;
                        weightSum += w;
                    }
                    float traversal = weightSum > 0.0 ? (accum / weightSum) : 1.0;
                    float ringNear = 0.0;
                    float ringMid = 0.0;
                    float ringFar = 0.0;
                    float wNear = 0.0;
                    float wMid = 0.0;
                    float wFar = 0.0;
                    for (int i = 0; i < 16; i++) {
                        float a = (6.2831853 * float(i)) / 16.0;
                        vec2 dir = vec2(cos(a), sin(a));
                        vec2 uvNear = clamp(uv + dir * texel * 2.4, vec2(0.0), vec2(1.0));
                        vec2 uvMid = clamp(uv + dir * texel * 5.8, vec2(0.0), vec2(1.0));
                        vec2 uvFar = clamp(uv + dir * texel * 10.2, vec2(0.0), vec2(1.0));
                        float wn = mix(1.0, 0.74, float(i & 1));
                        float wm = mix(0.84, 0.58, float(i & 1));
                        float wf = mix(0.62, 0.40, float(i & 1));
                        ringNear += texture(uShadowMap, vec4(uvNear, float(layer), compareDepth)) * wn;
                        ringMid += texture(uShadowMap, vec4(uvMid, float(layer), compareDepth)) * wm;
                        ringFar += texture(uShadowMap, vec4(uvFar, float(layer), compareDepth)) * wf;
                        wNear += wn;
                        wMid += wm;
                        wFar += wf;
                    }
                    float nearAvg = ringNear / max(wNear, 0.0001);
                    float midAvg = ringMid / max(wMid, 0.0001);
                    float farAvg = ringFar / max(wFar, 0.0001);
                    float stageA = mix(traversal, nearAvg, 0.38);
                    float stageB = mix(stageA, midAvg, 0.30);
                    float stageC = mix(stageB, farAvg, 0.18);
                    return clamp(stageC, 0.0, 1.0);
                }
                float rtNativeDenoiseStack(
                        float traversalVisibility,
                        vec2 uv,
                        float texel,
                        int layer,
                        float compareDepth,
                        float shadowRtDenoiseStrength,
                        float shadowTemporalStability
                ) {
                    float center = traversalVisibility;
                    float cross = 0.0;
                    float diag = 0.0;
                    float ringNear = 0.0;
                    float ringFar = 0.0;
                    float crossW = 0.0;
                    float diagW = 0.0;
                    float ringNearW = 0.0;
                    float ringFarW = 0.0;
                    vec2 crossOffsets[4] = vec2[](vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0));
                    vec2 diagOffsets[4] = vec2[](vec2(1.0, 1.0), vec2(1.0, -1.0), vec2(-1.0, 1.0), vec2(-1.0, -1.0));
                    for (int i = 0; i < 4; i++) {
                        vec2 c = clamp(uv + crossOffsets[i] * texel * 1.8, vec2(0.0), vec2(1.0));
                        vec2 d = clamp(uv + diagOffsets[i] * texel * 2.6, vec2(0.0), vec2(1.0));
                        float cv = texture(uShadowMap, vec4(c, float(layer), compareDepth));
                        float dv = texture(uShadowMap, vec4(d, float(layer), compareDepth));
                        cross += cv;
                        diag += dv;
                        crossW += 1.0;
                        diagW += 1.0;
                    }
                    for (int i = 0; i < 12; i++) {
                        float a = (6.2831853 * float(i)) / 12.0;
                        vec2 dir = vec2(cos(a), sin(a));
                        vec2 rn = clamp(uv + dir * texel * 4.2, vec2(0.0), vec2(1.0));
                        vec2 rf = clamp(uv + dir * texel * 8.6, vec2(0.0), vec2(1.0));
                        float nv = texture(uShadowMap, vec4(rn, float(layer), compareDepth));
                        float fv = texture(uShadowMap, vec4(rf, float(layer), compareDepth));
                        float nw = mix(0.86, 0.52, float(i & 1));
                        float fw = mix(0.64, 0.36, float(i & 1));
                        ringNear += nv * nw;
                        ringFar += fv * fw;
                        ringNearW += nw;
                        ringFarW += fw;
                    }
                    float crossAvg = cross / max(crossW, 0.0001);
                    float diagAvg = diag / max(diagW, 0.0001);
                    float nearAvg = ringNear / max(ringNearW, 0.0001);
                    float farAvg = ringFar / max(ringFarW, 0.0001);
                    float denoiseA = mix(center, crossAvg, clamp(0.40 + 0.34 * shadowRtDenoiseStrength, 0.16, 0.84));
                    float denoiseB = mix(denoiseA, diagAvg, clamp(0.28 + 0.30 * shadowRtDenoiseStrength, 0.10, 0.72));
                    float denoiseC = mix(denoiseB, nearAvg, clamp(0.18 + 0.34 * shadowRtDenoiseStrength, 0.08, 0.58));
                    float denoiseD = mix(denoiseC, farAvg, clamp(0.08 + 0.30 * shadowRtDenoiseStrength * (1.0 - shadowTemporalStability), 0.03, 0.44));
                    return clamp(denoiseD, 0.0, 1.0);
                }
                float finalizeShadowVisibility(
                        float pcfVisibility,
                        int shadowFilterMode,
                        bool shadowRtEnabled,
                        int shadowRtMode,
                        int shadowRtSampleCount,
                        float shadowRtDenoiseStrength,
                        float shadowRtRayLength,
                        vec2 uv,
                        float compareDepth,
                        int layer,
                        float ndl,
                        float depthRatio,
                        float pcssSoftness,
                        float shadowTemporalStability
                ) {
                    float visibility = clamp(pcfVisibility, 0.0, 1.0);
                    if (shadowFilterMode == 1) {
                        float texel = 1.0 / max(gbo.uShadowCascade.y, 1.0);
                        int blockerRadius = clamp(int(mix(1.0, 6.0, clamp(depthRatio * 0.9 + (1.0 - ndl) * 0.45, 0.0, 1.0))), 1, 6);
                        float blockerAccum = 0.0;
                        float blockerWeight = 0.0;
                        float blockerDepthAccum = 0.0;
                        float blockerDepthWeight = 0.0;
                        float hasMoments = textureQueryLevels(uShadowMomentMap) > 0 ? 1.0 : 0.0;
                        for (int y = -6; y <= 6; y++) {
                            for (int x = -6; x <= 6; x++) {
                                if (abs(x) > blockerRadius || abs(y) > blockerRadius) {
                                    continue;
                                }
                                vec2 bo = vec2(float(x), float(y));
                                vec2 offset = bo * texel;
                                float sampleDepth = texture(uShadowMap, vec4(clamp(uv + offset, vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                                float blocker = 1.0 - sampleDepth;
                                float radial = 1.0 / (1.0 + dot(bo, bo) * 0.35);
                                blockerAccum += blocker * radial;
                                blockerWeight += radial;
                                if (hasMoments > 0.5) {
                                    vec2 blockerMoments = textureLod(
                                            uShadowMomentMap,
                                            vec3(clamp(uv + offset, vec2(0.0), vec2(1.0)), float(layer)),
                                            0.0
                                    ).rg;
                                    float blockerDepth = clamp(blockerMoments.x, 0.0, 1.0);
                                    float w = radial * (0.35 + blocker * 0.65);
                                    blockerDepthAccum += blockerDepth * w;
                                    blockerDepthWeight += w;
                                }
                            }
                        }
                        float blockerMean = blockerWeight > 0.0 ? blockerAccum / blockerWeight : 0.0;
                        float blockerMeanDepth = blockerDepthWeight > 0.0
                                ? (blockerDepthAccum / blockerDepthWeight)
                                : clamp(compareDepth - blockerMean * 0.22, 0.0, 1.0);
                        float blockerDepth = clamp(mix(compareDepth - blockerMean * 0.24, blockerMeanDepth, hasMoments), 0.0, 1.0);
                        float blockerSeparation = clamp(compareDepth - blockerDepth, 0.0, 1.0);
                        int refineRadius = clamp(int(mix(1.0, 4.0, blockerSeparation * 1.6 + (1.0 - ndl) * 0.4)), 1, 4);
                        float refineDepthAccum = 0.0;
                        float refineDepthWeight = 0.0;
                        for (int i = 0; i < 8; i++) {
                            float ang = (6.2831853 * float(i)) / 8.0;
                            vec2 dir = vec2(cos(ang), sin(ang));
                            vec2 offset = dir * texel * float(refineRadius);
                            vec2 sampleUv = clamp(uv + offset, vec2(0.0), vec2(1.0));
                            float radial = mix(1.0, 0.45, float(i & 1));
                            float localDepth = compareDepth - (1.0 - texture(uShadowMap, vec4(sampleUv, float(layer), compareDepth))) * 0.24;
                            if (hasMoments > 0.5) {
                                vec2 localMoments = textureLod(uShadowMomentMap, vec3(sampleUv, float(layer)), 0.0).rg;
                                localDepth = mix(localDepth, clamp(localMoments.x, 0.0, 1.0), 0.72);
                            }
                            refineDepthAccum += localDepth * radial;
                            refineDepthWeight += radial;
                        }
                        float refinedBlockerDepth = refineDepthWeight > 0.0
                                ? clamp(refineDepthAccum / refineDepthWeight, 0.0, 1.0)
                                : blockerDepth;
                        blockerDepth = mix(blockerDepth, refinedBlockerDepth, clamp(0.55 + blockerSeparation * 0.35, 0.35, 0.90));
                        float farRefineDepthAccum = 0.0;
                        float farRefineDepthWeight = 0.0;
                        int farRefineRadius = clamp(refineRadius + int(clamp(blockerSeparation * 2.0 + (1.0 - ndl) * 1.5, 0.0, 3.0)), 2, 7);
                        for (int i = 0; i < 12; i++) {
                            float ang = (6.2831853 * float(i)) / 12.0;
                            vec2 dir = vec2(cos(ang), sin(ang));
                            vec2 offset = dir * texel * float(farRefineRadius);
                            vec2 sampleUv = clamp(uv + offset, vec2(0.0), vec2(1.0));
                            float radial = mix(0.88, 0.42, float(i & 1));
                            float localDepth = compareDepth - (1.0 - texture(uShadowMap, vec4(sampleUv, float(layer), compareDepth))) * 0.30;
                            if (hasMoments > 0.5) {
                                vec2 localMoments = textureLod(uShadowMomentMap, vec3(sampleUv, float(layer)), 0.0).rg;
                                localDepth = mix(localDepth, clamp(localMoments.x, 0.0, 1.0), 0.80);
                            }
                            farRefineDepthAccum += localDepth * radial;
                            farRefineDepthWeight += radial;
                        }
                        float farRefinedDepth = farRefineDepthWeight > 0.0
                                ? clamp(farRefineDepthAccum / farRefineDepthWeight, 0.0, 1.0)
                                : blockerDepth;
                        float farBlend = clamp(0.22 + blockerSeparation * 0.46 + (1.0 - ndl) * 0.18, 0.10, 0.70);
                        blockerDepth = mix(blockerDepth, farRefinedDepth, farBlend);
                        float penumbra = clamp((depthRatio - blockerDepth + (1.0 - ndl) * 0.82) * pcssSoftness * 1.8, 0.0, 1.0);
                        penumbra = clamp(penumbra * mix(0.85, 1.25, blockerSeparation), 0.0, 1.0);
                        float neigh = 0.0;
                        neigh += texture(uShadowMap, vec4(clamp(uv + vec2(texel, 0.0), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                        neigh += texture(uShadowMap, vec4(clamp(uv + vec2(-texel, 0.0), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                        neigh += texture(uShadowMap, vec4(clamp(uv + vec2(0.0, texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                        neigh += texture(uShadowMap, vec4(clamp(uv + vec2(0.0, -texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                        neigh *= 0.25;
                        float neighDiag = 0.0;
                        neighDiag += texture(uShadowMap, vec4(clamp(uv + vec2(texel, texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                        neighDiag += texture(uShadowMap, vec4(clamp(uv + vec2(texel, -texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                        neighDiag += texture(uShadowMap, vec4(clamp(uv + vec2(-texel, texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                        neighDiag += texture(uShadowMap, vec4(clamp(uv + vec2(-texel, -texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                        neighDiag *= 0.25;
                        float neighEdge = abs(neigh - neighDiag);
                        float soft = mix(visibility, sqrt(max(visibility, 0.0)), 0.42 * penumbra);
                        soft = mix(soft, neigh, clamp(0.32 * penumbra, 0.0, 0.45));
                        soft = mix(soft, mix(neigh, neighDiag, 0.5), clamp(0.18 * penumbra * (1.0 - neighEdge), 0.0, 0.20));
                        float edgeProtect = smoothstep(0.12, 0.50, visibility);
                        visibility = mix(soft, visibility, edgeProtect * 0.40);
                    } else if (shadowFilterMode == 2) {
                        float momentVis = momentVisibilityApprox(uv, compareDepth, layer);
                        visibility = min(visibility + 0.05, mix(visibility, momentVis, 0.76));
                    } else if (shadowFilterMode == 3) {
                        float evsmVis = evsmVisibilityApprox(uv, compareDepth, layer);
                        visibility = min(visibility + 0.07, mix(visibility, evsmVis, 0.84));
                    }
                    if (shadowRtEnabled) {
                        float texel = 1.0 / max(gbo.uShadowCascade.y, 1.0);
                        if (shadowRtMode > 6) {
                            float nativeVis = rtNativeTraversalVisibility(
                                    uv,
                                    texel,
                                    layer,
                                    compareDepth,
                                    ndl,
                                    depthRatio,
                                    shadowRtSampleCount,
                                    shadowRtRayLength
                            );
                            float nativeDenoised = rtNativeDenoiseStack(
                                    nativeVis,
                                    uv,
                                    texel,
                                    layer,
                                    compareDepth,
                                    shadowRtDenoiseStrength,
                                    shadowTemporalStability
                            );
                            visibility = mix(visibility, nativeDenoised, 0.84);
                        } else if (shadowRtMode > 5) {
                            float nativeVis = rtNativeTraversalVisibility(
                                    uv,
                                    texel,
                                    layer,
                                    compareDepth,
                                    ndl,
                                    depthRatio,
                                    shadowRtSampleCount,
                                    shadowRtRayLength
                            );
                            visibility = mix(visibility, nativeVis, 0.78);
                        } else if (shadowRtMode > 4) {
                            float bvhProductionVis = bvhProductionTraversalVisibility(
                                    uv,
                                    texel,
                                    layer,
                                    compareDepth,
                                    ndl,
                                    depthRatio,
                                    shadowRtSampleCount,
                                    shadowRtRayLength
                            );
                            float bvhProductionDenoised = rtProductionDenoiseStack(
                                    bvhProductionVis,
                                    uv,
                                    texel,
                                    layer,
                                    compareDepth,
                                    shadowRtDenoiseStrength,
                                    shadowTemporalStability
                            );
                            visibility = mix(visibility, bvhProductionDenoised, 0.78);
                        } else if (shadowRtMode > 2) {
                            float bvhVis = bvhTraversalVisibilityApprox(
                                    uv,
                                    texel,
                                    layer,
                                    compareDepth,
                                    ndl,
                                    depthRatio,
                                    shadowRtSampleCount,
                                    shadowRtDenoiseStrength,
                                    shadowRtRayLength
                            );
                            if (shadowRtMode > 3) {
                                float bvhDedicatedVis = bvhDedicatedTraversalVisibilityApprox(
                                        uv,
                                        texel,
                                        layer,
                                        compareDepth,
                                        ndl,
                                        depthRatio,
                                        shadowRtSampleCount,
                                        shadowRtDenoiseStrength,
                                        shadowRtRayLength
                                );
                                float bvhDedicatedDenoised = rtDedicatedDenoiseStack(
                                        bvhDedicatedVis,
                                        uv,
                                        texel,
                                        layer,
                                        compareDepth,
                                        shadowRtDenoiseStrength,
                                        shadowTemporalStability
                                );
                                visibility = mix(visibility, bvhDedicatedDenoised, 0.68);
                            } else {
                                visibility = mix(visibility, bvhVis, 0.62);
                            }
                        } else {
                            float rayScale = clamp(shadowRtRayLength / 120.0, 0.35, 4.0);
                            vec2 rayDir = normalize(vec2(0.57 + (1.0 - ndl) * 0.65, 0.44 + depthRatio * 0.55));
                            int rtSteps = clamp(shadowRtSampleCount * (shadowRtMode > 1 ? 2 : 1), 4, 24);
                            float traversal = 0.0;
                            float traversalW = 0.0;
                            for (int i = 0; i < rtSteps; i++) {
                                float t = (float(i) + 1.0) / float(rtSteps);
                                float stride = mix(0.8, 6.5, t * t) * rayScale;
                                vec2 sampleUv = clamp(uv + rayDir * texel * stride, vec2(0.0), vec2(1.0));
                                float sampleVis = texture(uShadowMap, vec4(sampleUv, float(layer), compareDepth));
                                float w = 1.0 - (0.65 * t);
                                traversal += sampleVis * w;
                                traversalW += w;
                            }
                            float rtVis = traversalW > 0.0 ? traversal / traversalW : visibility;
                            vec2 o = texel * vec2(1.0, 1.0);
                            float rtN = texture(uShadowMap, vec4(clamp(uv + vec2(0.0, o.y), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                            float rtS = texture(uShadowMap, vec4(clamp(uv - vec2(0.0, o.y), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                            float rtE = texture(uShadowMap, vec4(clamp(uv + vec2(o.x, 0.0), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                            float rtW = texture(uShadowMap, vec4(clamp(uv - vec2(o.x, 0.0), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                            float rtKernelBlend = mix(shadowRtMode > 1 ? 0.30 : 0.18, shadowRtMode > 1 ? 0.60 : 0.45, shadowRtDenoiseStrength);
                            float rtDenoised = mix(rtVis, (rtN + rtS + rtE + rtW) * 0.25, rtKernelBlend);
                            visibility = mix(visibility, clamp(rtDenoised, 0.0, 1.0), shadowRtMode > 1 ? 0.55 : 0.38);
                        }
                    }
                    return clamp(visibility, 0.0, 1.0);
                }
                void main() {
                    bool planarCapturePass = pc.uPlanar.x > 0.5;
                    float planarHeight = pc.uPlanar.y;
                    mat4 activeView = planarCapturePass ? gbo.uPlanarView : gbo.uView;
                    mat4 activeProj = planarCapturePass ? gbo.uPlanarProj : gbo.uProj;
                    mat4 activePrevViewProj = planarCapturePass ? gbo.uPlanarPrevViewProj : gbo.uPrevViewProj;
                    if (planarCapturePass && (vWorldPos.y - planarHeight) < 0.0) {
                        discard;
                    }
                    vec3 n0 = normalize(vNormal);
                    vec3 t = normalize(vTangent - dot(vTangent, n0) * n0);
                    vec3 b = normalize(cross(n0, t));
                    vec3 normalTex = texture(uNormalTexture, vUv).xyz * 2.0 - 1.0;
                    vec3 n = normalize(mat3(t, b, n0) * normalTex);
                    vec4 sampledAlbedoTex = texture(uAlbedoTexture, vUv);
                    vec3 sampledAlbedo = sampledAlbedoTex.rgb;
                    float sampledAlpha = sampledAlbedoTex.a;
                    vec3 baseColor = obj.uBaseColor.rgb * sampledAlbedo;
                    vec3 mrTex = texture(uMetallicRoughnessTexture, vUv).rgb;
                    float metallic = clamp(obj.uMaterial.x * mrTex.b, 0.0, 1.0);
                    float roughness = clamp(obj.uMaterial.y * max(mrTex.g, 0.04), 0.04, 1.0);
                    float reactiveStrength = clamp(obj.uMaterial.z, 0.0, 1.0);
                    float reactiveFlags = obj.uMaterial.w;
                    float reactiveBoost = clamp(obj.uMaterialReactive.x, 0.0, 2.0);
                    float taaHistoryClamp = clamp(obj.uMaterialReactive.y, 0.0, 1.0);
                    float emissiveReactiveBoost = clamp(obj.uMaterialReactive.z, 0.0, 3.0);
                    float reactivePreset = clamp(obj.uMaterialReactive.w, 0.0, 3.0);
                    bool alphaTested = mod(reactiveFlags, 2.0) >= 1.0;
                    bool foliage = mod(floor(reactiveFlags / 2.0), 2.0) >= 1.0;
                    int reflectionOverrideMode = int(mod(floor(reactiveFlags / 4.0), 4.0));
                    float normalVariance = clamp((length(dFdx(n)) + length(dFdy(n))) * 0.30, 0.0, 1.0);
                    float normalMapVariance = clamp((1.0 - clamp(length(normalTex), 0.0, 1.0)) * 1.55, 0.0, 1.0);
                    float toksvigVariance = clamp(normalVariance * 0.70 + normalMapVariance * 0.65, 0.0, 1.0);
                    roughness = clamp(sqrt(roughness * roughness + toksvigVariance * 0.52), 0.04, 1.0);
                    float dirIntensity = max(0.0, gbo.uLightIntensity.x);
                    int shadowModePacked = max(int(gbo.uLocalLightMeta.z + 0.5), 0);
                    int shadowFilterMode = shadowModePacked & 3;
                    int shadowRtMode = (shadowModePacked >> 2) & 7;
                    bool shadowRtActive = ((shadowModePacked >> 5) & 1) == 1;
                    int shadowRtSampleCount = max((shadowModePacked >> 6) & 31, 1);
                    bool shadowRtEnabled = shadowRtMode > 0 && shadowRtActive;
                    float shadowRtDenoiseStrength = clamp(gbo.uLightIntensity.y, 0.0, 1.0);
                    float shadowRtRayLength = clamp(gbo.uShadowCascadeExt.x, 1.0, 500.0);
                    bool contactShadows = gbo.uLocalLightMeta.w > 0.5;

                    float ao = clamp(texture(uOcclusionTexture, vUv).r, 0.0, 1.0);
                    vec3 lDir = normalize(-gbo.uDirLightDir.xyz);
                    vec3 viewPos = (activeView * vec4(vWorldPos, 1.0)).xyz;
                    vec3 viewDir = normalize(-viewPos);
                    float pcssSoftness = clamp(gbo.uDirLightDir.w, 0.25, 2.0);
                    float contactStrengthScale = clamp(gbo.uPointLightDir.w, 0.25, 2.0);
                    float taaEnabled = gbo.uAntiAlias.x > 0.5 ? 1.0 : 0.0;
                    float taaBlend = clamp(gbo.uAntiAlias.y, 0.0, 1.0);
                    float contactTemporalMotionScale = clamp(gbo.uLightIntensity.z, 0.1, 3.0);
                    float contactTemporalMinStability = clamp(gbo.uLightIntensity.w, 0.2, 1.0);
                    vec4 contactCurrClip = activeProj * activeView * vec4(vWorldPos, 1.0);
                    vec4 contactPrevClip = activePrevViewProj * (obj.uPrevModel * vec4(vLocalPos, 1.0));
                    float contactCurrW = abs(contactCurrClip.w) > 0.000001 ? contactCurrClip.w : 1.0;
                    float contactPrevW = abs(contactPrevClip.w) > 0.000001 ? contactPrevClip.w : 1.0;
                    vec2 contactMotionNdc = (contactPrevClip.xy / contactPrevW) - (contactCurrClip.xy / contactCurrW);
                    float contactMotionMag = length(clamp(contactMotionNdc, vec2(-1.0), vec2(1.0)));
                    vec3 prevWorldPos = (obj.uPrevModel * vec4(vLocalPos, 1.0)).xyz;
                    float contactDepthGrad = clamp(length(vec2(dFdx(vWorldPos.z), dFdy(vWorldPos.z))) * 22.0, 0.0, 1.0);
                    float contactNormalGrad = clamp((length(dFdx(n)) + length(dFdy(n))) * 0.85, 0.0, 1.0);
                    float contactHistoryProxy = clamp(1.0 - length(vWorldPos - prevWorldPos) * 4.0, 0.0, 1.0);
                    float contactReject = clamp(max(contactMotionMag * (1.05 + taaBlend), max(contactDepthGrad, contactNormalGrad)), 0.0, 1.0);
                    float contactTemporalStability = mix(
                            1.0,
                            clamp(1.0 - contactMotionMag * (0.85 + taaBlend * 0.85) * contactTemporalMotionScale, contactTemporalMinStability, 1.0),
                            taaEnabled
                    );
                    contactTemporalStability = mix(
                            contactTemporalStability,
                            max(contactTemporalMinStability, contactTemporalStability * (1.0 - 0.55 * contactReject)),
                            taaEnabled
                    );
                    float contactTemporalHistoryWeight = mix(contactHistoryProxy, 1.0 - contactReject, 0.58);

                    float ndl = max(dot(n, lDir), 0.0);
                    float ndv = max(dot(n, viewDir), 0.0);
                    vec3 halfVec = normalize(lDir + viewDir);
                    float ndh = max(dot(n, halfVec), 0.0);
                    float vdh = max(dot(viewDir, halfVec), 0.0);
                    vec3 f0 = mix(vec3(0.04), baseColor, metallic);
                    vec3 f = fresnelSchlick(vdh, f0);
                    float d = distributionGGX(ndh, roughness);
                    float g = geometrySmith(ndv, ndl, roughness);
                    vec3 numerator = d * g * f;
                    float denominator = max(4.0 * ndv * ndl, 0.0001);
                    vec3 specular = numerator / denominator;
                    vec3 kd = (1.0 - f) * (1.0 - metallic);
                    vec3 diffuse = kd * baseColor / 3.14159;
                    vec3 directional = (diffuse + specular) * gbo.uDirLightColor.rgb * (ndl * dirIntensity);
                    vec3 pointLit = vec3(0.0);
                    int localCount = clamp(int(gbo.uLocalLightMeta.x + 0.5), 0, 8);
                    int localShadowSlots = clamp(int(gbo.uLocalLightMeta.y + 0.5), 0, 24);
                    for (int i = 0; i < localCount; i++) {
                        vec3 localPos = gbo.uLocalLightPosRange[i].xyz;
                        float localRange = max(gbo.uLocalLightPosRange[i].w, 0.1);
                        vec3 localColor = gbo.uLocalLightColorIntensity[i].rgb;
                        float localIntensity = max(gbo.uLocalLightColorIntensity[i].a, 0.0);
                        vec3 localDir = normalize(gbo.uLocalLightDirInner[i].xyz);
                        float localInner = gbo.uLocalLightDirInner[i].w;
                        float localOuter = gbo.uLocalLightOuterTypeShadow[i].x;
                        float localIsSpot = gbo.uLocalLightOuterTypeShadow[i].y;
                        float localCastsShadow = gbo.uLocalLightOuterTypeShadow[i].z;
                        int localShadowLayer = clamp(int(gbo.uLocalLightOuterTypeShadow[i].w + 0.5) - 1, -1, 23);
                        vec3 localToLight = localPos - vWorldPos;
                        vec3 localLightDir = normalize(localToLight);
                        float localDist = max(length(localToLight), 0.1);
                        float normalizedDistance = clamp(localDist / localRange, 0.0, 1.0);
                        float rangeFade = 1.0 - pow(normalizedDistance, 4.0);
                        rangeFade = clamp(rangeFade * rangeFade, 0.0, 1.0);
                        float attenuation = (1.0 / (1.0 + 0.35 * localDist + 0.1 * localDist * localDist)) * rangeFade;
                        float localNdl = max(dot(n, localLightDir), 0.0);
                        float spotAttenuation = 1.0;
                        if (localIsSpot > 0.5) {
                            vec3 lightToFrag = normalize(vWorldPos - localPos);
                            float cosTheta = dot(localDir, lightToFrag);
                            float coneRange = max(localInner - localOuter, 0.0001);
                            spotAttenuation = clamp((cosTheta - localOuter) / coneRange, 0.0, 1.0);
                            spotAttenuation *= spotAttenuation;
                        }
                        float localShadowVisibility = 1.0;
                        if (localIsSpot > 0.5 && localCastsShadow > 0.5 && localShadowSlots > 0 && localShadowLayer >= 0) {
                            vec4 localShadowPos = gbo.uShadowLightViewProj[localShadowLayer] * vec4(vWorldPos, 1.0);
                            vec3 localShadowCoord = localShadowPos.xyz / max(localShadowPos.w, 0.0001);
                            localShadowCoord = localShadowCoord * 0.5 + 0.5;
                            if (localShadowCoord.z > 0.0
                                    && localShadowCoord.z < 1.0
                                    && localShadowCoord.x >= 0.0 && localShadowCoord.x <= 1.0
                                    && localShadowCoord.y >= 0.0 && localShadowCoord.y <= 1.0) {
                                int localRadius = clamp(int(gbo.uShadow.w + 0.5), 0, 4);
                                if (shadowFilterMode == 1) {
                                    localRadius = clamp(localRadius + int(clamp(localShadowCoord.z * 3.0, 0.0, 2.0)), 0, 4);
                                } else if (shadowFilterMode == 2 || shadowFilterMode == 3) {
                                    localRadius = clamp(localRadius + 1, 0, 4);
                                }
                                float texel = 1.0 / max(gbo.uShadowCascade.y, 1.0);
                                float compareDepth = clamp(localShadowCoord.z - gbo.uShadow.z, 0.0, 1.0);
                                float total = 0.0;
                                float taps = 0.0;
                                for (int y = -4; y <= 4; y++) {
                                    for (int x = -4; x <= 4; x++) {
                                        if (abs(x) > localRadius || abs(y) > localRadius) {
                                            continue;
                                        }
                                        vec2 offset = vec2(float(x), float(y)) * texel;
                                        total += texture(uShadowMap, vec4(localShadowCoord.xy + offset, float(localShadowLayer), compareDepth));
                                        taps += 1.0;
                                    }
                                }
                                float localPcfVisibility = (taps > 0.0) ? (total / taps) : 1.0;
                                localShadowVisibility = finalizeShadowVisibility(
                                        localPcfVisibility,
                                        shadowFilterMode,
                                        shadowRtEnabled,
                                        shadowRtMode,
                                        shadowRtSampleCount,
                                        shadowRtDenoiseStrength,
                                        shadowRtRayLength,
                                        localShadowCoord.xy,
                                        compareDepth,
                                        localShadowLayer,
                                        localNdl,
                                        localShadowCoord.z,
                                        pcssSoftness,
                                        contactTemporalStability
                                );
                            }
                        }
                        if (localIsSpot <= 0.5 && localCastsShadow > 0.5 && localShadowSlots > 0 && localShadowLayer >= 0) {
                            vec3 pointVec = normalize(vWorldPos - localPos);
                            int pointLayer = localShadowLayer;
                            if (localShadowLayer + 5 <= 23) {
                                vec3 absVec = abs(pointVec);
                                if (absVec.x >= absVec.y && absVec.x >= absVec.z) {
                                    pointLayer = localShadowLayer + (pointVec.x >= 0.0 ? 0 : 1);
                                } else if (absVec.y >= absVec.z) {
                                    pointLayer = localShadowLayer + (pointVec.y >= 0.0 ? 2 : 3);
                                } else {
                                    pointLayer = localShadowLayer + (pointVec.z >= 0.0 ? 4 : 5);
                                }
                            }
                            vec4 localShadowPos = gbo.uShadowLightViewProj[pointLayer] * vec4(vWorldPos, 1.0);
                            vec3 localShadowCoord = localShadowPos.xyz / max(localShadowPos.w, 0.0001);
                            localShadowCoord = localShadowCoord * 0.5 + 0.5;
                            if (localShadowCoord.z > 0.0
                                    && localShadowCoord.z < 1.0
                                    && localShadowCoord.x >= 0.0 && localShadowCoord.x <= 1.0
                                    && localShadowCoord.y >= 0.0 && localShadowCoord.y <= 1.0) {
                                int localRadius = clamp(int(gbo.uShadow.w + 0.5), 0, 4);
                                if (shadowFilterMode == 1) {
                                    localRadius = clamp(localRadius + int(clamp(localShadowCoord.z * 3.0, 0.0, 2.0)), 0, 4);
                                } else if (shadowFilterMode == 2 || shadowFilterMode == 3) {
                                    localRadius = clamp(localRadius + 1, 0, 4);
                                }
                                float texel = 1.0 / max(gbo.uShadowCascade.y, 1.0);
                                float compareDepth = clamp(localShadowCoord.z - gbo.uShadow.z, 0.0, 1.0);
                                float total = 0.0;
                                float taps = 0.0;
                                for (int y = -4; y <= 4; y++) {
                                    for (int x = -4; x <= 4; x++) {
                                        if (abs(x) > localRadius || abs(y) > localRadius) {
                                            continue;
                                        }
                                        vec2 offset = vec2(float(x), float(y)) * texel;
                                        total += texture(uShadowMap, vec4(localShadowCoord.xy + offset, float(pointLayer), compareDepth));
                                        taps += 1.0;
                                    }
                                }
                                float pointPcfVisibility = (taps > 0.0) ? (total / taps) : 1.0;
                                float pointLocalVisibility = finalizeShadowVisibility(
                                        pointPcfVisibility,
                                        shadowFilterMode,
                                        shadowRtEnabled,
                                        shadowRtMode,
                                        shadowRtSampleCount,
                                        shadowRtDenoiseStrength,
                                        shadowRtRayLength,
                                        localShadowCoord.xy,
                                        compareDepth,
                                        pointLayer,
                                        localNdl,
                                        localShadowCoord.z,
                                        pcssSoftness,
                                        contactTemporalStability
                                );
                                localShadowVisibility *= pointLocalVisibility;
                            }
                        }
                        float contact = 1.0;
                        if (contactShadows) {
                            float depthEdge = clamp(length(vec2(dFdx(gl_FragCoord.z), dFdy(gl_FragCoord.z))) * 220.0, 0.0, 1.0);
                            float normalEdge = clamp((length(dFdx(n)) + length(dFdy(n))) * 0.55, 0.0, 1.0);
                            float viewGrazing = clamp(pow(1.0 - ndv, 2.0), 0.0, 1.0);
                            float distFade = clamp(1.0 - normalizedDistance, 0.0, 1.0);
                            float contactStrength = (1.0 - localNdl)
                                    * (0.18 + 0.18 * depthEdge + 0.14 * normalEdge)
                                    * (1.0 - roughness)
                                    * (0.58 + 0.42 * viewGrazing)
                                    * distFade
                                    * contactStrengthScale
                                    * contactTemporalStability;
                            contactStrength *= mix(1.0, 0.78, contactTemporalHistoryWeight);
                            contact = clamp(1.0 - contactStrength, 0.50, 1.0);
                        }
                        pointLit += (kd * baseColor / 3.14159) * localColor * (localNdl * attenuation * spotAttenuation * localIntensity * localShadowVisibility * contact);
                    }
                    vec3 ambient = (0.08 + 0.1 * (1.0 - roughness)) * baseColor * ao;

                    vec3 color = ambient + directional + pointLit;
                    if (gbo.uShadow.x > 0.5 && gbo.uPointLightCone.w < 0.5 && localShadowSlots == 0) {
                        int cascadeCount = clamp(int(gbo.uShadowCascade.x + 0.5), 1, 4);
                        int cascadeIndex = 0;
                        float depthNdc = clamp(gl_FragCoord.z, 0.0, 1.0);
                        if (cascadeCount >= 2 && depthNdc > gbo.uShadowCascade.z) {
                            cascadeIndex = 1;
                        }
                        if (cascadeCount >= 3 && depthNdc > gbo.uShadowCascade.w) {
                            cascadeIndex = 2;
                        }
                        if (cascadeCount >= 4 && depthNdc > gbo.uShadowCascadeExt.y) {
                            cascadeIndex = 3;
                        }
                        vec4 shadowPos = gbo.uShadowLightViewProj[cascadeIndex] * vec4(vWorldPos, 1.0);
                        vec3 shadowCoord = shadowPos.xyz / max(shadowPos.w, 0.0001);
                        shadowCoord = shadowCoord * 0.5 + 0.5;
                        float shadowVisibility = 1.0;
                        if (shadowCoord.z > 0.0
                                && shadowCoord.z < 1.0
                                && shadowCoord.x >= 0.0 && shadowCoord.x <= 1.0
                                && shadowCoord.y >= 0.0 && shadowCoord.y <= 1.0) {
                            float cascadeT = float(cascadeIndex) / max(float(cascadeCount - 1), 1.0);
                            int radius = clamp(int(gbo.uShadow.w + 0.5) + (cascadeIndex / 2), 0, 4);
                            if (shadowFilterMode == 1) {
                                radius = clamp(radius + int(clamp(shadowCoord.z * 3.0, 0.0, 2.0)), 0, 4);
                            } else if (shadowFilterMode == 2 || shadowFilterMode == 3) {
                                radius = clamp(radius + 1, 0, 4);
                            }
                            float texel = (1.0 / max(gbo.uShadowCascade.y, 1.0)) * mix(1.0, 2.25, cascadeT);
                            float normalBiasScale = max(gbo.uShadowCascadeExt.z, 0.25);
                            float slopeBiasScale = max(gbo.uShadowCascadeExt.w, 0.25);
                            float compareBias = (gbo.uShadow.z * normalBiasScale) * mix(0.7, 1.8 * slopeBiasScale, cascadeT);
                            float compareDepth = clamp(shadowCoord.z - compareBias, 0.0, 1.0);
                            float total = 0.0;
                            float taps = 0.0;
                            for (int y = -4; y <= 4; y++) {
                                for (int x = -4; x <= 4; x++) {
                                    if (abs(x) > radius || abs(y) > radius) {
                                        continue;
                                    }
                                    vec2 offset = vec2(float(x), float(y)) * texel;
                                    total += texture(uShadowMap, vec4(shadowCoord.xy + offset, float(cascadeIndex), compareDepth));
                                    taps += 1.0;
                                }
                            }
                            float pcfVisibility = (taps > 0.0) ? (total / taps) : 1.0;
                            shadowVisibility = finalizeShadowVisibility(
                                    pcfVisibility,
                                    shadowFilterMode,
                                    shadowRtEnabled,
                                    shadowRtMode,
                                    shadowRtSampleCount,
                                    shadowRtDenoiseStrength,
                                    shadowRtRayLength,
                                    shadowCoord.xy,
                                    compareDepth,
                                    cascadeIndex,
                                    ndl,
                                    shadowCoord.z,
                                    pcssSoftness,
                                    contactTemporalStability
                            );
                        }
                        float shadowOcclusion = 1.0 - shadowVisibility;
                        float shadowFactor = clamp(shadowOcclusion * clamp(gbo.uShadow.y, 0.0, 1.0), 0.0, 0.9);
                        color *= (1.0 - shadowFactor);
                        if (contactShadows) {
                            float contactEdge = clamp(length(dFdx(n)) + length(dFdy(n)), 0.0, 1.0);
                            float contactFactor = shadowOcclusion * contactEdge * (1.0 - roughness) * (1.0 - ndl) * contactStrengthScale
                                    * contactTemporalStability * mix(1.0, 0.80, contactTemporalHistoryWeight);
                            color *= (1.0 - clamp(contactFactor * 0.22, 0.0, 0.24));
                        }
                    }
                    if (gbo.uPointLightCone.w > 0.5) {
                        vec3 pDir = normalize(gbo.uPointLightPos.xyz - vWorldPos);
                        float pNdl = max(dot(n, pDir), 0.0);
                        float dist = max(length(gbo.uPointLightPos.xyz - vWorldPos), 0.1);
                        int pointLayerCount = clamp(int(gbo.uShadowCascade.x + 0.5), 1, 6);
                        vec3 pointVec = normalize(vWorldPos - gbo.uPointLightPos.xyz);
                        int pointLayer = 0;
                        if (pointLayerCount >= 6) {
                            vec3 absVec = abs(pointVec);
                            if (absVec.x >= absVec.y && absVec.x >= absVec.z) {
                                pointLayer = pointVec.x >= 0.0 ? 0 : 1;
                            } else if (absVec.y >= absVec.z) {
                                pointLayer = pointVec.y >= 0.0 ? 2 : 3;
                            } else {
                                pointLayer = pointVec.z >= 0.0 ? 4 : 5;
                            }
                        } else if (pointLayerCount >= 4) {
                            if (abs(pointVec.x) >= abs(pointVec.z)) {
                                pointLayer = pointVec.x >= 0.0 ? 0 : 1;
                            } else {
                                pointLayer = pointVec.z >= 0.0 ? 2 : 3;
                            }
                        } else if (pointLayerCount == 3) {
                            if (abs(pointVec.x) >= abs(pointVec.z)) {
                                pointLayer = pointVec.x >= 0.0 ? 0 : 1;
                            } else {
                                pointLayer = 2;
                            }
                        } else if (pointLayerCount == 2) {
                            pointLayer = pointVec.x >= 0.0 ? 0 : 1;
                        }
                        vec4 pointShadowPos = gbo.uShadowLightViewProj[pointLayer] * vec4(vWorldPos, 1.0);
                        vec3 pointShadowCoord = pointShadowPos.xyz / max(pointShadowPos.w, 0.0001);
                        pointShadowCoord = pointShadowCoord * 0.5 + 0.5;
                        if (pointShadowCoord.z > 0.0
                                && pointShadowCoord.z < 1.0
                                && pointShadowCoord.x >= 0.0 && pointShadowCoord.x <= 1.0
                                && pointShadowCoord.y >= 0.0 && pointShadowCoord.y <= 1.0) {
                            float pointDepthRatio = clamp(dist / max(gbo.uPointLightPos.w, 0.0001), 0.0, 1.0);
                            int pointRadius = clamp(int(gbo.uShadow.w + 0.5), 0, 4);
                            if (shadowFilterMode == 1) {
                                pointRadius = clamp(pointRadius + int(clamp(pointDepthRatio * 3.0, 0.0, 2.0)), 0, 4);
                            } else if (shadowFilterMode == 2 || shadowFilterMode == 3) {
                                pointRadius = clamp(pointRadius + 1, 0, 4);
                            }
                            float texel = (1.0 / max(gbo.uShadowCascade.y, 1.0)) * mix(0.85, 2.0, pointDepthRatio);
                            float normalBiasScale = max(gbo.uShadowCascadeExt.z, 0.25);
                            float slopeBiasScale = max(gbo.uShadowCascadeExt.w, 0.25);
                            float compareBias = (gbo.uShadow.z * normalBiasScale) * mix(0.85, 1.65, pointDepthRatio) * (1.0 + (1.0 - pNdl) * 0.6 * slopeBiasScale);
                            float compareDepth = clamp(pointShadowCoord.z - compareBias, 0.0, 1.0);
                            float visibility = 0.0;
                            float taps = 0.0;
                            for (int y = -4; y <= 4; y++) {
                                for (int x = -4; x <= 4; x++) {
                                    if (abs(x) > pointRadius || abs(y) > pointRadius) {
                                        continue;
                                    }
                                    vec2 offset = vec2(float(x), float(y)) * texel;
                                    visibility += texture(uShadowMap, vec4(pointShadowCoord.xy + offset, float(pointLayer), compareDepth));
                                    taps += 1.0;
                                }
                            }
                            float pointPcfVisibility = (taps > 0.0) ? (visibility / taps) : 1.0;
                            float pointVisibility = finalizeShadowVisibility(
                                    pointPcfVisibility,
                                    shadowFilterMode,
                                    shadowRtEnabled,
                                    shadowRtMode,
                                    shadowRtSampleCount,
                                    shadowRtDenoiseStrength,
                                    shadowRtRayLength,
                                    pointShadowCoord.xy,
                                    compareDepth,
                                    pointLayer,
                                    pNdl,
                                    pointDepthRatio,
                                    pcssSoftness,
                                    contactTemporalStability
                            );
                            float pointOcclusion = 1.0 - pointVisibility;
                            float pointShadowFactor = clamp(pointOcclusion * min(clamp(gbo.uShadow.y, 0.0, 1.0), 0.85), 0.0, 0.9);
                            color *= (1.0 - pointShadowFactor);
                            if (contactShadows) {
                                float contactEdge = clamp((length(dFdx(n)) + length(dFdy(n))) * 0.9, 0.0, 1.0);
                                float contactFactor = pointOcclusion * contactEdge * (1.0 - roughness) * (1.0 - pNdl) * contactStrengthScale
                                        * contactTemporalStability * mix(1.0, 0.80, contactTemporalHistoryWeight);
                                color *= (1.0 - clamp(contactFactor * 0.20, 0.0, 0.22));
                            }
                        }
                    }
                    if (gbo.uFog.x > 0.5) {
                        float normalizedHeight = clamp((vHeight + 1.0) * 0.5, 0.0, 1.0);
                        float fogFactor = clamp(exp(-gbo.uFog.y * (1.0 - normalizedHeight)), 0.0, 1.0);
                        if (gbo.uFogColorSteps.w > 0.0) {
                            fogFactor = floor(fogFactor * gbo.uFogColorSteps.w) / gbo.uFogColorSteps.w;
                        }
                        color = mix(gbo.uFogColorSteps.rgb, color, fogFactor);
                    }
                    if (gbo.uSmoke.x > 0.5) {
                        vec2 safeViewport = max(gbo.uSmoke.zw, vec2(1.0));
                        float radial = clamp(1.0 - length(gl_FragCoord.xy / safeViewport - vec2(0.5)), 0.0, 1.0);
                        float smokeFactor = clamp(gbo.uSmoke.y * (0.35 + radial * 0.65), 0.0, 0.85);
                        color = mix(color, gbo.uSmokeColor.rgb, smokeFactor);
                    }
                    if (gbo.uIbl.x > 0.5) {
                        float iblDiffuseWeight = clamp(gbo.uIbl.y, 0.0, 2.0);
                        float iblSpecWeight = clamp(gbo.uIbl.z, 0.0, 2.0);
                        float prefilter = clamp(gbo.uIbl.w, 0.0, 1.0);
                        vec3 irr = texture(uIblIrradianceTexture, vUv).rgb;
                        vec3 reflectDir = reflect(-viewDir, n);
                        vec2 specUv = clamp(reflectDir.xy * 0.5 + vec2(0.5), vec2(0.0), vec2(1.0));
                        vec3 rad = sampleIblRadiance(specUv, vUv, roughness, prefilter);
                        int probeCount = clamp(probes.uProbeHeader.x, 0, 64);
                        int probeAtlasLayerCount = max(probes.uProbeHeader.y, 1);
                        if (probeCount > 0) {
                            vec3 probeAccum = vec3(0.0);
                            float probeWeightSum = 0.0;
                            float remainingCoverage = 1.0;
                            for (int i = 0; i < probeCount; i++) {
                                if (remainingCoverage <= 0.0) {
                                    break;
                                }
                                ProbeData probe = probes.uProbes[i];
                                if (probe.cubemapIndexAndFlags.x < 0) {
                                    continue;
                                }
                                float weight = probeWeightAtWorldPos(vWorldPos, probe);
                                if (weight <= 0.0) {
                                    continue;
                                }
                                float contribution = min(weight, remainingCoverage);
                                if (contribution <= 0.0) {
                                    continue;
                                }
                                vec3 probeDir = probeSampleDirection(vWorldPos, reflectDir, probe);
                                vec2 probeUv = clamp(probeDir.xy * 0.5 + vec2(0.5), vec2(0.0), vec2(1.0));
                                float probeRoughness = clamp(roughness + float(clamp(probe.cubemapIndexAndFlags.w, 0, 3)) * 0.18, 0.04, 1.0);
                                vec3 probeRad = sampleProbeRadiance(
                                        probeUv,
                                        vUv,
                                        probeRoughness,
                                        prefilter,
                                        probe.cubemapIndexAndFlags.x,
                                        probeAtlasLayerCount
                                );
                                probeAccum += probeRad * contribution;
                                probeWeightSum += contribution;
                                remainingCoverage -= contribution;
                            }
                            if (probeWeightSum > 0.0) {
                                vec3 blendedProbeRad = probeAccum / probeWeightSum;
                                float probeBlend = clamp(probeWeightSum, 0.0, 1.0);
                                rad = mix(rad, blendedProbeRad, probeBlend);
                            }
                        }
                        vec2 brdfUv = vec2(clamp(ndv, 0.0, 1.0), clamp(roughness, 0.0, 1.0));
                        vec2 brdf = texture(uIblBrdfLutTexture, brdfUv).rg;
                        float fresnel = pow(1.0 - ndv, 5.0);
                        vec3 fView = mix(vec3(0.03), f0, fresnel);
                        vec3 kS = clamp(fView, vec3(0.0), vec3(1.0));
                        vec3 kD = (vec3(1.0) - kS) * (1.0 - metallic);
                        float ndvEdge = 1.0 - ndv;
                        float grazing = ndvEdge * ndvEdge;
                        float horizon = clamp(0.35 + 0.65 * ndv, 0.0, 1.0);
                        float energyComp = 1.0 + (1.0 - roughness) * (0.30 + 0.20 * grazing) * ndvEdge;
                        float roughEnergy = mix(1.12, 0.74, roughness);
                        float brdfDiffuseLift = mix(0.84, 1.16, brdf.y);
                        float brdfSpecLift = mix(0.88, 1.12, brdf.x);
                        float roughEdge = smoothstep(0.02, 0.10, roughness) * (1.0 - smoothstep(0.90, 0.99, roughness));
                        vec3 iblDiffuse = kD * baseColor * ao * irr
                                * (0.22 + 0.58 * (1.0 - roughness))
                                * iblDiffuseWeight
                                * brdfDiffuseLift
                                * mix(0.90, 1.00, roughEdge);
                        float specLobe = mix(1.08, 0.64, roughness * roughness);
                        vec3 iblSpecBase = rad * ((kS * (0.30 + 0.70 * brdf.x) + vec3(0.16 + 0.30 * brdf.y)) * brdfSpecLift);
                        vec3 iblSpec = iblSpecBase
                                * (0.10 + 0.66 * (1.0 - roughness))
                                * iblSpecWeight
                                * energyComp
                                * horizon
                                * roughEnergy
                                * specLobe
                                * mix(0.92, 1.08, prefilter)
                                * mix(0.82, 1.00, roughEdge);
                        color += iblDiffuse + iblSpec;
                    }
                    if (gbo.uPostProcess.x > 0.5) {
                        float exposure = max(gbo.uPostProcess.y, 0.0001);
                        float gamma = max(gbo.uPostProcess.z, 0.0001);
                        color = vec3(1.0) - exp(-color * exposure);
                        color = pow(max(color, vec3(0.0)), vec3(1.0 / gamma));
                    }
                    if (gbo.uBloom.x > 0.5) {
                        float threshold = clamp(gbo.uBloom.y, 0.0, 4.0);
                        float strength = clamp(gbo.uBloom.z, 0.0, 2.0);
                        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                        float bright = max(0.0, luma - threshold);
                        float bloom = bright * strength;
                        color += color * bloom;
                    }
                    if (gbo.uPostProcess.w > 0.5) {
                        float depthDx = dFdx(gl_FragCoord.z);
                        float depthDy = dFdy(gl_FragCoord.z);
                        float depthDelta = length(vec2(depthDx, depthDy));
                        float normalDelta = length(dFdx(n)) + length(dFdy(n));
                        float curvature = clamp((depthDelta * 230.0) + (normalDelta * 0.42), 0.0, 1.0);
                        float micro = clamp((abs(dFdx(depthDx)) + abs(dFdy(depthDy))) * 3600.0, 0.0, 1.0);
                        float edge = clamp(curvature * 0.8 + micro * 0.2, 0.0, 1.0);
                        float ssaoStrength = clamp(gbo.uBloom.w, 0.0, 1.0);
                        float ssaoRadius = clamp(gbo.uFog.z, 0.2, 3.0);
                        float ssaoBias = clamp(gbo.uFog.w, 0.0, 0.2);
                        float ssaoPower = clamp(gbo.uSmokeColor.w, 0.5, 4.0);
                        float shapedEdge = clamp(edge * mix(0.75, 1.25, (ssaoRadius - 0.2) / 2.8) - ssaoBias, 0.0, 1.0);
                        float occlusion = pow(clamp(shapedEdge * ssaoStrength, 0.0, 0.92), max(0.60, 1.25 - (ssaoPower * 0.32)));
                        color *= (1.0 - occlusion * 0.82);
                    }
                    if (gbo.uAntiAlias.x > 0.5) {
                        float aaStrength = clamp(gbo.uAntiAlias.y, 0.0, 1.0);
                        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                        float edgeDx = abs(dFdx(luma));
                        float edgeDy = abs(dFdy(luma));
                        float edge = clamp((edgeDx + edgeDy) * 5.5, 0.0, 1.0);
                        color = mix(color, vec3(luma), edge * aaStrength * 0.20);
                    }
                    float emissiveMask = smoothstep(0.72, 0.97, dot(baseColor, vec3(0.2126, 0.7152, 0.0722))) * (1.0 - roughness) * 0.6;
                    float alphaTestMask = 1.0 - smoothstep(0.78, 0.98, sampledAlpha);
                    float foliageMask = smoothstep(0.06, 0.42, baseColor.g - max(baseColor.r, baseColor.b));
                    float specularMask = clamp((1.0 - roughness) * mix(0.35, 1.0, metallic), 0.0, 1.0);
                    float heuristicReactive = clamp(max(alphaTestMask, foliageMask) * 0.85 + specularMask * 0.30 + emissiveMask, 0.0, 1.0);
                    float authoredReactive = clamp(
                            reactiveStrength * reactiveBoost * (1.0 + 0.65 * max(alphaTested ? 1.0 : 0.0, foliage ? 1.0 : 0.0)),
                            0.0,
                            1.0
                    );
                    bool authoredEnabled = (reactiveStrength > 0.001) || alphaTested || foliage;
                    heuristicReactive = clamp(heuristicReactive + emissiveMask * emissiveReactiveBoost * 0.45, 0.0, 1.0);
                    float presetScale = reactivePreset < 0.5 ? 1.0 : (reactivePreset < 1.5 ? 0.82 : (reactivePreset < 2.5 ? 1.0 : 1.2));
                    float materialReactive = (authoredEnabled ? authoredReactive : heuristicReactive) * (1.0 + (1.0 - taaHistoryClamp) * 0.6) * presetScale;
                    float reflectionMask = float(clamp(reflectionOverrideMode, 0, 3)) / 3.0;
                    outColor = vec4(clamp(color, 0.0, 1.0), reflectionMask);
                    vec4 currClip = activeProj * activeView * vec4(vWorldPos, 1.0);
                    vec4 prevClip = activePrevViewProj * (obj.uPrevModel * vec4(vLocalPos, 1.0));
                    float currW = abs(currClip.w) > 0.000001 ? currClip.w : 1.0;
                    float prevW = abs(prevClip.w) > 0.000001 ? prevClip.w : 1.0;
                    vec2 currNdc = currClip.xy / currW;
                    vec2 prevNdc = prevClip.xy / prevW;
                    vec2 velocityNdc = clamp(prevNdc - currNdc, vec2(-1.0), vec2(1.0));
                outVelocity = vec4(velocityNdc * 0.5 + 0.5, clamp(gl_FragCoord.z, 0.0, 1.0), clamp(materialReactive, 0.0, 1.0));
                }
            """;
}
