package org.dynamislight.impl.vulkan.shader;

final class VulkanMainFragmentShaderPart2 {
    private VulkanMainFragmentShaderPart2() {
    }
    static final String TEXT = """
                float bvhTraversalVisibilityApprox(
                        vec2 uv,
                        float texel,
                        int layer,
                        float compareDepth,
                        float ndl,
                        float depthRatio,
                        int shadowRtSampleCount,
                        float shadowRtDenoiseStrength,
                        float shadowRtRayLength
                ) {
                    float rayScale = clamp(shadowRtRayLength / 120.0, 0.45, 4.5);
                    int rtSteps = clamp(shadowRtSampleCount * 3, 8, 36);
                    vec2 axisA = normalize(vec2(0.63 + (1.0 - ndl) * 0.42, 0.41 + depthRatio * 0.58));
                    vec2 axisB = vec2(-axisA.y, axisA.x);
                    float accum = 0.0;
                    float weightSum = 0.0;
                    for (int i = 0; i < rtSteps; i++) {
                        float t = (float(i) + 0.5) / float(rtSteps);
                        float stride = mix(0.6, 9.0, t * t) * rayScale;
                        float arc = mix(-1.0, 1.0, t);
                        vec2 rayDir = normalize(mix(axisA, axisB, arc * arc * 0.55 * sign(arc)));
                        vec2 sampleUv = clamp(uv + rayDir * texel * stride, vec2(0.0), vec2(1.0));
                        float sampleVis = texture(uShadowMap, vec4(sampleUv, float(layer), compareDepth));
                        float weight = mix(1.0, 0.25, t) * mix(1.0, 0.70, abs(arc));
                        accum += sampleVis * weight;
                        weightSum += weight;
                    }
                    float traversal = weightSum > 0.0 ? (accum / weightSum) : 1.0;
                    float n = texture(uShadowMap, vec4(clamp(uv + vec2(0.0, texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float s = texture(uShadowMap, vec4(clamp(uv + vec2(0.0, -texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float e = texture(uShadowMap, vec4(clamp(uv + vec2(texel, 0.0), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float w = texture(uShadowMap, vec4(clamp(uv + vec2(-texel, 0.0), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float ne = texture(uShadowMap, vec4(clamp(uv + vec2(texel, texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float nw = texture(uShadowMap, vec4(clamp(uv + vec2(-texel, texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float se = texture(uShadowMap, vec4(clamp(uv + vec2(texel, -texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float sw = texture(uShadowMap, vec4(clamp(uv + vec2(-texel, -texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float cross = (n + s + e + w) * 0.25;
                    float diag = (ne + nw + se + sw) * 0.25;
                    float neighborhood = mix(cross, diag, 0.45);
                    float denoise = mix(0.28, 0.72, shadowRtDenoiseStrength);
                    return clamp(mix(traversal, neighborhood, denoise), 0.0, 1.0);
                }
                float bvhDedicatedTraversalVisibilityApprox(
                        vec2 uv,
                        float texel,
                        int layer,
                        float compareDepth,
                        float ndl,
                        float depthRatio,
                        int shadowRtSampleCount,
                        float shadowRtDenoiseStrength,
                        float shadowRtRayLength
                ) {
                    float rayScale = clamp(shadowRtRayLength / 120.0, 0.55, 5.0);
                    int rtSteps = clamp(shadowRtSampleCount * 4, 12, 48);
                    vec2 axisA = normalize(vec2(0.68 + (1.0 - ndl) * 0.55, 0.36 + depthRatio * 0.62));
                    vec2 axisB = vec2(-axisA.y, axisA.x);
                    float accum = 0.0;
                    float weightSum = 0.0;
                    for (int i = 0; i < rtSteps; i++) {
                        float t = (float(i) + 0.5) / float(rtSteps);
                        float stride = mix(0.5, 10.0, t * t) * rayScale;
                        float fan = mix(-1.0, 1.0, t);
                        vec2 fanDir = normalize(mix(axisA, axisB, fan * 0.72));
                        vec2 sampleUv = clamp(uv + fanDir * texel * stride, vec2(0.0), vec2(1.0));
                        float sampleVis = texture(uShadowMap, vec4(sampleUv, float(layer), compareDepth));
                        float w = mix(1.0, 0.18, t) * mix(1.0, 0.62, abs(fan));
                        accum += sampleVis * w;
                        weightSum += w;
                    }
                    float traversal = weightSum > 0.0 ? (accum / weightSum) : 1.0;
                    float n = texture(uShadowMap, vec4(clamp(uv + vec2(0.0, texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float s = texture(uShadowMap, vec4(clamp(uv + vec2(0.0, -texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float e = texture(uShadowMap, vec4(clamp(uv + vec2(texel, 0.0), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float w = texture(uShadowMap, vec4(clamp(uv + vec2(-texel, 0.0), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float ne = texture(uShadowMap, vec4(clamp(uv + vec2(texel, texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float nw = texture(uShadowMap, vec4(clamp(uv + vec2(-texel, texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float se = texture(uShadowMap, vec4(clamp(uv + vec2(texel, -texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float sw = texture(uShadowMap, vec4(clamp(uv + vec2(-texel, -texel), vec2(0.0), vec2(1.0)), float(layer), compareDepth));
                    float ring1 = (n + s + e + w) * 0.25;
                    float ring2 = (ne + nw + se + sw) * 0.25;
                    float wide = mix(ring1, ring2, 0.52);
                    float denoise = mix(0.34, 0.84, shadowRtDenoiseStrength);
                    return clamp(mix(traversal, wide, denoise), 0.0, 1.0);
                }
                float rtDedicatedDenoiseStack(
                        float baseVisibility,
                        vec2 uv,
                        float texel,
                        int layer,
                        float compareDepth,
                        float shadowRtDenoiseStrength,
                        float shadowTemporalStability
                ) {
                    float ringNear = 0.0;
                    float ringNearW = 0.0;
                    float ringFar = 0.0;
                    float ringFarW = 0.0;
                    for (int i = 0; i < 8; i++) {
                        float a = (6.2831853 * float(i)) / 8.0;
                        vec2 dir = vec2(cos(a), sin(a));
                        vec2 nearUv = clamp(uv + dir * texel * 1.8, vec2(0.0), vec2(1.0));
                        vec2 farUv = clamp(uv + dir * texel * 4.0, vec2(0.0), vec2(1.0));
                        float wNear = mix(1.0, 0.62, float(i & 1));
                        float wFar = mix(0.70, 0.40, float(i & 1));
                        ringNear += texture(uShadowMap, vec4(nearUv, float(layer), compareDepth)) * wNear;
                        ringNearW += wNear;
                        ringFar += texture(uShadowMap, vec4(farUv, float(layer), compareDepth)) * wFar;
                        ringFarW += wFar;
                    }
                    float nearAvg = ringNear / max(ringNearW, 0.0001);
                    float farAvg = ringFar / max(ringFarW, 0.0001);
                    float stageA = mix(baseVisibility, nearAvg, clamp(0.30 + 0.35 * shadowRtDenoiseStrength, 0.10, 0.75));
                    float stageB = mix(stageA, farAvg, clamp(0.16 + 0.34 * shadowRtDenoiseStrength * (1.0 - shadowTemporalStability), 0.06, 0.45));
                    return clamp(stageB, 0.0, 1.0);
                }
            """;
}
