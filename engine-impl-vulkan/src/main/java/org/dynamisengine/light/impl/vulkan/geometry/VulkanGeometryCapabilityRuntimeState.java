package org.dynamisengine.light.impl.vulkan.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.config.QualityTier;
import org.dynamisengine.light.api.event.EngineWarning;
import org.dynamisengine.light.api.runtime.GeometryCapabilityDiagnostics;
import org.dynamisengine.light.api.runtime.GeometryPromotionDiagnostics;
import org.dynamisengine.light.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;
import org.dynamisengine.light.impl.vulkan.runtime.model.MeshGeometryCacheProfile;

/**
 * Runtime geometry/detail capability and promotion diagnostics (Phase 1 scaffold).
 */
public final class VulkanGeometryCapabilityRuntimeState {
    private boolean instancedRenderingRequested;
    private boolean frustumCullingRequested = true;
    private boolean meshStreamingRequested = true;
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
    private MeshGeometryCacheProfile cacheProfileLastFrame = new MeshGeometryCacheProfile(0, 0, 0, 0, 0);

    public void reset() {
        stableStreak = 0;
        highStreak = 0;
        warnCooldownRemaining = 0;
        envelopeBreachedLastFrame = false;
        promotionReadyLastFrame = false;
        expectedFeaturesLastFrame = List.of();
        activeFeaturesLastFrame = List.of();
        prunedFeaturesLastFrame = List.of();
        cacheProfileLastFrame = new MeshGeometryCacheProfile(0, 0, 0, 0, 0);
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        instancedRenderingRequested = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.geometry.instancedRenderingEnabled", "false"));
        frustumCullingRequested = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.geometry.frustumCullingEnabled", "true"));
        meshStreamingRequested = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.geometry.meshStreamingEnabled", "true"));
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.geometry.warnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.geometry.warnCooldownFrames", warnCooldownFrames, 0, 100_000);
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.geometry.promotionReadyMinFrames", promotionReadyMinFrames, 1, 100_000);
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions != null && backendOptions.containsKey("vulkan.geometry.promotionReadyMinFrames")) {
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

    public void emitFrameWarnings(MeshGeometryCacheProfile cacheProfile, List<EngineWarning> warnings) {
        cacheProfileLastFrame = cacheProfile == null ? new MeshGeometryCacheProfile(0, 0, 0, 0, 0) : cacheProfile;
        boolean staticRenderingActive = true;
        boolean instancedActive = false; // Phase 1 scaffold: explicit runtime path not yet active.
        boolean frustumCullingActive = frustumCullingRequested;
        boolean meshStreamingActive = meshStreamingRequested;

        expectedFeaturesLastFrame = expectedFeatureList(
                instancedRenderingRequested,
                frustumCullingRequested,
                meshStreamingRequested
        );
        activeFeaturesLastFrame = activeFeatureList(
                staticRenderingActive,
                instancedActive,
                frustumCullingActive,
                meshStreamingActive
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
                "GEOMETRY_CAPABILITY_MODE_ACTIVE",
                "Geometry capability mode active (active=[" + String.join(", ", activeFeaturesLastFrame)
                        + "], pruned=[" + String.join(", ", prunedFeaturesLastFrame) + "])"
        ));
        warnings.add(new EngineWarning(
                "GEOMETRY_POLICY_ACTIVE",
                "Geometry policy (instancedExpected=" + instancedRenderingRequested
                        + ", frustumCullingExpected=" + frustumCullingRequested
                        + ", meshStreamingExpected=" + meshStreamingRequested + ")"
        ));
        warnings.add(new EngineWarning(
                "GEOMETRY_PROMOTION_ENVELOPE",
                "Geometry promotion envelope (risk=" + risk
                        + ", expectedCount=" + expectedFeaturesLastFrame.size()
                        + ", activeCount=" + activeFeaturesLastFrame.size()
                        + ", cacheEntries=" + cacheProfileLastFrame.entries()
                        + ", cacheMaxEntries=" + cacheProfileLastFrame.maxEntries()
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (envelopeBreachedLastFrame && warnCooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "GEOMETRY_PROMOTION_ENVELOPE_BREACH",
                    "Geometry promotion envelope breach (highStreak=" + highStreak
                            + ", cooldown=" + warnCooldownRemaining + ")"
            ));
            warnCooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "GEOMETRY_PROMOTION_READY",
                    "Geometry promotion-ready envelope satisfied (stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public GeometryCapabilityDiagnostics diagnostics() {
        return new GeometryCapabilityDiagnostics(
                true,
                true,
                expectedFeaturesLastFrame.contains("vulkan.geometry.instanced_rendering"),
                activeFeaturesLastFrame.contains("vulkan.geometry.instanced_rendering"),
                expectedFeaturesLastFrame.contains("vulkan.geometry.frustum_culling"),
                activeFeaturesLastFrame.contains("vulkan.geometry.frustum_culling"),
                expectedFeaturesLastFrame.contains("vulkan.geometry.mesh_streaming"),
                activeFeaturesLastFrame.contains("vulkan.geometry.mesh_streaming"),
                cacheProfileLastFrame.hits(),
                cacheProfileLastFrame.misses(),
                cacheProfileLastFrame.evictions(),
                cacheProfileLastFrame.entries(),
                cacheProfileLastFrame.maxEntries(),
                expectedFeaturesLastFrame,
                activeFeaturesLastFrame,
                prunedFeaturesLastFrame
        );
    }

    public GeometryPromotionDiagnostics promotionDiagnostics() {
        return new GeometryPromotionDiagnostics(
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

    private static List<String> expectedFeatureList(boolean instanced, boolean frustum, boolean streaming) {
        List<String> out = new ArrayList<>();
        if (instanced) out.add("vulkan.geometry.instanced_rendering");
        if (frustum) out.add("vulkan.geometry.frustum_culling");
        if (streaming) out.add("vulkan.geometry.mesh_streaming");
        return List.copyOf(out);
    }

    private static List<String> activeFeatureList(boolean staticRendering, boolean instanced, boolean frustum, boolean streaming) {
        List<String> out = new ArrayList<>();
        if (staticRendering) out.add("vulkan.geometry.static_mesh_rendering");
        if (instanced) out.add("vulkan.geometry.instanced_rendering");
        if (frustum) out.add("vulkan.geometry.frustum_culling");
        if (streaming) out.add("vulkan.geometry.mesh_streaming");
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
