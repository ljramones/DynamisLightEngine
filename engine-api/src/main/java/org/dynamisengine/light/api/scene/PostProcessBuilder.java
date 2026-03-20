package org.dynamisengine.light.api.scene;

/**
 * Fluent builder for {@link PostProcessDesc}.
 *
 * <pre>{@code
 * PostProcessDesc pp = PostProcessBuilder.create()
 *     .tonemap(true, 1.0f, 2.2f)
 *     .bloom(true, 0.8f, 0.3f)
 *     .ssao(true, 0.5f)
 *     .build();
 * }</pre>
 */
public final class PostProcessBuilder {

    private boolean enabled = true;
    private boolean tonemapEnabled = true;
    private float exposure = 1.0f;
    private float gamma = 2.2f;
    private boolean bloomEnabled = false;
    private float bloomThreshold = 0.8f;
    private float bloomStrength = 0.3f;
    private boolean ssaoEnabled = false;
    private float ssaoStrength = 0.5f;
    private float ssaoRadius = 1.0f;
    private float ssaoBias = 0.02f;
    private float ssaoPower = 1.0f;
    private boolean smaaEnabled = false;
    private float smaaStrength = 0.5f;
    private boolean taaEnabled = false;
    private float taaBlend = 0.1f;
    private boolean taaLumaClipEnabled = false;
    private AntiAliasingDesc antiAliasing;
    private ReflectionDesc reflections;
    private ReflectionAdvancedDesc reflectionAdvanced;

    private PostProcessBuilder() {}

    public static PostProcessBuilder create() { return new PostProcessBuilder(); }

    public PostProcessBuilder enabled(boolean v) { this.enabled = v; return this; }
    public PostProcessBuilder tonemap(boolean enabled, float exposure, float gamma) {
        this.tonemapEnabled = enabled; this.exposure = exposure; this.gamma = gamma; return this; }
    public PostProcessBuilder bloom(boolean enabled, float threshold, float strength) {
        this.bloomEnabled = enabled; this.bloomThreshold = threshold; this.bloomStrength = strength; return this; }
    public PostProcessBuilder ssao(boolean enabled, float strength) {
        this.ssaoEnabled = enabled; this.ssaoStrength = strength; return this; }
    public PostProcessBuilder ssao(boolean enabled, float strength, float radius, float bias, float power) {
        this.ssaoEnabled = enabled; this.ssaoStrength = strength; this.ssaoRadius = radius;
        this.ssaoBias = bias; this.ssaoPower = power; return this; }
    public PostProcessBuilder smaa(boolean enabled, float strength) {
        this.smaaEnabled = enabled; this.smaaStrength = strength; return this; }
    public PostProcessBuilder taa(boolean enabled, float blend, boolean lumaClip) {
        this.taaEnabled = enabled; this.taaBlend = blend; this.taaLumaClipEnabled = lumaClip; return this; }
    public PostProcessBuilder antiAliasing(AntiAliasingDesc aa) { this.antiAliasing = aa; return this; }
    public PostProcessBuilder reflections(ReflectionDesc r) { this.reflections = r; return this; }
    public PostProcessBuilder reflectionAdvanced(ReflectionAdvancedDesc r) { this.reflectionAdvanced = r; return this; }

    public PostProcessDesc build() {
        return new PostProcessDesc(enabled, tonemapEnabled, exposure, gamma,
                bloomEnabled, bloomThreshold, bloomStrength,
                ssaoEnabled, ssaoStrength, ssaoRadius, ssaoBias, ssaoPower,
                smaaEnabled, smaaStrength, taaEnabled, taaBlend, taaLumaClipEnabled,
                antiAliasing, reflections, reflectionAdvanced);
    }
}
