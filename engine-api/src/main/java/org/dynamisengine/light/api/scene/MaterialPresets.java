package org.dynamisengine.light.api.scene;

/**
 * Ready-made PBR material configurations.
 */
public final class MaterialPresets {
    private MaterialPresets() {}

    /** Rough dielectric: stone, concrete, plaster. */
    public static MaterialDesc matteGray(String id) {
        return MaterialBuilder.create(id).albedo(0.5f, 0.5f, 0.5f).roughness(0.9f).metallic(0f).build();
    }

    /** Smooth dielectric: plastic, painted surface. */
    public static MaterialDesc plastic(String id, float r, float g, float b) {
        return MaterialBuilder.create(id).albedo(r, g, b).roughness(0.3f).metallic(0f).build();
    }

    /** Polished metal. */
    public static MaterialDesc metal(String id, float r, float g, float b) {
        return MaterialBuilder.create(id).albedo(r, g, b).roughness(0.15f).metallic(1f).build();
    }

    /** Gold metal. */
    public static MaterialDesc gold(String id) {
        return metal(id, 1.0f, 0.8f, 0.3f);
    }

    /** Silver/chrome metal. */
    public static MaterialDesc chrome(String id) {
        return metal(id, 0.85f, 0.85f, 0.9f);
    }

    /** Copper metal. */
    public static MaterialDesc copper(String id) {
        return metal(id, 0.9f, 0.5f, 0.3f);
    }

    /** Rough wood-like surface. */
    public static MaterialDesc wood(String id) {
        return MaterialBuilder.create(id).albedo(0.55f, 0.35f, 0.2f).roughness(0.7f).metallic(0f).build();
    }

    /** Ground/dirt surface. */
    public static MaterialDesc ground(String id) {
        return MaterialBuilder.create(id).albedo(0.4f, 0.35f, 0.28f).roughness(0.95f).metallic(0f).build();
    }

    /** Emissive/debug: bright flat color, useful for debug visualization. */
    public static MaterialDesc emissive(String id, float r, float g, float b) {
        return MaterialBuilder.create(id).albedo(r, g, b).roughness(1f).metallic(0f)
                .reactiveStrength(1f).emissiveReactiveBoost(2f).build();
    }

    /** Foliage/vegetation with alpha testing. */
    public static MaterialDesc foliage(String id) {
        return MaterialBuilder.create(id).albedo(0.3f, 0.5f, 0.15f).roughness(0.8f).metallic(0f)
                .alphaTested(true).foliage(true).build();
    }
}
