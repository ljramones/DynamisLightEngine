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
            int localLightBudget,
            double budgetWarnRatioThreshold
    ) {
        VulkanLightingCapabilityPlan plan = VulkanLightingCapabilityPlanner.plan(
                new VulkanLightingCapabilityPlanner.PlanInput(
                        qualityTier,
                        lights,
                        physicallyBasedUnitsEnabled,
                        prioritizationEnabled,
                        emissiveMeshEnabled,
                        localLightBudget,
                        budgetWarnRatioThreshold
                )
        );
        java.util.List<EngineWarning> warnings = new java.util.ArrayList<>();
        warnings.add(new EngineWarning(
                "LIGHTING_CAPABILITY_MODE_ACTIVE",
                "Lighting capability mode active (mode=" + plan.modeId()
                        + ", directionalLights=" + plan.directionalLights()
                        + ", pointLights=" + plan.pointLights()
                        + ", spotLights=" + plan.spotLights()
                        + ", localLightCount=" + plan.localLightCount()
                        + ", localLightBudget=" + plan.localLightBudget()
                        + ", localLightLoadRatio=" + plan.localLightLoadRatio()
                        + ", physicallyBasedUnitsEnabled=" + plan.physicallyBasedUnitsEnabled()
                        + ", prioritizationEnabled=" + plan.prioritizationEnabled()
                        + ", emissiveMeshEnabled=" + plan.emissiveMeshEnabled()
                        + ", active=[" + String.join(", ", plan.activeCapabilities()) + "]"
                        + ", pruned=[" + String.join(", ", plan.prunedCapabilities()) + "]"
                        + ", signals=[" + String.join(", ", plan.signals()) + "])"
        ));
        warnings.add(new EngineWarning(
                "LIGHTING_BUDGET_ENVELOPE",
                "Lighting budget envelope (localLights=" + plan.localLightCount()
                        + ", budget=" + plan.localLightBudget()
                        + ", ratio=" + plan.localLightLoadRatio()
                        + ", threshold=" + budgetWarnRatioThreshold + ")"
        ));
        if (plan.budgetEnvelopeBreached()) {
            warnings.add(new EngineWarning(
                    "LIGHTING_BUDGET_ENVELOPE_BREACH",
                    "Lighting budget envelope breached (localLights=" + plan.localLightCount()
                            + ", budget=" + plan.localLightBudget()
                            + ", ratio=" + plan.localLightLoadRatio()
                            + ", threshold=" + budgetWarnRatioThreshold + ")"
            ));
        }
        return new Result(warnings, plan);
    }

    public record Result(
            List<EngineWarning> warnings,
            VulkanLightingCapabilityPlan plan
    ) {
        public Result {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
