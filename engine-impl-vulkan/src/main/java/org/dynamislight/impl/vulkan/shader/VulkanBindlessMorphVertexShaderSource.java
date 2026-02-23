package org.dynamislight.impl.vulkan.shader;

public final class VulkanBindlessMorphVertexShaderSource {
    private VulkanBindlessMorphVertexShaderSource() {
    }

    public static String mainVertex() {
        return """
                #version 450
                #extension GL_EXT_nonuniform_qualifier : require
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

                layout(set = 3, binding = 1) readonly buffer MorphDeltas {
                    float deltaData[];
                } morphHeap[];
                layout(set = 3, binding = 2) uniform MorphWeights {
                    float weights[256];
                } morphWeightHeap[];

                struct DrawMeta {
                    uint jointPaletteIndex;
                    uint morphDeltaIndex;
                    uint morphWeightIndex;
                    uint instanceDataIndex;
                    uint materialIndex;
                    uint drawFlags;
                    uint meshIndex;
                    uint reserved0;
                };

                layout(set = 3, binding = 4) readonly buffer DrawMetaBuffer {
                    DrawMeta entries[];
                } drawMeta;

                layout(push_constant) uniform MainPush {
                    vec4 uPlanar;
                } pc;

                void main() {
                    uint drawId = uint(gl_DrawID);
                    DrawMeta meta = drawMeta.entries[drawId];
                    uint morphDeltaIdx = meta.morphDeltaIndex;
                    uint morphWeightIdx = meta.morphWeightIndex;
                    bool planarCapturePass = pc.uPlanar.x > 0.5;
                    int morphTargetCount = max(0, int(pc.uPlanar.z + 0.5));
                    int vertexCount = max(1, int(pc.uPlanar.w + 0.5));

                    vec3 morphedPos = inPos;
                    vec3 morphedNormal = inNormal;
                    for (int t = 0; t < morphTargetCount; t++) {
                        int base = (t * vertexCount + gl_VertexIndex) * 6;
                        float w = morphWeightHeap[nonuniformEXT(morphWeightIdx)].weights[t];
                        morphedPos += w * vec3(
                            morphHeap[nonuniformEXT(morphDeltaIdx)].deltaData[base],
                            morphHeap[nonuniformEXT(morphDeltaIdx)].deltaData[base + 1],
                            morphHeap[nonuniformEXT(morphDeltaIdx)].deltaData[base + 2]
                        );
                        morphedNormal += w * vec3(
                            morphHeap[nonuniformEXT(morphDeltaIdx)].deltaData[base + 3],
                            morphHeap[nonuniformEXT(morphDeltaIdx)].deltaData[base + 4],
                            morphHeap[nonuniformEXT(morphDeltaIdx)].deltaData[base + 5]
                        );
                    }
                    morphedNormal = normalize(morphedNormal);
                    vec4 worldPos = obj.uModel * vec4(morphedPos, 1.0);
                    vWorldPos = worldPos.xyz;
                    vHeight = worldPos.y;
                    vNormal = normalize(mat3(obj.uModel) * morphedNormal);
                    vTangent = normalize(mat3(obj.uModel) * inTangent);
                    vUv = inUv;
                    vLocalPos = morphedPos;
                    mat4 activeView = planarCapturePass ? gbo.uPlanarView : gbo.uView;
                    mat4 activeProj = planarCapturePass ? gbo.uPlanarProj : gbo.uProj;
                    gl_Position = activeProj * activeView * worldPos;
                }
                """;
    }
}
