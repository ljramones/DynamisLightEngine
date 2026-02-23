package org.dynamislight.impl.vulkan.shader;

public final class VulkanBindlessInstancedVertexShaderSource {
    private VulkanBindlessInstancedVertexShaderSource() {
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

                layout(push_constant) uniform MainPush {
                    vec4 uPlanar;
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
                    mat4 model = instanceModel(meta.instanceDataIndex, gl_InstanceIndex);
                    vec4 world = model * vec4(inPos, 1.0);
                    bool planarCapturePass = pc.uPlanar.x > 0.5;
                    vWorldPos = world.xyz;
                    vHeight = world.y;
                    vNormal = normalize(mat3(model) * inNormal);
                    vTangent = normalize(mat3(model) * inTangent);
                    vUv = inUv;
                    vLocalPos = inPos;
                    mat4 activeView = planarCapturePass ? gbo.uPlanarView : gbo.uView;
                    mat4 activeProj = planarCapturePass ? gbo.uPlanarProj : gbo.uProj;
                    gl_Position = activeProj * activeView * world;
                }
                """;
    }
}
