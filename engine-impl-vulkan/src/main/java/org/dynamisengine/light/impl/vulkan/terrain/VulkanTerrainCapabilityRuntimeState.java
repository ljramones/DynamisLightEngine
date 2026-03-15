package org.dynamisengine.light.impl.vulkan.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.event.EngineWarning;
import org.dynamisengine.light.api.runtime.TerrainCapabilityDiagnostics;
import org.dynamisengine.light.api.runtime.TerrainPromotionDiagnostics;
import org.dynamisengine.light.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;

/**
 * Runtime terrain capability and promotion diagnostics (Phase 1 scaffold).
 */
public final class VulkanTerrainCapabilityRuntimeState {
    private boolean heightmapRequested;
    private boolean virtualTexturingRequested;
    private boolean splattingRequested;
    private boolean streamingRequested;
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
        heightmapRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.terrain.heightmapEnabled", "false"));
        virtualTexturingRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.terrain.virtualTexturingEnabled", "false"));
        splattingRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.terrain.splattingEnabled", "false"));
        streamingRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.terrain.streamingEnabled", "false"));
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.terrain.warnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.terrain.warnCooldownFrames", warnCooldownFrames, 0, 100_000);
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.terrain.promotionReadyMinFrames", promotionReadyMinFrames, 1, 100_000);
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions != null && backendOptions.containsKey("vulkan.terrain.promotionReadyMinFrames")) {
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
        // Phase 1: contract/promotion scaffold only.
        boolean heightmapActive = false;
        boolean virtualTexturingActive = false;
        boolean splattingActive = false;
        boolean streamingActive = false;

        expectedFeaturesLastFrame = expectedFeatureList(
                heightmapRequested, virtualTexturingRequested, splattingRequested, streamingRequested);
        activeFeaturesLastFrame = activeFeatureList(
                heightmapActive, virtualTexturingActive, splattingActive, streamingActive);
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
                "TERRAIN_CAPABILITY_MODE_ACTIVE",
                "Terrain capability mode active (active=[" + String.join(", ", activeFeaturesLastFrame)
                        + "], pruned=[" + String.join(", ", prunedFeaturesLastFrame) + "])"
        ));
        warnings.add(new EngineWarning(
                "TERRAIN_POLICY_ACTIVE",
                "Terrain policy (heightmapExpected=" + heightmapRequested
                        + ", virtualTexturingExpected=" + virtualTexturingRequested
                        + ", splattingExpected=" + splattingRequested
                        + ", streamingExpected=" + streamingRequested + ")"
        ));
        warnings.add(new EngineWarning(
                "TERRAIN_PROMOTION_ENVELOPE",
                "Terrain promotion envelope (risk=" + risk
                        + ", expectedCount=" + expectedFeaturesLastFrame.size()
                        + ", activeCount=" + activeFeaturesLastFrame.size()
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (envelopeBreachedLastFrame && warnCooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "TERRAIN_PROMOTION_ENVELOPE_BREACH",
                    "Terrain promotion envelope breach (highStreak=" + highStreak
                            + ", cooldown=" + warnCooldownRemaining + ")"
            ));
            warnCooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "TERRAIN_PROMOTION_READY",
                    "Terrain promotion-ready envelope satisfied (stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public TerrainCapabilityDiagnostics diagnostics() {
        return new TerrainCapabilityDiagnostics(
                true,
                expectedFeaturesLastFrame.contains("vulkan.terrain.heightmap"),
                activeFeaturesLastFrame.contains("vulkan.terrain.heightmap"),
                expectedFeaturesLastFrame.contains("vulkan.terrain.virtual_texturing"),
                activeFeaturesLastFrame.contains("vulkan.terrain.virtual_texturing"),
                expectedFeaturesLastFrame.contains("vulkan.terrain.splatting"),
                activeFeaturesLastFrame.contains("vulkan.terrain.splatting"),
                expectedFeaturesLastFrame.contains("vulkan.terrain.streaming"),
                activeFeaturesLastFrame.contains("vulkan.terrain.streaming"),
                expectedFeaturesLastFrame,
                activeFeaturesLastFrame,
                prunedFeaturesLastFrame
        );
    }

    public TerrainPromotionDiagnostics promotionDiagnostics() {
        return new TerrainPromotionDiagnostics(
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

    private static List<String> expectedFeatureList(boolean heightmap, boolean vt, boolean splatting, boolean streaming) {
        List<String> out = new ArrayList<>();
        if (heightmap) out.add("vulkan.terrain.heightmap");
        if (vt) out.add("vulkan.terrain.virtual_texturing");
        if (splatting) out.add("vulkan.terrain.splatting");
        if (streaming) out.add("vulkan.terrain.streaming");
        return List.copyOf(out);
    }

    private static List<String> activeFeatureList(boolean heightmap, boolean vt, boolean splatting, boolean streaming) {
        List<String> out = new ArrayList<>();
        if (heightmap) out.add("vulkan.terrain.heightmap");
        if (vt) out.add("vulkan.terrain.virtual_texturing");
        if (splatting) out.add("vulkan.terrain.splatting");
        if (streaming) out.add("vulkan.terrain.streaming");
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
