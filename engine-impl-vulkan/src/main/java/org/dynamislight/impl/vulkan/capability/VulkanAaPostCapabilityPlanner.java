package org.dynamislight.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.spi.render.RenderFeatureCapability;

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
            boolean fogCompositeEnabled
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
                    true
            );
        }
    }
}
