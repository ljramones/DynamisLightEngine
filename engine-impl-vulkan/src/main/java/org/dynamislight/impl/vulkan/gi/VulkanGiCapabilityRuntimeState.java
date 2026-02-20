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
    private double probeGridWarnMinActiveRatio = 1.0;
    private int probeGridWarnMinFrames = 2;
    private int probeGridWarnCooldownFrames = 120;
    private int probeGridPromotionReadyMinFrames = 4;
    private int stableStreak;
    private int ssgiStableStreak;
    private int probeGridStableStreak;
    private boolean promotionReadyLastFrame;
    private boolean ssgiPromotionReadyLastFrame;
    private boolean probeGridPromotionReadyLastFrame;
    private boolean rtFallbackActiveLastFrame;
    private boolean ssgiActiveLastFrame;
    private boolean ssgiExpectedLastFrame;
    private double ssgiActiveRatioLastFrame;
    private boolean ssgiEnvelopeBreachedLastFrame;
    private int ssgiHighStreak;
    private int ssgiWarnCooldownRemaining;
    private boolean probeGridExpectedLastFrame;
    private boolean probeGridActiveLastFrame;
    private double probeGridActiveRatioLastFrame;
    private boolean probeGridEnvelopeBreachedLastFrame;
    private int probeGridHighStreak;
    private int probeGridWarnCooldownRemaining;
    private boolean rtDetailActiveLastFrame;
    private String modeLastFrame = "ssgi";
    private boolean rtAvailableLastFrame;
    private List<String> activeCapabilitiesLastFrame = List.of();
    private List<String> prunedCapabilitiesLastFrame = List.of();

    public void reset() {
        stableStreak = 0;
        ssgiStableStreak = 0;
        probeGridStableStreak = 0;
        promotionReadyLastFrame = false;
        ssgiPromotionReadyLastFrame = false;
        probeGridPromotionReadyLastFrame = false;
        rtFallbackActiveLastFrame = false;
        ssgiActiveLastFrame = false;
        ssgiExpectedLastFrame = false;
        ssgiActiveRatioLastFrame = 0.0;
        ssgiEnvelopeBreachedLastFrame = false;
        ssgiHighStreak = 0;
        ssgiWarnCooldownRemaining = 0;
        probeGridExpectedLastFrame = false;
        probeGridActiveLastFrame = false;
        probeGridActiveRatioLastFrame = 0.0;
        probeGridEnvelopeBreachedLastFrame = false;
        probeGridHighStreak = 0;
        probeGridWarnCooldownRemaining = 0;
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
        probeGridWarnMinActiveRatio = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe,
                "vulkan.gi.probeWarnMinActiveRatio",
                probeGridWarnMinActiveRatio,
                0.0,
                1.0
        );
        probeGridWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.probeWarnMinFrames",
                probeGridWarnMinFrames,
                1,
                100000
        );
        probeGridWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.probeWarnCooldownFrames",
                probeGridWarnCooldownFrames,
                0,
                100000
        );
        probeGridPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.probePromotionReadyMinFrames",
                probeGridPromotionReadyMinFrames,
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
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.probeWarnMinFrames")) {
            probeGridWarnMinFrames = switch (resolved) {
                case LOW -> 3;
                case MEDIUM -> 2;
                case HIGH, ULTRA -> 1;
            };
        }
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.probeWarnCooldownFrames")) {
            probeGridWarnCooldownFrames = switch (resolved) {
                case LOW -> 180;
                case MEDIUM -> 120;
                case HIGH -> 90;
                case ULTRA -> 75;
            };
        }
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.probePromotionReadyMinFrames")) {
            probeGridPromotionReadyMinFrames = switch (resolved) {
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
        probeGridExpectedLastFrame = configuredEnabled
                && (configuredMode == GiMode.PROBE_GRID || configuredMode == GiMode.HYBRID_PROBE_SSGI_RT);
        probeGridActiveLastFrame = activeCapabilitiesLastFrame.contains("vulkan.gi.probe_grid");
        probeGridActiveRatioLastFrame = probeGridActiveLastFrame ? 1.0 : 0.0;
        probeGridEnvelopeBreachedLastFrame = probeGridExpectedLastFrame
                && probeGridActiveRatioLastFrame < probeGridWarnMinActiveRatio;
        if (probeGridEnvelopeBreachedLastFrame) {
            probeGridHighStreak++;
            probeGridStableStreak = 0;
        } else {
            probeGridHighStreak = 0;
            if (probeGridExpectedLastFrame) {
                probeGridStableStreak++;
            } else {
                probeGridStableStreak = 0;
            }
        }
        if (probeGridWarnCooldownRemaining > 0) {
            probeGridWarnCooldownRemaining--;
        }
        rtDetailActiveLastFrame = activeCapabilitiesLastFrame.contains("vulkan.gi.rtgi_single")
                || activeCapabilitiesLastFrame.contains("vulkan.gi.rt_detail");

        boolean stableThisFrame = configuredEnabled && !activeCapabilitiesLastFrame.isEmpty();
        stableStreak = stableThisFrame ? stableStreak + 1 : 0;
        promotionReadyLastFrame = stableThisFrame && stableStreak >= promotionReadyMinFrames;
        ssgiPromotionReadyLastFrame = ssgiExpectedLastFrame
                && !ssgiEnvelopeBreachedLastFrame
                && ssgiStableStreak >= ssgiPromotionReadyMinFrames;
        probeGridPromotionReadyLastFrame = probeGridExpectedLastFrame
                && !probeGridEnvelopeBreachedLastFrame
                && probeGridStableStreak >= probeGridPromotionReadyMinFrames;

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
            warnings.add(new EngineWarning(
                    "GI_PROBE_GRID_POLICY_ACTIVE",
                    "GI probe-grid policy active (mode=" + modeLastFrame
                            + ", probeGridActive=" + probeGridActiveLastFrame
                            + ", probeGridExpected=" + probeGridExpectedLastFrame
                            + ", probeGridActiveRatio=" + probeGridActiveRatioLastFrame
                            + ", probeGridWarnMinActiveRatio=" + probeGridWarnMinActiveRatio
                            + ", probeGridWarnMinFrames=" + probeGridWarnMinFrames
                            + ", probeGridWarnCooldownFrames=" + probeGridWarnCooldownFrames
                            + ", probeGridWarnCooldownRemaining=" + probeGridWarnCooldownRemaining
                            + ", probeGridStableStreak=" + probeGridStableStreak
                            + ", probeGridPromotionReadyMinFrames=" + probeGridPromotionReadyMinFrames + ")"
            ));
            boolean emitSsgiBreach = ssgiEnvelopeBreachedLastFrame
                    && ssgiHighStreak >= ssgiWarnMinFrames
                    && ssgiWarnCooldownRemaining <= 0;
            boolean emitProbeGridBreach = probeGridEnvelopeBreachedLastFrame
                    && probeGridHighStreak >= probeGridWarnMinFrames
                    && probeGridWarnCooldownRemaining <= 0;
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
            warnings.add(new EngineWarning(
                    "GI_PROBE_GRID_ENVELOPE",
                    "GI probe-grid envelope (mode=" + modeLastFrame
                            + ", expected=" + probeGridExpectedLastFrame
                            + ", active=" + probeGridActiveLastFrame
                            + ", activeRatio=" + probeGridActiveRatioLastFrame
                            + ", warnMinActiveRatio=" + probeGridWarnMinActiveRatio
                            + ", breached=" + probeGridEnvelopeBreachedLastFrame
                            + ", highStreak=" + probeGridHighStreak
                            + ", warnMinFrames=" + probeGridWarnMinFrames
                            + ", cooldownRemaining=" + probeGridWarnCooldownRemaining + ")"
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
            if (emitProbeGridBreach) {
                probeGridWarnCooldownRemaining = probeGridWarnCooldownFrames;
                warnings.add(new EngineWarning(
                        "GI_PROBE_GRID_ENVELOPE_BREACH",
                        "GI probe-grid envelope breach (mode=" + modeLastFrame
                                + ", expected=" + probeGridExpectedLastFrame
                                + ", activeRatio=" + probeGridActiveRatioLastFrame
                                + ", warnMinActiveRatio=" + probeGridWarnMinActiveRatio
                                + ", highStreak=" + probeGridHighStreak
                                + ", cooldownFrames=" + probeGridWarnCooldownFrames + ")"
                ));
            }
            if (probeGridPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "GI_PROBE_GRID_PROMOTION_READY",
                        "GI probe-grid promotion ready (mode=" + modeLastFrame
                                + ", stableStreak=" + probeGridStableStreak
                                + ", minFrames=" + probeGridPromotionReadyMinFrames + ")"
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
                probeGridExpectedLastFrame,
                probeGridActiveRatioLastFrame,
                probeGridWarnMinActiveRatio,
                probeGridWarnMinFrames,
                probeGridWarnCooldownFrames,
                probeGridWarnCooldownRemaining,
                probeGridEnvelopeBreachedLastFrame,
                rtDetailActiveLastFrame,
                stableStreak,
                promotionReadyMinFrames,
                promotionReadyLastFrame,
                ssgiStableStreak,
                ssgiPromotionReadyMinFrames,
                ssgiPromotionReadyLastFrame,
                probeGridStableStreak,
                probeGridPromotionReadyMinFrames,
                probeGridPromotionReadyLastFrame
        );
    }
}
