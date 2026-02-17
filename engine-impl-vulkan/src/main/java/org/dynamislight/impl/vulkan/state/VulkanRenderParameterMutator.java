package org.dynamislight.impl.vulkan.state;

public final class VulkanRenderParameterMutator {
    private VulkanRenderParameterMutator() {
    }

    public static FogResult applyFog(FogState current, FogUpdate update) {
        boolean changed = false;
        boolean enabled = current.enabled();
        float r = current.r();
        float g = current.g();
        float b = current.b();
        float density = current.density();
        int steps = current.steps();

        if (enabled != update.enabled()) {
            enabled = update.enabled();
            changed = true;
        }
        if (!floatEquals(r, update.r()) || !floatEquals(g, update.g()) || !floatEquals(b, update.b())) {
            r = update.r();
            g = update.g();
            b = update.b();
            changed = true;
        }
        float clampedDensity = Math.max(0f, update.density());
        if (!floatEquals(density, clampedDensity)) {
            density = clampedDensity;
            changed = true;
        }
        int clampedSteps = Math.max(0, update.steps());
        if (steps != clampedSteps) {
            steps = clampedSteps;
            changed = true;
        }
        return new FogResult(new FogState(enabled, r, g, b, density, steps), changed);
    }

    public static SmokeResult applySmoke(SmokeState current, SmokeUpdate update) {
        boolean changed = false;
        boolean enabled = current.enabled();
        float r = current.r();
        float g = current.g();
        float b = current.b();
        float intensity = current.intensity();

        if (enabled != update.enabled()) {
            enabled = update.enabled();
            changed = true;
        }
        if (!floatEquals(r, update.r()) || !floatEquals(g, update.g()) || !floatEquals(b, update.b())) {
            r = update.r();
            g = update.g();
            b = update.b();
            changed = true;
        }
        float clampedIntensity = Math.max(0f, Math.min(1f, update.intensity()));
        if (!floatEquals(intensity, clampedIntensity)) {
            intensity = clampedIntensity;
            changed = true;
        }
        return new SmokeResult(new SmokeState(enabled, r, g, b, intensity), changed);
    }

    public static IblResult applyIbl(IblState current, IblUpdate update) {
        boolean changed = false;
        boolean enabled = current.enabled();
        float diffuseStrength = current.diffuseStrength();
        float specularStrength = current.specularStrength();
        float prefilterStrength = current.prefilterStrength();

        if (enabled != update.enabled()) {
            enabled = update.enabled();
            changed = true;
        }
        float clampedDiffuse = Math.max(0f, Math.min(2.0f, update.diffuseStrength()));
        float clampedSpecular = Math.max(0f, Math.min(2.0f, update.specularStrength()));
        float clampedPrefilter = Math.max(0f, Math.min(1.0f, update.prefilterStrength()));
        if (!floatEquals(diffuseStrength, clampedDiffuse)
                || !floatEquals(specularStrength, clampedSpecular)
                || !floatEquals(prefilterStrength, clampedPrefilter)) {
            diffuseStrength = clampedDiffuse;
            specularStrength = clampedSpecular;
            prefilterStrength = clampedPrefilter;
            changed = true;
        }
        return new IblResult(new IblState(enabled, diffuseStrength, specularStrength, prefilterStrength), changed);
    }

