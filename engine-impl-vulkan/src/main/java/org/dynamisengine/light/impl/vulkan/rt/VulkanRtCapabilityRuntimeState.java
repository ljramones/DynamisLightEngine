package org.dynamisengine.light.impl.vulkan.rt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.event.EngineWarning;
import org.dynamisengine.light.api.runtime.RtCapabilityDiagnostics;
import org.dynamisengine.light.api.runtime.RtCapabilityPromotionDiagnostics;
import org.dynamisengine.light.impl.vulkan.capability.VulkanRtCapabilityPlanner;
import org.dynamisengine.light.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;

/**
 * Runtime RT capability/promotion diagnostics for outstanding RT vertical features.
 */
public final class VulkanRtCapabilityRuntimeState {
    private boolean rtAoRequested;
    private boolean rtTranslucencyCausticsRequested;
    private boolean bvhCompactionRequested;
    private boolean denoiserFrameworkRequested;
    private boolean hybridCompositionRequested;
    private boolean qualityTiersRequested;
    private boolean inlineRayQueryRequested;
    private boolean dedicatedRaygenRequested;
    private int warnMinFrames = 2;
    private int warnCooldownFrames = 120;
    private int promotionReadyMinFrames = 4;
    private int raysPerPixelLow = 1;
    private int raysPerPixelMedium = 2;
    private int raysPerPixelHigh = 4;
    private int raysPerPixelUltra = 6;
    private int bounceCountLow = 1;
    private int bounceCountMedium = 1;
    private int bounceCountHigh = 2;
    private int bounceCountUltra = 3;
    private float denoiseStrengthLow = 0.45f;
    private float denoiseStrengthMedium = 0.55f;
    private float denoiseStrengthHigh = 0.65f;
    private float denoiseStrengthUltra = 0.75f;

    private int stableStreak;
    private int highStreak;
    private int warnCooldownRemaining;
    private boolean envelopeBreachedLastFrame;
    private boolean promotionReadyLastFrame;
    private int aoStableStreak;
    private int aoHighStreak;
    private int aoWarnCooldownRemaining;
    private boolean aoEnvelopeBreachedLastFrame;
    private boolean aoPromotionReadyLastFrame;
    private int translucencyStableStreak;
    private int translucencyHighStreak;
    private int translucencyWarnCooldownRemaining;
    private boolean translucencyEnvelopeBreachedLastFrame;
    private boolean translucencyPromotionReadyLastFrame;
    private String modeIdLastFrame = "";
    private List<String> expectedFeaturesLastFrame = List.of();
    private List<String> activeFeaturesLastFrame = List.of();
    private List<String> prunedFeaturesLastFrame = List.of();

