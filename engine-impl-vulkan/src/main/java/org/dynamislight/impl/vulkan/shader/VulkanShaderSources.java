package org.dynamislight.impl.vulkan.shader;

public final class VulkanShaderSources {
    private VulkanShaderSources() {
    }

    public static String shadowVertex() {
        return """
                #version 450
                layout(location = 0) in vec3 inPos;
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
                } gbo;
                layout(set = 0, binding = 1) uniform ObjectData {
                    mat4 uModel;
                    mat4 uPrevModel;
                    vec4 uBaseColor;
                    vec4 uMaterial;
                    vec4 uMaterialReactive;
                } obj;
                layout(push_constant) uniform ShadowPush {
                    int uCascadeIndex;
                } pc;
                void main() {
                    int cascadeIndex = clamp(pc.uCascadeIndex, 0, 23);
                    gl_Position = gbo.uShadowLightViewProj[cascadeIndex] * obj.uModel * vec4(inPos, 1.0);
                }
                """;
    }

    public static String shadowFragment() {
        return """
                #version 450
                void main() { }
                """;
    }

    public static String shadowFragmentMoments() {
        return """
                #version 450
                layout(location = 0) out vec4 outMoments;
                void main() {
                    float d = clamp(gl_FragCoord.z, 0.0, 1.0);
                    float d2 = d * d;
                    outMoments = vec4(d, d2, 0.0, 0.0);
                }
                """;
    }

    public static String mainVertex() {
        return """
                #version 450
                layout(location = 0) in vec3 inPos;
                layout(location = 1) in vec3 inNormal;
                layout(location = 2) in vec2 inUv;
                layout(location = 3) in vec3 inTangent;
                layout(location = 0) out vec3 vWorldPos;
                layout(location = 1) out vec3 vNormal;
                layout(location = 2) out float vHeight;
                layout(location = 3) out vec2 vUv;
                layout(location = 4) out vec3 vTangent;
                layout(location = 5) out vec3 vLocalPos;
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
                } gbo;
                layout(set = 0, binding = 1) uniform ObjectData {
                    mat4 uModel;
                    mat4 uPrevModel;
                    vec4 uBaseColor;
                    vec4 uMaterial;
                    vec4 uMaterialReactive;
                } obj;
                void main() {
                    vec4 world = obj.uModel * vec4(inPos, 1.0);
                    vWorldPos = world.xyz;
                    vHeight = world.y;
                    vec3 tangent = normalize(mat3(obj.uModel) * inTangent);
                    vec3 normal = normalize(mat3(obj.uModel) * inNormal);
                    vNormal = normal;
                    vTangent = tangent;
                    vUv = inUv;
                    vLocalPos = inPos;
                    gl_Position = gbo.uProj * gbo.uView * world;
                }
                """;
    }

