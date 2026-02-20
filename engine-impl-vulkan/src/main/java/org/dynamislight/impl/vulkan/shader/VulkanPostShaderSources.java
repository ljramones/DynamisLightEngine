package org.dynamislight.impl.vulkan.shader;

public final class VulkanPostShaderSources {
    private VulkanPostShaderSources() {
    }
    public static String postVertex() {
        return """
                #version 450
                layout(location = 0) out vec2 vUv;
                vec2 POS[3] = vec2[](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
                void main() {
                    vec2 p = POS[gl_VertexIndex];
                    vUv = p * 0.5 + vec2(0.5);
                    gl_Position = vec4(p, 0.0, 1.0);
                }
                """;
    }

    public static String postFragment() {
        return """
                #version 450
                layout(location = 0) in vec2 vUv;
                layout(location = 0) out vec4 outColor;
                layout(set = 0, binding = 0) uniform sampler2D uSceneColor;
                layout(set = 0, binding = 1) uniform sampler2D uHistoryColor;
                layout(set = 0, binding = 2) uniform sampler2D uVelocityColor;
                layout(set = 0, binding = 3) uniform sampler2D uHistoryVelocityColor;
                layout(set = 0, binding = 4) uniform sampler2D uPlanarCaptureColor;
                layout(push_constant) uniform PostPush {
                    vec4 tonemap;
                    vec4 bloom;
                    vec4 ssao;
                    vec4 smaa;
                    vec4 motion;
                    vec4 taa;
                    vec4 reflectionsA;
                    vec4 reflectionsB;
                } pc;
                float smaaLuma(vec3 c) {
                    return dot(c, vec3(0.2126, 0.7152, 0.0722));
                }
                vec2 smaaEdge(vec2 uv, vec3 color) {
                    vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                    vec3 cN = texture(uSceneColor, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cS = texture(uSceneColor, clamp(uv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cE = texture(uSceneColor, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cW = texture(uSceneColor, clamp(uv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                    float l = smaaLuma(color);
                    float ln = smaaLuma(cN);
                    float ls = smaaLuma(cS);
                    float le = smaaLuma(cE);
                    float lw = smaaLuma(cW);
                    return vec2(
                        clamp(abs(l - le) + abs(l - lw), 0.0, 1.0),
                        clamp(abs(l - ln) + abs(l - ls), 0.0, 1.0)
                    );
                }
                vec4 smaaBlendWeights(vec2 uv, vec2 edgeMask) {
                    vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                    float ex = edgeMask.x;
                    float ey = edgeMask.y;
                    vec2 diagA = uv + vec2(texel.x, texel.y);
                    vec2 diagB = uv + vec2(-texel.x, texel.y);
                    vec2 diagC = uv + vec2(texel.x, -texel.y);
                    vec2 diagD = uv + vec2(-texel.x, -texel.y);
                    float center = smaaLuma(texture(uSceneColor, uv).rgb);
                    float dA = abs(smaaLuma(texture(uSceneColor, clamp(diagA, vec2(0.0), vec2(1.0))).rgb) - center);
                    float dB = abs(smaaLuma(texture(uSceneColor, clamp(diagB, vec2(0.0), vec2(1.0))).rgb) - center);
                    float dC = abs(smaaLuma(texture(uSceneColor, clamp(diagC, vec2(0.0), vec2(1.0))).rgb) - center);
                    float dD = abs(smaaLuma(texture(uSceneColor, clamp(diagD, vec2(0.0), vec2(1.0))).rgb) - center);
                    float diag = clamp((dA + dB + dC + dD) * 0.5, 0.0, 1.0);
                    float wH = ex * (1.0 - ey * 0.55);
                    float wV = ey * (1.0 - ex * 0.55);
                    float wDiag = diag * max(ex, ey) * 0.65;
                    float sum = wH + wV + wDiag + 0.0001;
                    return vec4(wH / sum, wV / sum, wDiag / sum, clamp(max(ex, ey), 0.0, 1.0));
                }
                vec3 smaaNeighborhoodResolve(vec2 uv, vec3 color, vec4 weights) {
                    vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                    vec3 cN = texture(uSceneColor, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cS = texture(uSceneColor, clamp(uv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cE = texture(uSceneColor, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cW = texture(uSceneColor, clamp(uv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cNE = texture(uSceneColor, clamp(uv + texel, vec2(0.0), vec2(1.0))).rgb;
                    vec3 cSW = texture(uSceneColor, clamp(uv - texel, vec2(0.0), vec2(1.0))).rgb;
                    vec3 horiz = (cE + cW) * 0.5;
                    vec3 vert = (cN + cS) * 0.5;
                    vec3 diag = (cNE + cSW) * 0.5;
                    return clamp(
                        (horiz * weights.x) + (vert * weights.y) + (diag * weights.z) + (color * (1.0 - weights.w)),
                        vec3(0.0),
                        vec3(1.0)
                    );
                }
                vec3 smaaFull(vec2 uv, vec3 color) {
                    vec2 edgeMask = smaaEdge(uv, color);
                    vec4 weights = smaaBlendWeights(uv, edgeMask);
                    float strength = clamp(pc.smaa.y, 0.0, 1.0);
                    vec3 resolved = smaaNeighborhoodResolve(uv, color, weights);
                    float blend = weights.w * strength * 0.68;
                    return mix(color, resolved, blend);
                }
                vec3 taaSharpen(vec2 uv, vec3 color, float amount) {
                    vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                    vec3 cN = texture(uSceneColor, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cS = texture(uSceneColor, clamp(uv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cE = texture(uSceneColor, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                    vec3 cW = texture(uSceneColor, clamp(uv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                    vec3 blur = (cN + cS + cE + cW) * 0.25;
                    vec3 sharpened = color + (color - blur) * amount;
                    return clamp(sharpened, vec3(0.0), vec3(1.0));
                }
                vec3 applyReflections(
                    vec2 uv,
                    vec3 color,
                    float currentDepth,
                    float historyConfidenceOut,
                    int reflectionOverrideMode,
                    vec2 surfaceVelocityUv,
                    float materialReactive,
                    float disocclusionSignal
                ) {
                    if (pc.reflectionsA.x < 0.5 || int(pc.reflectionsA.y + 0.5) == 0) {
                        return color;
                    }
                    int packedMode = max(int(pc.reflectionsA.y + 0.5), 0);
                    int mode = reflectionOverrideMode == 2 ? 1 : (packedMode & 7);
                    bool hiZEnabled = (packedMode & (1 << 3)) != 0;
                    int denoisePasses = (packedMode >> 4) & 7;
                    bool planarClipEnabled = (packedMode & (1 << 7)) != 0;
                    bool probeVolumeEnabled = (packedMode & (1 << 8)) != 0;
                    bool probeBoxProjectionEnabled = (packedMode & (1 << 9)) != 0;
                    bool rtRequested = (packedMode & (1 << 10)) != 0;
                    bool reflectionSpaceReprojection = (packedMode & (1 << 11)) != 0;
                    bool strictHistoryReject = (packedMode & (1 << 12)) != 0;
                    bool disocclusionRejectPolicy = (packedMode & (1 << 13)) != 0;
                    bool planarSelectiveExec = (packedMode & (1 << 14)) != 0;
                    bool rtActive = (packedMode & (1 << 15)) != 0;
                    bool transparencyIntegration = (packedMode & (1 << 16)) != 0;
                    bool rtMultiBounce = (packedMode & (1 << 17)) != 0;
                    bool planarCaptureExecuted = (packedMode & (1 << 18)) != 0;
                    bool rtDedicatedDenoisePipeline = (packedMode & (1 << 19)) != 0;
                    vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                    float roughnessProxy = clamp(1.0 - dot(color, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
                    bool probeOnlyOverride = reflectionOverrideMode == 1;
                    bool transparentCandidate = materialReactive >= 0.30;
                    float roughnessMask = 1.0 - smoothstep(clamp(pc.reflectionsA.w, 0.05, 1.0), 1.0, roughnessProxy);
                    float ssrStrength = clamp(pc.reflectionsA.z, 0.0, 1.0) * roughnessMask;
                    float stepScale = clamp(pc.reflectionsB.x, 0.5, 3.0);
                    float rtDenoiseStrength = clamp(pc.reflectionsB.w, 0.0, 1.0);
                    vec2 rayDir = normalize(vec2((uv.x - 0.5) * 2.0, (0.5 - uv.y) * 2.0) + vec2(0.0001));
                    vec2 traceUv = uv;
                    vec3 ssrColor = color;
                    float ssrHit = 0.0;
                    float ssrBestDepth = currentDepth;
                    float mipBias = hiZEnabled ? 0.8 : 0.0;
                    for (int i = 0; i < 16; i++) {
                        float hiZStep = hiZEnabled ? pow(1.24, float(i)) : 1.0;
                        float stepMul = (float(i) + 1.0) * stepScale * hiZStep;
                        traceUv = clamp(traceUv + rayDir * texel * stepMul, vec2(0.0), vec2(1.0));
                        vec3 sampleColor = textureLod(uSceneColor, traceUv, mipBias).rgb;
                        float sampleDepth = texture(uVelocityColor, traceUv).b;
                        float depthMatch = 1.0 - smoothstep(0.008, hiZEnabled ? 0.16 : 0.12, abs(sampleDepth - currentDepth));
                        float sampleLuma = dot(sampleColor, vec3(0.2126, 0.7152, 0.0722));
                        float gate = sampleLuma * depthMatch;
                        if (gate > ssrHit) {
                            ssrHit = gate;
                            ssrColor = sampleColor;
                            ssrBestDepth = sampleDepth;
                        }
                    }
                    float contactHardening = 1.0 - smoothstep(0.01, 0.16, abs(ssrBestDepth - currentDepth));
                    float contactRoughnessRamp = mix(1.0, 0.58, contactHardening);
                    roughnessMask = clamp(roughnessMask / max(contactRoughnessRamp, 0.1), 0.0, 1.0);
                    ssrStrength = clamp(ssrStrength * mix(1.0, 1.22, contactHardening), 0.0, 1.0);
                    if (denoisePasses > 0) {
                        for (int i = 0; i < denoisePasses; i++) {
                            float radius = float(i + 1);
                            vec2 o = texel * radius;
                            vec3 n0 = texture(uSceneColor, clamp(traceUv + vec2(o.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                            vec3 n1 = texture(uSceneColor, clamp(traceUv - vec2(o.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                            vec3 n2 = texture(uSceneColor, clamp(traceUv + vec2(0.0, o.y), vec2(0.0), vec2(1.0))).rgb;
                            vec3 n3 = texture(uSceneColor, clamp(traceUv - vec2(0.0, o.y), vec2(0.0), vec2(1.0))).rgb;
                            ssrColor = mix(ssrColor, (n0 + n1 + n2 + n3) * 0.25, 0.28);
                        }
                    }
                    vec2 planarUv = vec2(uv.x, 1.0 - uv.y);
                    vec3 planarColor = planarCaptureExecuted
                        ? texture(uPlanarCaptureColor, planarUv).rgb
                        : texture(uSceneColor, planarUv).rgb;
                    float temporalWeight = clamp(pc.reflectionsB.y, 0.0, 0.98);
                    vec2 reprojectionUv = clamp(uv + pc.motion.xy + surfaceVelocityUv * 0.20, vec2(0.0), vec2(1.0));
                    if (reflectionSpaceReprojection) {
                        vec2 reflectedMotion = vec2(-surfaceVelocityUv.x, surfaceVelocityUv.y);
                        reprojectionUv = clamp(
                            uv + pc.motion.xy + (reflectedMotion * 0.34) + (rayDir * texel * (2.0 + stepScale * 2.0)),
                            vec2(0.0),
                            vec2(1.0)
                        );
                    }
                    vec3 historyColor = texture(uHistoryColor, reprojectionUv).rgb;
                    float historyWeight = temporalWeight * clamp(historyConfidenceOut, 0.0, 1.0);
                    float policyReject = strictHistoryReject ? clamp(0.35 + disocclusionSignal * 0.65, 0.0, 1.0) : 0.0;
                    float disocclusionReject = disocclusionRejectPolicy ? clamp(disocclusionSignal * 0.90, 0.0, 1.0) : 0.0;
                    historyWeight *= (1.0 - max(policyReject, disocclusionReject));
                    vec3 temporalColor = mix(ssrColor, historyColor, historyWeight);
                    float planarStrength = clamp(pc.reflectionsB.z, 0.0, 1.0);
                    if (planarSelectiveExec && reflectionOverrideMode != 0) {
                        planarStrength = 0.0;
                    }
                    if (planarClipEnabled) {
                        float planeFade = 1.0 - smoothstep(0.05, 0.75, currentDepth);
                        planarStrength *= planeFade;
                    }
                    planarStrength = clamp(planarStrength * mix(1.0, 1.10, contactHardening), 0.0, 1.0);
                    if (probeVolumeEnabled) {
                        vec2 boxUv = probeBoxProjectionEnabled ? clamp((uv - 0.5) * 1.35 + 0.5, vec2(0.0), vec2(1.0)) : uv;
                        vec3 probeColor = texture(uSceneColor, boxUv).rgb;
                        float dist = length((uv - 0.5) * vec2(2.0));
                        float probeBlend = clamp(1.0 - dist, 0.0, 1.0) * 0.42;
                        planarColor = mix(planarColor, probeColor, probeBlend);
                    }
                    vec3 rtColor = temporalColor;
                    if (rtRequested && rtActive) {
                        vec2 rtTraceUv = uv;
                        float rtHit = 0.0;
                        vec3 rtAccum = vec3(0.0);
                        float rtAccumW = 0.0;
                        int rtSteps = rtMultiBounce ? 28 : 20;
                        for (int i = 0; i < rtSteps; i++) {
                            float t = (float(i) + 1.0) / float(rtSteps);
                            float stride = mix(0.8, 7.5, t * t) * stepScale;
                            rtTraceUv = clamp(rtTraceUv + rayDir * texel * stride, vec2(0.0), vec2(1.0));
                            vec3 c = textureLod(uSceneColor, rtTraceUv, 0.5 + roughnessProxy * 1.8).rgb;
                            float d = texture(uVelocityColor, rtTraceUv).b;
                            float w = (1.0 - t * 0.55) * (1.0 - smoothstep(0.004, 0.12, abs(d - currentDepth)));
                            rtAccum += c * w;
                            rtAccumW += w;
                            rtHit = max(rtHit, w);
                        }
                        if (rtAccumW > 0.0001) {
                            rtColor = rtAccum / rtAccumW;
                        }
                        if (rtMultiBounce) {
                            vec2 bounceUv = clamp(rtTraceUv - rayDir * texel * 8.0, vec2(0.0), vec2(1.0));
                            vec3 bounce = textureLod(uSceneColor, bounceUv, 1.0 + roughnessProxy * 2.2).rgb;
                            rtColor = mix(rtColor, bounce, 0.22);
                        }
                        int rtDenoisePasses = rtDedicatedDenoisePipeline ? max(2, denoisePasses + 1) : max(1, denoisePasses);
                        for (int i = 0; i < rtDenoisePasses; i++) {
                            float radius = float(i + 1);
                            vec2 o = texel * radius;
                            vec3 n0 = texture(uSceneColor, clamp(rtTraceUv + vec2(o.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                            vec3 n1 = texture(uSceneColor, clamp(rtTraceUv - vec2(o.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                            vec3 n2 = texture(uSceneColor, clamp(rtTraceUv + vec2(0.0, o.y), vec2(0.0), vec2(1.0))).rgb;
                            vec3 n3 = texture(uSceneColor, clamp(rtTraceUv - vec2(0.0, o.y), vec2(0.0), vec2(1.0))).rgb;
                            vec3 cross = (n0 + n1 + n2 + n3) * 0.25;
                            rtColor = mix(rtColor, cross, 0.25 + rtDenoiseStrength * 0.45);
                        }
                        if (rtDedicatedDenoisePipeline) {
                            vec3 rtHistory = texture(uHistoryColor, clamp(reprojectionUv, vec2(0.0), vec2(1.0))).rgb;
                            float temporalDenoise = clamp(0.25 + rtDenoiseStrength * 0.55, 0.0, 0.95);
                            rtColor = mix(rtColor, rtHistory, temporalDenoise * clamp(historyConfidenceOut, 0.0, 1.0));
                        }
                        temporalColor = mix(temporalColor, rtColor, clamp(rtHit * 1.20, 0.0, 1.0));
                    }
                    vec3 reflected = color;
                    if (probeOnlyOverride) {
                        reflected = color;
                    } else if (mode == 1) {
                        reflected = mix(color, temporalColor, ssrStrength * clamp(ssrHit * 1.15, 0.0, 1.0));
                    } else if (mode == 2) {
                        reflected = mix(color, planarColor, planarStrength * (0.75 + 0.25 * (1.0 - roughnessProxy)));
                    } else {
                        vec3 hybrid = mix(temporalColor, planarColor, planarStrength);
                        float hybridWeight = clamp(max(ssrStrength, planarStrength), 0.0, 1.0);
                        if (mode == 4 || rtRequested || rtActive) {
                            hybridWeight = clamp(hybridWeight * 1.08, 0.0, 1.0);
                        }
                        reflected = mix(color, hybrid, hybridWeight);
                    }
                    if (transparencyIntegration && transparentCandidate) {
                        float transparencyWeight = clamp(materialReactive * 1.15, 0.0, 1.0);
                        vec2 refractOffset = (surfaceVelocityUv * 0.08) + (rayDir * texel * (2.0 + currentDepth * 10.0));
                        vec3 refracted = texture(uSceneColor, clamp(uv - refractOffset, vec2(0.0), vec2(1.0))).rgb;
                        vec3 probeFallback = texture(uSceneColor, clamp((uv - 0.5) * 1.20 + 0.5, vec2(0.0), vec2(1.0))).rgb;
                        vec3 reflectionSource = rtActive ? rtColor : mix(planarColor, probeFallback, 0.35);
                        float fresnel = clamp(0.04 + pow(clamp(1.0 - currentDepth, 0.0, 1.0), 5.0) * 0.92, 0.04, 1.0);
                        float depthHardening = clamp(1.0 - disocclusionSignal * 0.75, 0.2, 1.0);
                        vec3 transparentComposite = mix(refracted, reflectionSource, fresnel * depthHardening);
                        reflected = mix(reflected, transparentComposite, transparencyWeight * 0.78);
                    }
                    return clamp(reflected, vec3(0.0), vec3(1.0));
                }
                void main() {
                    vec4 sceneSample = texture(uSceneColor, vUv);
                    vec3 color = sceneSample.rgb;
                    vec4 centerVelocitySample = texture(uVelocityColor, vUv);
                    float currentDepth = centerVelocitySample.b;
                    int reflectionOverrideMode = int(clamp(floor(sceneSample.a * 3.0 + 0.5), 0.0, 3.0));
                    float centerMaterialReactive = clamp(centerVelocitySample.a, 0.0, 1.0);
                    vec2 centerVelocityUv = centerVelocitySample.rg * 2.0 - 1.0;
                    float reflectionDisocclusionSignal = 0.0;
                    float historyConfidenceOut = 1.0;
                    if (pc.tonemap.x > 0.5) {
                        float exposure = max(pc.tonemap.y, 0.0001);
                        float gamma = max(pc.tonemap.z, 0.0001);
                        color = vec3(1.0) - exp(-color * exposure);
                        color = pow(max(color, vec3(0.0)), vec3(1.0 / gamma));
                    }
                    if (pc.bloom.x > 0.5) {
                        float threshold = clamp(pc.bloom.y, 0.0, 4.0);
                        float strength = clamp(pc.bloom.z, 0.0, 2.0);
                        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                        float bright = max(0.0, luma - threshold);
                        color += color * (bright * strength);
                    }
                    if (pc.tonemap.w > 0.5) {
                        float radius = clamp(pc.ssao.x, 0.2, 3.0);
                        vec2 texel = (1.0 / vec2(textureSize(uSceneColor, 0))) * mix(0.75, 2.0, (radius - 0.2) / 2.8);
                        vec3 c = color;
                        vec3 cx = texture(uSceneColor, clamp(vUv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                        vec3 cy = texture(uSceneColor, clamp(vUv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                        vec3 cxy = texture(uSceneColor, clamp(vUv + texel, vec2(0.0), vec2(1.0))).rgb;
                        vec3 cxny = texture(uSceneColor, clamp(vUv + vec2(texel.x, -texel.y), vec2(0.0), vec2(1.0))).rgb;
                        float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
                        float lx = dot(cx, vec3(0.2126, 0.7152, 0.0722));
                        float ly = dot(cy, vec3(0.2126, 0.7152, 0.0722));
                        float lxy = dot(cxy, vec3(0.2126, 0.7152, 0.0722));
                        float lxny = dot(cxny, vec3(0.2126, 0.7152, 0.0722));
                        float edge = clamp(
                            abs(l - lx) * 0.30 +
                            abs(l - ly) * 0.30 +
                            abs(l - lxy) * 0.20 +
                            abs(l - lxny) * 0.20,
                            0.0,
                            1.0
                        );
                        float ssaoStrength = clamp(pc.bloom.w, 0.0, 1.0);
                        float ssaoBias = clamp(pc.ssao.y, 0.0, 0.2);
                        float ssaoPower = clamp(pc.ssao.z, 0.5, 4.0);
                        float shapedEdge = clamp(edge - ssaoBias, 0.0, 1.0);
                        float occlusion = pow(clamp(shapedEdge * ssaoStrength, 0.0, 0.92), max(0.60, 1.18 - (ssaoPower * 0.30)));
                        color *= (1.0 - occlusion * 0.82);
                    }
                    if (pc.smaa.x > 0.5) {
                        color = smaaFull(vUv, color);
                    }
                    if (pc.taa.x > 0.5 && pc.taa.z > 0.5) {
                        vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                        bool taaLumaClip = pc.motion.z >= 1.0;
                        float taaSharpenStrength = clamp(pc.motion.z - (taaLumaClip ? 1.0 : 0.0), 0.0, 0.35);
                        vec4 velocitySample = centerVelocitySample;
                        vec2 velocityUv = centerVelocityUv;
                        float materialReactive = centerMaterialReactive;
                        vec2 historyUv = clamp(vUv + pc.smaa.zw + pc.motion.xy + (velocityUv * 0.5), vec2(0.0), vec2(1.0));
                        vec4 historySample = texture(uHistoryColor, historyUv);
                        vec3 history = historySample.rgb;
                        float historyConfidence = clamp(historySample.a, 0.0, 1.0);
                        float hc1 = texture(uHistoryColor, clamp(historyUv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).a;
                        float hc2 = texture(uHistoryColor, clamp(historyUv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).a;
                        float hc3 = texture(uHistoryColor, clamp(historyUv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).a;
                        float hc4 = texture(uHistoryColor, clamp(historyUv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).a;
                        float dilatedHistoryConfidence = clamp(max(historyConfidence, max(max(hc1, hc2), max(hc3, hc4)) * 0.92), 0.0, 1.0);
                        float historyDepth = texture(uHistoryVelocityColor, historyUv).b;
                        vec3 n1 = texture(uSceneColor, clamp(vUv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                        vec3 n2 = texture(uSceneColor, clamp(vUv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                        vec3 n3 = texture(uSceneColor, clamp(vUv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                        vec3 n4 = texture(uSceneColor, clamp(vUv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                        vec3 neighMin = min(min(min(color, n1), min(n2, n3)), n4);
                        vec3 neighMax = max(max(max(color, n1), max(n2, n3)), n4);
                        float d1 = texture(uVelocityColor, clamp(vUv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).b;
                        float d2 = texture(uVelocityColor, clamp(vUv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).b;
                        float d3 = texture(uVelocityColor, clamp(vUv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).b;
                        float d4 = texture(uVelocityColor, clamp(vUv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).b;
                        float depthEdge = max(max(abs(currentDepth - d1), abs(currentDepth - d2)), max(abs(currentDepth - d3), abs(currentDepth - d4)));
                        float lCurr = dot(color, vec3(0.2126, 0.7152, 0.0722));
                        float lHist = dot(history, vec3(0.2126, 0.7152, 0.0722));
                        float lN1 = dot(n1, vec3(0.2126, 0.7152, 0.0722));
                        float lN2 = dot(n2, vec3(0.2126, 0.7152, 0.0722));
                        float lN3 = dot(n3, vec3(0.2126, 0.7152, 0.0722));
                        float lN4 = dot(n4, vec3(0.2126, 0.7152, 0.0722));
                        float lMean = (lCurr + lN1 + lN2 + lN3 + lN4) * 0.2;
                        float lVar = (
                                (lCurr - lMean) * (lCurr - lMean) +
                                        (lN1 - lMean) * (lN1 - lMean) +
                                        (lN2 - lMean) * (lN2 - lMean) +
                                        (lN3 - lMean) * (lN3 - lMean) +
                                        (lN4 - lMean) * (lN4 - lMean)
                        ) * 0.2;
                        float varianceReject = smoothstep(0.002, 0.028, lVar);
                        float depthReject = smoothstep(0.0012, 0.012, depthEdge + abs(historyDepth - currentDepth));
                        float upsamplePenalty = clamp((1.0 - clamp(pc.ssao.w, 0.5, 1.0)) * 1.6, 0.0, 0.75);
                        float reactive = clamp(
                                abs(lCurr - lHist) * 2.4 +
                                        length(velocityUv) * 1.25 +
                                        depthReject * 0.95 +
                                        varianceReject * 1.15 +
                                        materialReactive * 1.35 +
                                        upsamplePenalty * (0.55 + materialReactive * 0.5),
                                0.0,
                                1.0
                        );
                        float clipExpand = mix(0.06, 0.015, reactive);
                        float taaClipScale = clamp(pc.motion.w, 0.5, 1.6);
                        vec3 clampedHistory = clamp(history, neighMin - vec3(clipExpand * taaClipScale), neighMax + vec3(clipExpand * taaClipScale));
                        if (taaLumaClip) {
                            float lumaMin = min(min(lCurr, lN1), min(min(lN2, lN3), lN4));
                            float lumaMax = max(max(lCurr, lN1), max(max(lN2, lN3), lN4));
                            float lumaHist = dot(clampedHistory, vec3(0.2126, 0.7152, 0.0722));
                            float lumaClamped = clamp(lumaHist, lumaMin, lumaMax);
                            if (lumaHist > 0.0001) {
                                clampedHistory *= (lumaClamped / lumaHist);
                            }
                        }
                        float disocclusionReject = smoothstep(0.0012, 0.0095, abs(historyDepth - currentDepth) + depthEdge * (0.9 + upsamplePenalty * 0.65));
                        reflectionDisocclusionSignal = disocclusionReject;
                        float instability = clamp(
                                abs(lCurr - lHist) * 1.9 +
                                        varianceReject * 1.15 +
                                        length(velocityUv) * (0.78 + upsamplePenalty * 0.28),
                                0.0,
                                1.0
                        );
                        float confidenceDecay = clamp(max(disocclusionReject, instability * 0.72), 0.0, 1.0);
                        float confidenceRecover = clamp((1.0 - confidenceDecay) * (1.0 - varianceReject) * (1.0 - depthReject), 0.0, 1.0);
                        float confidenceState = clamp(
                                dilatedHistoryConfidence + confidenceRecover * 0.13 - confidenceDecay * 0.30,
                                0.02,
                                1.0
                        );
                        float historyTrust = clamp(confidenceState * (1.0 - reactive * 0.65), 0.0, 1.0);
                        float blend = clamp(pc.taa.y, 0.0, 0.95) * historyTrust * (1.0 - reactive * 0.88);
                        color = mix(color, clampedHistory, blend);
                        color = taaSharpen(vUv, color, taaSharpenStrength * (1.0 - reactive));
                        historyConfidenceOut = clamp(max(confidenceState * 0.94, 1.0 - reactive * 0.86), 0.02, 1.0);
                        int debugView = int(pc.taa.w + 0.5);
                        if (debugView == 1) {
                            color = vec3(reactive);
                        } else if (debugView == 2) {
                            color = vec3(disocclusionReject);
                        } else if (debugView == 3) {
                            color = vec3(historyTrust);
                        } else if (debugView == 4) {
                            color = vec3(abs(velocityUv.x), abs(velocityUv.y), length(velocityUv) * 0.5);
                        } else if (debugView == 5) {
                            vec2 tileUv = fract(vUv * 2.0);
                            vec3 velocityViz = vec3(abs(velocityUv.x), abs(velocityUv.y), length(velocityUv) * 0.5);
                            if (vUv.x < 0.5 && vUv.y < 0.5) {
                                color = vec3(reactive);
                            } else if (vUv.x >= 0.5 && vUv.y < 0.5) {
                                color = vec3(disocclusionReject);
                            } else if (vUv.x < 0.5) {
                                color = vec3(historyTrust);
                            } else {
                                color = velocityViz;
                            }
                            float line = max(step(0.495, abs(tileUv.x - 0.5)), step(0.495, abs(tileUv.y - 0.5)));
                            color = mix(color, vec3(1.0, 1.0, 0.2), line * 0.65);
                        }
                    }
                    color = applyReflections(
                        vUv,
                        color,
                        currentDepth,
                        historyConfidenceOut,
                        reflectionOverrideMode,
                        centerVelocityUv,
                        centerMaterialReactive,
                        reflectionDisocclusionSignal
                    );
                    vec4 giResolved = resolveGiIndirect(vec4(clamp(color, 0.0, 1.0), historyConfidenceOut), vUv);
                    color = giResolved.rgb;
                    historyConfidenceOut = clamp(max(historyConfidenceOut, giResolved.a), 0.0, 1.0);
                    outColor = vec4(clamp(color, 0.0, 1.0), historyConfidenceOut);
                }
                """;
    }
}
