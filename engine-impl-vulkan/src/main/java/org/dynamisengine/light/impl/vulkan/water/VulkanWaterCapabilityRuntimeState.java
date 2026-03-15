package org.dynamisengine.light.impl.vulkan.water;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.event.EngineWarning;
import org.dynamisengine.light.api.runtime.WaterCapabilityDiagnostics;
import org.dynamisengine.light.api.runtime.WaterPromotionDiagnostics;
import org.dynamisengine.light.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;

/**
 * Runtime water/ocean capability and promotion diagnostics (Phase 1 scaffold).
 */
public final class VulkanWaterCapabilityRuntimeState {
    private boolean flatWaterRequested;
    private boolean waveSimulationRequested;
    private boolean foamRequested;
    private boolean underwaterRequested;
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
        flatWaterRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.water.flatWaterEnabled", "false"));
        waveSimulationRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.water.waveSimulationEnabled", "false"));
        foamRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.water.foamEnabled", "false"));
        underwaterRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.water.underwaterEnabled", "false"));
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.water.warnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.water.warnCooldownFrames", warnCooldownFrames, 0, 100_000);
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.water.promotionReadyMinFrames", promotionReadyMinFrames, 1, 100_000);
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions != null && backendOptions.containsKey("vulkan.water.promotionReadyMinFrames")) {
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

    public void emitFrameWarnings(List<EngineWarning> warnings) {
        // Phase 1: capability/promotion contract only; execution paths are scaffolded inactive.
        boolean flatWaterActive = false;
        boolean waveSimulationActive = false;
        boolean foamActive = false;
        boolean underwaterActive = false;

        expectedFeaturesLastFrame = expectedFeatureList(
                flatWaterRequested, waveSimulationRequested, foamRequested, underwaterRequested);
        activeFeaturesLastFrame = activeFeatureList(
                flatWaterActive, waveSimulationActive, foamActive, underwaterActive);
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
                "WATER_CAPABILITY_MODE_ACTIVE",
                "Water capability mode active (active=[" + String.join(", ", activeFeaturesLastFrame)
                        + "], pruned=[" + String.join(", ", prunedFeaturesLastFrame) + "])"
        ));
        warnings.add(new EngineWarning(
                "WATER_POLICY_ACTIVE",
                "Water policy (flatWaterExpected=" + flatWaterRequested
                        + ", waveSimulationExpected=" + waveSimulationRequested
                        + ", foamExpected=" + foamRequested
                        + ", underwaterExpected=" + underwaterRequested + ")"
        ));
        warnings.add(new EngineWarning(
                "WATER_PROMOTION_ENVELOPE",
                "Water promotion envelope (risk=" + risk
                        + ", expectedCount=" + expectedFeaturesLastFrame.size()
                        + ", activeCount=" + activeFeaturesLastFrame.size()
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (envelopeBreachedLastFrame && warnCooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "WATER_PROMOTION_ENVELOPE_BREACH",
                    "Water promotion envelope breach (highStreak=" + highStreak
                            + ", cooldown=" + warnCooldownRemaining + ")"
            ));
            warnCooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "WATER_PROMOTION_READY",
                    "Water promotion-ready envelope satisfied (stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public WaterCapabilityDiagnostics diagnostics() {
        return new WaterCapabilityDiagnostics(
                true,
                expectedFeaturesLastFrame.contains("vulkan.water.flat_water"),
                activeFeaturesLastFrame.contains("vulkan.water.flat_water"),
                expectedFeaturesLastFrame.contains("vulkan.water.wave_simulation"),
                activeFeaturesLastFrame.contains("vulkan.water.wave_simulation"),
                expectedFeaturesLastFrame.contains("vulkan.water.foam"),
                activeFeaturesLastFrame.contains("vulkan.water.foam"),
                expectedFeaturesLastFrame.contains("vulkan.water.underwater"),
                activeFeaturesLastFrame.contains("vulkan.water.underwater"),
                expectedFeaturesLastFrame,
                activeFeaturesLastFrame,
                prunedFeaturesLastFrame
        );
    }

    public WaterPromotionDiagnostics promotionDiagnostics() {
        return new WaterPromotionDiagnostics(
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

    private static List<String> expectedFeatureList(boolean flat, boolean wave, boolean foam, boolean underwater) {
        List<String> out = new ArrayList<>();
        if (flat) out.add("vulkan.water.flat_water");
        if (wave) out.add("vulkan.water.wave_simulation");
        if (foam) out.add("vulkan.water.foam");
        if (underwater) out.add("vulkan.water.underwater");
        return List.copyOf(out);
    }

    private static List<String> activeFeatureList(boolean flat, boolean wave, boolean foam, boolean underwater) {
        List<String> out = new ArrayList<>();
        if (flat) out.add("vulkan.water.flat_water");
        if (wave) out.add("vulkan.water.wave_simulation");
        if (foam) out.add("vulkan.water.foam");
        if (underwater) out.add("vulkan.water.underwater");
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
