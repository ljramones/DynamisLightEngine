package org.dynamislight.impl.vulkan.profile;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.spi.render.RenderFeatureMode;

/**
 * Phase C profile identity: tier + active capability modes.
 */
public record VulkanPipelineProfileKey(
        QualityTier tier,
        RenderFeatureMode shadowMode,
        RenderFeatureMode reflectionMode,
        RenderFeatureMode aaMode,
        RenderFeatureMode postMode,
        RenderFeatureMode rtMode,
        RenderFeatureMode lightingMode,
        RenderFeatureMode pbrMode,
        RenderFeatureMode giMode,
        RenderFeatureMode skyMode
) {
    public VulkanPipelineProfileKey {
        tier = tier == null ? QualityTier.MEDIUM : tier;
        shadowMode = normalize(shadowMode, "pcf");
        reflectionMode = normalize(reflectionMode, "hybrid");
        aaMode = normalize(aaMode, "taa");
        postMode = normalize(postMode, "taa_resolve");
        rtMode = normalize(rtMode, "rt_quality_tiers");
        lightingMode = normalize(lightingMode, "baseline_directional_point_spot");
        pbrMode = normalize(pbrMode, "metallic_roughness_baseline");
        giMode = normalize(giMode, "ssgi");
        skyMode = normalize(skyMode, "hdri");
    }

    public static VulkanPipelineProfileKey defaults() {
        return new VulkanPipelineProfileKey(
                QualityTier.MEDIUM,
                new RenderFeatureMode("pcf"),
                new RenderFeatureMode("hybrid"),
                new RenderFeatureMode("taa"),
                new RenderFeatureMode("taa_resolve"),
                new RenderFeatureMode("rt_quality_tiers"),
                new RenderFeatureMode("baseline_directional_point_spot"),
                new RenderFeatureMode("metallic_roughness_baseline"),
                new RenderFeatureMode("ssgi"),
                new RenderFeatureMode("hdri")
        );
    }

    public String id() {
        return tier.name().toLowerCase() + "|shadow=" + shadowMode.id()
                + "|refl=" + reflectionMode.id()
                + "|aa=" + aaMode.id()
                + "|post=" + postMode.id()
                + "|rt=" + rtMode.id()
                + "|lighting=" + lightingMode.id()
                + "|pbr=" + pbrMode.id()
                + "|gi=" + giMode.id()
                + "|sky=" + skyMode.id();
    }

    private static RenderFeatureMode normalize(RenderFeatureMode mode, String fallback) {
        if (mode == null || mode.id() == null || mode.id().isBlank()) {
            return new RenderFeatureMode(fallback);
        }
        return mode;
    }
}