    public static PostResult applyPost(PostState current, PostUpdate update) {
        boolean changed = false;
        boolean tonemapEnabled = current.tonemapEnabled();
        float exposure = current.exposure();
        float gamma = current.gamma();
        boolean bloomEnabled = current.bloomEnabled();
        float bloomThreshold = current.bloomThreshold();
        float bloomStrength = current.bloomStrength();
        boolean ssaoEnabled = current.ssaoEnabled();
        float ssaoStrength = current.ssaoStrength();
        float ssaoRadius = current.ssaoRadius();
        float ssaoBias = current.ssaoBias();
        float ssaoPower = current.ssaoPower();
        boolean smaaEnabled = current.smaaEnabled();
        float smaaStrength = current.smaaStrength();
        boolean taaEnabled = current.taaEnabled();
        float taaBlend = current.taaBlend();
        float taaClipScale = current.taaClipScale();
        float taaRenderScale = current.taaRenderScale();
        boolean taaLumaClipEnabled = current.taaLumaClipEnabled();
        float taaSharpenStrength = current.taaSharpenStrength();

        if (tonemapEnabled != update.tonemapEnabled()) {
            tonemapEnabled = update.tonemapEnabled();
            changed = true;
        }
        float clampedExposure = Math.max(0.05f, Math.min(8.0f, update.exposure()));
        float clampedGamma = Math.max(0.8f, Math.min(3.2f, update.gamma()));
        if (!floatEquals(exposure, clampedExposure) || !floatEquals(gamma, clampedGamma)) {
            exposure = clampedExposure;
            gamma = clampedGamma;
            changed = true;
        }
        if (bloomEnabled != update.bloomEnabled()) {
            bloomEnabled = update.bloomEnabled();
            changed = true;
        }
        float clampedThreshold = Math.max(0f, Math.min(4.0f, update.bloomThreshold()));
        float clampedStrength = Math.max(0f, Math.min(2.0f, update.bloomStrength()));
        if (!floatEquals(bloomThreshold, clampedThreshold) || !floatEquals(bloomStrength, clampedStrength)) {
            bloomThreshold = clampedThreshold;
            bloomStrength = clampedStrength;
            changed = true;
        }
        if (ssaoEnabled != update.ssaoEnabled()) {
            ssaoEnabled = update.ssaoEnabled();
            changed = true;
        }
        float clampedSsaoStrength = Math.max(0f, Math.min(1.0f, update.ssaoStrength()));
        if (!floatEquals(ssaoStrength, clampedSsaoStrength)) {
            ssaoStrength = clampedSsaoStrength;
            changed = true;
        }
        float clampedSsaoRadius = Math.max(0.2f, Math.min(3.0f, update.ssaoRadius()));
        float clampedSsaoBias = Math.max(0f, Math.min(0.2f, update.ssaoBias()));
        float clampedSsaoPower = Math.max(0.5f, Math.min(4.0f, update.ssaoPower()));
        if (!floatEquals(ssaoRadius, clampedSsaoRadius)
                || !floatEquals(ssaoBias, clampedSsaoBias)
                || !floatEquals(ssaoPower, clampedSsaoPower)) {
            ssaoRadius = clampedSsaoRadius;
            ssaoBias = clampedSsaoBias;
            ssaoPower = clampedSsaoPower;
            changed = true;
        }
        if (smaaEnabled != update.smaaEnabled()) {
            smaaEnabled = update.smaaEnabled();
            changed = true;
        }
        float clampedSmaaStrength = Math.max(0f, Math.min(1.0f, update.smaaStrength()));
        if (!floatEquals(smaaStrength, clampedSmaaStrength)) {
            smaaStrength = clampedSmaaStrength;
            changed = true;
        }
        if (taaEnabled != update.taaEnabled()) {
            taaEnabled = update.taaEnabled();
            changed = true;
        }
        float clampedTaaBlend = Math.max(0f, Math.min(0.95f, update.taaBlend()));
        if (!floatEquals(taaBlend, clampedTaaBlend)) {
            taaBlend = clampedTaaBlend;
            changed = true;
        }
        float clampedTaaClipScale = Math.max(0.5f, Math.min(1.6f, update.taaClipScale()));
        if (!floatEquals(taaClipScale, clampedTaaClipScale)) {
            taaClipScale = clampedTaaClipScale;
            changed = true;
        }
        float clampedTaaRenderScale = Math.max(0.5f, Math.min(1.0f, update.taaRenderScale()));
        if (!floatEquals(taaRenderScale, clampedTaaRenderScale)) {
            taaRenderScale = clampedTaaRenderScale;
            changed = true;
        }
        if (taaLumaClipEnabled != update.taaLumaClipEnabled()) {
            taaLumaClipEnabled = update.taaLumaClipEnabled();
            changed = true;
        }
        float clampedTaaSharpenStrength = Math.max(0f, Math.min(0.35f, update.taaSharpenStrength()));
        if (!floatEquals(taaSharpenStrength, clampedTaaSharpenStrength)) {
            taaSharpenStrength = clampedTaaSharpenStrength;
            changed = true;
        }
        return new PostResult(
                new PostState(
                        tonemapEnabled, exposure, gamma, bloomEnabled, bloomThreshold, bloomStrength,
                        ssaoEnabled, ssaoStrength, ssaoRadius, ssaoBias, ssaoPower,
                        smaaEnabled, smaaStrength, taaEnabled, taaBlend, taaClipScale, taaRenderScale, taaLumaClipEnabled, taaSharpenStrength
                ),
                changed
        );
    }

    public static CameraResult applyCameraMatrices(CameraState current, CameraUpdate update) {
        boolean changed = false;
        float[] view = current.view();
        float[] proj = current.proj();
        if (update.view() != null && update.view().length == 16) {
            if (!java.util.Arrays.equals(view, update.view())) {
                view = update.view().clone();
                changed = true;
            }
        }
        if (update.proj() != null && update.proj().length == 16) {
            if (!java.util.Arrays.equals(proj, update.proj())) {
                proj = update.proj().clone();
                changed = true;
            }
        }
        return new CameraResult(new CameraState(view, proj), changed);
    }

    private static boolean floatEquals(float a, float b) {
        return Math.abs(a - b) <= 0.000001f;
    }

    public record FogState(boolean enabled, float r, float g, float b, float density, int steps) {
    }

    public record FogUpdate(boolean enabled, float r, float g, float b, float density, int steps) {
    }

    public record FogResult(FogState state, boolean changed) {
    }

    public record SmokeState(boolean enabled, float r, float g, float b, float intensity) {
    }

    public record SmokeUpdate(boolean enabled, float r, float g, float b, float intensity) {
    }

    public record SmokeResult(SmokeState state, boolean changed) {
    }

    public record IblState(boolean enabled, float diffuseStrength, float specularStrength, float prefilterStrength) {
    }

    public record IblUpdate(boolean enabled, float diffuseStrength, float specularStrength, float prefilterStrength) {
    }

    public record IblResult(IblState state, boolean changed) {
    }

    public record PostState(
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength,
            boolean ssaoEnabled,
            float ssaoStrength,
            float ssaoRadius,
            float ssaoBias,
            float ssaoPower,
            boolean smaaEnabled,
            float smaaStrength,
            boolean taaEnabled,
            float taaBlend,
            float taaClipScale,
            float taaRenderScale,
            boolean taaLumaClipEnabled,
            float taaSharpenStrength
    ) {
    }

    public record PostUpdate(
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength,
            boolean ssaoEnabled,
            float ssaoStrength,
            float ssaoRadius,
            float ssaoBias,
            float ssaoPower,
            boolean smaaEnabled,
            float smaaStrength,
            boolean taaEnabled,
            float taaBlend,
            float taaClipScale,
            float taaRenderScale,
            boolean taaLumaClipEnabled,
            float taaSharpenStrength
    ) {
    }

    public record PostResult(PostState state, boolean changed) {
    }

    public record CameraState(float[] view, float[] proj) {
    }

    public record CameraUpdate(float[] view, float[] proj) {
    }

    public record CameraResult(CameraState state, boolean changed) {
    }
}
