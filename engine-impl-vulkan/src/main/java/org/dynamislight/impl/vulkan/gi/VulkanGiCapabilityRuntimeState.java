package org.dynamislight.impl.vulkan.gi;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.GiCapabilityDiagnostics;
import org.dynamislight.api.runtime.GiPromotionDiagnostics;
import org.dynamislight.impl.vulkan.capability.VulkanGiCapabilityPlan;
import org.dynamislight.impl.vulkan.runtime.config.GiMode;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;
import org.dynamislight.impl.vulkan.warning.gi.VulkanGiWarningEmitter;

/**
 * Runtime GI capability diagnostics/promotion state.
 */
public final class VulkanGiCapabilityRuntimeState {
    private GiMode configuredMode = GiMode.SSGI;
    private boolean configuredEnabled;
    private int promotionReadyMinFrames = 4;
    private double ssgiWarnMinActiveRatio = 1.0;
    private int ssgiWarnMinFrames = 2;
    private int ssgiWarnCooldownFrames = 120;
    private int ssgiPromotionReadyMinFrames = 4;
    private int stableStreak;
    private int ssgiStableStreak;
    private boolean promotionReadyLastFrame;
    private boolean ssgiPromotionReadyLastFrame;
    private boolean rtFallbackActiveLastFrame;
    private boolean ssgiActiveLastFrame;
    private boolean ssgiExpectedLastFrame;
    private double ssgiActiveRatioLastFrame;
    private boolean ssgiEnvelopeBreachedLastFrame;
    private int ssgiHighStreak;
    private int ssgiWarnCooldownRemaining;
    private boolean probeGridActiveLastFrame;
    private boolean rtDetailActiveLastFrame;
    private String modeLastFrame = "ssgi";
    private boolean rtAvailableLastFrame;
    private List<String> activeCapabilitiesLastFrame = List.of();
    private List<String> prunedCapabilitiesLastFrame = List.of();

