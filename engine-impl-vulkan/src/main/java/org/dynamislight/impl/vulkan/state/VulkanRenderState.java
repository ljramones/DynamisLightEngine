package org.dynamislight.impl.vulkan.state;

public final class VulkanRenderState {
    public boolean shadowEnabled;
    public float shadowStrength = 0.45f;
    public float shadowBias = 0.0015f;
    public float shadowNormalBiasScale = 1.0f;
    public float shadowSlopeBiasScale = 1.0f;
    public int shadowPcfRadius = 1;
    public int shadowCascadeCount = 1;
    public int shadowMapResolution = 1024;
    public boolean shadowDirectionalTexelSnapEnabled = true;
    public float shadowDirectionalTexelSnapScale = 1.0f;
    public int shadowFilterMode = 0; // 0=pcf,1=pcss,2=vsm,3=evsm
    public boolean shadowMomentPipelineRequested;
    public int shadowMomentMode; // 0=none,1=vsm,2=evsm
    public boolean shadowMomentInitialized;
    public boolean shadowContactShadows;
    public int shadowRtMode = 0; // 0=off,1=optional,2=force
    public final float[] shadowCascadeSplitNdc = new float[]{1f, 1f, 1f};
    public final float[][] shadowLightViewProjMatrices = new float[][]{
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
            identityMatrix(),
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
    public boolean taaEnabled;
    public float taaBlend;
    public float taaClipScale = 1.0f;
    public float taaRenderScale = 1.0f;
    public boolean taaLumaClipEnabled;
    public float taaSharpenStrength = 0.16f;
    public boolean reflectionsEnabled;
    public int reflectionsMode;
    public float reflectionsSsrStrength = 0.6f;
    public float reflectionsSsrMaxRoughness = 0.78f;
    public float reflectionsSsrStepScale = 1.0f;
    public float reflectionsTemporalWeight = 0.80f;
    public float reflectionsPlanarStrength = 0.35f;
    public float taaJitterNdcX;
    public float taaJitterNdcY;
    public float taaPrevJitterNdcX;
    public float taaPrevJitterNdcY;
    public float taaMotionUvX;
    public float taaMotionUvY;
    public int taaDebugView;
    public boolean postTaaHistoryInitialized;
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
