package org.dynamislight.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.impl.vulkan.runtime.config.GiMode;

/**
 * Deterministic GI Phase 1 capability planner.
 */
public final class VulkanGiCapabilityPlanner {
    private VulkanGiCapabilityPlanner() {
    }

    public static VulkanGiCapabilityPlan plan(PlanInput input) {
        PlanInput safe = input == null ? PlanInput.defaults() : input;
        String giModeId = safe.giMode().name().toLowerCase(java.util.Locale.ROOT);
        List<String> active = new ArrayList<>();
        List<String> pruned = new ArrayList<>();

        if (!safe.giEnabled()) {
            pruned.add("vulkan.gi." + giModeId + " (gi disabled)");
            return new VulkanGiCapabilityPlan(giModeId, false, safe.rtAvailable(), active, pruned);
        }

        switch (safe.giMode()) {
            case SSGI -> active.add("vulkan.gi.ssgi");
            case PROBE_GRID -> active.add("vulkan.gi.probe_grid");
            case RTGI_SINGLE -> {
                if (safe.rtAvailable() && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal()) {
                    active.add("vulkan.gi.rtgi_single");
                } else {
                    pruned.add("vulkan.gi.rtgi_single (rt unavailable or quality tier too low)");
                    active.add("vulkan.gi.ssgi");
                }
            }
            case HYBRID_PROBE_SSGI_RT -> {
                active.add("vulkan.gi.probe_grid");
                active.add("vulkan.gi.ssgi");
                if (safe.rtAvailable() && safe.qualityTier().ordinal() >= QualityTier.HIGH.ordinal()) {
                    active.add("vulkan.gi.rt_detail");
                } else {
                    pruned.add("vulkan.gi.rt_detail (rt unavailable or quality tier too low)");
                }
            }
        }
        return new VulkanGiCapabilityPlan(giModeId, true, safe.rtAvailable(), active, pruned);
    }

    public record PlanInput(
            QualityTier qualityTier,
            GiMode giMode,
            boolean giEnabled,
            boolean rtAvailable
    ) {
        public PlanInput {
            qualityTier = qualityTier == null ? QualityTier.MEDIUM : qualityTier;
            giMode = giMode == null ? GiMode.SSGI : giMode;
        }

        public static PlanInput defaults() {
            return new PlanInput(
                    QualityTier.MEDIUM,
                    GiMode.SSGI,
                    false,
                    false
            );
        }
    }
}
