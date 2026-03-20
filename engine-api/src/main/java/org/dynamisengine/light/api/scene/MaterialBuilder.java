package org.dynamisengine.light.api.scene;

/**
 * Fluent builder for {@link MaterialDesc}.
 *
 * <pre>{@code
 * MaterialDesc mat = MaterialBuilder.create("ground")
 *     .albedo(0.4f, 0.35f, 0.3f)
 *     .roughness(0.9f)
 *     .metallic(0.0f)
 *     .build();
 * }</pre>
 */
public final class MaterialBuilder {

    private String id;
    private Vec3 albedo = new Vec3(0.8f, 0.8f, 0.8f);
    private float metallic = 0f;
    private float roughness = 0.5f;
    private String albedoTexturePath;
    private String normalTexturePath;
    private String metallicRoughnessTexturePath;
    private String occlusionTexturePath;
    private float reactiveStrength = 0f;
    private boolean alphaTested = false;
    private boolean foliage = false;
    private float reactiveBoost = 1.0f;
    private float taaHistoryClamp = 1.0f;
    private float emissiveReactiveBoost = 1.0f;
    private ReactivePreset reactivePreset = ReactivePreset.AUTO;
    private ReflectionOverrideMode reflectionOverride = ReflectionOverrideMode.AUTO;

    private MaterialBuilder(String id) { this.id = id; }

    public static MaterialBuilder create(String id) { return new MaterialBuilder(id); }

    public MaterialBuilder albedo(Vec3 color) { this.albedo = color; return this; }
    public MaterialBuilder albedo(float r, float g, float b) { this.albedo = new Vec3(r, g, b); return this; }
    public MaterialBuilder metallic(float v) { this.metallic = v; return this; }
    public MaterialBuilder roughness(float v) { this.roughness = v; return this; }
    public MaterialBuilder albedoTexture(String path) { this.albedoTexturePath = path; return this; }
    public MaterialBuilder normalTexture(String path) { this.normalTexturePath = path; return this; }
    public MaterialBuilder metallicRoughnessTexture(String path) { this.metallicRoughnessTexturePath = path; return this; }
    public MaterialBuilder occlusionTexture(String path) { this.occlusionTexturePath = path; return this; }
    public MaterialBuilder reactiveStrength(float v) { this.reactiveStrength = v; return this; }
    public MaterialBuilder alphaTested(boolean v) { this.alphaTested = v; return this; }
    public MaterialBuilder foliage(boolean v) { this.foliage = v; return this; }
    public MaterialBuilder reactiveBoost(float v) { this.reactiveBoost = v; return this; }
    public MaterialBuilder taaHistoryClamp(float v) { this.taaHistoryClamp = v; return this; }
    public MaterialBuilder emissiveReactiveBoost(float v) { this.emissiveReactiveBoost = v; return this; }
    public MaterialBuilder reactivePreset(ReactivePreset v) { this.reactivePreset = v; return this; }
    public MaterialBuilder reflectionOverride(ReflectionOverrideMode v) { this.reflectionOverride = v; return this; }

    public MaterialDesc build() {
        return new MaterialDesc(id, albedo, metallic, roughness,
                albedoTexturePath, normalTexturePath,
                metallicRoughnessTexturePath, occlusionTexturePath,
                reactiveStrength, alphaTested, foliage,
                reactiveBoost, taaHistoryClamp, emissiveReactiveBoost,
                reactivePreset, reflectionOverride);
    }
}
