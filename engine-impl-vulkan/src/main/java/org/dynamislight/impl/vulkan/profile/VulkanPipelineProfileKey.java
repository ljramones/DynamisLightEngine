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
        RenderFeatureMode lightingMode
) {
    public VulkanPipelineProfileKey {
        tier = tier == null ? QualityTier.MEDIUM : tier;
        shadowMode = normalize(shadowMode, "pcf");
        reflectionMode = normalize(reflectionMode, "hybrid");
        aaMode = normalize(aaMode, "taa");
        postMode = normalize(postMode, "taa_resolve");
        lightingMode = normalize(lightingMode, "baseline_directional_point_spot");
    }

    public static VulkanPipelineProfileKey defaults() {
        return new VulkanPipelineProfileKey(
                QualityTier.MEDIUM,
                new RenderFeatureMode("pcf"),
                new RenderFeatureMode("hybrid"),
                new RenderFeatureMode("taa"),
                new RenderFeatureMode("taa_resolve"),
                new RenderFeatureMode("baseline_directional_point_spot")
        );
    }

    public String id() {
        return tier.name().toLowerCase() + "|shadow=" + shadowMode.id()
                + "|refl=" + reflectionMode.id()
                + "|aa=" + aaMode.id()
                + "|post=" + postMode.id()
                + "|lighting=" + lightingMode.id();
    }

    private static RenderFeatureMode normalize(RenderFeatureMode mode, String fallback) {
        if (mode == null || mode.id() == null || mode.id().isBlank()) {
            return new RenderFeatureMode(fallback);
        }
        return mode;
    }
}
