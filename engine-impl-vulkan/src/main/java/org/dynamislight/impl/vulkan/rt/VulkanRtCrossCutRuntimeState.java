package org.dynamislight.impl.vulkan.rt;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.GiPromotionDiagnostics;
import org.dynamislight.api.runtime.RtCrossCutDiagnostics;
import org.dynamislight.api.runtime.ShadowRtDiagnostics;
import org.dynamislight.impl.vulkan.reflection.ReflectionRtPathDiagnostics;
import org.dynamislight.impl.vulkan.reflection.ReflectionRtPromotionDiagnostics;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;

/**
 * Runtime holder for RT cross-cut promotion diagnostics.
 */
public final class VulkanRtCrossCutRuntimeState {
    private int warnMinFrames = 2;
    private int warnCooldownFrames = 120;
    private int promotionReadyMinFrames = 4;
    private boolean requireDomainPromotionReady = true;
    private int stableStreak;
    private int highStreak;
    private int warnCooldownRemaining;
    private boolean envelopeBreachedLastFrame;
    private boolean promotionReadyLastFrame;
    private boolean shadowRtExpectedLastFrame;
    private boolean shadowRtActiveLastFrame;
    private boolean reflectionRtExpectedLastFrame;
    private boolean reflectionRtActiveLastFrame;
    private boolean reflectionRtFallbackActiveLastFrame;
    private boolean reflectionRtPromotionReadyLastFrame;
    private boolean giRtExpectedLastFrame;
    private boolean giRtActiveLastFrame;
    private boolean giRtFallbackActiveLastFrame;
    private boolean giRtPromotionReadyLastFrame;
    private boolean allExpectedRtDomainsActiveLastFrame;

