package org.dynamislight.impl.vulkan.warning.lighting;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.impl.vulkan.capability.VulkanLightingCapabilityPlan;
import org.dynamislight.impl.vulkan.capability.VulkanLightingCapabilityPlanner;

/**
 * Emits per-frame lighting capability-plan warning payload.
 */
public final class VulkanLightingWarningEmitter {
    private VulkanLightingWarningEmitter() {
    }

    public static Result emit(
            QualityTier qualityTier,
            List<LightDesc> lights,
            boolean physicallyBasedUnitsEnabled,
            boolean prioritizationEnabled,
            boolean emissiveMeshEnabled,
            int localLightBudget
    ) {
        VulkanLightingCapabilityPlan plan = VulkanLightingCapabilityPlanner.plan(
                new VulkanLightingCapabilityPlanner.PlanInput(
                        qualityTier,
                        lights,
                        physicallyBasedUnitsEnabled,
                        prioritizationEnabled,
                        emissiveMeshEnabled,
                        localLightBudget
                )
        );
        EngineWarning warning = new EngineWarning(
                "LIGHTING_CAPABILITY_MODE_ACTIVE",
                "Lighting capability mode active (mode=" + plan.modeId()
                        + ", directionalLights=" + plan.directionalLights()
                        + ", pointLights=" + plan.pointLights()
                        + ", spotLights=" + plan.spotLights()
                        + ", physicallyBasedUnitsEnabled=" + plan.physicallyBasedUnitsEnabled()
                        + ", prioritizationEnabled=" + plan.prioritizationEnabled()
                        + ", emissiveMeshEnabled=" + plan.emissiveMeshEnabled()
                        + ", active=[" + String.join(", ", plan.activeCapabilities()) + "]"
                        + ", pruned=[" + String.join(", ", plan.prunedCapabilities()) + "]"
                        + ", signals=[" + String.join(", ", plan.signals()) + "])"
        );
        return new Result(warning, plan);
    }

    public record Result(
            EngineWarning warning,
            VulkanLightingCapabilityPlan plan
    ) {
    }
}
