package org.dynamislight.impl.vulkan.shader;

public final class VulkanShadowShaderSources {
    private VulkanShadowShaderSources() {
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

}