    public static String mainFragment() {
        return """
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
                } gbo;
                layout(set = 0, binding = 1) uniform ObjectData {
                    mat4 uModel;
                    mat4 uPrevModel;
                    vec4 uBaseColor;
                    vec4 uMaterial;
                    vec4 uMaterialReactive;
                } obj;
                layout(set = 1, binding = 0) uniform sampler2D uAlbedoTexture;
                layout(set = 1, binding = 1) uniform sampler2D uNormalTexture;
                layout(set = 1, binding = 2) uniform sampler2D uMetallicRoughnessTexture;
                layout(set = 1, binding = 3) uniform sampler2D uOcclusionTexture;
                layout(set = 1, binding = 4) uniform sampler2DArrayShadow uShadowMap;
                layout(set = 1, binding = 5) uniform sampler2D uIblIrradianceTexture;
                layout(set = 1, binding = 6) uniform sampler2D uIblRadianceTexture;
                layout(set = 1, binding = 7) uniform sampler2D uIblBrdfLutTexture;
                layout(set = 1, binding = 8) uniform sampler2DArray uShadowMomentMap;
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
                float reduceLightBleed(float visibility, float amount) {
                    return clamp((visibility - amount) / max(1.0 - amount, 0.0001), 0.0, 1.0);
                }
                float momentVisibilityApprox(vec2 uv, float compareDepth, int layer) {
                    vec3 momentUv = vec3(clamp(uv, vec2(0.0), vec2(1.0)), float(clamp(layer, 0, 23)));
                    float maxLod = float(max(textureQueryLevels(uShadowMomentMap) - 1, 0));
                    float lod = min(1.5, maxLod);
                    vec2 baseMoments = textureLod(uShadowMomentMap, momentUv, 0.0).rg;
                    vec2 filteredMoments = textureLod(uShadowMomentMap, momentUv, lod).rg;
                    vec2 moments = mix(baseMoments, filteredMoments, 0.68);
                    // Neutral fallback for uninitialized/provisional moment data.
                    if (moments.y <= 0.000001) {
                        return 1.0;
                    }
                    float mean = clamp(moments.x, 0.0, 1.0);
                    if (compareDepth <= mean) {
                        return 1.0;
                    }
                    float second = max(moments.y, mean * mean);
                    float variance = max(second - (mean * mean), 0.00003 + (1.0 - mean) * 0.00006);
                    float diff = compareDepth - mean;
                    float pMax = variance / (variance + diff * diff);
                    float antiBleed = reduceLightBleed(clamp(pMax, 0.0, 1.0), 0.22);
                    return clamp(mix(pMax, antiBleed, 0.86), 0.0, 1.0);
                }
                float evsmVisibilityApprox(vec2 uv, float compareDepth, int layer) {
                    vec3 momentUv = vec3(clamp(uv, vec2(0.0), vec2(1.0)), float(clamp(layer, 0, 23)));
                    float maxLod = float(max(textureQueryLevels(uShadowMomentMap) - 1, 0));
                    float lod = min(2.0, maxLod);
                    vec2 baseMoments = textureLod(uShadowMomentMap, momentUv, 0.0).rg;
                    vec2 filteredMoments = textureLod(uShadowMomentMap, momentUv, lod).rg;
                    vec2 moments = mix(baseMoments, filteredMoments, 0.75);
                    if (moments.y <= 0.000001) {
                        return 1.0;
                    }
                    float warp = 40.0;
                    float mean = clamp(moments.x, 0.0, 1.0);
                    float second = max(moments.y, mean * mean);
                    float variance = max(second - (mean * mean), 0.00008 + (1.0 - mean) * 0.00010);
                    float warpedCompare = exp(warp * clamp(compareDepth, 0.0, 1.0));
                    float warpedMean = exp(warp * clamp(mean, 0.0, 1.0));
                    float warpedVariance = variance * (1.0 + 0.45 * warp);
                    float diff = max(warpedCompare - warpedMean, 0.0);
                    float pMax = warpedVariance / (warpedVariance + diff * diff);
                    float momentBase = momentVisibilityApprox(uv, compareDepth, layer);
                    float antiBleed = reduceLightBleed(clamp(pMax, 0.0, 1.0), 0.30);
                    return clamp(mix(momentBase, antiBleed, 0.72), 0.0, 1.0);
                }
                float finalizeShadowVisibility(
                        float pcfVisibility,
                        int shadowFilterMode,
                        vec2 uv,
                        float compareDepth,
                        int layer,
                        float ndl,
                        float depthRatio
                ) {
                    float visibility = clamp(pcfVisibility, 0.0, 1.0);
                    if (shadowFilterMode == 1) {
                        float penumbra = clamp((1.0 - ndl) * 0.78 + depthRatio * 0.92, 0.0, 1.0);
                        float soft = mix(visibility, sqrt(max(visibility, 0.0)), 0.42 * penumbra);
                        float edgeProtect = smoothstep(0.12, 0.50, visibility);
                        visibility = mix(soft, visibility, edgeProtect * 0.40);
                    } else if (shadowFilterMode == 2) {
                        float momentVis = momentVisibilityApprox(uv, compareDepth, layer);
                        visibility = min(visibility + 0.05, mix(visibility, momentVis, 0.76));
                    } else if (shadowFilterMode == 3) {
                        float evsmVis = evsmVisibilityApprox(uv, compareDepth, layer);
                        visibility = min(visibility + 0.07, mix(visibility, evsmVis, 0.84));
                    }
                    return clamp(visibility, 0.0, 1.0);
                }
                void main() {
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
                    float normalVariance = clamp((length(dFdx(n)) + length(dFdy(n))) * 0.30, 0.0, 1.0);
                    float normalMapVariance = clamp((1.0 - clamp(length(normalTex), 0.0, 1.0)) * 1.55, 0.0, 1.0);
                    float toksvigVariance = clamp(normalVariance * 0.70 + normalMapVariance * 0.65, 0.0, 1.0);
                    roughness = clamp(sqrt(roughness * roughness + toksvigVariance * 0.52), 0.04, 1.0);
                    float dirIntensity = max(0.0, gbo.uLightIntensity.x);
                    int shadowFilterMode = clamp(int(gbo.uLocalLightMeta.z + 0.5), 0, 3);
                    bool contactShadows = gbo.uLocalLightMeta.w > 0.5;

                    float ao = clamp(texture(uOcclusionTexture, vUv).r, 0.0, 1.0);
                    vec3 lDir = normalize(-gbo.uDirLightDir.xyz);
                    vec3 viewPos = (gbo.uView * vec4(vWorldPos, 1.0)).xyz;
                    vec3 viewDir = normalize(-viewPos);

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
                                        localShadowCoord.xy,
                                        compareDepth,
                                        localShadowLayer,
                                        localNdl,
                                        localShadowCoord.z
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
                                        localShadowCoord.xy,
                                        compareDepth,
                                        pointLayer,
                                        localNdl,
                                        localShadowCoord.z
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
                                    * distFade;
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
                                    shadowCoord.xy,
                                    compareDepth,
                                    cascadeIndex,
                                    ndl,
                                    shadowCoord.z
                            );
                        }
                        float shadowOcclusion = 1.0 - shadowVisibility;
                        float shadowFactor = clamp(shadowOcclusion * clamp(gbo.uShadow.y, 0.0, 1.0), 0.0, 0.9);
                        color *= (1.0 - shadowFactor);
                        if (contactShadows) {
                            float contactEdge = clamp(length(dFdx(n)) + length(dFdy(n)), 0.0, 1.0);
                            float contactFactor = shadowOcclusion * contactEdge * (1.0 - roughness) * (1.0 - ndl);
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
                                    pointShadowCoord.xy,
                                    compareDepth,
                                    pointLayer,
                                    pNdl,
                                    pointDepthRatio
                            );
                            float pointOcclusion = 1.0 - pointVisibility;
                            float pointShadowFactor = clamp(pointOcclusion * min(clamp(gbo.uShadow.y, 0.0, 1.0), 0.85), 0.0, 0.9);
                            color *= (1.0 - pointShadowFactor);
                            if (contactShadows) {
                                float contactEdge = clamp((length(dFdx(n)) + length(dFdy(n))) * 0.9, 0.0, 1.0);
                                float contactFactor = pointOcclusion * contactEdge * (1.0 - roughness) * (1.0 - pNdl);
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
                    outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
                    vec4 currClip = gbo.uProj * gbo.uView * vec4(vWorldPos, 1.0);
                    vec4 prevClip = gbo.uPrevViewProj * (obj.uPrevModel * vec4(vLocalPos, 1.0));
                    float currW = abs(currClip.w) > 0.000001 ? currClip.w : 1.0;
                    float prevW = abs(prevClip.w) > 0.000001 ? prevClip.w : 1.0;
                    vec2 currNdc = currClip.xy / currW;
                    vec2 prevNdc = prevClip.xy / prevW;
                    vec2 velocityNdc = clamp(prevNdc - currNdc, vec2(-1.0), vec2(1.0));
                    outVelocity = vec4(velocityNdc * 0.5 + 0.5, clamp(gl_FragCoord.z, 0.0, 1.0), materialReactive);
                }
                """;
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
                vec3 applyReflections(vec2 uv, vec3 color, float currentDepth, float historyConfidenceOut) {
                    if (pc.reflectionsA.x < 0.5 || int(pc.reflectionsA.y + 0.5) == 0) {
                        return color;
                    }
                    int packedMode = max(int(pc.reflectionsA.y + 0.5), 0);
                    int mode = packedMode & 7;
                    bool hiZEnabled = (packedMode & (1 << 3)) != 0;
                    int denoisePasses = (packedMode >> 4) & 7;
                    bool planarClipEnabled = (packedMode & (1 << 7)) != 0;
                    bool probeVolumeEnabled = (packedMode & (1 << 8)) != 0;
                    bool probeBoxProjectionEnabled = (packedMode & (1 << 9)) != 0;
                    bool rtRequested = (packedMode & (1 << 10)) != 0;
                    vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                    float roughnessProxy = clamp(1.0 - dot(color, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
                    float roughnessMask = 1.0 - smoothstep(clamp(pc.reflectionsA.w, 0.05, 1.0), 1.0, roughnessProxy);
                    float ssrStrength = clamp(pc.reflectionsA.z, 0.0, 1.0) * roughnessMask;
                    float stepScale = clamp(pc.reflectionsB.x, 0.5, 3.0);
                    vec2 rayDir = normalize(vec2((uv.x - 0.5) * 2.0, (0.5 - uv.y) * 2.0) + vec2(0.0001));
                    vec2 traceUv = uv;
                    vec3 ssrColor = color;
                    float ssrHit = 0.0;
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
                        }
                    }
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
                    vec3 planarColor = texture(uSceneColor, planarUv).rgb;
                    float temporalWeight = clamp(pc.reflectionsB.y, 0.0, 0.98);
                    vec3 historyColor = texture(uHistoryColor, clamp(uv + pc.motion.xy, vec2(0.0), vec2(1.0))).rgb;
                    vec3 temporalColor = mix(ssrColor, historyColor, temporalWeight * clamp(historyConfidenceOut, 0.0, 1.0));
                    float planarStrength = clamp(pc.reflectionsB.z, 0.0, 1.0);
                    if (planarClipEnabled) {
                        float planeFade = 1.0 - smoothstep(0.05, 0.75, currentDepth);
                        planarStrength *= planeFade;
                    }
                    if (probeVolumeEnabled) {
                        vec2 boxUv = probeBoxProjectionEnabled ? clamp((uv - 0.5) * 1.35 + 0.5, vec2(0.0), vec2(1.0)) : uv;
                        vec3 probeColor = texture(uSceneColor, boxUv).rgb;
                        float dist = length((uv - 0.5) * vec2(2.0));
                        float probeBlend = clamp(1.0 - dist, 0.0, 1.0) * 0.42;
                        planarColor = mix(planarColor, probeColor, probeBlend);
                    }
                    vec3 reflected = color;
                    if (mode == 1) {
                        reflected = mix(color, temporalColor, ssrStrength * clamp(ssrHit * 1.15, 0.0, 1.0));
                    } else if (mode == 2) {
                        reflected = mix(color, planarColor, planarStrength * (0.75 + 0.25 * (1.0 - roughnessProxy)));
                    } else {
                        vec3 hybrid = mix(temporalColor, planarColor, planarStrength);
                        float hybridWeight = clamp(max(ssrStrength, planarStrength), 0.0, 1.0);
                        if (mode == 4 || rtRequested) {
                            hybridWeight = clamp(hybridWeight * 1.08, 0.0, 1.0);
                        }
                        reflected = mix(color, hybrid, hybridWeight);
                    }
                    return clamp(reflected, vec3(0.0), vec3(1.0));
                }
                void main() {
                    vec3 color = texture(uSceneColor, vUv).rgb;
                    float currentDepth = texture(uVelocityColor, vUv).b;
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
                        vec4 velocitySample = texture(uVelocityColor, vUv);
                        vec2 velocityUv = velocitySample.rg * 2.0 - 1.0;
                        float materialReactive = velocitySample.a;
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
                    color = applyReflections(vUv, color, currentDepth, historyConfidenceOut);
                    outColor = vec4(clamp(color, 0.0, 1.0), historyConfidenceOut);
                }
                """;
    }
}
