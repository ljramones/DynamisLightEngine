package org.dynamislight.impl.vulkan.sky;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.SkyCapabilityDiagnostics;
import org.dynamislight.api.runtime.SkyPromotionDiagnostics;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;
import org.dynamislight.impl.vulkan.runtime.model.IblRenderConfig;

/**
 * Runtime sky/atmosphere capability and promotion state (Phase 1 scaffold).
 */
public final class VulkanSkyCapabilityRuntimeState {
    private String configuredMode = "hdri";
    private boolean proceduralRequested;
    private boolean atmosphereRequested;
    private boolean dynamicTimeOfDayRequested;
    private boolean volumetricCloudsRequested;
    private boolean cloudShadowProjectionRequested;
    private boolean aerialPerspectiveRequested;
    private int warnMinFrames = 2;
    private int warnCooldownFrames = 120;
    private int promotionReadyMinFrames = 4;

    private int stableStreak;
    private int highStreak;
    private int warnCooldownRemaining;
    private boolean envelopeBreachedLastFrame;
    private boolean promotionReadyLastFrame;
    private String modeLastFrame = "hdri";
    private List<String> expectedFeaturesLastFrame = List.of();
    private List<String> activeFeaturesLastFrame = List.of();
    private List<String> prunedFeaturesLastFrame = List.of();