    public void reset() {
        stableStreak = 0;
        highStreak = 0;
        warnCooldownRemaining = 0;
        envelopeBreachedLastFrame = false;
        promotionReadyLastFrame = false;
        shadowRtExpectedLastFrame = false;
        shadowRtActiveLastFrame = false;
        reflectionRtExpectedLastFrame = false;
        reflectionRtActiveLastFrame = false;
        reflectionRtFallbackActiveLastFrame = false;
        reflectionRtPromotionReadyLastFrame = false;
        giRtExpectedLastFrame = false;
        giRtActiveLastFrame = false;
        giRtFallbackActiveLastFrame = false;
        giRtPromotionReadyLastFrame = false;
        allExpectedRtDomainsActiveLastFrame = false;
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.crossCutWarnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.crossCutWarnCooldownFrames", warnCooldownFrames, 0, 100_000);
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.rt.crossCutPromotionReadyMinFrames", promotionReadyMinFrames, 1, 100_000);
        requireDomainPromotionReady = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.rt.crossCutRequireDomainPromotionReady",
                        Boolean.toString(requireDomainPromotionReady)));
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions == null || !backendOptions.containsKey("vulkan.rt.crossCutPromotionReadyMinFrames")) {
            QualityTier safeTier = tier == null ? QualityTier.MEDIUM : tier;
            promotionReadyMinFrames = switch (safeTier) {
                case LOW -> 2;
                case MEDIUM -> 3;
                case HIGH -> 4;
                case ULTRA -> 5;
            };
        }
    }

    public void emitFrameWarnings(
            ShadowRtDiagnostics shadowRt,
            ReflectionRtPathDiagnostics reflectionRtPath,
            ReflectionRtPromotionDiagnostics reflectionRtPromotion,
            GiPromotionDiagnostics giPromotion,
            List<EngineWarning> warnings
    ) {
        ShadowRtDiagnostics safeShadow = shadowRt == null ? ShadowRtDiagnostics.unavailable() : shadowRt;
        ReflectionRtPathDiagnostics safeReflectionPath = reflectionRtPath == null
                ? new ReflectionRtPathDiagnostics(false, false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, 0.0, "")
                : reflectionRtPath;
        ReflectionRtPromotionDiagnostics safeReflectionPromotion = reflectionRtPromotion == null
                ? new ReflectionRtPromotionDiagnostics(false, 0, 1, false, false, false, false, false, "")
                : reflectionRtPromotion;
        GiPromotionDiagnostics safeGi = giPromotion == null ? GiPromotionDiagnostics.unavailable() : giPromotion;

        shadowRtExpectedLastFrame = safeShadow.available()
                && safeShadow.mode() != null
                && safeShadow.mode().toLowerCase(java.util.Locale.ROOT).contains("rt");
        shadowRtActiveLastFrame = safeShadow.available() && safeShadow.active();

        reflectionRtExpectedLastFrame = safeReflectionPath.laneRequested();
        reflectionRtActiveLastFrame = safeReflectionPath.laneActive();
        reflectionRtFallbackActiveLastFrame = safeReflectionPath.fallbackChain() != null
                && !safeReflectionPath.fallbackChain().isBlank()
                && !safeReflectionPath.fallbackChain().toLowerCase(java.util.Locale.ROOT).startsWith("rt");
        reflectionRtPromotionReadyLastFrame = safeReflectionPromotion.readyLastFrame();

        giRtExpectedLastFrame = safeGi.available() && safeGi.rtDetailExpected();
        giRtActiveLastFrame = safeGi.available() && safeGi.rtDetailActive();
        giRtFallbackActiveLastFrame = safeGi.available() && safeGi.rtFallbackActive();
        giRtPromotionReadyLastFrame = safeGi.available() && safeGi.rtDetailPromotionReady();

        boolean shadowRisk = shadowRtExpectedLastFrame && !shadowRtActiveLastFrame;
        boolean reflectionRisk = reflectionRtExpectedLastFrame && !reflectionRtActiveLastFrame;
        boolean giRisk = giRtExpectedLastFrame && !giRtActiveLastFrame;
        boolean risk = shadowRisk || reflectionRisk || giRisk;
        allExpectedRtDomainsActiveLastFrame = (!shadowRtExpectedLastFrame || shadowRtActiveLastFrame)
                && (!reflectionRtExpectedLastFrame || reflectionRtActiveLastFrame)
                && (!giRtExpectedLastFrame || giRtActiveLastFrame);

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
        boolean shadowPromotionReady = !shadowRtExpectedLastFrame
                || (shadowRtActiveLastFrame && !safeShadow.envelopeBreachedLastFrame());
        boolean reflectionPromotionReady = !reflectionRtExpectedLastFrame
                || (!requireDomainPromotionReady || (reflectionRtActiveLastFrame && reflectionRtPromotionReadyLastFrame));
        boolean giPromotionReady = !giRtExpectedLastFrame
                || (!requireDomainPromotionReady || (giRtActiveLastFrame && giRtPromotionReadyLastFrame));
        promotionReadyLastFrame = allExpectedRtDomainsActiveLastFrame
                && !envelopeBreachedLastFrame
                && shadowPromotionReady
                && reflectionPromotionReady
                && giPromotionReady
                && stableStreak >= promotionReadyMinFrames;

        warnings.add(new EngineWarning(
                "RT_CROSSCUT_POLICY_ACTIVE",
                "RT cross-cut policy (shadowExpected=" + shadowRtExpectedLastFrame
                        + ", reflectionExpected=" + reflectionRtExpectedLastFrame
                        + ", giExpected=" + giRtExpectedLastFrame
                        + ", requireDomainPromotionReady=" + requireDomainPromotionReady + ")"
        ));
        warnings.add(new EngineWarning(
                "RT_CROSSCUT_ENVELOPE",
                "RT cross-cut envelope (risk=" + risk
                        + ", shadowRisk=" + shadowRisk
                        + ", reflectionRisk=" + reflectionRisk
                        + ", giRisk=" + giRisk
                        + ", allExpectedActive=" + allExpectedRtDomainsActiveLastFrame
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (envelopeBreachedLastFrame && warnCooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "RT_CROSSCUT_ENVELOPE_BREACH",
                    "RT cross-cut envelope breach (highStreak=" + highStreak
                            + ", cooldown=" + warnCooldownRemaining + ")"
            ));
            warnCooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "RT_CROSSCUT_PROMOTION_READY",
                    "RT cross-cut promotion-ready envelope satisfied (stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public RtCrossCutDiagnostics diagnostics() {
        return new RtCrossCutDiagnostics(
                true,
                shadowRtExpectedLastFrame,
                shadowRtActiveLastFrame,
                reflectionRtExpectedLastFrame,
                reflectionRtActiveLastFrame,
                reflectionRtFallbackActiveLastFrame,
                reflectionRtPromotionReadyLastFrame,
                giRtExpectedLastFrame,
                giRtActiveLastFrame,
                giRtFallbackActiveLastFrame,
                giRtPromotionReadyLastFrame,
                allExpectedRtDomainsActiveLastFrame,
                envelopeBreachedLastFrame,
                promotionReadyLastFrame,
                stableStreak,
                highStreak,
                warnCooldownRemaining,
                warnMinFrames,
                warnCooldownFrames,
                promotionReadyMinFrames
        );
    }
}
