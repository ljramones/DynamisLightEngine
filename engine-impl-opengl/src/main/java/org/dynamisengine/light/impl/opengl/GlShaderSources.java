package org.dynamisengine.light.impl.opengl;

/**
 * GLSL shader source string constants extracted from {@link OpenGlContext}.
 */
final class GlShaderSources {

    private GlShaderSources() {
    }

    static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec3 aData1;
            layout (location = 2) in vec2 aData2;
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProj;
            uniform int uVertexFormat;
            out vec3 vColor;
            out vec3 vWorldPos;
            out vec3 vLocalPos;
            out vec3 vNormal;
            out float vHeight;
            out vec2 vUv;
            out vec4 vLightSpacePos;
            uniform mat4 uLightViewProj;
            void main() {
                vec4 world = uModel * vec4(aPos, 1.0);
                vWorldPos = world.xyz;
                vLocalPos = aPos;
                vHeight = world.y;
                vLightSpacePos = uLightViewProj * world;
                if (uVertexFormat == 1) {
                    vColor = vec3(1.0);
                    vNormal = normalize(mat3(uModel) * aData1);
                    vUv = aData2;
                } else {
                    vColor = aData1;
                    vNormal = normalize(mat3(uModel) * vec3(0.0, 1.0, 0.0));
                    vUv = aPos.xy * 0.5 + vec2(0.5);
                }
                gl_Position = uProj * uView * world;
            }
            """;

    static final String SHADOW_VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform mat4 uModel;
            uniform mat4 uLightViewProj;
            void main() {
                gl_Position = uLightViewProj * uModel * vec4(aPos, 1.0);
            }
            """;

    static final String SHADOW_FRAGMENT_SHADER = """
            #version 330 core
            void main() { }
            """;

