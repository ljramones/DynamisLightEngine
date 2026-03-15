package org.dynamisengine.light.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.spi.render.RenderFeatureCapability;

/**
 * Metadata-only AA/post capability planner for Vulkan.
 *
 * This planner does not execute rendering work. It provides deterministic
 * activation/pruning decisions for Phase 3 modularization.
 */
public final class VulkanAaPostCapabilityPlanner {
    private VulkanAaPostCapabilityPlanner() {
    }

    public static VulkanAaPostCapabilityPlan plan(PlanInput input) {
        PlanInput safe = input == null ? PlanInput.defaults() : input;
        List<RenderFeatureCapability> active = new ArrayList<>();
        List<String> pruned = new ArrayList<>();

        // Post modules (upstream post shaping)
        addPostModule(active, pruned, VulkanPostCapabilityId.TONEMAP, safe.tonemapEnabled(), "tonemap disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.BLOOM, safe.bloomEnabled(), "bloom disabled");

        boolean ssaoAllowed = safe.qualityTier() != QualityTier.LOW;
        addPostModule(active, pruned, VulkanPostCapabilityId.SSAO,
                safe.ssaoEnabled() && ssaoAllowed,
                safe.ssaoEnabled() ? "ssao pruned by low tier" : "ssao disabled");

        boolean smaaNeeded = safe.smaaEnabled() || usesSmaaByAaMode(safe.aaMode());
        addPostModule(active, pruned, VulkanPostCapabilityId.SMAA, smaaNeeded, "smaa disabled");

        // AA resolve module
        if (safe.aaEnabled()) {
            active.add(VulkanAaCapability.of(safe.aaMode()));
        } else {
            pruned.add("vulkan.aa." + safe.aaMode().name().toLowerCase() + " (aa disabled)");
        }

        // Temporal resolve only if temporal AA path is active.
        addPostModule(active, pruned, VulkanPostCapabilityId.TAA_RESOLVE,
                safe.taaEnabled() && requiresTemporalHistory(safe.aaMode()),
                "taa resolve disabled");

        addPostModule(active, pruned, VulkanPostCapabilityId.FOG_COMPOSITE,
                safe.fogCompositeEnabled(),
                "fog composite disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.DEPTH_OF_FIELD,
                safe.depthOfFieldEnabled(),
                "depth of field disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.MOTION_BLUR,
                safe.motionBlurEnabled(),
                "motion blur disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.CHROMATIC_ABERRATION,
                safe.chromaticAberrationEnabled(),
                "chromatic aberration disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.FILM_GRAIN,
                safe.filmGrainEnabled(),
                "film grain disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.VIGNETTE,
                safe.vignetteEnabled(),
                "vignette disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.COLOR_GRADING,
                safe.colorGradingEnabled(),
                "color grading disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.SHARPENING,
                safe.sharpeningEnabled(),
                "sharpening disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.VOLUMETRIC_FOG,
                safe.volumetricFogEnabled(),
                "volumetric fog disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.CLOUD_SHADOWS,
                safe.cloudShadowsEnabled(),
                "cloud shadows disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.SCREEN_SPACE_BENT_NORMALS,
                safe.screenSpaceBentNormalsEnabled(),
                "screen-space bent normals disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.LENS_FLARE,
                safe.lensFlareEnabled(),
                "lens flare disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.PANINI,
                safe.paniniEnabled(),
                "panini projection disabled");
        addPostModule(active, pruned, VulkanPostCapabilityId.LENS_DISTORTION,
                safe.lensDistortionEnabled(),
                "lens distortion disabled");

        return new VulkanAaPostCapabilityPlan(active, pruned);
    }

    private static void addPostModule(
            List<RenderFeatureCapability> active,
            List<String> pruned,
            VulkanPostCapabilityId id,
            boolean enabled,
            String reason
    ) {
        String featureId = "vulkan.post." + id.name().toLowerCase();
        if (enabled) {
            active.add(VulkanPostCapability.of(id));
        } else {
            pruned.add(featureId + " (" + reason + ")");
        }
    }

    private static boolean requiresTemporalHistory(VulkanAaCapabilityMode mode) {
        return switch (mode) {
            case TAA, TSR, TUUA, HYBRID_TUUA_MSAA, DLAA -> true;
            case MSAA_SELECTIVE, FXAA_LOW -> false;
        };
    }

    private static boolean usesSmaaByAaMode(VulkanAaCapabilityMode mode) {
        return switch (mode) {
            case TAA, TSR, TUUA, HYBRID_TUUA_MSAA -> true;
            case MSAA_SELECTIVE, DLAA, FXAA_LOW -> false;
        };
    }

    public record PlanInput(
            QualityTier qualityTier,
            VulkanAaCapabilityMode aaMode,
            boolean aaEnabled,
            boolean taaEnabled,
            boolean smaaEnabled,
            boolean tonemapEnabled,
            boolean bloomEnabled,
            boolean ssaoEnabled,
            boolean fogCompositeEnabled,
            boolean depthOfFieldEnabled,
            boolean motionBlurEnabled,
            boolean chromaticAberrationEnabled,
            boolean filmGrainEnabled,
            boolean vignetteEnabled,
            boolean colorGradingEnabled,
            boolean sharpeningEnabled,
            boolean volumetricFogEnabled,
            boolean cloudShadowsEnabled,
            boolean screenSpaceBentNormalsEnabled,
            boolean lensFlareEnabled,
            boolean paniniEnabled,
            boolean lensDistortionEnabled
    ) {
        public PlanInput {
            qualityTier = qualityTier == null ? QualityTier.MEDIUM : qualityTier;
            aaMode = aaMode == null ? VulkanAaCapabilityMode.TAA : aaMode;
        }

        public static PlanInput defaults() {
            return new PlanInput(
                    QualityTier.MEDIUM,
                    VulkanAaCapabilityMode.TAA,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }
    }
}
