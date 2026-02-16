package org.dynamislight.impl.vulkan.uniform;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;

import static org.dynamislight.impl.vulkan.math.VulkanMath.identityMatrix;

public final class VulkanUniformWriters {
    private VulkanUniformWriters() {
    }

    public static void writeGlobalSceneUniform(ByteBuffer target, GlobalSceneUniformInput in) {
        target.position(0);
        target.limit(in.globalSceneUniformBytes());
        FloatBuffer fb = target.slice().order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(in.viewMatrix());
        fb.put(in.projMatrix());
        fb.put(new float[]{in.dirLightDirX(), in.dirLightDirY(), in.dirLightDirZ(), 0f});
        fb.put(new float[]{in.dirLightColorR(), in.dirLightColorG(), in.dirLightColorB(), 0f});
        fb.put(new float[]{in.pointLightPosX(), in.pointLightPosY(), in.pointLightPosZ(), in.pointShadowFarPlane()});
        fb.put(new float[]{in.pointLightColorR(), in.pointLightColorG(), in.pointLightColorB(), 0f});
        fb.put(new float[]{in.pointLightDirX(), in.pointLightDirY(), in.pointLightDirZ(), 0f});
        fb.put(new float[]{in.pointLightInnerCos(), in.pointLightOuterCos(), in.pointLightIsSpot(), in.pointShadowEnabled() ? 1f : 0f});
        fb.put(new float[]{in.dirLightIntensity(), in.pointLightIntensity(), 0f, 0f});
        fb.put(new float[]{in.shadowEnabled() ? 1f : 0f, in.shadowStrength(), in.shadowBias(), (float) in.shadowPcfRadius()});
        float split1 = in.shadowCascadeSplitNdc()[0];
        float split2 = in.shadowCascadeSplitNdc()[1];
        float split3 = in.shadowCascadeSplitNdc()[2];
        fb.put(new float[]{(float) in.shadowCascadeCount(), (float) in.shadowMapResolution(), split1, split2});
        fb.put(new float[]{0f, split3, 0f, 0f});
        fb.put(new float[]{in.fogEnabled() ? 1f : 0f, in.fogDensity(), in.ssaoRadius(), in.ssaoBias()});
        fb.put(new float[]{in.fogR(), in.fogG(), in.fogB(), (float) in.fogSteps()});
        float viewportW = (float) Math.max(1, in.swapchainWidth());
        float viewportH = (float) Math.max(1, in.swapchainHeight());
        fb.put(new float[]{in.smokeEnabled() ? 1f : 0f, in.smokeIntensity(), viewportW, viewportH});
        fb.put(new float[]{in.smokeR(), in.smokeG(), in.smokeB(), in.ssaoPower()});
        fb.put(new float[]{in.iblEnabled() ? 1f : 0f, in.iblDiffuseStrength(), in.iblSpecularStrength(), in.iblPrefilterStrength()});
        boolean scenePostEnabled = !in.postOffscreenActive();
        fb.put(new float[]{scenePostEnabled && in.tonemapEnabled() ? 1f : 0f, in.tonemapExposure(), in.tonemapGamma(), scenePostEnabled && in.ssaoEnabled() ? 1f : 0f});
        fb.put(new float[]{scenePostEnabled && in.bloomEnabled() ? 1f : 0f, in.bloomThreshold(), in.bloomStrength(), in.ssaoStrength()});
        for (int i = 0; i < in.shadowLightViewProjMatrices().length; i++) {
            fb.put(in.shadowLightViewProjMatrices()[i]);
        }
    }

    public static void writeObjectUniform(ByteBuffer target, int offset, int objectUniformBytes, VulkanGpuMesh mesh) {
        ByteBuffer slice = target.duplicate();
        slice.position(offset);
        slice.limit(offset + objectUniformBytes);
        FloatBuffer fb = slice.slice().order(ByteOrder.nativeOrder()).asFloatBuffer();
        if (mesh == null) {
            fb.put(identityMatrix());
            fb.put(new float[]{1f, 1f, 1f, 1f});
            fb.put(new float[]{0f, 0.8f, 0f, 0f});
        } else {
            fb.put(mesh.modelMatrix);
            fb.put(new float[]{mesh.colorR, mesh.colorG, mesh.colorB, 1f});
            fb.put(new float[]{mesh.metallic, mesh.roughness, 0f, 0f});
        }
    }

    public record GlobalSceneUniformInput(
            int globalSceneUniformBytes,
            float[] viewMatrix,
            float[] projMatrix,
            float dirLightDirX,
            float dirLightDirY,
            float dirLightDirZ,
            float dirLightColorR,
            float dirLightColorG,
            float dirLightColorB,
            float pointLightPosX,
            float pointLightPosY,
            float pointLightPosZ,
            float pointShadowFarPlane,
            float pointLightColorR,
            float pointLightColorG,
            float pointLightColorB,
            float pointLightDirX,
            float pointLightDirY,
            float pointLightDirZ,
            float pointLightInnerCos,
            float pointLightOuterCos,
            float pointLightIsSpot,
            boolean pointShadowEnabled,
            float dirLightIntensity,
            float pointLightIntensity,
            boolean shadowEnabled,
            float shadowStrength,
            float shadowBias,
            int shadowPcfRadius,
            int shadowCascadeCount,
            int shadowMapResolution,
            float[] shadowCascadeSplitNdc,
            boolean fogEnabled,
            float fogDensity,
            float fogR,
            float fogG,
            float fogB,
            int fogSteps,
            boolean smokeEnabled,
            float smokeIntensity,
            int swapchainWidth,
            int swapchainHeight,
            float smokeR,
            float smokeG,
            float smokeB,
            boolean iblEnabled,
            float iblDiffuseStrength,
            float iblSpecularStrength,
            float iblPrefilterStrength,
            boolean postOffscreenActive,
            boolean tonemapEnabled,
            float tonemapExposure,
            float tonemapGamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength,
            boolean ssaoEnabled,
            float ssaoStrength,
            float ssaoRadius,
            float ssaoBias,
            float ssaoPower,
            float[][] shadowLightViewProjMatrices
    ) {
    }
}