    static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec3 vColor;
            in vec3 vWorldPos;
            in vec3 vLocalPos;
            in vec3 vNormal;
            in float vHeight;
            in vec2 vUv;
            in vec4 vLightSpacePos;
            uniform vec3 uMaterialAlbedo;
            uniform float uMaterialMetallic;
            uniform float uMaterialRoughness;
            uniform vec4 uMaterialReactive;
            uniform vec4 uMaterialReactiveTuning;
            uniform int uUseAlbedoTexture;
            uniform sampler2D uAlbedoTexture;
            uniform int uUseNormalTexture;
            uniform sampler2D uNormalTexture;
            uniform int uUseMetallicRoughnessTexture;
            uniform sampler2D uMetallicRoughnessTexture;
            uniform int uUseOcclusionTexture;
            uniform sampler2D uOcclusionTexture;
            uniform sampler2D uIblIrradiance;
            uniform sampler2D uIblRadiance;
            uniform sampler2D uIblBrdfLut;
            uniform float uIblRadianceMaxLod;
            uniform vec3 uDirLightDir;
            uniform vec3 uDirLightColor;
            uniform float uDirLightIntensity;
            uniform int uLocalLightCount;
            uniform vec4 uLocalLightPosRange[8];
            uniform vec4 uLocalLightColorIntensity[8];
            uniform vec4 uLocalLightDirInner[8];
            uniform vec4 uLocalLightOuterTypeShadow[8];
            uniform vec3 uPointLightPos;
            uniform vec3 uPointLightDir;
            uniform int uPointShadowEnabled;
            uniform samplerCube uPointShadowMap;
            uniform float uPointShadowFarPlane;
            uniform int uPointShadowLightIndex;
            uniform int uShadowEnabled;
            uniform float uShadowStrength;
            uniform float uShadowBias;
            uniform float uShadowNormalBiasScale;
            uniform float uShadowSlopeBiasScale;
            uniform int uShadowPcfRadius;
            uniform int uShadowCascadeCount;
            uniform sampler2D uShadowMap;
            uniform int uLocalShadowCount;
            uniform sampler2D uLocalShadowMap;
            uniform mat4 uLocalShadowMatrix[4];
            uniform vec4 uLocalShadowAtlasRect[4];
            uniform vec4 uLocalShadowMeta[4];
            uniform int uFogEnabled;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform int uFogSteps;
            uniform int uSmokeEnabled;
            uniform vec3 uSmokeColor;
            uniform float uSmokeIntensity;
            uniform vec2 uViewportSize;
            uniform vec4 uIblParams;
            uniform mat4 uCurrentViewProj;
            uniform mat4 uPrevViewProj;
            uniform mat4 uPrevModel;
            uniform int uTonemapEnabled;
            uniform float uTonemapExposure;
            uniform float uTonemapGamma;
            uniform int uBloomEnabled;
            uniform float uBloomThreshold;
            uniform float uBloomStrength;
            uniform int uSsaoEnabled;
            uniform float uSsaoStrength;
            uniform float uSsaoRadius;
            uniform float uSsaoBias;
            uniform float uSsaoPower;
            uniform int uSmaaEnabled;
            uniform float uSmaaStrength;
            uniform int uTaaEnabled;
            uniform float uTaaBlend;
            uniform mat4 uView;
            uniform vec3 uAmbientColor;
            uniform float uAmbientIntensity;
            uniform float uAlphaCutoff;
            layout(location = 0) out vec4 FragColor;
            layout(location = 1) out vec4 VelocityColor;
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
                float maxLod = max(uIblRadianceMaxLod, 0.0);
                float lod = roughMix * maxLod;
                vec2 texel = 1.0 / vec2(textureSize(uIblRadiance, 0));
                vec2 axis = normalize(vec2(0.37, 0.93) + vec2(roughMix, 1.0 - roughMix) * 0.45);
                vec2 side = vec2(-axis.y, axis.x);
                float spread = mix(0.5, 3.0, roughMix);
                vec3 c0 = textureLod(uIblRadiance, roughUv, lod).rgb;
                vec3 c1 = textureLod(uIblRadiance, clamp(roughUv + axis * texel * spread, vec2(0.0), vec2(1.0)), lod).rgb;
                vec3 c2 = textureLod(uIblRadiance, clamp(roughUv - axis * texel * spread, vec2(0.0), vec2(1.0)), lod).rgb;
                vec3 c3 = textureLod(uIblRadiance, clamp(roughUv + side * texel * spread * 0.75, vec2(0.0), vec2(1.0)), lod).rgb;
                vec3 c4 = textureLod(uIblRadiance, clamp(roughUv - side * texel * spread * 0.75, vec2(0.0), vec2(1.0)), lod).rgb;
                return (c0 * 0.44) + (c1 * 0.18) + (c2 * 0.18) + (c3 * 0.10) + (c4 * 0.10);
            }
            float shadowTerm(vec3 normal, float ndl) {
                vec3 projCoords = vLightSpacePos.xyz / max(vLightSpacePos.w, 0.0001);
                projCoords = projCoords * 0.5 + 0.5;
                if (projCoords.z > 1.0 || projCoords.x < 0.0 || projCoords.x > 1.0 || projCoords.y < 0.0 || projCoords.y > 1.0) {
                    return 0.0;
                }
                float cascadeBiasScale = 1.0 + float(max(uShadowCascadeCount - 1, 0)) * 0.12;
                float baseBias = uShadowBias * max(uShadowNormalBiasScale, 0.25);
                float slopeBias = (1.0 - ndl) * uShadowBias * 2.0 * max(uShadowSlopeBiasScale, 0.25);
                float bias = max(baseBias, slopeBias) * cascadeBiasScale;
                int radius = max(uShadowPcfRadius, 0);
                vec2 texel = 1.0 / vec2(textureSize(uShadowMap, 0));
                float occlusion = 0.0;
                int taps = 0;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        float depth = texture(uShadowMap, projCoords.xy + vec2(x, y) * texel).r;
                        occlusion += (projCoords.z - bias) > depth ? 1.0 : 0.0;
                        taps++;
                    }
                }
                return taps > 0 ? (occlusion / float(taps)) : 0.0;
            }
            float pointShadowTerm(vec3 normal, vec3 lightDir, float currentDepth) {
                if (uPointShadowEnabled == 0 || currentDepth >= uPointShadowFarPlane) {
                    return 0.0;
                }
                float ndl = max(dot(normal, lightDir), 0.0);
                float depthRatio = clamp(currentDepth / max(uPointShadowFarPlane, 0.0001), 0.0, 1.0);
                float baseBias = uShadowBias * max(uShadowNormalBiasScale, 0.25);
                float slopeBias = (1.0 - ndl) * uShadowBias * 2.0 * max(uShadowSlopeBiasScale, 0.25);
                float bias = max(baseBias, slopeBias) * mix(0.85, 1.65, depthRatio);
                int radius = max(uShadowPcfRadius, 0);
                float diskRadius = (0.005 + depthRatio * 0.035) * (1.0 + float(radius) * 0.6);
                vec3 fragToLight = vWorldPos - uPointLightPos;
                vec3 dirs[6] = vec3[](
                    vec3( 1.0,  0.0,  0.0),
                    vec3(-1.0,  0.0,  0.0),
                    vec3( 0.0,  1.0,  0.0),
                    vec3( 0.0, -1.0,  0.0),
                    vec3( 0.0,  0.0,  1.0),
                    vec3( 0.0,  0.0, -1.0)
                );
                float occlusion = 0.0;
                int taps = 0;
                for (int i = 0; i < 6; i++) {
                    for (int r = -4; r <= 4; r++) {
                        if (abs(r) > radius) {
                            continue;
                        }
                        vec3 sampleVec = fragToLight + dirs[i] * diskRadius * float(r);
                        float closestDepth = texture(uPointShadowMap, sampleVec).r * uPointShadowFarPlane;
                        occlusion += (currentDepth - bias) > closestDepth ? 1.0 : 0.0;
                        taps++;
                    }
                }
                return taps > 0 ? (occlusion / float(taps)) : 0.0;
            }
            float localSpotShadowTerm(int shadowIdx, vec3 normal, float ndl) {
                vec4 lightSpace = uLocalShadowMatrix[shadowIdx] * vec4(vWorldPos, 1.0);
                vec3 projCoords = lightSpace.xyz / max(lightSpace.w, 0.0001);
                projCoords = projCoords * 0.5 + 0.5;
                if (projCoords.z > 1.0 || projCoords.z < 0.0 || projCoords.x < 0.0 || projCoords.x > 1.0 || projCoords.y < 0.0 || projCoords.y > 1.0) {
                    return 0.0;
                }
                vec4 rect = uLocalShadowAtlasRect[shadowIdx];
                vec2 uv = rect.xy + projCoords.xy * rect.zw;
                float baseBias = uShadowBias * max(uShadowNormalBiasScale, 0.25);
                float slopeBias = (1.0 - ndl) * uShadowBias * 2.0 * max(uShadowSlopeBiasScale, 0.25);
                float bias = max(baseBias, slopeBias);
                int radius = max(uShadowPcfRadius, 0);
                vec2 texel = 1.0 / vec2(textureSize(uLocalShadowMap, 0));
                float occlusion = 0.0;
                int taps = 0;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        float depth = texture(uLocalShadowMap, uv + vec2(x, y) * texel).r;
                        occlusion += (projCoords.z - bias) > depth ? 1.0 : 0.0;
                        taps++;
                    }
                }
                return taps > 0 ? (occlusion / float(taps)) : 0.0;
            }
            void main() {
                vec3 albedo = vColor * uMaterialAlbedo;
                float albedoAlpha = 1.0;
                vec3 normal = normalize(vNormal);
                if (uUseAlbedoTexture == 1) {
                    vec4 tex = texture(uAlbedoTexture, vUv);
                    albedo *= tex.rgb;
                    albedoAlpha = tex.a;
                }
                if (uAlphaCutoff > 0.0 && albedoAlpha < uAlphaCutoff) discard;
                if (uUseNormalTexture == 1) {
                    vec3 ntex = texture(uNormalTexture, vUv).rgb * 2.0 - 1.0;
                    vec3 dPdx = dFdx(vWorldPos);
                    vec3 dPdy = dFdy(vWorldPos);
                    vec2 dUVdx = dFdx(vUv);
                    vec2 dUVdy = dFdy(vUv);
                    float invDet = 1.0 / max(abs(dUVdx.x * dUVdy.y - dUVdx.y * dUVdy.x), 0.00001);
                    vec3 T = normalize((dPdx * dUVdy.y - dPdy * dUVdx.y) * invDet);
                    vec3 B = normalize((dPdy * dUVdx.x - dPdx * dUVdy.x) * invDet);
                    vec3 N = normal;
                    T = normalize(T - dot(T, N) * N);
                    B = cross(N, T);
                    normal = normalize(T * ntex.x + B * ntex.y + N * ntex.z);
                }
                float metallic = clamp(uMaterialMetallic, 0.0, 1.0);
                float roughness = clamp(uMaterialRoughness, 0.04, 1.0);
                if (uUseMetallicRoughnessTexture == 1) {
                    vec3 mrTex = texture(uMetallicRoughnessTexture, vUv).rgb;
                    metallic = clamp(metallic * mrTex.b, 0.0, 1.0);
                    roughness = clamp(roughness * max(mrTex.g, 0.04), 0.04, 1.0);
                }
                float normalVariance = clamp((length(dFdx(normal)) + length(dFdy(normal))) * 0.30, 0.0, 1.0);
                float normalMapVariance = 0.0;
                if (uUseNormalTexture == 1) {
                    vec3 ntex = texture(uNormalTexture, vUv).rgb * 2.0 - 1.0;
                    normalMapVariance = clamp((1.0 - clamp(length(ntex), 0.0, 1.0)) * 1.55, 0.0, 1.0);
                }
                float toksvigVariance = clamp(normalVariance * 0.70 + normalMapVariance * 0.65, 0.0, 1.0);
                roughness = clamp(sqrt(roughness * roughness + toksvigVariance * 0.52), 0.04, 1.0);
                vec3 lDir = normalize(-uDirLightDir);
                vec3 viewPos = (uView * vec4(vWorldPos, 1.0)).xyz;
                vec3 viewDir = normalize(-viewPos);
                vec3 halfVec = normalize(lDir + viewDir);
                float ndl = max(dot(normal, lDir), 0.0);
                float ndv = max(dot(normal, viewDir), 0.0);
                float ndh = max(dot(normal, halfVec), 0.0);
                float vdh = max(dot(viewDir, halfVec), 0.0);
                vec3 f0 = mix(vec3(0.04), albedo, metallic);
                vec3 f = fresnelSchlick(vdh, f0);
                float d = distributionGGX(ndh, roughness);
                float g = geometrySmith(ndv, ndl, roughness);
                vec3 numerator = d * g * f;
                float denominator = max(4.0 * ndv * ndl, 0.0001);
                vec3 specular = numerator / denominator;
                vec3 kd = (1.0 - f) * (1.0 - metallic);
                vec3 diffuse = kd * albedo / 3.14159;
                vec3 directional = (diffuse + specular) * uDirLightColor * (ndl * uDirLightIntensity);

                vec3 pointLit = vec3(0.0);
                int lightCount = clamp(uLocalLightCount, 0, 8);
                for (int i = 0; i < lightCount; i++) {
                    vec3 localPos = uLocalLightPosRange[i].xyz;
                    float localRange = max(uLocalLightPosRange[i].w, 0.1);
                    vec3 localColor = uLocalLightColorIntensity[i].rgb;
                    float localIntensity = max(uLocalLightColorIntensity[i].a, 0.0);
                    vec3 localDir = normalize(uLocalLightDirInner[i].xyz);
                    float localInner = uLocalLightDirInner[i].w;
                    float localOuter = uLocalLightOuterTypeShadow[i].x;
                    float localIsSpot = uLocalLightOuterTypeShadow[i].y;
                    vec3 localToLight = localPos - vWorldPos;
                    vec3 localLightDir = normalize(localToLight);
                    float localDist = max(length(localToLight), 0.1);
                    float localNdl = max(dot(normal, localLightDir), 0.0);
                    float normalizedDistance = clamp(localDist / localRange, 0.0, 1.0);
                    float rangeFade = 1.0 - pow(normalizedDistance, 4.0);
                    rangeFade = clamp(rangeFade * rangeFade, 0.0, 1.0);
                    float attenuation = (1.0 / (1.0 + 0.35 * localDist + 0.1 * localDist * localDist)) * rangeFade;
                    float spotAttenuation = 1.0;
                    if (localIsSpot > 0.5) {
                        vec3 lightToFrag = normalize(vWorldPos - localPos);
                        float cosTheta = dot(localDir, lightToFrag);
                        float coneRange = max(localInner - localOuter, 0.0001);
                        spotAttenuation = clamp((cosTheta - localOuter) / coneRange, 0.0, 1.0);
                        spotAttenuation *= spotAttenuation;
                    }
                    float localShadowMul = 1.0;
                    int localShadowCount = clamp(uLocalShadowCount, 0, 4);
                    for (int s = 0; s < localShadowCount; s++) {
                        int shadowLightIndex = int(uLocalShadowMeta[s].x + 0.5);
                        float shadowType = uLocalShadowMeta[s].y;
                        if (shadowLightIndex == i && shadowType > 0.5) {
                            float occlusion = localSpotShadowTerm(s, normal, localNdl);
                            localShadowMul = 1.0 - clamp(occlusion * min(uShadowStrength, 0.9), 0.0, 0.9);
                            break;
                        }
                    }
                    if (localIsSpot <= 0.5 && uPointShadowEnabled == 1 && i == uPointShadowLightIndex) {
                        float pointOcclusion = pointShadowTerm(normal, localLightDir, localDist);
                        localShadowMul *= (1.0 - clamp(pointOcclusion * min(uShadowStrength, 0.85), 0.0, 0.9));
                    }
                    pointLit += (kd * albedo / 3.14159) * localColor * (localNdl * attenuation * spotAttenuation * localIntensity * localShadowMul);
                }
                float ao = 1.0;
                vec3 ambient = uAmbientColor * uAmbientIntensity * albedo;
                if (uUseOcclusionTexture == 1) {
                    ao = clamp(texture(uOcclusionTexture, vUv).r, 0.0, 1.0);
                    ambient *= ao;
                }
                if (uIblParams.x > 0.5) {
                    float iblDiffuseWeight = clamp(uIblParams.y, 0.0, 2.0);
                    float iblSpecWeight = clamp(uIblParams.z, 0.0, 2.0);
                    float prefilter = clamp(uIblParams.w, 0.0, 1.0);
                    vec3 irr = texture(uIblIrradiance, vUv).rgb;
                    vec3 reflectDir = reflect(-viewDir, normal);
                    vec2 specUv = clamp(reflectDir.xy * 0.5 + vec2(0.5), vec2(0.0), vec2(1.0));
                    vec3 rad = sampleIblRadiance(specUv, vUv, roughness, prefilter);
                    vec2 brdfUv = vec2(clamp(ndv, 0.0, 1.0), clamp(roughness, 0.0, 1.0));
                    vec2 brdf = texture(uIblBrdfLut, brdfUv).rg;
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
                    vec3 iblDiffuse = kD * albedo * ao * irr
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
                    ambient += iblDiffuse + iblSpec;
                }
                vec3 color = ambient + directional + pointLit;
                if (uShadowEnabled == 1) {
                    float shadowFactor = clamp(shadowTerm(normal, ndl) * uShadowStrength, 0.0, 0.9);
                    color *= (1.0 - shadowFactor);
                }
                if (uFogEnabled == 1) {
                    float normalizedHeight = clamp((vHeight + 1.0) * 0.5, 0.0, 1.0);
                    float fogFactor = clamp(exp(-uFogDensity * (1.0 - normalizedHeight)), 0.0, 1.0);
                    if (uFogSteps > 0) {
                        fogFactor = floor(fogFactor * float(uFogSteps)) / float(uFogSteps);
                    }
                    color = mix(uFogColor, color, fogFactor);
                }
                if (uSmokeEnabled == 1) {
                    vec2 safeViewport = max(uViewportSize, vec2(1.0));
                    float radial = clamp(1.0 - length(gl_FragCoord.xy / safeViewport - vec2(0.5)), 0.0, 1.0);
                    float smokeFactor = clamp(uSmokeIntensity * (0.35 + radial * 0.65), 0.0, 0.85);
                    color = mix(color, uSmokeColor, smokeFactor);
                }
                if (uTonemapEnabled == 1) {
                    float exposure = max(uTonemapExposure, 0.0001);
                    float gamma = max(uTonemapGamma, 0.0001);
                    color = vec3(1.0) - exp(-color * exposure);
                    color = pow(max(color, vec3(0.0)), vec3(1.0 / gamma));
                }
                if (uBloomEnabled == 1) {
                    float threshold = clamp(uBloomThreshold, 0.0, 4.0);
                    float strength = clamp(uBloomStrength, 0.0, 2.0);
                    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    float bright = max(0.0, luma - threshold);
                    float bloom = bright * strength;
                    color += color * bloom;
                }
                if (uSsaoEnabled == 1) {
                    float depthDx = dFdx(gl_FragCoord.z);
                    float depthDy = dFdy(gl_FragCoord.z);
                    float depthDelta = length(vec2(depthDx, depthDy));
                    float normalDelta = length(dFdx(normal)) + length(dFdy(normal));
                    float curvature = clamp((depthDelta * 230.0) + (normalDelta * 0.42), 0.0, 1.0);
                    float micro = clamp((abs(dFdx(depthDx)) + abs(dFdy(depthDy))) * 3600.0, 0.0, 1.0);
                    float edge = clamp(curvature * 0.8 + micro * 0.2, 0.0, 1.0);
                    float ssaoStrength = clamp(uSsaoStrength, 0.0, 1.0);
                    float ssaoRadius = clamp(uSsaoRadius, 0.2, 3.0);
                    float ssaoBias = clamp(uSsaoBias, 0.0, 0.2);
                    float ssaoPower = clamp(uSsaoPower, 0.5, 4.0);
                    float shapedEdge = clamp(edge * mix(0.75, 1.25, (ssaoRadius - 0.2) / 2.8) - ssaoBias, 0.0, 1.0);
                    float occlusion = pow(clamp(shapedEdge * ssaoStrength, 0.0, 0.92), max(0.60, 1.25 - (ssaoPower * 0.32)));
                    color *= (1.0 - occlusion * 0.82);
                }
                if (uSmaaEnabled == 1) {
                    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    float edgeDx = abs(dFdx(luma));
                    float edgeDy = abs(dFdy(luma));
                    float edge = clamp((edgeDx + edgeDy) * 5.5, 0.0, 1.0);
                    float aaStrength = clamp(uSmaaStrength, 0.0, 1.0);
                    color = mix(color, vec3(luma), edge * aaStrength * 0.20);
                }
                if (uTaaEnabled == 1) {
                    float blend = clamp(uTaaBlend, 0.0, 0.95);
                    color = mix(color, vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))), blend * 0.05);
                }
                vec4 currClip = uCurrentViewProj * vec4(vWorldPos, 1.0);
                vec4 prevClip = uPrevViewProj * (uPrevModel * vec4(vLocalPos, 1.0));
                float currW = abs(currClip.w) > 0.000001 ? currClip.w : 1.0;
                float prevW = abs(prevClip.w) > 0.000001 ? prevClip.w : 1.0;
                vec2 currNdc = currClip.xy / currW;
                vec2 prevNdc = prevClip.xy / prevW;
                vec2 velocityNdc = clamp(prevNdc - currNdc, vec2(-1.0), vec2(1.0));
                float emissiveMask = smoothstep(0.72, 0.97, dot(albedo, vec3(0.2126, 0.7152, 0.0722))) * (1.0 - roughness) * 0.6;
                float alphaTestMask = 1.0 - smoothstep(0.78, 0.98, albedoAlpha);
                float foliageMask = smoothstep(0.06, 0.42, albedo.g - max(albedo.r, albedo.b));
                float specularMask = clamp((1.0 - roughness) * mix(0.35, 1.0, metallic), 0.0, 1.0);
                float heuristicReactive = clamp(max(alphaTestMask, foliageMask) * 0.85 + specularMask * 0.30 + emissiveMask, 0.0, 1.0);
                float reactiveBoost = clamp(uMaterialReactiveTuning.x, 0.0, 2.0);
                float taaHistoryClamp = clamp(uMaterialReactiveTuning.y, 0.0, 1.0);
                float emissiveReactiveBoost = clamp(uMaterialReactiveTuning.z, 0.0, 3.0);
                float reactivePreset = clamp(uMaterialReactiveTuning.w, 0.0, 3.0);
                float authoredReactive = clamp(uMaterialReactive.x * reactiveBoost * (1.0 + 0.65 * max(uMaterialReactive.y, uMaterialReactive.z)), 0.0, 1.0);
                bool authoredEnabled = (uMaterialReactive.x > 0.001) || (uMaterialReactive.y > 0.5) || (uMaterialReactive.z > 0.5);
                heuristicReactive = clamp(heuristicReactive + emissiveMask * emissiveReactiveBoost * 0.45, 0.0, 1.0);
                float presetScale = reactivePreset < 0.5 ? 1.0 : (reactivePreset < 1.5 ? 0.82 : (reactivePreset < 2.5 ? 1.0 : 1.2));
                float materialReactive = (authoredEnabled ? authoredReactive : heuristicReactive) * (1.0 + (1.0 - taaHistoryClamp) * 0.6) * presetScale;
                FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
                VelocityColor = vec4(velocityNdc * 0.5 + 0.5, clamp(gl_FragCoord.z, 0.0, 1.0), materialReactive);
            }
            """;

    static final String POST_VERTEX_SHADER = """
            #version 330 core
            out vec2 vUv;
            const vec2 POS[3] = vec2[](
                vec2(-1.0, -1.0),
                vec2(3.0, -1.0),
                vec2(-1.0, 3.0)
            );
            void main() {
                vec2 p = POS[gl_VertexID];
                vUv = p * 0.5 + vec2(0.5);
                gl_Position = vec4(p, 0.0, 1.0);
            }
            """;

    static final String POST_FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vUv;
            uniform sampler2D uSceneColor;
            uniform sampler2D uSceneVelocity;
            uniform int uTonemapEnabled;
            uniform float uTonemapExposure;
            uniform float uTonemapGamma;
            uniform int uBloomEnabled;
            uniform float uBloomThreshold;
            uniform float uBloomStrength;
            uniform int uSsaoEnabled;
            uniform float uSsaoStrength;
            uniform float uSsaoRadius;
            uniform float uSsaoBias;
            uniform float uSsaoPower;
            uniform int uSmaaEnabled;
            uniform float uSmaaStrength;
            uniform int uTaaEnabled;
            uniform float uTaaBlend;
            uniform int uTaaHistoryValid;
            uniform vec2 uTaaJitterDelta;
            uniform vec2 uTaaMotionUv;
            uniform int uTaaDebugView;
            uniform float uTaaClipScale;
            uniform int uTaaLumaClipEnabled;
            uniform float uTaaSharpenStrength;
            uniform float uTaaUpsampleScale;
            uniform int uReflectionsEnabled;
            uniform int uReflectionsMode;
            uniform float uReflectionsSsrStrength;
            uniform float uReflectionsSsrMaxRoughness;
            uniform float uReflectionsSsrStepScale;
            uniform float uReflectionsTemporalWeight;
            uniform float uReflectionsPlanarStrength;
            uniform sampler2D uTaaHistory;
            uniform sampler2D uTaaHistoryVelocity;
            out vec4 FragColor;
            float ssaoLite(vec2 uv) {
                float radius = clamp(uSsaoRadius, 0.2, 3.0);
                vec2 texel = (1.0 / vec2(textureSize(uSceneColor, 0))) * mix(0.75, 2.0, (radius - 0.2) / 2.8);
                vec3 c = texture(uSceneColor, uv).rgb;
                vec3 cx = texture(uSceneColor, clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                vec3 cy = texture(uSceneColor, clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                vec3 cxy = texture(uSceneColor, clamp(uv + texel, vec2(0.0), vec2(1.0))).rgb;
                vec3 cxny = texture(uSceneColor, clamp(uv + vec2(texel.x, -texel.y), vec2(0.0), vec2(1.0))).rgb;
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
                float ssaoStrength = clamp(uSsaoStrength, 0.0, 1.0);
                float ssaoBias = clamp(uSsaoBias, 0.0, 0.2);
                float ssaoPower = clamp(uSsaoPower, 0.5, 4.0);
                float shapedEdge = clamp(edge - ssaoBias, 0.0, 1.0);
                float occlusion = pow(clamp(shapedEdge * ssaoStrength, 0.0, 0.92), max(0.60, 1.18 - (ssaoPower * 0.30)));
                return 1.0 - (occlusion * 0.82);
            }
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
                float dA = abs(smaaLuma(texture(uSceneColor, clamp(diagA, vec2(0.0), vec2(1.0))).rgb) - smaaLuma(texture(uSceneColor, uv).rgb));
                float dB = abs(smaaLuma(texture(uSceneColor, clamp(diagB, vec2(0.0), vec2(1.0))).rgb) - smaaLuma(texture(uSceneColor, uv).rgb));
                float dC = abs(smaaLuma(texture(uSceneColor, clamp(diagC, vec2(0.0), vec2(1.0))).rgb) - smaaLuma(texture(uSceneColor, uv).rgb));
                float dD = abs(smaaLuma(texture(uSceneColor, clamp(diagD, vec2(0.0), vec2(1.0))).rgb) - smaaLuma(texture(uSceneColor, uv).rgb));
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
                float strength = clamp(uSmaaStrength, 0.0, 1.0);
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
                if (uReflectionsEnabled == 0 || uReflectionsMode == 0) {
                    return color;
                }
                int packedMode = max(uReflectionsMode, 0);
                int mode = packedMode & 7;
                bool hiZEnabled = (packedMode & (1 << 3)) != 0;
                int denoisePasses = (packedMode >> 4) & 7;
                bool planarClipEnabled = (packedMode & (1 << 7)) != 0;
                bool probeVolumeEnabled = (packedMode & (1 << 8)) != 0;
                bool probeBoxProjectionEnabled = (packedMode & (1 << 9)) != 0;
                bool rtRequested = (packedMode & (1 << 10)) != 0;
                vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                float roughnessProxy = clamp(1.0 - dot(color, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
                float roughnessMask = 1.0 - smoothstep(clamp(uReflectionsSsrMaxRoughness, 0.05, 1.0), 1.0, roughnessProxy);
                float ssrStrength = clamp(uReflectionsSsrStrength, 0.0, 1.0) * roughnessMask;
                float stepScale = clamp(uReflectionsSsrStepScale, 0.5, 3.0);
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
                    float sampleDepth = texture(uSceneVelocity, traceUv).b;
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
                float temporalWeight = clamp(uReflectionsTemporalWeight, 0.0, 0.98);
                vec3 historyColor = texture(uTaaHistory, clamp(uv + uTaaMotionUv, vec2(0.0), vec2(1.0))).rgb;
                vec3 temporalColor = mix(ssrColor, historyColor, temporalWeight * clamp(historyConfidenceOut, 0.0, 1.0));
                float planarStrength = clamp(uReflectionsPlanarStrength, 0.0, 1.0);
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
                float currentDepth = texture(uSceneVelocity, vUv).b;
                float historyConfidenceOut = 1.0;
                if (uTonemapEnabled == 1) {
                    float exposure = max(uTonemapExposure, 0.0001);
                    float gamma = max(uTonemapGamma, 0.0001);
                    color = vec3(1.0) - exp(-color * exposure);
                    color = pow(max(color, vec3(0.0)), vec3(1.0 / gamma));
                }
                if (uBloomEnabled == 1) {
                    float threshold = clamp(uBloomThreshold, 0.0, 4.0);
                    float strength = clamp(uBloomStrength, 0.0, 2.0);
                    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    float bright = max(0.0, luma - threshold);
                    float bloom = bright * strength;
                    color += color * bloom;
                }
                if (uSsaoEnabled == 1) {
                    color *= ssaoLite(vUv);
                }
                if (uSmaaEnabled == 1) {
                    color = smaaFull(vUv, color);
                }
                if (uTaaEnabled == 1 && uTaaHistoryValid == 1) {
                    vec2 texel = 1.0 / vec2(textureSize(uSceneColor, 0));
                    vec4 velocitySample = texture(uSceneVelocity, vUv);
                    vec2 velocityUv = velocitySample.rg * 2.0 - 1.0;
                    float materialReactive = velocitySample.a;
                    vec2 historyUv = clamp(vUv + uTaaJitterDelta + uTaaMotionUv + (velocityUv * 0.5), vec2(0.0), vec2(1.0));
                    vec4 historySample = texture(uTaaHistory, historyUv);
                    vec3 history = historySample.rgb;
                    float historyConfidence = clamp(historySample.a, 0.0, 1.0);
                    float hc1 = texture(uTaaHistory, clamp(historyUv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).a;
                    float hc2 = texture(uTaaHistory, clamp(historyUv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).a;
                    float hc3 = texture(uTaaHistory, clamp(historyUv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).a;
                    float hc4 = texture(uTaaHistory, clamp(historyUv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).a;
                    float dilatedHistoryConfidence = clamp(max(historyConfidence, max(max(hc1, hc2), max(hc3, hc4)) * 0.92), 0.0, 1.0);
                    float historyDepth = texture(uTaaHistoryVelocity, historyUv).b;
                    vec3 n1 = texture(uSceneColor, clamp(vUv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                    vec3 n2 = texture(uSceneColor, clamp(vUv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).rgb;
                    vec3 n3 = texture(uSceneColor, clamp(vUv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                    vec3 n4 = texture(uSceneColor, clamp(vUv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).rgb;
                    vec3 neighMin = min(min(min(color, n1), min(n2, n3)), n4);
                    vec3 neighMax = max(max(max(color, n1), max(n2, n3)), n4);
                    float d1 = texture(uSceneVelocity, clamp(vUv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).b;
                    float d2 = texture(uSceneVelocity, clamp(vUv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).b;
                    float d3 = texture(uSceneVelocity, clamp(vUv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).b;
                    float d4 = texture(uSceneVelocity, clamp(vUv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).b;
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
                    float upsamplePenalty = clamp((1.0 - clamp(uTaaUpsampleScale, 0.5, 1.0)) * 1.6, 0.0, 0.75);
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
                    vec3 clampedHistory = clamp(history, neighMin - vec3(clipExpand * uTaaClipScale), neighMax + vec3(clipExpand * uTaaClipScale));
                    if (uTaaLumaClipEnabled == 1) {
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
                    float blend = clamp(uTaaBlend, 0.0, 0.95) * historyTrust * (1.0 - reactive * 0.88);
                    color = mix(color, clampedHistory, blend);
                    color = taaSharpen(vUv, color, clamp(uTaaSharpenStrength, 0.0, 0.35) * (1.0 - reactive));
                    historyConfidenceOut = clamp(max(confidenceState * 0.94, 1.0 - reactive * 0.86), 0.02, 1.0);
                    if (uTaaDebugView == 1) {
                        color = vec3(reactive);
                    } else if (uTaaDebugView == 2) {
                        color = vec3(disocclusionReject);
                    } else if (uTaaDebugView == 3) {
                        color = vec3(historyTrust);
                    } else if (uTaaDebugView == 4) {
                        color = vec3(abs(velocityUv.x), abs(velocityUv.y), length(velocityUv) * 0.5);
                    } else if (uTaaDebugView == 5) {
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
                FragColor = vec4(clamp(color, 0.0, 1.0), historyConfidenceOut);
            }
            """;
}