    public void reset() {
        stableStreak = 0;
        highStreak = 0;
        warnCooldownRemaining = 0;
        envelopeBreachedLastFrame = false;
        promotionReadyLastFrame = false;
        modeLastFrame = configuredMode;
        expectedFeaturesLastFrame = List.of();
        activeFeaturesLastFrame = List.of();
        prunedFeaturesLastFrame = List.of();
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        configuredMode = safe.getOrDefault("vulkan.sky.mode", configuredMode);
        proceduralRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.sky.proceduralEnabled", "false"));
        atmosphereRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.sky.atmosphereEnabled", "false"));
        dynamicTimeOfDayRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.sky.dynamicTimeOfDayEnabled", "false"));
        volumetricCloudsRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.sky.volumetricCloudsEnabled", "false"));
        cloudShadowProjectionRequested = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.sky.cloudShadowProjectionEnabled", "false"));
        aerialPerspectiveRequested = Boolean.parseBoolean(safe.getOrDefault("vulkan.sky.aerialPerspectiveEnabled", "false"));
        warnMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.sky.warnMinFrames", warnMinFrames, 1, 100_000);
        warnCooldownFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.sky.warnCooldownFrames", warnCooldownFrames, 0, 100_000);
        promotionReadyMinFrames = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe, "vulkan.sky.promotionReadyMinFrames", promotionReadyMinFrames, 1, 100_000);
    }

    public void applyProfileDefaults(Map<String, String> backendOptions, QualityTier tier) {
        if (backendOptions != null && backendOptions.containsKey("vulkan.sky.promotionReadyMinFrames")) {
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

    public void emitFrameWarnings(IblRenderConfig ibl, List<EngineWarning> warnings) {
        IblRenderConfig safeIbl = ibl == null
                ? new IblRenderConfig(false, 0f, 0f, false, false, false, false, 0, 0, 0, 0f, false, 0, null, null, null)
                : ibl;
        boolean hdriExpected = safeIbl.enabled();
        boolean hdriActive = safeIbl.enabled();

        boolean proceduralExpected = proceduralRequested;
        boolean atmosphereExpected = atmosphereRequested;
        boolean todExpected = dynamicTimeOfDayRequested;
        boolean volumetricCloudsExpected = volumetricCloudsRequested;
        boolean cloudShadowProjectionExpected = cloudShadowProjectionRequested;
        boolean aerialPerspectiveExpected = aerialPerspectiveRequested;

        // Phase 1: HDRI path is active; advanced sky features are scaffolded and currently inactive.
        boolean proceduralActive = false;
        boolean atmosphereActive = false;
        boolean todActive = false;
        boolean volumetricCloudsActive = false;
        boolean cloudShadowProjectionActive = false;
        boolean aerialPerspectiveActive = false;

        modeLastFrame = normalizeMode(configuredMode, hdriExpected, proceduralExpected, atmosphereExpected);
        expectedFeaturesLastFrame = expectedFeatureList(
                hdriExpected,
                proceduralExpected,
                atmosphereExpected,
                todExpected,
                volumetricCloudsExpected,
                cloudShadowProjectionExpected,
                aerialPerspectiveExpected
        );
        activeFeaturesLastFrame = activeFeatureList(
                hdriActive,
                proceduralActive,
                atmosphereActive,
                todActive,
                volumetricCloudsActive,
                cloudShadowProjectionActive,
                aerialPerspectiveActive
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
                "SKY_CAPABILITY_MODE_ACTIVE",
                "Sky capability mode active (mode=" + modeLastFrame
                        + ", active=[" + String.join(", ", activeFeaturesLastFrame) + "]"
                        + ", pruned=[" + String.join(", ", prunedFeaturesLastFrame) + "])"
        ));
        warnings.add(new EngineWarning(
                "SKY_POLICY_ACTIVE",
                "Sky policy (hdriExpected=" + hdriExpected
                        + ", proceduralExpected=" + proceduralExpected
                        + ", atmosphereExpected=" + atmosphereExpected
                        + ", dynamicTimeOfDayExpected=" + todExpected
                        + ", volumetricCloudsExpected=" + volumetricCloudsExpected
                        + ", cloudShadowProjectionExpected=" + cloudShadowProjectionExpected
                        + ", aerialPerspectiveExpected=" + aerialPerspectiveExpected + ")"
        ));
        warnings.add(new EngineWarning(
                "SKY_PROMOTION_ENVELOPE",
                "Sky promotion envelope (risk=" + risk
                        + ", expectedCount=" + expectedFeaturesLastFrame.size()
                        + ", activeCount=" + activeFeaturesLastFrame.size()
                        + ", warnMinFrames=" + warnMinFrames
                        + ", warnCooldownFrames=" + warnCooldownFrames
                        + ", promotionReadyMinFrames=" + promotionReadyMinFrames + ")"
        ));
        if (envelopeBreachedLastFrame && warnCooldownRemaining <= 0) {
            warnings.add(new EngineWarning(
                    "SKY_PROMOTION_ENVELOPE_BREACH",
                    "Sky promotion envelope breach (highStreak=" + highStreak
                            + ", cooldown=" + warnCooldownRemaining + ")"
            ));
            warnCooldownRemaining = warnCooldownFrames;
        }
        if (promotionReadyLastFrame) {
            warnings.add(new EngineWarning(
                    "SKY_PROMOTION_READY",
                    "Sky promotion-ready envelope satisfied (stableStreak=" + stableStreak
                            + ", minFrames=" + promotionReadyMinFrames + ")"
            ));
        }
    }

    public SkyCapabilityDiagnostics diagnostics() {
        return new SkyCapabilityDiagnostics(
                true,
                modeLastFrame,
                activeFeaturesLastFrame.contains("vulkan.sky.hdri_skybox"),
                activeFeaturesLastFrame.contains("vulkan.sky.procedural_sky"),
                activeFeaturesLastFrame.contains("vulkan.sky.atmosphere"),
                activeFeaturesLastFrame.contains("vulkan.sky.dynamic_time_of_day"),
                activeFeaturesLastFrame.contains("vulkan.sky.volumetric_clouds"),
                activeFeaturesLastFrame.contains("vulkan.sky.cloud_shadow_projection"),
                activeFeaturesLastFrame.contains("vulkan.sky.aerial_perspective"),
                expectedFeaturesLastFrame,
                activeFeaturesLastFrame,
                prunedFeaturesLastFrame
        );
    }

    public SkyPromotionDiagnostics promotionDiagnostics() {
        return new SkyPromotionDiagnostics(
                true,
                modeLastFrame,
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

    private static String normalizeMode(String configured, boolean hdriExpected, boolean proceduralExpected, boolean atmosphereExpected) {
        if (configured == null || configured.isBlank()) {
            if (atmosphereExpected) return "atmosphere";
            if (proceduralExpected) return "procedural";
            if (hdriExpected) return "hdri";
            return "off";
        }
        return configured.toLowerCase(java.util.Locale.ROOT);
    }

    private static List<String> expectedFeatureList(
            boolean hdri,
            boolean procedural,
            boolean atmosphere,
            boolean tod,
            boolean clouds,
            boolean cloudShadows,
            boolean aerial
    ) {
        List<String> out = new ArrayList<>();
        if (hdri) out.add("vulkan.sky.hdri_skybox");
        if (procedural) out.add("vulkan.sky.procedural_sky");
        if (atmosphere) out.add("vulkan.sky.atmosphere");
        if (tod) out.add("vulkan.sky.dynamic_time_of_day");
        if (clouds) out.add("vulkan.sky.volumetric_clouds");
        if (cloudShadows) out.add("vulkan.sky.cloud_shadow_projection");
        if (aerial) out.add("vulkan.sky.aerial_perspective");
        return List.copyOf(out);
    }

    private static List<String> activeFeatureList(
            boolean hdri,
            boolean procedural,
            boolean atmosphere,
            boolean tod,
            boolean clouds,
            boolean cloudShadows,
            boolean aerial
    ) {
        List<String> out = new ArrayList<>();
        if (hdri) out.add("vulkan.sky.hdri_skybox");
        if (procedural) out.add("vulkan.sky.procedural_sky");
        if (atmosphere) out.add("vulkan.sky.atmosphere");
        if (tod) out.add("vulkan.sky.dynamic_time_of_day");
        if (clouds) out.add("vulkan.sky.volumetric_clouds");
        if (cloudShadows) out.add("vulkan.sky.cloud_shadow_projection");
        if (aerial) out.add("vulkan.sky.aerial_perspective");
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
