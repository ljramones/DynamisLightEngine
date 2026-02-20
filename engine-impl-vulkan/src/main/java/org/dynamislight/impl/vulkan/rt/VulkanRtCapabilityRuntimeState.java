package org.dynamislight.impl.vulkan.rt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.RtCapabilityDiagnostics;
import org.dynamislight.api.runtime.RtCapabilityPromotionDiagnostics;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;

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

    private int stableStreak;
    private int highStreak;
    private int warnCooldownRemaining;
    private boolean envelopeBreachedLastFrame;
    private boolean promotionReadyLastFrame;
    private List<String> expectedFeaturesLastFrame = List.of();
    private List<String> activeFeaturesLastFrame = List.of();
    private List<String> prunedFeaturesLastFrame = List.of();

    public void reset() {
        stableStreak = 0;
        highStreak = 0;
        warnCooldownRemaining = 0;
        envelopeBreachedLastFrame = false;
        promotionReadyLastFrame = false;
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
        QualityTier safeTier = tier == null ? QualityTier.MEDIUM : tier;
        boolean rtAoActive = rtAoRequested
                && rtTraversalSupported
                && safeTier.ordinal() >= QualityTier.MEDIUM.ordinal();
        boolean rtTranslucencyCausticsActive = rtTranslucencyCausticsRequested
                && rtTraversalSupported
                && safeTier.ordinal() >= QualityTier.ULTRA.ordinal();
        boolean bvhCompactionActive = bvhCompactionRequested && rtBvhSupported;
        boolean denoiserFrameworkActive = denoiserFrameworkRequested
                && (reflectionRtLaneActive || giRtActive || rtAoActive || rtTranslucencyCausticsActive);
        boolean hybridCompositionActive = hybridCompositionRequested
                && (reflectionRtLaneActive || giRtActive || rtAoActive || rtTranslucencyCausticsActive);
        boolean qualityTiersActive = qualityTiersRequested;
        boolean inlineRayQueryActive = inlineRayQueryRequested && rtTraversalSupported;
        boolean dedicatedRaygenActive = dedicatedRaygenRequested && rtBvhSupported;

        expectedFeaturesLastFrame = expectedFeatureList();
        activeFeaturesLastFrame = activeFeatureList(
                rtAoActive,
                rtTranslucencyCausticsActive,
                bvhCompactionActive,
                denoiserFrameworkActive,
                hybridCompositionActive,
                qualityTiersActive,
                inlineRayQueryActive,
                dedicatedRaygenActive
        );
        prunedFeaturesLastFrame = diff(expectedFeaturesLastFrame, activeFeaturesLastFrame);

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
                "RT capability mode active (expected=[" + String.join(", ", expectedFeaturesLastFrame)
                        + "], active=[" + String.join(", ", activeFeaturesLastFrame)
                        + "], pruned=[" + String.join(", ", prunedFeaturesLastFrame) + "])"
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
                promotionReadyLastFrame
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

    private static List<String> activeFeatureList(
            boolean ao,
            boolean translucencyCaustics,
            boolean bvhCompaction,
            boolean denoiserFramework,
            boolean hybrid,
            boolean qualityTiers,
            boolean rayQuery,
            boolean dedicatedRaygen
    ) {
        List<String> out = new ArrayList<>();
        if (ao) out.add("vulkan.rt.ao");
        if (translucencyCaustics) out.add("vulkan.rt.translucency_caustics");
        if (bvhCompaction) out.add("vulkan.rt.bvh_compaction");
        if (denoiserFramework) out.add("vulkan.rt.denoiser_framework");
        if (hybrid) out.add("vulkan.rt.hybrid_composition");
        if (qualityTiers) out.add("vulkan.rt.quality_tiers");
        if (rayQuery) out.add("vulkan.rt.inline_ray_query");
        if (dedicatedRaygen) out.add("vulkan.rt.dedicated_raygen");
        return List.copyOf(out);
    }

    private static List<String> diff(List<String> expected, List<String> active) {
        if (expected == null || expected.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String f : expected) {
            if (!active.contains(f)) {
                out.add(f);
            }
        }
        return List.copyOf(out);
    }
}