    public void reset() {
        stableStreak = 0;
        ssgiStableStreak = 0;
        promotionReadyLastFrame = false;
        ssgiPromotionReadyLastFrame = false;
        rtFallbackActiveLastFrame = false;
        ssgiActiveLastFrame = false;
        ssgiExpectedLastFrame = false;
        ssgiActiveRatioLastFrame = 0.0;
        ssgiEnvelopeBreachedLastFrame = false;
        ssgiHighStreak = 0;
        ssgiWarnCooldownRemaining = 0;
        probeGridActiveLastFrame = false;
        rtDetailActiveLastFrame = false;
        modeLastFrame = configuredMode.name().toLowerCase(java.util.Locale.ROOT);
        rtAvailableLastFrame = false;
        activeCapabilitiesLastFrame = List.of();
        prunedCapabilitiesLastFrame = List.of();
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        configuredMode = VulkanRuntimeOptionParsing.parseGiMode(safe.get("vulkan.gi.mode"));
        configuredEnabled = Boolean.parseBoolean(safe.getOrDefault("vulkan.gi.enabled", "false"));
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.promotionReadyMinFrames",
                promotionReadyMinFrames,
                1,
                100000
        );
        ssgiWarnMinActiveRatio = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe,
                "vulkan.gi.ssgiWarnMinActiveRatio",
                ssgiWarnMinActiveRatio,
                0.0,
                1.0
        );
        ssgiWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.ssgiWarnMinFrames",
                ssgiWarnMinFrames,
                1,
                100000
        );
        ssgiWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.ssgiWarnCooldownFrames",
                ssgiWarnCooldownFrames,
                0,
                100000
        );
        ssgiPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.ssgiPromotionReadyMinFrames",
                ssgiPromotionReadyMinFrames,
                1,
                100000
        );
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        QualityTier resolved = tier == null ? QualityTier.MEDIUM : tier;
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.promotionReadyMinFrames")) {
            promotionReadyMinFrames = switch (resolved) {
                case LOW -> 6;
                case MEDIUM -> 5;
                case HIGH -> 4;
                case ULTRA -> 3;
            };
        }
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.ssgiWarnMinFrames")) {
            ssgiWarnMinFrames = switch (resolved) {
                case LOW -> 3;
                case MEDIUM -> 2;
                case HIGH, ULTRA -> 1;
            };
        }
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.ssgiWarnCooldownFrames")) {
            ssgiWarnCooldownFrames = switch (resolved) {
                case LOW -> 180;
                case MEDIUM -> 120;
                case HIGH -> 90;
                case ULTRA -> 75;
            };
        }
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.ssgiPromotionReadyMinFrames")) {
            ssgiPromotionReadyMinFrames = switch (resolved) {
                case LOW -> 6;
                case MEDIUM -> 5;
                case HIGH -> 4;
                case ULTRA -> 3;
            };
        }
    }

    public void emitFrameWarnings(QualityTier qualityTier, boolean rtAvailable, List<EngineWarning> warnings) {
        VulkanGiWarningEmitter.Result emission = VulkanGiWarningEmitter.emit(
                qualityTier,
                configuredMode,
                configuredEnabled,
                rtAvailable
        );
        VulkanGiCapabilityPlan plan = emission.plan();
        modeLastFrame = plan.giModeId();
        rtAvailableLastFrame = plan.rtAvailable();
        activeCapabilitiesLastFrame = plan.activeCapabilities();
        prunedCapabilitiesLastFrame = plan.prunedCapabilities();
        rtFallbackActiveLastFrame = prunedCapabilitiesLastFrame.stream().anyMatch(s -> s.contains("rt"));
        ssgiActiveLastFrame = activeCapabilitiesLastFrame.contains("vulkan.gi.ssgi");
        ssgiExpectedLastFrame = configuredEnabled
                && (configuredMode == GiMode.SSGI
                || configuredMode == GiMode.HYBRID_PROBE_SSGI_RT
                || (configuredMode == GiMode.RTGI_SINGLE && rtFallbackActiveLastFrame));
        ssgiActiveRatioLastFrame = ssgiActiveLastFrame ? 1.0 : 0.0;
        ssgiEnvelopeBreachedLastFrame = ssgiExpectedLastFrame && ssgiActiveRatioLastFrame < ssgiWarnMinActiveRatio;
        if (ssgiEnvelopeBreachedLastFrame) {
            ssgiHighStreak++;
            ssgiStableStreak = 0;
        } else {
            ssgiHighStreak = 0;
            if (ssgiExpectedLastFrame) {
                ssgiStableStreak++;
            } else {
                ssgiStableStreak = 0;
            }
        }
        if (ssgiWarnCooldownRemaining > 0) {
            ssgiWarnCooldownRemaining--;
        }
        probeGridActiveLastFrame = activeCapabilitiesLastFrame.contains("vulkan.gi.probe_grid");
        rtDetailActiveLastFrame = activeCapabilitiesLastFrame.contains("vulkan.gi.rtgi_single")
                || activeCapabilitiesLastFrame.contains("vulkan.gi.rt_detail");

        boolean stableThisFrame = configuredEnabled && !activeCapabilitiesLastFrame.isEmpty();
        stableStreak = stableThisFrame ? stableStreak + 1 : 0;
        promotionReadyLastFrame = stableThisFrame && stableStreak >= promotionReadyMinFrames;
        ssgiPromotionReadyLastFrame = ssgiExpectedLastFrame
                && !ssgiEnvelopeBreachedLastFrame
                && ssgiStableStreak >= ssgiPromotionReadyMinFrames;

        if (warnings != null) {
            warnings.add(emission.warning());
            warnings.add(new EngineWarning(
                    "GI_PROMOTION_POLICY_ACTIVE",
                    "GI promotion policy active (mode=" + modeLastFrame
                            + ", enabled=" + configuredEnabled
                            + ", rtAvailable=" + rtAvailableLastFrame
                            + ", rtFallbackActive=" + rtFallbackActiveLastFrame
                            + ", ssgiActive=" + ssgiActiveLastFrame
                            + ", ssgiExpected=" + ssgiExpectedLastFrame
                            + ", ssgiActiveRatio=" + ssgiActiveRatioLastFrame
                            + ", probeGridActive=" + probeGridActiveLastFrame
                            + ", rtDetailActive=" + rtDetailActiveLastFrame
                            + ", stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
            warnings.add(new EngineWarning(
                    "GI_SSGI_POLICY_ACTIVE",
                    "GI SSGI policy active (mode=" + modeLastFrame
                            + ", ssgiActive=" + ssgiActiveLastFrame
                            + ", ssgiExpected=" + ssgiExpectedLastFrame
                            + ", ssgiActiveRatio=" + ssgiActiveRatioLastFrame
                            + ", ssgiWarnMinActiveRatio=" + ssgiWarnMinActiveRatio
                            + ", ssgiWarnMinFrames=" + ssgiWarnMinFrames
                            + ", ssgiWarnCooldownFrames=" + ssgiWarnCooldownFrames
                            + ", ssgiWarnCooldownRemaining=" + ssgiWarnCooldownRemaining
                            + ", ssgiStableStreak=" + ssgiStableStreak
                            + ", ssgiPromotionReadyMinFrames=" + ssgiPromotionReadyMinFrames
                            + ", probeGridActive=" + probeGridActiveLastFrame
                            + ", rtDetailActive=" + rtDetailActiveLastFrame + ")"
            ));
            boolean emitSsgiBreach = ssgiEnvelopeBreachedLastFrame
                    && ssgiHighStreak >= ssgiWarnMinFrames
                    && ssgiWarnCooldownRemaining <= 0;
            warnings.add(new EngineWarning(
                    "GI_SSGI_ENVELOPE",
                    "GI SSGI envelope (mode=" + modeLastFrame
                            + ", expected=" + ssgiExpectedLastFrame
                            + ", active=" + ssgiActiveLastFrame
                            + ", activeRatio=" + ssgiActiveRatioLastFrame
                            + ", warnMinActiveRatio=" + ssgiWarnMinActiveRatio
                            + ", breached=" + ssgiEnvelopeBreachedLastFrame
                            + ", highStreak=" + ssgiHighStreak
                            + ", warnMinFrames=" + ssgiWarnMinFrames
                            + ", cooldownRemaining=" + ssgiWarnCooldownRemaining + ")"
            ));
            if (emitSsgiBreach) {
                ssgiWarnCooldownRemaining = ssgiWarnCooldownFrames;
                warnings.add(new EngineWarning(
                        "GI_SSGI_ENVELOPE_BREACH",
                        "GI SSGI envelope breach (mode=" + modeLastFrame
                                + ", expected=" + ssgiExpectedLastFrame
                                + ", activeRatio=" + ssgiActiveRatioLastFrame
                                + ", warnMinActiveRatio=" + ssgiWarnMinActiveRatio
                                + ", highStreak=" + ssgiHighStreak
                                + ", cooldownFrames=" + ssgiWarnCooldownFrames + ")"
                ));
            }
            if (ssgiPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "GI_SSGI_PROMOTION_READY",
                        "GI SSGI promotion ready (mode=" + modeLastFrame
                                + ", stableStreak=" + ssgiStableStreak
                                + ", minFrames=" + ssgiPromotionReadyMinFrames + ")"
                ));
            }
            if (promotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "GI_PROMOTION_READY",
                        "GI promotion ready (mode=" + modeLastFrame
                                + ", stableStreak=" + stableStreak
                                + ", minFrames=" + promotionReadyMinFrames
                                + ", rtFallbackActive=" + rtFallbackActiveLastFrame + ")"
                ));
            }
        }
    }

    public GiCapabilityDiagnostics diagnostics() {
        return new GiCapabilityDiagnostics(
                !modeLastFrame.isBlank(),
                modeLastFrame,
                configuredEnabled,
                rtAvailableLastFrame,
                activeCapabilitiesLastFrame,
                prunedCapabilitiesLastFrame
        );
    }

    public GiPromotionDiagnostics promotionDiagnostics() {
        return new GiPromotionDiagnostics(
                !modeLastFrame.isBlank(),
                modeLastFrame,
                configuredEnabled,
                rtAvailableLastFrame,
                rtFallbackActiveLastFrame,
                ssgiActiveLastFrame,
                ssgiExpectedLastFrame,
                ssgiActiveRatioLastFrame,
                ssgiWarnMinActiveRatio,
                ssgiWarnMinFrames,
                ssgiWarnCooldownFrames,
                ssgiWarnCooldownRemaining,
                ssgiEnvelopeBreachedLastFrame,
                probeGridActiveLastFrame,
                rtDetailActiveLastFrame,
                stableStreak,
                promotionReadyMinFrames,
                promotionReadyLastFrame,
                ssgiStableStreak,
                ssgiPromotionReadyMinFrames,
                ssgiPromotionReadyLastFrame
        );
    }
}
