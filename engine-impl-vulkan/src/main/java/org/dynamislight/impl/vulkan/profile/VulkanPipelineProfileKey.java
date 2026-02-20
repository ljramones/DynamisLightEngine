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
        RenderFeatureMode postMode
) {
    public VulkanPipelineProfileKey {
        tier = tier == null ? QualityTier.MEDIUM : tier;
        shadowMode = normalize(shadowMode, "pcf");
        reflectionMode = normalize(reflectionMode, "hybrid");
        aaMode = normalize(aaMode, "taa");
        postMode = normalize(postMode, "taa_resolve");
    }

    public static VulkanPipelineProfileKey defaults() {
        return new VulkanPipelineProfileKey(
                QualityTier.MEDIUM,
                new RenderFeatureMode("pcf"),
                new RenderFeatureMode("hybrid"),
                new RenderFeatureMode("taa"),
                new RenderFeatureMode("taa_resolve")
        );
    }

    public String id() {
        return tier.name().toLowerCase() + "|shadow=" + shadowMode.id()
                + "|refl=" + reflectionMode.id()
                + "|aa=" + aaMode.id()
                + "|post=" + postMode.id();
    }

    private static RenderFeatureMode normalize(RenderFeatureMode mode, String fallback) {
        if (mode == null || mode.id() == null || mode.id().isBlank()) {
            return new RenderFeatureMode(fallback);
        }
        return mode;
    }
}
