package org.dynamislight.impl.vulkan.shader;

final class VulkanMainFragmentShaderPart0 {
    private VulkanMainFragmentShaderPart0() {
    }
    static final String TEXT = """
                #version 450
                layout(location = 0) in vec3 vWorldPos;
                layout(location = 1) in vec3 vNormal;
                layout(location = 2) in float vHeight;
                layout(location = 3) in vec2 vUv;
                layout(location = 4) in vec3 vTangent;
                layout(location = 5) in vec3 vLocalPos;
                layout(set = 0, binding = 0) uniform GlobalData {
                    mat4 uView;
                    mat4 uProj;
                    vec4 uDirLightDir;
                    vec4 uDirLightColor;
                    vec4 uPointLightPos;
                    vec4 uPointLightColor;
                    vec4 uPointLightDir;
                    vec4 uPointLightCone;
                    vec4 uLocalLightMeta;
                    vec4 uLocalLightPosRange[8];
                    vec4 uLocalLightColorIntensity[8];
                    vec4 uLocalLightDirInner[8];
                    vec4 uLocalLightOuterTypeShadow[8];
                    vec4 uLightIntensity;
                    vec4 uShadow;
                    vec4 uShadowCascade;
                    vec4 uShadowCascadeExt;
                    vec4 uFog;
                    vec4 uFogColorSteps;
                    vec4 uSmoke;
                    vec4 uSmokeColor;
                    vec4 uIbl;
                    vec4 uPostProcess;
                    vec4 uBloom;
                    vec4 uAntiAlias;
                    mat4 uPrevViewProj;
                    mat4 uShadowLightViewProj[24];
                    mat4 uPlanarView;
                    mat4 uPlanarProj;
                    mat4 uPlanarPrevViewProj;
                } gbo;
                layout(set = 0, binding = 1) uniform ObjectData {
                    mat4 uModel;
                    mat4 uPrevModel;
                    vec4 uBaseColor;
                    vec4 uMaterial;
                    vec4 uMaterialReactive;
                } obj;
                layout(push_constant) uniform MainPush {
                    vec4 uPlanar;
                } pc;
                struct ProbeData {
                    vec4 positionAndIntensity;
                    vec4 extentsMin;
                    vec4 extentsMax;
                    ivec4 cubemapIndexAndFlags;
                };
                layout(std430, set = 0, binding = 2) readonly buffer ReflectionProbeData {
                    ivec4 uProbeHeader;
                    ProbeData uProbes[];
                } probes;
                layout(set = 1, binding = 0) uniform sampler2D uAlbedoTexture;
                layout(set = 1, binding = 1) uniform sampler2D uNormalTexture;
                layout(set = 1, binding = 2) uniform sampler2D uMetallicRoughnessTexture;
                layout(set = 1, binding = 3) uniform sampler2D uOcclusionTexture;
                layout(set = 1, binding = 4) uniform sampler2DArrayShadow uShadowMap;
                layout(set = 1, binding = 5) uniform sampler2D uIblIrradianceTexture;
                layout(set = 1, binding = 6) uniform sampler2D uIblRadianceTexture;
                layout(set = 1, binding = 7) uniform sampler2D uIblBrdfLutTexture;
                layout(set = 1, binding = 8) uniform sampler2DArray uShadowMomentMap;
                layout(set = 1, binding = 9) uniform sampler2DArray uProbeRadianceTexture;
                layout(location = 0) out vec4 outColor;
                layout(location = 1) out vec4 outVelocity;
                float distributionGGX(float ndh, float roughness) {
                    float a = roughness * roughness;
                    float a2 = a * a;
                    float d = (ndh * ndh) * (a2 - 1.0) + 1.0;
                    return a2 / max(3.14159 * d * d, 0.0001);
                }
                float geometrySchlickGGX(float ndv, float roughness) {
                    float r = roughness + 1.0;
                    float k = (r * r) / 8.0;
                    return ndv / max(ndv * (1.0 - k) + k, 0.0001);
                }
                float geometrySmith(float ndv, float ndl, float roughness) {
                    return geometrySchlickGGX(ndv, roughness) * geometrySchlickGGX(ndl, roughness);
                }
                vec3 fresnelSchlick(float cosTheta, vec3 f0) {
                    return f0 + (1.0 - f0) * pow(1.0 - cosTheta, 5.0);
                }
                float probeAxisWeight(float pointValue, float minValue, float maxValue, float blendDistance) {
                    float fromMin = (pointValue - minValue) / blendDistance;
                    float fromMax = (maxValue - pointValue) / blendDistance;
                    return clamp(min(fromMin, fromMax), 0.0, 1.0);
                }
                float probeWeightAtWorldPos(vec3 worldPos, ProbeData probe) {
                    float blendDistance = max(probe.extentsMin.w, 0.0001);
                    float wx = probeAxisWeight(worldPos.x, probe.extentsMin.x, probe.extentsMax.x, blendDistance);
                    float wy = probeAxisWeight(worldPos.y, probe.extentsMin.y, probe.extentsMax.y, blendDistance);
                    float wz = probeAxisWeight(worldPos.z, probe.extentsMin.z, probe.extentsMax.z, blendDistance);
                    vec3 extents = max(probe.extentsMax.xyz - probe.extentsMin.xyz, vec3(0.0001));
                    vec3 center = (probe.extentsMin.xyz + probe.extentsMax.xyz) * 0.5;
                    vec3 normalizedOffset = abs(worldPos - center) / max(extents * 0.5, vec3(0.0001));
                    float distanceWeight = clamp(1.0 - (length(clamp(normalizedOffset, vec3(0.0), vec3(1.5))) / 1.73205), 0.0, 1.0);
                    float priorityNormalized = clamp((probe.extentsMax.w + 64.0) / 128.0, 0.0, 1.0);
                    float priorityWeight = mix(0.55, 1.35, priorityNormalized);
                    float intensity = max(probe.positionAndIntensity.w, 0.0);
                    return wx * wy * wz * distanceWeight * priorityWeight * intensity;
                }
                vec3 probeSampleDirection(vec3 worldPos, vec3 reflectDir, ProbeData probe) {
                    bool boxProjection = probe.cubemapIndexAndFlags.y != 0;
                    if (!boxProjection) {
                        return normalize(reflectDir);
                    }
                    vec3 dir = normalize(reflectDir);
                    vec3 dirSafe = vec3(
                            abs(dir.x) < 0.0001 ? (dir.x >= 0.0 ? 0.0001 : -0.0001) : dir.x,
                            abs(dir.y) < 0.0001 ? (dir.y >= 0.0 ? 0.0001 : -0.0001) : dir.y,
                            abs(dir.z) < 0.0001 ? (dir.z >= 0.0 ? 0.0001 : -0.0001) : dir.z
                    );
                    vec3 t0 = (probe.extentsMin.xyz - worldPos) / dirSafe;
                    vec3 t1 = (probe.extentsMax.xyz - worldPos) / dirSafe;
                    vec3 tMin = min(t0, t1);
                    vec3 tMax = max(t0, t1);
                    float nearT = max(max(tMin.x, tMin.y), tMin.z);
                    float farT = min(min(tMax.x, tMax.y), tMax.z);
                    if (farT < 0.0 || farT < nearT) {
                        return dir;
                    }
                    float t = nearT > 0.0 ? nearT : farT;
                    vec3 hitPoint = worldPos + dir * t;
                    vec3 corrected = hitPoint - probe.positionAndIntensity.xyz;
                    if (length(corrected) < 0.0001) {
                        return dir;
                    }
                    return normalize(corrected);
                }
                vec3 sampleIblRadiance(vec2 specUv, vec2 baseUv, float roughness, float prefilter) {
                    float roughMix = clamp(roughness * (0.45 + 0.55 * prefilter), 0.0, 1.0);
                    vec2 roughUv = mix(specUv, baseUv, roughMix);
                    float maxLod = float(max(textureQueryLevels(uIblRadianceTexture) - 1, 0));
                    float lod = roughMix * maxLod;
                    vec2 texel = 1.0 / vec2(textureSize(uIblRadianceTexture, 0));
                    vec2 axis = normalize(vec2(0.37, 0.93) + vec2(roughMix, 1.0 - roughMix) * 0.45);
                    vec2 side = vec2(-axis.y, axis.x);
                    float spread = mix(0.5, 3.0, roughMix);
                    vec3 c0 = textureLod(uIblRadianceTexture, roughUv, lod).rgb;
                    vec3 c1 = textureLod(uIblRadianceTexture, clamp(roughUv + axis * texel * spread, vec2(0.0), vec2(1.0)), lod).rgb;
                    vec3 c2 = textureLod(uIblRadianceTexture, clamp(roughUv - axis * texel * spread, vec2(0.0), vec2(1.0)), lod).rgb;
                    vec3 c3 = textureLod(uIblRadianceTexture, clamp(roughUv + side * texel * spread * 0.75, vec2(0.0), vec2(1.0)), lod).rgb;
                    vec3 c4 = textureLod(uIblRadianceTexture, clamp(roughUv - side * texel * spread * 0.75, vec2(0.0), vec2(1.0)), lod).rgb;
                    return (c0 * 0.44) + (c1 * 0.18) + (c2 * 0.18) + (c3 * 0.10) + (c4 * 0.10);
                }
                vec3 sampleProbeRadiance(vec2 specUv, vec2 baseUv, float roughness, float prefilter, int layerIndex, int layerCount) {
                    float roughMix = clamp(roughness * (0.45 + 0.55 * prefilter), 0.0, 1.0);
                    vec2 roughUv = mix(specUv, baseUv, roughMix);
                    float safeLayer = float(clamp(layerIndex, 0, max(layerCount - 1, 0)));
                    float maxLod = float(max(textureQueryLevels(uProbeRadianceTexture) - 1, 0));
                    float lod = roughMix * maxLod;
                    vec2 texel = 1.0 / vec2(textureSize(uProbeRadianceTexture, 0));
                    vec2 axis = normalize(vec2(0.37, 0.93) + vec2(roughMix, 1.0 - roughMix) * 0.45);
                    vec2 side = vec2(-axis.y, axis.x);
                    float spread = mix(0.5, 3.0, roughMix);
                    vec3 c0 = textureLod(uProbeRadianceTexture, vec3(roughUv, safeLayer), lod).rgb;
                    vec3 c1 = textureLod(uProbeRadianceTexture, vec3(clamp(roughUv + axis * texel * spread, vec2(0.0), vec2(1.0)), safeLayer), lod).rgb;
                    vec3 c2 = textureLod(uProbeRadianceTexture, vec3(clamp(roughUv - axis * texel * spread, vec2(0.0), vec2(1.0)), safeLayer), lod).rgb;
                    vec3 c3 = textureLod(uProbeRadianceTexture, vec3(clamp(roughUv + side * texel * spread * 0.75, vec2(0.0), vec2(1.0)), safeLayer), lod).rgb;
                    vec3 c4 = textureLod(uProbeRadianceTexture, vec3(clamp(roughUv - side * texel * spread * 0.75, vec2(0.0), vec2(1.0)), safeLayer), lod).rgb;
                    return (c0 * 0.44) + (c1 * 0.18) + (c2 * 0.18) + (c3 * 0.10) + (c4 * 0.10);
                }
            """;
}
