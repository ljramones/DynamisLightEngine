package org.dynamislight.impl.vulkan.lighting;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.LightingBudgetDiagnostics;
import org.dynamislight.api.runtime.LightingCapabilityDiagnostics;
import org.dynamislight.api.runtime.LightingEmissiveDiagnostics;
import org.dynamislight.api.runtime.LightingPromotionDiagnostics;
import org.dynamislight.api.runtime.LightingAdvancedDiagnostics;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;
import org.dynamislight.impl.vulkan.warning.lighting.VulkanLightingWarningEmitter;

/**
 * Runtime holder for lighting capability mode telemetry and typed diagnostics.
 */
public final class VulkanLightingCapabilityRuntimeState {
    private boolean physicallyBasedUnitsEnabled = true;
    private boolean prioritizationEnabled = true;
    private boolean emissiveMeshEnabled;
    private boolean areaApproxEnabled;
    private boolean iesProfilesEnabled;
    private boolean cookiesEnabled;
    private boolean volumetricShaftsEnabled;
    private boolean clusteringEnabled;
    private boolean lightLayersEnabled;
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
    private int baselinePromotionReadyMinFrames = 4;
    private int budgetPromotionReadyMinFrames = 6;
    private int physUnitsPromotionReadyMinFrames = 6;
    private int emissivePromotionReadyMinFrames = 8;
    private int advancedPromotionReadyMinFrames = 4;
    private boolean advancedRequireActive;
    private int advancedRequireMinFrames = 2;
    private int advancedRequireCooldownFrames = 120;
    private int budgetHighStreak;
    private int baselineStableStreak;
    private int budgetStableStreak;
    private int physUnitsStableStreak;
    private int emissiveStableStreak;
    private int advancedStableStreak;
    private int advancedRequireUnavailableStreak;
    private int budgetWarnCooldownRemaining;
    private int advancedRequireCooldownRemaining;
    private boolean baselinePromotionReadyLastFrame;
    private boolean budgetPromotionReadyLastFrame;
    private boolean physUnitsPromotionReadyLastFrame;
    private boolean emissivePromotionReadyLastFrame;
    private boolean phase2PromotionReadyLastFrame;
    private boolean advancedPromotionReadyLastFrame;
    private boolean advancedRequiredUnavailableBreachedLastFrame;
    private double emissiveWarnMinCandidateRatio = 0.05;
    private int emissiveCandidateCountLastFrame;
    private int emissiveMaterialCountLastFrame;
    private double emissiveCandidateRatioLastFrame;
    private boolean emissiveEnvelopeBreachedLastFrame;
    private List<String> activeCapabilitiesLastFrame = List.of();
    private List<String> prunedCapabilitiesLastFrame = List.of();
    private List<String> signalsLastFrame = List.of();
    private boolean areaApproxActiveLastFrame;
    private boolean iesProfilesActiveLastFrame;
    private boolean cookiesActiveLastFrame;
    private boolean volumetricShaftsActiveLastFrame;
    private boolean clusteringActiveLastFrame;
    private boolean lightLayersActiveLastFrame;
    private int advancedExpectedCountLastFrame;
    private int advancedActiveCountLastFrame;
    private int advancedRequiredCountLastFrame;

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
        baselineStableStreak = 0;
        budgetStableStreak = 0;
        budgetWarnCooldownRemaining = 0;
        baselinePromotionReadyLastFrame = false;
        budgetPromotionReadyLastFrame = false;
        physUnitsStableStreak = 0;
        emissiveStableStreak = 0;
        advancedStableStreak = 0;
        advancedRequireUnavailableStreak = 0;
        physUnitsPromotionReadyLastFrame = false;
        emissivePromotionReadyLastFrame = false;
        phase2PromotionReadyLastFrame = false;
        advancedPromotionReadyLastFrame = false;
        advancedRequiredUnavailableBreachedLastFrame = false;
        advancedRequireCooldownRemaining = 0;
        emissiveCandidateCountLastFrame = 0;
        emissiveMaterialCountLastFrame = 0;
        emissiveCandidateRatioLastFrame = 0.0;
        emissiveEnvelopeBreachedLastFrame = false;
        activeCapabilitiesLastFrame = List.of();
        prunedCapabilitiesLastFrame = List.of();
        signalsLastFrame = List.of();
        areaApproxActiveLastFrame = false;
        iesProfilesActiveLastFrame = false;
        cookiesActiveLastFrame = false;
        volumetricShaftsActiveLastFrame = false;
        clusteringActiveLastFrame = false;
        lightLayersActiveLastFrame = false;
        advancedExpectedCountLastFrame = 0;
        advancedActiveCountLastFrame = 0;
        advancedRequiredCountLastFrame = 0;
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
        areaApproxEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.areaApproxEnabled", "false")
        );
        iesProfilesEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.iesProfilesEnabled", "false")
        );
        cookiesEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.cookiesEnabled", "false")
        );
        volumetricShaftsEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.volumetricShaftsEnabled", "false")
        );
        clusteringEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.clusteringEnabled", "false")
        );
        lightLayersEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.lightLayersEnabled", "false")
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
        baselinePromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.baselinePromotionReadyMinFrames",
                baselinePromotionReadyMinFrames,
                1,
                100000
        );
        budgetPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.budgetPromotionReadyMinFrames",
                budgetPromotionReadyMinFrames,
                1,
                100000
        );
        physUnitsPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.physUnitsPromotionReadyMinFrames",
                physUnitsPromotionReadyMinFrames,
                1,
                100000
        );
        emissivePromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.emissivePromotionReadyMinFrames",
                emissivePromotionReadyMinFrames,
                1,
                100000
        );
        advancedPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.advancedPromotionReadyMinFrames",
                advancedPromotionReadyMinFrames,
                1,
                100000
        );
        advancedRequireActive = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.advancedRequireActive", "false")
        );
        advancedRequireMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.advancedRequireMinFrames",
                advancedRequireMinFrames,
                1,
                100000
        );
        advancedRequireCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.advancedRequireCooldownFrames",
                advancedRequireCooldownFrames,
                0,
                100000
        );
        emissiveWarnMinCandidateRatio = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe,
                "vulkan.lighting.emissiveWarnMinCandidateRatio",
                emissiveWarnMinCandidateRatio,
                0.0,
                1.0
        );
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        QualityTier resolved = tier == null ? QualityTier.MEDIUM : tier;
        switch (resolved) {
            case LOW -> {
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnRatioThreshold")) {
                    budgetWarnRatioThreshold = 1.50;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnMinFrames")) {
                    budgetWarnMinFrames = 4;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnCooldownFrames")) {
                    budgetWarnCooldownFrames = 180;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.baselinePromotionReadyMinFrames")) {
                    baselinePromotionReadyMinFrames = 5;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetPromotionReadyMinFrames")) {
                    budgetPromotionReadyMinFrames = 8;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.physUnitsPromotionReadyMinFrames")) {
                    physUnitsPromotionReadyMinFrames = 8;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.emissivePromotionReadyMinFrames")) {
                    emissivePromotionReadyMinFrames = 10;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.advancedPromotionReadyMinFrames")) {
                    advancedPromotionReadyMinFrames = 6;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.emissiveWarnMinCandidateRatio")) {
                    emissiveWarnMinCandidateRatio = 0.02;
                }
            }
            case MEDIUM -> {
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnRatioThreshold")) {
                    budgetWarnRatioThreshold = 1.25;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnMinFrames")) {
                    budgetWarnMinFrames = 3;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnCooldownFrames")) {
                    budgetWarnCooldownFrames = 120;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.baselinePromotionReadyMinFrames")) {
                    baselinePromotionReadyMinFrames = 4;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetPromotionReadyMinFrames")) {
                    budgetPromotionReadyMinFrames = 6;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.physUnitsPromotionReadyMinFrames")) {
                    physUnitsPromotionReadyMinFrames = 6;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.emissivePromotionReadyMinFrames")) {
                    emissivePromotionReadyMinFrames = 8;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.advancedPromotionReadyMinFrames")) {
                    advancedPromotionReadyMinFrames = 5;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.emissiveWarnMinCandidateRatio")) {
                    emissiveWarnMinCandidateRatio = 0.05;
                }
            }
            case HIGH -> {
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnRatioThreshold")) {
                    budgetWarnRatioThreshold = 1.10;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnMinFrames")) {
                    budgetWarnMinFrames = 2;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnCooldownFrames")) {
                    budgetWarnCooldownFrames = 90;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.baselinePromotionReadyMinFrames")) {
                    baselinePromotionReadyMinFrames = 3;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetPromotionReadyMinFrames")) {
                    budgetPromotionReadyMinFrames = 5;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.physUnitsPromotionReadyMinFrames")) {
                    physUnitsPromotionReadyMinFrames = 5;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.emissivePromotionReadyMinFrames")) {
                    emissivePromotionReadyMinFrames = 6;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.advancedPromotionReadyMinFrames")) {
                    advancedPromotionReadyMinFrames = 4;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.emissiveWarnMinCandidateRatio")) {
                    emissiveWarnMinCandidateRatio = 0.08;
                }
            }
            case ULTRA -> {
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnRatioThreshold")) {
                    budgetWarnRatioThreshold = 1.00;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnMinFrames")) {
                    budgetWarnMinFrames = 2;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetWarnCooldownFrames")) {
                    budgetWarnCooldownFrames = 75;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.baselinePromotionReadyMinFrames")) {
                    baselinePromotionReadyMinFrames = 2;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.budgetPromotionReadyMinFrames")) {
                    budgetPromotionReadyMinFrames = 4;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.physUnitsPromotionReadyMinFrames")) {
                    physUnitsPromotionReadyMinFrames = 4;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.emissivePromotionReadyMinFrames")) {
                    emissivePromotionReadyMinFrames = 5;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.advancedPromotionReadyMinFrames")) {
                    advancedPromotionReadyMinFrames = 3;
                }
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.lighting.emissiveWarnMinCandidateRatio")) {
                    emissiveWarnMinCandidateRatio = 0.10;
                }
            }
        }
    }

    public void emitFrameWarning(
            QualityTier qualityTier,
            List<LightDesc> lights,
            List<MaterialDesc> materials,
            List<EngineWarning> warnings
    ) {
        VulkanLightingWarningEmitter.Result emission = VulkanLightingWarningEmitter.emit(
                qualityTier,
                lights == null ? List.of() : lights,
                physicallyBasedUnitsEnabled,
                prioritizationEnabled,
                emissiveMeshEnabled,
                areaApproxEnabled,
                iesProfilesEnabled,
                cookiesEnabled,
                volumetricShaftsEnabled,
                clusteringEnabled,
                lightLayersEnabled,
                localLightBudget,
                budgetWarnRatioThreshold
        );
        localLightCountLastFrame = emission.plan().localLightCount();
        localLightBudgetLastFrame = emission.plan().localLightBudget();
        localLightLoadRatioLastFrame = emission.plan().localLightLoadRatio();
        budgetEnvelopeBreachedLastFrame = emission.plan().budgetEnvelopeBreached();
        emissiveMaterialCountLastFrame = materials == null ? 0 : materials.size();
        emissiveCandidateCountLastFrame = materials == null ? 0 : (int) materials.stream()
                .filter(m -> m != null && m.emissiveReactiveBoost() > 1.0f)
                .count();
        emissiveCandidateRatioLastFrame = emissiveMaterialCountLastFrame <= 0
                ? 0.0
                : (double) emissiveCandidateCountLastFrame / (double) emissiveMaterialCountLastFrame;
        emissiveEnvelopeBreachedLastFrame = emissiveMeshEnabled
                && emissiveCandidateRatioLastFrame < emissiveWarnMinCandidateRatio;
        modeLastFrame = emission.plan().modeId();
        directionalCountLastFrame = emission.plan().directionalLights();
        pointCountLastFrame = emission.plan().pointLights();
        spotCountLastFrame = emission.plan().spotLights();
        activeCapabilitiesLastFrame = emission.plan().activeCapabilities();
        prunedCapabilitiesLastFrame = emission.plan().prunedCapabilities();
        signalsLastFrame = emission.plan().signals();
        areaApproxActiveLastFrame = emission.plan().areaApproxEnabled();
        iesProfilesActiveLastFrame = emission.plan().iesProfilesEnabled();
        cookiesActiveLastFrame = emission.plan().cookiesEnabled();
        volumetricShaftsActiveLastFrame = emission.plan().volumetricShaftsEnabled();
        clusteringActiveLastFrame = emission.plan().clusteringEnabled();
        lightLayersActiveLastFrame = emission.plan().lightLayersEnabled();
        if (budgetEnvelopeBreachedLastFrame) {
            budgetHighStreak++;
            baselineStableStreak = 0;
            budgetStableStreak = 0;
        } else {
            baselineStableStreak++;
            budgetStableStreak++;
            budgetHighStreak = 0;
        }
        if (budgetWarnCooldownRemaining > 0) {
            budgetWarnCooldownRemaining--;
        }
        baselinePromotionReadyLastFrame = baselineStableStreak >= baselinePromotionReadyMinFrames
                && !budgetEnvelopeBreachedLastFrame;
        budgetPromotionReadyLastFrame = budgetStableStreak >= budgetPromotionReadyMinFrames
                && !budgetEnvelopeBreachedLastFrame;
        if (physicallyBasedUnitsEnabled && !budgetEnvelopeBreachedLastFrame) {
            physUnitsStableStreak++;
        } else {
            physUnitsStableStreak = 0;
        }
        physUnitsPromotionReadyLastFrame = physicallyBasedUnitsEnabled
                && physUnitsStableStreak >= physUnitsPromotionReadyMinFrames
                && !budgetEnvelopeBreachedLastFrame;
        if (emissiveMeshEnabled && !emissiveEnvelopeBreachedLastFrame) {
            emissiveStableStreak++;
        } else {
            emissiveStableStreak = 0;
        }
        emissivePromotionReadyLastFrame = emissiveMeshEnabled
                && emissiveStableStreak >= emissivePromotionReadyMinFrames
                && !emissiveEnvelopeBreachedLastFrame;
        int expectedAdvancedCount = expectedAdvancedCapabilityCount(qualityTier);
        int activeAdvancedCount = activeAdvancedCapabilityCount();
        int requiredAdvancedCount = advancedRequireActive
                ? requiredAdvancedCapabilityCount()
                : expectedAdvancedCount;
        advancedExpectedCountLastFrame = expectedAdvancedCount;
        advancedActiveCountLastFrame = activeAdvancedCount;
        advancedRequiredCountLastFrame = requiredAdvancedCount;
        boolean advancedStableThisFrame = expectedAdvancedCount > 0 && activeAdvancedCount >= expectedAdvancedCount;
        if (advancedStableThisFrame) {
            advancedStableStreak++;
        } else {
            advancedStableStreak = 0;
        }
        advancedPromotionReadyLastFrame = expectedAdvancedCount > 0
                && advancedStableStreak >= advancedPromotionReadyMinFrames;
        if (advancedRequireActive && requiredAdvancedCount > activeAdvancedCount) {
            advancedRequireUnavailableStreak++;
        } else {
            advancedRequireUnavailableStreak = 0;
        }
        if (advancedRequireCooldownRemaining > 0) {
            advancedRequireCooldownRemaining--;
        }
        boolean emitAdvancedRequireBreach = advancedRequireActive
                && requiredAdvancedCount > activeAdvancedCount
                && advancedRequireUnavailableStreak >= advancedRequireMinFrames
                && advancedRequireCooldownRemaining <= 0;
        advancedRequiredUnavailableBreachedLastFrame = emitAdvancedRequireBreach;
        if (emitAdvancedRequireBreach) {
            advancedRequireCooldownRemaining = advancedRequireCooldownFrames;
        }
        phase2PromotionReadyLastFrame = budgetPromotionReadyLastFrame
                && physUnitsPromotionReadyLastFrame
                && (!emissiveMeshEnabled || emissivePromotionReadyLastFrame);
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
                    "LIGHTING_TELEMETRY_PROFILE_ACTIVE",
                    "Lighting telemetry profile active (mode=" + modeLastFrame
                            + ", budgetWarnRatioThreshold=" + budgetWarnRatioThreshold
                            + ", budgetWarnMinFrames=" + budgetWarnMinFrames
                            + ", budgetWarnCooldownFrames=" + budgetWarnCooldownFrames
                            + ", baselinePromotionReadyMinFrames=" + baselinePromotionReadyMinFrames
                            + ", budgetPromotionReadyMinFrames=" + budgetPromotionReadyMinFrames
                            + ", physUnitsPromotionReadyMinFrames=" + physUnitsPromotionReadyMinFrames
                            + ", emissivePromotionReadyMinFrames=" + emissivePromotionReadyMinFrames
                            + ", advancedPromotionReadyMinFrames=" + advancedPromotionReadyMinFrames
                            + ", advancedRequireActive=" + advancedRequireActive
                            + ", advancedRequireMinFrames=" + advancedRequireMinFrames
                            + ", advancedRequireCooldownFrames=" + advancedRequireCooldownFrames
                            + ", emissiveWarnMinCandidateRatio=" + emissiveWarnMinCandidateRatio + ")"
            ));
            warnings.add(new EngineWarning(
                    "LIGHTING_BUDGET_POLICY",
                    "Lighting budget policy (warnMinFrames=" + budgetWarnMinFrames
                            + ", warnCooldownFrames=" + budgetWarnCooldownFrames
                            + ", promotionReadyMinFrames=" + budgetPromotionReadyMinFrames
                            + ", highStreak=" + budgetHighStreak
                            + ", stableStreak=" + budgetStableStreak
                            + ", cooldownRemaining=" + budgetWarnCooldownRemaining + ")"
            ));
            warnings.add(new EngineWarning(
                    "LIGHTING_PHYS_UNITS_POLICY",
                    "Lighting physically-based units policy (enabled=" + physicallyBasedUnitsEnabled
                            + ", mode=" + modeLastFrame + ")"
            ));
            warnings.add(new EngineWarning(
                    "LIGHTING_EMISSIVE_POLICY",
                    "Lighting emissive policy (enabled=" + emissiveMeshEnabled
                            + ", candidateCount=" + emissiveCandidateCountLastFrame
                            + ", totalMaterials=" + emissiveMaterialCountLastFrame
                            + ", candidateRatio=" + emissiveCandidateRatioLastFrame
                            + ", minCandidateRatio=" + emissiveWarnMinCandidateRatio + ")"
            ));
            warnings.add(new EngineWarning(
                    "LIGHTING_ADVANCED_REQUIRED_PATH_POLICY",
                    "Lighting advanced required-path policy (enabled=" + advancedRequireActive
                            + ", requiredCount=" + advancedRequiredCountLastFrame
                            + ", activeCount=" + advancedActiveCountLastFrame
                            + ", unavailableStreak=" + advancedRequireUnavailableStreak
                            + ", minFrames=" + advancedRequireMinFrames
                            + ", cooldownFrames=" + advancedRequireCooldownFrames
                            + ", cooldownRemaining=" + advancedRequireCooldownRemaining + ")"
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
            if (emissiveEnvelopeBreachedLastFrame) {
                warnings.add(new EngineWarning(
                        "LIGHTING_EMISSIVE_ENVELOPE_BREACH",
                        "Lighting emissive envelope breached (candidateCount=" + emissiveCandidateCountLastFrame
                                + ", totalMaterials=" + emissiveMaterialCountLastFrame
                                + ", candidateRatio=" + emissiveCandidateRatioLastFrame
                                + ", minCandidateRatio=" + emissiveWarnMinCandidateRatio + ")"
                ));
            }
            if (baselinePromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "LIGHTING_BASELINE_PROMOTION_READY",
                        "Lighting baseline promotion ready (stableStreak=" + baselineStableStreak
                                + ", minFrames=" + baselinePromotionReadyMinFrames
                                + ", mode=" + modeLastFrame + ")"
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
            if (physUnitsPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "LIGHTING_PHYS_UNITS_PROMOTION_READY",
                        "Lighting physically-based units promotion ready (stableStreak=" + physUnitsStableStreak
                                + ", minFrames=" + physUnitsPromotionReadyMinFrames
                                + ", mode=" + modeLastFrame + ")"
                ));
            }
            if (emissivePromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "LIGHTING_EMISSIVE_PROMOTION_READY",
                        "Lighting emissive promotion ready (stableStreak=" + emissiveStableStreak
                                + ", minFrames=" + emissivePromotionReadyMinFrames
                                + ", mode=" + modeLastFrame + ")"
                ));
            }
            if (phase2PromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "LIGHTING_PHASE2_PROMOTION_READY",
                        "Lighting phase2 promotion ready (budgetReady=" + budgetPromotionReadyLastFrame
                                + ", physUnitsReady=" + physUnitsPromotionReadyLastFrame
                                + ", emissiveReady=" + emissivePromotionReadyLastFrame
                                + ", emissiveEnabled=" + emissiveMeshEnabled
                                + ", mode=" + modeLastFrame + ")"
                ));
            }
            if (advancedPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "LIGHTING_ADVANCED_PROMOTION_READY",
                        "Lighting advanced promotion ready (stableStreak=" + advancedStableStreak
                                + ", minFrames=" + advancedPromotionReadyMinFrames
                                + ", mode=" + modeLastFrame + ")"
                ));
            }
            if (emitAdvancedRequireBreach) {
                warnings.add(new EngineWarning(
                        "LIGHTING_ADVANCED_REQUIRED_UNAVAILABLE_BREACH",
                        "Lighting advanced required path unavailable (requiredCount=" + advancedRequiredCountLastFrame
                                + ", activeCount=" + advancedActiveCountLastFrame
                                + ", unavailableStreak=" + advancedRequireUnavailableStreak
                                + ", minFrames=" + advancedRequireMinFrames
                                + ", cooldownFrames=" + advancedRequireCooldownFrames
                                + ", mode=" + modeLastFrame + ")"
                ));
            }
        }
    }

    private int expectedAdvancedCapabilityCount(QualityTier qualityTier) {
        QualityTier tier = qualityTier == null ? QualityTier.MEDIUM : qualityTier;
        int expected = 0;
        if (areaApproxEnabled && tier.ordinal() >= QualityTier.HIGH.ordinal()) {
            expected++;
        }
        if (iesProfilesEnabled && tier.ordinal() >= QualityTier.HIGH.ordinal()) {
            expected++;
        }
        if (cookiesEnabled && tier.ordinal() >= QualityTier.MEDIUM.ordinal()) {
            expected++;
        }
        if (volumetricShaftsEnabled && tier.ordinal() >= QualityTier.HIGH.ordinal()) {
            expected++;
        }
        if (clusteringEnabled && tier.ordinal() >= QualityTier.HIGH.ordinal()) {
            expected++;
        }
        if (lightLayersEnabled && tier.ordinal() >= QualityTier.MEDIUM.ordinal()) {
            expected++;
        }
        return expected;
    }

    private int activeAdvancedCapabilityCount() {
        int active = 0;
        if (activeCapabilitiesLastFrame.contains("vulkan.lighting.area_approx")) {
            active++;
        }
        if (activeCapabilitiesLastFrame.contains("vulkan.lighting.ies_profiles")) {
            active++;
        }
        if (activeCapabilitiesLastFrame.contains("vulkan.lighting.cookies")) {
            active++;
        }
        if (activeCapabilitiesLastFrame.contains("vulkan.lighting.volumetric_shafts")) {
            active++;
        }
        if (activeCapabilitiesLastFrame.contains("vulkan.lighting.clustering")) {
            active++;
        }
        if (activeCapabilitiesLastFrame.contains("vulkan.lighting.light_layers")) {
            active++;
        }
        return active;
    }

    private int requiredAdvancedCapabilityCount() {
        int required = 0;
        if (areaApproxEnabled) {
            required++;
        }
        if (iesProfilesEnabled) {
            required++;
        }
        if (cookiesEnabled) {
            required++;
        }
        if (volumetricShaftsEnabled) {
            required++;
        }
        if (clusteringEnabled) {
            required++;
        }
        if (lightLayersEnabled) {
            required++;
        }
        return required;
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
                areaApproxActiveLastFrame,
                iesProfilesActiveLastFrame,
                cookiesActiveLastFrame,
                volumetricShaftsActiveLastFrame,
                clusteringActiveLastFrame,
                lightLayersActiveLastFrame,
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
                baselineStableStreak,
                baselinePromotionReadyMinFrames,
                baselinePromotionReadyLastFrame,
                budgetHighStreak,
                budgetStableStreak,
                budgetWarnMinFrames,
                budgetWarnCooldownFrames,
                budgetWarnCooldownRemaining,
                budgetPromotionReadyMinFrames,
                physUnitsStableStreak,
                physUnitsPromotionReadyMinFrames,
                physUnitsPromotionReadyLastFrame,
                emissiveStableStreak,
                emissivePromotionReadyMinFrames,
                emissivePromotionReadyLastFrame,
                advancedStableStreak,
                advancedPromotionReadyMinFrames,
                advancedPromotionReadyLastFrame,
                phase2PromotionReadyLastFrame,
                budgetEnvelopeBreachedLastFrame,
                budgetPromotionReadyLastFrame
        );
    }

    public LightingEmissiveDiagnostics emissiveDiagnostics() {
        return new LightingEmissiveDiagnostics(
                !modeLastFrame.isBlank(),
                emissiveMeshEnabled,
                emissiveCandidateCountLastFrame,
                emissiveMaterialCountLastFrame,
                emissiveCandidateRatioLastFrame,
                emissiveWarnMinCandidateRatio,
                emissiveEnvelopeBreachedLastFrame
        );
    }

    public LightingAdvancedDiagnostics advancedDiagnostics() {
        return new LightingAdvancedDiagnostics(
                !modeLastFrame.isBlank(),
                advancedExpectedCountLastFrame,
                advancedActiveCountLastFrame,
                advancedRequiredCountLastFrame,
                advancedStableStreak,
                advancedPromotionReadyMinFrames,
                advancedPromotionReadyLastFrame,
                advancedRequireActive,
                advancedRequireMinFrames,
                advancedRequireCooldownFrames,
                advancedRequireCooldownRemaining,
                advancedRequireUnavailableStreak,
                advancedRequiredUnavailableBreachedLastFrame,
                areaApproxActiveLastFrame,
                iesProfilesActiveLastFrame,
                cookiesActiveLastFrame,
                volumetricShaftsActiveLastFrame,
                clusteringActiveLastFrame,
                lightLayersActiveLastFrame
        );
    }
}
