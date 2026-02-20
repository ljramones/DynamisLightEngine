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
    private int stableStreak;
    private boolean promotionReadyLastFrame;
    private boolean rtFallbackActiveLastFrame;
    private String modeLastFrame = "ssgi";
    private boolean rtAvailableLastFrame;
    private List<String> activeCapabilitiesLastFrame = List.of();
    private List<String> prunedCapabilitiesLastFrame = List.of();

    public void reset() {
        stableStreak = 0;
        promotionReadyLastFrame = false;
        rtFallbackActiveLastFrame = false;
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
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        if (VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.gi.promotionReadyMinFrames")) {
            return;
        }
        QualityTier resolved = tier == null ? QualityTier.MEDIUM : tier;
        promotionReadyMinFrames = switch (resolved) {
            case LOW -> 6;
            case MEDIUM -> 5;
            case HIGH -> 4;
            case ULTRA -> 3;
        };
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

        boolean stableThisFrame = configuredEnabled && !activeCapabilitiesLastFrame.isEmpty();
        stableStreak = stableThisFrame ? stableStreak + 1 : 0;
        promotionReadyLastFrame = stableThisFrame && stableStreak >= promotionReadyMinFrames;

        if (warnings != null) {
            warnings.add(emission.warning());
            warnings.add(new EngineWarning(
                    "GI_PROMOTION_POLICY_ACTIVE",
                    "GI promotion policy active (mode=" + modeLastFrame
                            + ", enabled=" + configuredEnabled
                            + ", rtAvailable=" + rtAvailableLastFrame
                            + ", rtFallbackActive=" + rtFallbackActiveLastFrame
                            + ", stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
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
                stableStreak,
                promotionReadyMinFrames,
                promotionReadyLastFrame
        );
    }
}
