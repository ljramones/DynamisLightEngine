package org.dynamisengine.light.impl.vulkan.warning.gi;

import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.event.EngineWarning;
import org.dynamisengine.light.impl.vulkan.capability.VulkanGiCapabilityPlan;
import org.dynamisengine.light.impl.vulkan.capability.VulkanGiCapabilityPlanner;
import org.dynamisengine.light.impl.vulkan.runtime.config.GiMode;

/**
 * Emits GI Phase 1 capability-plan warning payload.
 */
public final class VulkanGiWarningEmitter {
    private VulkanGiWarningEmitter() {
    }

    public static Result emit(
            QualityTier qualityTier,
            GiMode giMode,
            boolean giEnabled,
            boolean rtAvailable
    ) {
        GiMode safeMode = giMode == null ? GiMode.SSGI : giMode;
        VulkanGiCapabilityPlan plan = VulkanGiCapabilityPlanner.plan(
                new VulkanGiCapabilityPlanner.PlanInput(
                        qualityTier,
                        safeMode,
                        giEnabled,
                        rtAvailable
                )
        );
        EngineWarning warning = new EngineWarning(
                "GI_CAPABILITY_PLAN_ACTIVE",
                "GI capability plan active (giMode=" + plan.giModeId()
                        + ", giEnabled=" + plan.giEnabled()
                        + ", rtAvailable=" + plan.rtAvailable()
                        + ", active=[" + String.join(", ", plan.activeCapabilities()) + "]"
                        + ", pruned=[" + String.join(", ", plan.prunedCapabilities()) + "])"
        );
        return new Result(warning, plan);
    }

    public record Result(
            EngineWarning warning,
            VulkanGiCapabilityPlan plan
    ) {
    }
}
