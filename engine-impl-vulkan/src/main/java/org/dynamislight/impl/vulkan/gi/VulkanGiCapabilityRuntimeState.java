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
    private double rtDetailWarnMinActiveRatio = 1.0;
    private int rtDetailWarnMinFrames = 2;
    private int rtDetailWarnCooldownFrames = 120;
    private int rtDetailPromotionReadyMinFrames = 4;
    private int hybridWarnMinFrames = 2;
    private int hybridWarnCooldownFrames = 120;
    private int stableStreak;
    private int ssgiStableStreak;
    private int probeGridStableStreak;
    private int rtDetailStableStreak;
    private boolean promotionReadyLastFrame;
    private boolean phase2PromotionReadyLastFrame;
    private boolean ssgiPromotionReadyLastFrame;
    private boolean probeGridPromotionReadyLastFrame;
    private boolean rtDetailPromotionReadyLastFrame;
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
    private boolean hybridExpectedLastFrame;
    private int hybridExpectedComponentCountLastFrame;
    private int hybridActiveComponentCountLastFrame;
    private boolean hybridEnvelopeBreachedLastFrame;
    private int hybridHighStreak;
    private int hybridWarnCooldownRemaining;
    private boolean rtDetailExpectedLastFrame;
    private boolean rtDetailActiveLastFrame;
    private double rtDetailActiveRatioLastFrame;
    private boolean rtDetailEnvelopeBreachedLastFrame;
    private int rtDetailHighStreak;
    private int rtDetailWarnCooldownRemaining;
    private String modeLastFrame = "ssgi";
    private boolean rtAvailableLastFrame;
    private List<String> activeCapabilitiesLastFrame = List.of();
    private List<String> prunedCapabilitiesLastFrame = List.of();

    public void reset() {
        stableStreak = 0;
        ssgiStableStreak = 0;
        probeGridStableStreak = 0;
        rtDetailStableStreak = 0;
        promotionReadyLastFrame = false;
        phase2PromotionReadyLastFrame = false;
        ssgiPromotionReadyLastFrame = false;
        probeGridPromotionReadyLastFrame = false;
        rtDetailPromotionReadyLastFrame = false;
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
        hybridExpectedLastFrame = false;
        hybridExpectedComponentCountLastFrame = 0;
        hybridActiveComponentCountLastFrame = 0;
        hybridEnvelopeBreachedLastFrame = false;
        hybridHighStreak = 0;
        hybridWarnCooldownRemaining = 0;
        rtDetailExpectedLastFrame = false;
        rtDetailActiveLastFrame = false;
        rtDetailActiveRatioLastFrame = 0.0;
        rtDetailEnvelopeBreachedLastFrame = false;
        rtDetailHighStreak = 0;
        rtDetailWarnCooldownRemaining = 0;
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
        rtDetailWarnMinActiveRatio = VulkanRuntimeOptionParsing.parseBackendDoubleOption(
                safe,
                "vulkan.gi.rtWarnMinActiveRatio",
                rtDetailWarnMinActiveRatio,
                0.0,
                1.0
        );
        rtDetailWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.rtWarnMinFrames",
                rtDetailWarnMinFrames,
                1,
                100000
        );
        rtDetailWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.rtWarnCooldownFrames",
                rtDetailWarnCooldownFrames,
                0,
                100000
        );
        rtDetailPromotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.rtPromotionReadyMinFrames",
                rtDetailPromotionReadyMinFrames,
                1,
                100000
        );
        hybridWarnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.hybridWarnMinFrames",
                hybridWarnMinFrames,
                1,
                100000
        );
        hybridWarnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.gi.hybridWarnCooldownFrames",
                hybridWarnCooldownFrames,
                0,
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
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.rtWarnMinFrames")) {
            rtDetailWarnMinFrames = switch (resolved) {
                case LOW -> 3;
                case MEDIUM -> 2;
                case HIGH, ULTRA -> 1;
            };
        }
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.rtWarnCooldownFrames")) {
            rtDetailWarnCooldownFrames = switch (resolved) {
                case LOW -> 180;
                case MEDIUM -> 120;
                case HIGH -> 90;
                case ULTRA -> 75;
            };
        }
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.rtPromotionReadyMinFrames")) {
            rtDetailPromotionReadyMinFrames = switch (resolved) {
                case LOW -> 6;
                case MEDIUM -> 5;
                case HIGH -> 4;
                case ULTRA -> 3;
            };
        }
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.hybridWarnMinFrames")) {
            hybridWarnMinFrames = switch (resolved) {
                case LOW -> 3;
                case MEDIUM -> 2;
                case HIGH, ULTRA -> 1;
            };
        }
        if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.hybridWarnCooldownFrames")) {
            hybridWarnCooldownFrames = switch (resolved) {
                case LOW -> 180;
                case MEDIUM -> 120;
                case HIGH -> 90;
                case ULTRA -> 75;
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
        rtDetailExpectedLastFrame = configuredEnabled
                && (configuredMode == GiMode.RTGI_SINGLE || configuredMode == GiMode.HYBRID_PROBE_SSGI_RT);
        rtDetailActiveLastFrame = activeCapabilitiesLastFrame.contains("vulkan.gi.rtgi_single")
                || activeCapabilitiesLastFrame.contains("vulkan.gi.rt_detail");
        rtDetailActiveRatioLastFrame = rtDetailActiveLastFrame ? 1.0 : 0.0;
        rtDetailEnvelopeBreachedLastFrame = rtDetailExpectedLastFrame
                && rtDetailActiveRatioLastFrame < rtDetailWarnMinActiveRatio;
        if (rtDetailEnvelopeBreachedLastFrame) {
            rtDetailHighStreak++;
            rtDetailStableStreak = 0;
        } else {
            rtDetailHighStreak = 0;
            if (rtDetailExpectedLastFrame) {
                rtDetailStableStreak++;
            } else {
                rtDetailStableStreak = 0;
            }
        }
        if (rtDetailWarnCooldownRemaining > 0) {
            rtDetailWarnCooldownRemaining--;
        }
        hybridExpectedLastFrame = configuredEnabled && configuredMode == GiMode.HYBRID_PROBE_SSGI_RT;
        hybridExpectedComponentCountLastFrame = hybridExpectedLastFrame ? 3 : 0;
        hybridActiveComponentCountLastFrame = (ssgiActiveLastFrame ? 1 : 0)
                + (probeGridActiveLastFrame ? 1 : 0)
                + (rtDetailActiveLastFrame ? 1 : 0);
        hybridEnvelopeBreachedLastFrame = hybridExpectedLastFrame
                && hybridActiveComponentCountLastFrame < hybridExpectedComponentCountLastFrame;
        if (hybridEnvelopeBreachedLastFrame) {
            hybridHighStreak++;
        } else {
            hybridHighStreak = 0;
        }
        if (hybridWarnCooldownRemaining > 0) {
            hybridWarnCooldownRemaining--;
        }

        boolean stableThisFrame = configuredEnabled && !activeCapabilitiesLastFrame.isEmpty();
        stableStreak = stableThisFrame ? stableStreak + 1 : 0;
        promotionReadyLastFrame = stableThisFrame && stableStreak >= promotionReadyMinFrames;
        ssgiPromotionReadyLastFrame = ssgiExpectedLastFrame
                && !ssgiEnvelopeBreachedLastFrame
                && ssgiStableStreak >= ssgiPromotionReadyMinFrames;
        probeGridPromotionReadyLastFrame = probeGridExpectedLastFrame
                && !probeGridEnvelopeBreachedLastFrame
                && probeGridStableStreak >= probeGridPromotionReadyMinFrames;
        rtDetailPromotionReadyLastFrame = rtDetailExpectedLastFrame
                && !rtDetailEnvelopeBreachedLastFrame
                && rtDetailStableStreak >= rtDetailPromotionReadyMinFrames;
        phase2PromotionReadyLastFrame = promotionReadyLastFrame
                && (!ssgiExpectedLastFrame || ssgiPromotionReadyLastFrame)
                && (!probeGridExpectedLastFrame || probeGridPromotionReadyLastFrame)
                && (!rtDetailExpectedLastFrame || rtDetailPromotionReadyLastFrame);

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
            warnings.add(new EngineWarning(
                    "GI_RT_DETAIL_POLICY_ACTIVE",
                    "GI RT-detail policy active (mode=" + modeLastFrame
                            + ", rtDetailActive=" + rtDetailActiveLastFrame
                            + ", rtDetailExpected=" + rtDetailExpectedLastFrame
                            + ", rtDetailActiveRatio=" + rtDetailActiveRatioLastFrame
                            + ", rtDetailWarnMinActiveRatio=" + rtDetailWarnMinActiveRatio
                            + ", rtDetailWarnMinFrames=" + rtDetailWarnMinFrames
                            + ", rtDetailWarnCooldownFrames=" + rtDetailWarnCooldownFrames
                            + ", rtDetailWarnCooldownRemaining=" + rtDetailWarnCooldownRemaining
                            + ", rtDetailStableStreak=" + rtDetailStableStreak
                            + ", rtDetailPromotionReadyMinFrames=" + rtDetailPromotionReadyMinFrames + ")"
            ));
            boolean emitSsgiBreach = ssgiEnvelopeBreachedLastFrame
                    && ssgiHighStreak >= ssgiWarnMinFrames
                    && ssgiWarnCooldownRemaining <= 0;
            boolean emitProbeGridBreach = probeGridEnvelopeBreachedLastFrame
                    && probeGridHighStreak >= probeGridWarnMinFrames
                    && probeGridWarnCooldownRemaining <= 0;
            boolean emitRtDetailBreach = rtDetailEnvelopeBreachedLastFrame
                    && rtDetailHighStreak >= rtDetailWarnMinFrames
                    && rtDetailWarnCooldownRemaining <= 0;
            boolean emitHybridBreach = hybridEnvelopeBreachedLastFrame
                    && hybridHighStreak >= hybridWarnMinFrames
                    && hybridWarnCooldownRemaining <= 0;
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
            warnings.add(new EngineWarning(
                    "GI_RT_DETAIL_ENVELOPE",
                    "GI RT-detail envelope (mode=" + modeLastFrame
                            + ", expected=" + rtDetailExpectedLastFrame
                            + ", active=" + rtDetailActiveLastFrame
                            + ", activeRatio=" + rtDetailActiveRatioLastFrame
                            + ", warnMinActiveRatio=" + rtDetailWarnMinActiveRatio
                            + ", breached=" + rtDetailEnvelopeBreachedLastFrame
                            + ", highStreak=" + rtDetailHighStreak
                            + ", warnMinFrames=" + rtDetailWarnMinFrames
                            + ", cooldownRemaining=" + rtDetailWarnCooldownRemaining + ")"
            ));
            warnings.add(new EngineWarning(
                    "GI_HYBRID_COMPOSITION",
                    "GI hybrid composition (mode=" + modeLastFrame
                            + ", expected=" + hybridExpectedLastFrame
                            + ", expectedComponentCount=" + hybridExpectedComponentCountLastFrame
                            + ", activeComponentCount=" + hybridActiveComponentCountLastFrame
                            + ", breached=" + hybridEnvelopeBreachedLastFrame
                            + ", highStreak=" + hybridHighStreak
                            + ", warnMinFrames=" + hybridWarnMinFrames
                            + ", cooldownRemaining=" + hybridWarnCooldownRemaining + ")"
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
            if (emitRtDetailBreach) {
                rtDetailWarnCooldownRemaining = rtDetailWarnCooldownFrames;
                warnings.add(new EngineWarning(
                        "GI_RT_DETAIL_ENVELOPE_BREACH",
                        "GI RT-detail envelope breach (mode=" + modeLastFrame
                                + ", expected=" + rtDetailExpectedLastFrame
                                + ", activeRatio=" + rtDetailActiveRatioLastFrame
                                + ", warnMinActiveRatio=" + rtDetailWarnMinActiveRatio
                                + ", highStreak=" + rtDetailHighStreak
                                + ", cooldownFrames=" + rtDetailWarnCooldownFrames + ")"
                ));
            }
            if (emitHybridBreach) {
                hybridWarnCooldownRemaining = hybridWarnCooldownFrames;
                warnings.add(new EngineWarning(
                        "GI_HYBRID_COMPOSITION_BREACH",
                        "GI hybrid composition breach (mode=" + modeLastFrame
                                + ", expectedComponentCount=" + hybridExpectedComponentCountLastFrame
                                + ", activeComponentCount=" + hybridActiveComponentCountLastFrame
                                + ", highStreak=" + hybridHighStreak
                                + ", warnMinFrames=" + hybridWarnMinFrames
                                + ", cooldownFrames=" + hybridWarnCooldownFrames + ")"
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
            if (rtDetailPromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "GI_RT_DETAIL_PROMOTION_READY",
                        "GI RT-detail promotion ready (mode=" + modeLastFrame
                                + ", stableStreak=" + rtDetailStableStreak
                                + ", minFrames=" + rtDetailPromotionReadyMinFrames + ")"
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
            if (phase2PromotionReadyLastFrame) {
                warnings.add(new EngineWarning(
                        "GI_PHASE2_PROMOTION_READY",
                        "GI phase2 promotion ready (mode=" + modeLastFrame
                                + ", promotionReady=" + promotionReadyLastFrame
                                + ", ssgiExpected=" + ssgiExpectedLastFrame
                                + ", ssgiReady=" + ssgiPromotionReadyLastFrame
                                + ", probeExpected=" + probeGridExpectedLastFrame
                                + ", probeReady=" + probeGridPromotionReadyLastFrame
                                + ", rtExpected=" + rtDetailExpectedLastFrame
                                + ", rtReady=" + rtDetailPromotionReadyLastFrame + ")"
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
                rtDetailExpectedLastFrame,
                rtDetailActiveRatioLastFrame,
                rtDetailWarnMinActiveRatio,
                rtDetailWarnMinFrames,
                rtDetailWarnCooldownFrames,
                rtDetailWarnCooldownRemaining,
                rtDetailEnvelopeBreachedLastFrame,
                stableStreak,
                promotionReadyMinFrames,
                promotionReadyLastFrame,
                phase2PromotionReadyLastFrame,
                ssgiStableStreak,
                ssgiPromotionReadyMinFrames,
                ssgiPromotionReadyLastFrame,
                rtDetailStableStreak,
                rtDetailPromotionReadyMinFrames,
                rtDetailPromotionReadyLastFrame,
                probeGridStableStreak,
                probeGridPromotionReadyMinFrames,
                probeGridPromotionReadyLastFrame
        );
    }
}
