package org.dynamislight.impl.vulkan.warning.aa;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityMode;
import org.dynamislight.impl.vulkan.capability.VulkanAaPostCapabilityPlan;
import org.dynamislight.impl.vulkan.capability.VulkanAaPostCapabilityPlanner;

/**
 * Emits AA/post capability-plan warning payload and typed summary fields.
 */
public final class VulkanAaPostWarningEmitter {
    private VulkanAaPostWarningEmitter() {
    }

    public static Result emit(
            QualityTier qualityTier,
            VulkanAaCapabilityMode aaMode,
            boolean taaEnabled,
            boolean smaaEnabled,
            boolean tonemapEnabled,
            boolean bloomEnabled,
            boolean ssaoEnabled,
            boolean fogCompositeEnabled
    ) {
        VulkanAaCapabilityMode safeMode = aaMode == null ? VulkanAaCapabilityMode.TAA : aaMode;
        VulkanAaPostCapabilityPlan plan = VulkanAaPostCapabilityPlanner.plan(
                new VulkanAaPostCapabilityPlanner.PlanInput(
                        qualityTier,
                        safeMode,
                        true,
                        taaEnabled,
                        smaaEnabled,
                        tonemapEnabled,
                        bloomEnabled,
                        ssaoEnabled,
                        fogCompositeEnabled
                )
        );
        String aaModeId = safeMode.name().toLowerCase(java.util.Locale.ROOT);
        boolean temporalHistoryActive = taaEnabled
                && (safeMode == VulkanAaCapabilityMode.TAA
                || safeMode == VulkanAaCapabilityMode.TSR
                || safeMode == VulkanAaCapabilityMode.TUUA
                || safeMode == VulkanAaCapabilityMode.HYBRID_TUUA_MSAA
                || safeMode == VulkanAaCapabilityMode.DLAA);
        List<String> activeCapabilities = plan.activeCapabilities().stream()
                .map(capability -> capability.contract().featureId())
                .toList();
        List<String> prunedCapabilities = plan.prunedCapabilities();
        EngineWarning warning = new EngineWarning(
                "AA_POST_CAPABILITY_PLAN_ACTIVE",
                "AA/post capability plan active (aaMode=" + aaModeId
                        + ", aaEnabled=true"
                        + ", temporalHistoryActive=" + temporalHistoryActive
                        + ", active=[" + String.join(", ", activeCapabilities) + "]"
                        + ", pruned=[" + String.join(", ", prunedCapabilities) + "])"
        );
        return new Result(
                warning,
                aaModeId,
                temporalHistoryActive,
                activeCapabilities,
                prunedCapabilities
        );
    }

    public record Result(
            EngineWarning warning,
            String aaModeId,
            boolean temporalHistoryActive,
            List<String> activeCapabilities,
            List<String> prunedCapabilities
    ) {
        public Result {
            aaModeId = aaModeId == null ? "" : aaModeId;
            activeCapabilities = activeCapabilities == null ? List.of() : List.copyOf(activeCapabilities);
            prunedCapabilities = prunedCapabilities == null ? List.of() : List.copyOf(prunedCapabilities);
        }
    }
}
