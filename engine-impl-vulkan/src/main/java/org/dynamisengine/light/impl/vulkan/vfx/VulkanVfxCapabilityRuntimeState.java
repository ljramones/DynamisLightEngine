package org.dynamisengine.light.impl.vulkan.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.event.EngineWarning;
import org.dynamisengine.light.api.runtime.VfxCapabilityDiagnostics;
import org.dynamisengine.light.api.runtime.VfxPromotionDiagnostics;
import org.dynamisengine.light.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;

/**
 * Runtime VFX/particles capability and promotion diagnostics (Phase 1 scaffold).
 */
public final class VulkanVfxCapabilityRuntimeState {
    private boolean cpuParticlesRequested;
    private boolean gpuParticlesRequested;
    private boolean softParticlesRequested;
    private boolean meshParticlesRequested;
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
        cpuParticlesRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.vfx.cpuParticlesEnabled", "false"));
        gpuParticlesRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.vfx.gpuParticlesEnabled", "false"));
        softParticlesRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.vfx.softParticlesEnabled", "false"));
        meshParticlesRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.vfx.meshParticlesEnabled", "false"));
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.vfx.warnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.vfx.warnCooldownFrames", warnCooldownFrames, 0, 100_000);
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.vfx.promotionReadyMinFrames", promotionReadyMinFrames, 1, 100_000);
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions != null && backendOptions.containsKey("vulkan.vfx.promotionReadyMinFrames")) {
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
        boolean cpuParticlesActive = false;
        boolean gpuParticlesActive = false;
        boolean softParticlesActive = false;
        boolean meshParticlesActive = false;

        expectedFeaturesLastFrame = expectedFeatureList(
                cpuParticlesRequested, gpuParticlesRequested, softParticlesRequested, meshParticlesRequested);
        activeFeaturesLastFrame = activeFeatureList(
                cpuParticlesActive, gpuParticlesActive, softParticlesActive, meshParticlesActive);
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
                "VFX_CAPABILITY_MODE_ACTIVE",
                "VFX capability mode active (active=[" + String.join(", ", activeFeaturesLastFrame)
                        + "], pruned=[" + String.join(", ", prunedFeaturesLastFrame) + "])"
        ));
        warnings.add(new EngineWarning(
                "VFX_POLICY_ACTIVE",
                "VFX policy (cpuParticlesExpected=" + cpuParticlesRequested
                        + ", gpuParticlesExpected=" + gpuParticlesRequested
                        + ", softParticlesExpected=" + softParticlesRequested
                        + ", meshParticlesExpected=" + meshParticlesRequested + ")"
        ));
        warnings.add(new EngineWarning(
                "VFX_PROMOTION_ENVELOPE",
                "VFX promotion envelope (risk=" + risk
                        + ", expectedCount=" + expectedFeaturesLastFrame.size()
                        + ", activeCount=" + activeFeaturesLastFrame.size()
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (envelopeBreachedLastFrame && warnCooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "VFX_PROMOTION_ENVELOPE_BREACH",
                    "VFX promotion envelope breach (highStreak=" + highStreak
                            + ", cooldown=" + warnCooldownRemaining + ")"
            ));
            warnCooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "VFX_PROMOTION_READY",
                    "VFX promotion-ready envelope satisfied (stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public VfxCapabilityDiagnostics diagnostics() {
        return new VfxCapabilityDiagnostics(
                true,
                expectedFeaturesLastFrame.contains("vulkan.vfx.cpu_particles"),
                activeFeaturesLastFrame.contains("vulkan.vfx.cpu_particles"),
                expectedFeaturesLastFrame.contains("vulkan.vfx.gpu_particles"),
                activeFeaturesLastFrame.contains("vulkan.vfx.gpu_particles"),
                expectedFeaturesLastFrame.contains("vulkan.vfx.soft_particles"),
                activeFeaturesLastFrame.contains("vulkan.vfx.soft_particles"),
                expectedFeaturesLastFrame.contains("vulkan.vfx.mesh_particles"),
                activeFeaturesLastFrame.contains("vulkan.vfx.mesh_particles"),
                expectedFeaturesLastFrame,
                activeFeaturesLastFrame,
                prunedFeaturesLastFrame
        );
    }

    public VfxPromotionDiagnostics promotionDiagnostics() {
        return new VfxPromotionDiagnostics(
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

    private static List<String> expectedFeatureList(boolean cpu, boolean gpu, boolean soft, boolean mesh) {
        List<String> out = new ArrayList<>();
        if (cpu) out.add("vulkan.vfx.cpu_particles");
        if (gpu) out.add("vulkan.vfx.gpu_particles");
        if (soft) out.add("vulkan.vfx.soft_particles");
        if (mesh) out.add("vulkan.vfx.mesh_particles");
        return List.copyOf(out);
    }

    private static List<String> activeFeatureList(boolean cpu, boolean gpu, boolean soft, boolean mesh) {
        List<String> out = new ArrayList<>();
        if (cpu) out.add("vulkan.vfx.cpu_particles");
        if (gpu) out.add("vulkan.vfx.gpu_particles");
        if (soft) out.add("vulkan.vfx.soft_particles");
        if (mesh) out.add("vulkan.vfx.mesh_particles");
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
