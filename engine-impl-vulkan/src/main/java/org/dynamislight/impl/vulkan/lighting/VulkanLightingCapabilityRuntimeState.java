package org.dynamislight.impl.vulkan.lighting;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.LightingBudgetDiagnostics;
import org.dynamislight.api.runtime.LightingCapabilityDiagnostics;
import org.dynamislight.api.runtime.LightingPromotionDiagnostics;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;
import org.dynamislight.impl.vulkan.warning.lighting.VulkanLightingWarningEmitter;

/**
 * Runtime holder for lighting capability mode telemetry and typed diagnostics.
 */
public final class VulkanLightingCapabilityRuntimeState {
    private boolean physicallyBasedUnitsEnabled = true;
    private boolean prioritizationEnabled = true;
    private boolean emissiveMeshEnabled;
    private int localLightBudget = 8;
    private double budgetWarnRatioThreshold = 1.0;
    private String modeLastFrame = "baseline_directional_point_spot";
    private int directionalCountLastFrame;
    private int pointCountLastFrame;
    private int spotCountLastFrame;
    private int localLightCountLastFrame;
    private int localLightBudgetLastFrame = 8;
    private double localLightLoadRatioLastFrame;
    private boolean budgetEnvelopeBreachedLastFrame;
    private int budgetWarnMinFrames = 3;
    private int budgetWarnCooldownFrames = 120;
    private int budgetPromotionReadyMinFrames = 6;
    private int budgetHighStreak;
    private int budgetStableStreak;
    private int budgetWarnCooldownRemaining;
    private boolean budgetPromotionReadyLastFrame;
    private List<String> activeCapabilitiesLastFrame = List.of();
    private List<String> prunedCapabilitiesLastFrame = List.of();
    private List<String> signalsLastFrame = List.of();