    public void reset() {
        stableStreak = 0;
        highStreak = 0;
        warnCooldownRemaining = 0;
        envelopeBreachedLastFrame = false;
        promotionReadyLastFrame = false;
        aoStableStreak = 0;
        aoHighStreak = 0;
        aoWarnCooldownRemaining = 0;
        aoEnvelopeBreachedLastFrame = false;
        aoPromotionReadyLastFrame = false;
        translucencyStableStreak = 0;
        translucencyHighStreak = 0;
        translucencyWarnCooldownRemaining = 0;
        translucencyEnvelopeBreachedLastFrame = false;
        translucencyPromotionReadyLastFrame = false;
        modeIdLastFrame = "";
        expectedFeaturesLastFrame = List.of();
        activeFeaturesLastFrame = List.of();
        prunedFeaturesLastFrame = List.of();
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        rtAoRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.rt.aoEnabled", "false"));
        rtTranslucencyCausticsRequested = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.rt.translucencyCausticsEnabled", "false"));
        bvhCompactionRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.rt.bvhCompactionEnabled", "false"));
        denoiserFrameworkRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.rt.denoiserFrameworkEnabled", "false"));
        hybridCompositionRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.rt.hybridCompositionEnabled", "false"));
        qualityTiersRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.rt.qualityTiersEnabled", "true"));
        inlineRayQueryRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.rt.inlineRayQueryEnabled", "false"));
        dedicatedRaygenRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.rt.dedicatedRaygenEnabled", "false"));
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.capabilityWarnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.capabilityWarnCooldownFrames", warnCooldownFrames, 0, 100_000);
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.capabilityPromotionReadyMinFrames", promotionReadyMinFrames, 1, 100_000);
        raysPerPixelLow = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.quality.raysPerPixelLow", raysPerPixelLow, 1, 64);
        raysPerPixelMedium = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.quality.raysPerPixelMedium", raysPerPixelMedium, 1, 64);
        raysPerPixelHigh = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.quality.raysPerPixelHigh", raysPerPixelHigh, 1, 64);
        raysPerPixelUltra = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.quality.raysPerPixelUltra", raysPerPixelUltra, 1, 64);
        bounceCountLow = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.quality.bounceCountLow", bounceCountLow, 1, 16);
        bounceCountMedium = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.quality.bounceCountMedium", bounceCountMedium, 1, 16);
        bounceCountHigh = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.quality.bounceCountHigh", bounceCountHigh, 1, 16);
        bounceCountUltra = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.quality.bounceCountUltra", bounceCountUltra, 1, 16);
        denoiseStrengthLow = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.rt.quality.denoiseStrengthLow", denoiseStrengthLow, 0.0, 1.0);
        denoiseStrengthMedium = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.rt.quality.denoiseStrengthMedium", denoiseStrengthMedium, 0.0, 1.0);
        denoiseStrengthHigh = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.rt.quality.denoiseStrengthHigh", denoiseStrengthHigh, 0.0, 1.0);
        denoiseStrengthUltra = (float) VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe, "vulkan.rt.quality.denoiseStrengthUltra", denoiseStrengthUltra, 0.0, 1.0);
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions != null && backendOptions.containsKey("vulkan.rt.capabilityPromotionReadyMinFrames")) {
            return;
        }
        QualityTier safeTier = tier == null ? QualityTier.MEDIUM : tier;
        promotionReadyMinFrames = switch (safeTier) {
            case LOW -> 2;
            case MEDIUM -> 3;
            case HIGH -> 4;
            case ULTRA -> 5;
        };
    }

    public void emitFrameWarnings(
            QualityTier tier,
            boolean rtTraversalSupported,
            boolean rtBvhSupported,
            boolean reflectionRtLaneActive,
            boolean giRtActive,
            List<EngineWarning> warnings
    ) {
        VulkanRtCapabilityPlanner.PlanInput planInput = new VulkanRtCapabilityPlanner.PlanInput(
                tier,
                rtTraversalSupported,
                rtBvhSupported,
                reflectionRtLaneActive,
                giRtActive,
                rtAoRequested,
                rtTranslucencyCausticsRequested,
                bvhCompactionRequested,
                denoiserFrameworkRequested,
                hybridCompositionRequested,
                qualityTiersRequested,
                inlineRayQueryRequested,
                dedicatedRaygenRequested
        );
        var plan = VulkanRtCapabilityPlanner.plan(planInput);
        QualityTier safeTier = tier == null ? QualityTier.MEDIUM : tier;
        int activeRaysPerPixel = switch (safeTier) {
            case LOW -> raysPerPixelLow;
            case MEDIUM -> raysPerPixelMedium;
            case HIGH -> raysPerPixelHigh;
            case ULTRA -> raysPerPixelUltra;
        };
        int activeBounceCount = switch (safeTier) {
            case LOW -> bounceCountLow;
            case MEDIUM -> bounceCountMedium;
            case HIGH -> bounceCountHigh;
            case ULTRA -> bounceCountUltra;
        };
        float activeDenoiseStrength = switch (safeTier) {
            case LOW -> denoiseStrengthLow;
            case MEDIUM -> denoiseStrengthMedium;
            case HIGH -> denoiseStrengthHigh;
            case ULTRA -> denoiseStrengthUltra;
        };

        expectedFeaturesLastFrame = expectedFeatureList();
        activeFeaturesLastFrame = plan.activeCapabilities();
        prunedFeaturesLastFrame = plan.prunedCapabilities();
        modeIdLastFrame = plan.modeId();
        boolean qualityTiersActive = activeFeaturesLastFrame.contains("vulkan.rt.quality_tiers");

        boolean risk = !prunedFeaturesLastFrame.isEmpty();
        if (risk) {
            highStreak += 1;
            stableStreak = 0;
            if (warnCooldownRemaining > 0) {
                warnCooldownRemaining -= 1;
            }
        } else {
            highStreak = 0;
            stableStreak += 1;
            if (warnCooldownRemaining > 0) {
                warnCooldownRemaining -= 1;
            }
        }

        envelopeBreachedLastFrame = risk && highStreak >= warnMinFrames;
        promotionReadyLastFrame = !risk && stableStreak >= promotionReadyMinFrames;

        warnings.add(new EngineWarning(
                "RT_CAPABILITY_MODE_ACTIVE",
                "RT capability mode active (resolvedMode=" + modeIdLastFrame
                        + ", expected=[" + String.join(", ", expectedFeaturesLastFrame)
                        + "], active=[" + String.join(", ", activeFeaturesLastFrame)
                        + "], pruned=[" + String.join(", ", prunedFeaturesLastFrame)
                        + "], signals=[" + String.join(", ", plan.signals()) + "])"
        ));
        warnings.add(new EngineWarning(
                "RT_CAPABILITY_POLICY_ACTIVE",
                "RT capability policy (aoExpected=" + rtAoRequested
                        + ", translucencyCausticsExpected=" + rtTranslucencyCausticsRequested
                        + ", bvhCompactionExpected=" + bvhCompactionRequested
                        + ", denoiserFrameworkExpected=" + denoiserFrameworkRequested
                        + ", hybridExpected=" + hybridCompositionRequested
                        + ", qualityTiersExpected=" + qualityTiersRequested
                        + ", inlineRayQueryExpected=" + inlineRayQueryRequested
                        + ", dedicatedRaygenExpected=" + dedicatedRaygenRequested + ")"
        ));
        emitLaneWarnings(
                warnings,
                "RT_AO",
                rtAoRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.ao"),
                aoHighStreak,
                aoStableStreak,
                aoWarnCooldownRemaining
        );
        aoHighStreak = laneHighStreakNext;
        aoStableStreak = laneStableStreakNext;
        aoWarnCooldownRemaining = laneWarnCooldownRemainingNext;
        aoEnvelopeBreachedLastFrame = laneEnvelopeBreachedLastFrameNext;
        aoPromotionReadyLastFrame = lanePromotionReadyLastFrameNext;

        emitLaneWarnings(
                warnings,
                "RT_TRANSLUCENCY",
                rtTranslucencyCausticsRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.translucency_caustics"),
                translucencyHighStreak,
                translucencyStableStreak,
                translucencyWarnCooldownRemaining
        );
        translucencyHighStreak = laneHighStreakNext;
        translucencyStableStreak = laneStableStreakNext;
        translucencyWarnCooldownRemaining = laneWarnCooldownRemainingNext;
        translucencyEnvelopeBreachedLastFrame = laneEnvelopeBreachedLastFrameNext;
        translucencyPromotionReadyLastFrame = lanePromotionReadyLastFrameNext;

        if (qualityTiersRequested || qualityTiersActive) {
            warnings.add(new EngineWarning(
                    "RT_QUALITY_TIERS_ACTIVE",
                    "RT quality tiers active (tier=" + safeTier.name().toLowerCase(java.util.Locale.ROOT)
                            + ", raysPerPixel=" + activeRaysPerPixel
                            + ", bounceCount=" + activeBounceCount
                            + ", denoiseStrength=" + activeDenoiseStrength + ")"
            ));
        }
        warnings.add(new EngineWarning(
                "RT_CAPABILITY_ENVELOPE",
                "RT capability envelope (risk=" + risk
                        + ", expectedCount=" + expectedFeaturesLastFrame.size()
                        + ", activeCount=" + activeFeaturesLastFrame.size()
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (envelopeBreachedLastFrame && warnCooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "RT_CAPABILITY_ENVELOPE_BREACH",
                    "RT capability envelope breach (highStreak=" + highStreak
                            + ", cooldown=" + warnCooldownRemaining + ")"
            ));
            warnCooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "RT_CAPABILITY_PROMOTION_READY",
                    "RT capability promotion-ready envelope satisfied (stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public RtCapabilityDiagnostics diagnostics() {
        return new RtCapabilityDiagnostics(
                true,
                modeIdLastFrame,
                rtAoRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.ao"),
                rtTranslucencyCausticsRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.translucency_caustics"),
                bvhCompactionRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.bvh_compaction"),
                denoiserFrameworkRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.denoiser_framework"),
                hybridCompositionRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.hybrid_composition"),
                qualityTiersRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.quality_tiers"),
                inlineRayQueryRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.inline_ray_query"),
                dedicatedRaygenRequested,
                activeFeaturesLastFrame.contains("vulkan.rt.dedicated_raygen"),
                expectedFeaturesLastFrame,
                activeFeaturesLastFrame,
                prunedFeaturesLastFrame
        );
    }

    public RtCapabilityPromotionDiagnostics promotionDiagnostics() {
        return new RtCapabilityPromotionDiagnostics(
                true,
                expectedFeaturesLastFrame.size(),
                activeFeaturesLastFrame.size(),
                warnMinFrames,
                warnCooldownFrames,
                warnCooldownRemaining,
                promotionReadyMinFrames,
                stableStreak,
                highStreak,
                envelopeBreachedLastFrame,
                promotionReadyLastFrame,
                aoEnvelopeBreachedLastFrame,
                aoPromotionReadyLastFrame,
                translucencyEnvelopeBreachedLastFrame,
                translucencyPromotionReadyLastFrame
        );
    }

    private List<String> expectedFeatureList() {
        List<String> out = new ArrayList<>();
        if (rtAoRequested) out.add("vulkan.rt.ao");
        if (rtTranslucencyCausticsRequested) out.add("vulkan.rt.translucency_caustics");
        if (bvhCompactionRequested) out.add("vulkan.rt.bvh_compaction");
        if (denoiserFrameworkRequested) out.add("vulkan.rt.denoiser_framework");
        if (hybridCompositionRequested) out.add("vulkan.rt.hybrid_composition");
        if (qualityTiersRequested) out.add("vulkan.rt.quality_tiers");
        if (inlineRayQueryRequested) out.add("vulkan.rt.inline_ray_query");
        if (dedicatedRaygenRequested) out.add("vulkan.rt.dedicated_raygen");
        return List.copyOf(out);
    }

    private int laneHighStreakNext;
    private int laneStableStreakNext;
    private int laneWarnCooldownRemainingNext;
    private boolean laneEnvelopeBreachedLastFrameNext;
    private boolean lanePromotionReadyLastFrameNext;

    private void emitLaneWarnings(
            List<EngineWarning> warnings,
            String prefix,
            boolean expected,
            boolean active,
            int currentHighStreak,
            int currentStableStreak,
            int currentWarnCooldownRemaining
    ) {
        warnings.add(new EngineWarning(
                prefix + "_POLICY_ACTIVE",
                prefix + " policy (expected=" + expected + ", active=" + active + ")"
        ));
        boolean risk = expected && !active;
        int nextHigh = risk ? currentHighStreak + 1 : 0;
        int nextStable = risk ? 0 : currentStableStreak + 1;
        int nextCooldown = currentWarnCooldownRemaining > 0 ? currentWarnCooldownRemaining - 1 : 0;
        boolean breached = risk && nextHigh >= warnMinFrames;
        boolean promotionReady = !risk && nextStable >= promotionReadyMinFrames;
        warnings.add(new EngineWarning(
                prefix + "_ENVELOPE",
                prefix + " envelope (risk=" + risk
                        + ", expected=" + expected
                        + ", active=" + active
                        + ", highStreak=" + nextHigh
                        + ", stableStreak=" + nextStable
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (breached && nextCooldown <= 0) {
            warnings.add(new EngineWarning(
                    prefix + "_ENVELOPE_BREACH",
                    prefix + " envelope breach (highStreak=" + nextHigh
                            + ", cooldown=" + nextCooldown + ")"
            ));
            nextCooldown = warnCooldownFrames;
        }
        if (promotionReady) {
            warnings.add(new EngineWarning(
                    prefix + "_PROMOTION_READY",
                    prefix + " promotion-ready envelope satisfied (stableStreak=" + nextStable
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
        laneHighStreakNext = nextHigh;
        laneStableStreakNext = nextStable;
        laneWarnCooldownRemainingNext = nextCooldown;
        laneEnvelopeBreachedLastFrameNext = breached;
        lanePromotionReadyLastFrameNext = promotionReady;
    }

}
