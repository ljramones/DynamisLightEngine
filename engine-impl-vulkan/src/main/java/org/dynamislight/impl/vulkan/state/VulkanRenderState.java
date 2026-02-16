package org.dynamislight.impl.vulkan.state;

public final class VulkanRenderState {
    public boolean shadowEnabled;
    public float shadowStrength = 0.45f;
    public float shadowBias = 0.0015f;
    public int shadowPcfRadius = 1;
    public int shadowCascadeCount = 1;
    public int shadowMapResolution = 1024;
    public final float[] shadowCascadeSplitNdc = new float[]{1f, 1f, 1f};
    public final float[][] shadowLightViewProjMatrices = new float[][]{
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
            identityMatrix()
    };

    public boolean fogEnabled;
    public float fogR = 0.5f;
    public float fogG = 0.5f;
    public float fogB = 0.5f;
    public float fogDensity;
    public int fogSteps;

    public boolean smokeEnabled;
    public float smokeR = 0.6f;
    public float smokeG = 0.6f;
    public float smokeB = 0.6f;
    public float smokeIntensity;

    public boolean tonemapEnabled;
    public float tonemapExposure = 1.0f;
    public float tonemapGamma = 2.2f;
    public boolean bloomEnabled;
    public float bloomThreshold = 1.0f;
    public float bloomStrength = 0.8f;
    public boolean ssaoEnabled;
    public float ssaoStrength;
    public float ssaoRadius = 1.0f;
    public float ssaoBias = 0.02f;
    public float ssaoPower = 1.0f;
    public boolean smaaEnabled;
    public float smaaStrength;
    public boolean postOffscreenRequested;
    public boolean postOffscreenActive;
    public boolean postIntermediateInitialized;

    private static float[] identityMatrix() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }
}