    public void reset() {
        modeLastFrame = "baseline_directional_point_spot";
        directionalCountLastFrame = 0;
        pointCountLastFrame = 0;
        spotCountLastFrame = 0;
        localLightCountLastFrame = 0;
        localLightBudgetLastFrame = localLightBudget;
        localLightLoadRatioLastFrame = 0.0;
        budgetEnvelopeBreachedLastFrame = false;
        budgetHighStreak = 0;
        budgetStableStreak = 0;
        budgetWarnCooldownRemaining = 0;
        budgetPromotionReadyLastFrame = false;
        activeCapabilitiesLastFrame = List.of();
        prunedCapabilitiesLastFrame = List.of();
        signalsLastFrame = List.of();
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        physicallyBasedUnitsEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.physicallyBasedUnitsEnabled", "true")
        );
        prioritizationEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.prioritizationEnabled", "true")
        );
        emissiveMeshEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.emissiveMeshEnabled", "false")
        );
        localLightBudget = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.localLightBudget",
                localLightBudget,
                1,
                4096
        );
        budgetWarnRatioThreshold = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe,
                "vulkan.lighting.budgetWarnRatioThreshold",
                budgetWarnRatioThreshold,
                1.0,
                1000.0
        );
        budgetWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.budgetWarnMinFrames",
                budgetWarnMinFrames,
                1,
                100000
        );
        budgetWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.budgetWarnCooldownFrames",
                budgetWarnCooldownFrames,
                0,
                100000
        );
        budgetPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.budgetPromotionReadyMinFrames",
                budgetPromotionReadyMinFrames,
                1,
                100000
        );
    }

    public void emitFrameWarning(QualityTier qualityTier, List<LightDesc> lights, List<EngineWarning> warnings) {
        VulkanLightingWarningEmitter.Result emission = VulkanLightingWarningEmitter.emit(
                qualityTier,
                lights == null ? List.of() : lights,
                physicallyBasedUnitsEnabled,
                prioritizationEnabled,
                emissiveMeshEnabled,
                localLightBudget,
                budgetWarnRatioThreshold
        );
        localLightCountLastFrame = emission.plan().localLightCount();
        localLightBudgetLastFrame = emission.plan().localLightBudget();
        localLightLoadRatioLastFrame = emission.plan().localLightLoadRatio();
        budgetEnvelopeBreachedLastFrame = emission.plan().budgetEnvelopeBreached();
        modeLastFrame = emission.plan().modeId();
        directionalCountLastFrame = emission.plan().directionalLights();
        pointCountLastFrame = emission.plan().pointLights();
        spotCountLastFrame = emission.plan().spotLights();
        activeCapabilitiesLastFrame = emission.plan().activeCapabilities();
        prunedCapabilitiesLastFrame = emission.plan().prunedCapabilities();
        signalsLastFrame = emission.plan().signals();
        if (budgetEnvelopeBreachedLastFrame) {
            budgetHighStreak++;
            budgetStableStreak = 0;
        } else {
            budgetStableStreak++;
            budgetHighStreak = 0;
        }
        if (budgetWarnCooldownRemaining > 0) {
            budgetWarnCooldownRemaining--;
        }
        budgetPromotionReadyLastFrame = budgetStableStreak >= budgetPromotionReadyMinFrames
                && !budgetEnvelopeBreachedLastFrame;
        boolean emitBreach = budgetEnvelopeBreachedLastFrame
                && budgetHighStreak >= budgetWarnMinFrames
                && budgetWarnCooldownRemaining <= 0;
        if (emitBreach) {
            budgetWarnCooldownRemaining = budgetWarnCooldownFrames;
        }

        if (warnings != null) {
            warnings.addAll(emission.warnings().stream()
                    .filter(w -> !"LIGHTING_BUDGET_ENVELOPE_BREACH".equals(w.code()))
                    .toList());
            warnings.add(new EngineWarning(
                    "LIGHTING_BUDGET_POLICY",
                    "Lighting budget policy (warnMinFrames=" + budgetWarnMinFrames
                            + ", warnCooldownFrames=" + budgetWarnCooldownFrames
                            + ", promotionReadyMinFrames=" + budgetPromotionReadyMinFrames
                            + ", highStreak=" + budgetHighStreak
                            + ", stableStreak=" + budgetStableStreak
                            + ", cooldownRemaining=" + budgetWarnCooldownRemaining + ")"
            ));
            if (emitBreach) {
                warnings.add(new EngineWarning(
                        "LIGHTING_BUDGET_ENVELOPE_BREACH",
                        "Lighting budget envelope breached (localLights=" + localLightCountLastFrame
                                + ", budget=" + localLightBudgetLastFrame
                                + ", ratio=" + localLightLoadRatioLastFrame
                                + ", threshold=" + budgetWarnRatioThreshold
                                + ", highStreak=" + budgetHighStreak
                                + ", cooldownFrames=" + budgetWarnCooldownFrames + ")"
                ));
            }
            if (budgetPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "LIGHTING_BUDGET_PROMOTION_READY",
                        "Lighting budget promotion ready (stableStreak=" + budgetStableStreak
                                + ", minFrames=" + budgetPromotionReadyMinFrames
                                + ", mode=" + modeLastFrame + ")"
                ));
            }
        }
    }

    public LightingCapabilityDiagnostics diagnostics() {
        return new LightingCapabilityDiagnostics(
                !modeLastFrame.isBlank(),
                modeLastFrame,
                directionalCountLastFrame,
                pointCountLastFrame,
                spotCountLastFrame,
                physicallyBasedUnitsEnabled,
                prioritizationEnabled,
                emissiveMeshEnabled,
                activeCapabilitiesLastFrame,
                prunedCapabilitiesLastFrame,
                signalsLastFrame
        );
    }

    public LightingBudgetDiagnostics budgetDiagnostics() {
        return new LightingBudgetDiagnostics(
                !modeLastFrame.isBlank(),
                localLightCountLastFrame,
                localLightBudgetLastFrame,
                localLightLoadRatioLastFrame,
                budgetWarnRatioThreshold,
                budgetEnvelopeBreachedLastFrame
        );
    }

    public LightingPromotionDiagnostics promotionDiagnostics() {
        return new LightingPromotionDiagnostics(
                !modeLastFrame.isBlank(),
                modeLastFrame,
                budgetHighStreak,
                budgetStableStreak,
                budgetWarnMinFrames,
                budgetWarnCooldownFrames,
                budgetWarnCooldownRemaining,
                budgetPromotionReadyMinFrames,
                budgetEnvelopeBreachedLastFrame,
                budgetPromotionReadyLastFrame
        );
    }
}
