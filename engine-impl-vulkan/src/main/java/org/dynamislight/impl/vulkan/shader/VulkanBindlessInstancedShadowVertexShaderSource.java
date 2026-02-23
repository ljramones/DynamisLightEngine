package org.dynamislight.impl.vulkan.shader;

public final class VulkanBindlessInstancedShadowVertexShaderSource {
    private VulkanBindlessInstancedShadowVertexShaderSource() {
    }

    public static String shadowVertex() {
        return """
                #version 450
                #extension GL_EXT_nonuniform_qualifier : require
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
                    mat4 uPlanarView;
                    mat4 uPlanarProj;
                    mat4 uPlanarPrevViewProj;
                } gbo;

                layout(set = 3, binding = 3) readonly buffer InstanceData {
                    vec4 raw[];
                } instanceHeap[];

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

                layout(push_constant) uniform ShadowPush {
                    int uCascadeIndex;
                } pc;

                mat4 instanceModel(uint heapIndex, uint instanceIndex) {
                    int base = int(instanceIndex) * 5;
                    return mat4(
                        instanceHeap[nonuniformEXT(heapIndex)].raw[base + 0],
                        instanceHeap[nonuniformEXT(heapIndex)].raw[base + 1],
                        instanceHeap[nonuniformEXT(heapIndex)].raw[base + 2],
                        instanceHeap[nonuniformEXT(heapIndex)].raw[base + 3]
                    );
                }

                void main() {
                    uint drawId = uint(gl_DrawID);
                    DrawMeta meta = drawMeta.entries[drawId];
                    int cascadeIndex = clamp(pc.uCascadeIndex, 0, 23);
                    mat4 model = instanceModel(meta.instanceDataIndex, gl_InstanceIndex);
                    gl_Position = gbo.uShadowLightViewProj[cascadeIndex] * model * vec4(inPos, 1.0);
                }
                """;
    }
}
