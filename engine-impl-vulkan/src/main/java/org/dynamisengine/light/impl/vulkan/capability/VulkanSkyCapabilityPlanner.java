package org.dynamisengine.light.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.dynamisengine.light.api.config.QualityTier;

/**
 * Deterministic planner for Vulkan sky/atmosphere capability mode and telemetry.
 */
public final class VulkanSkyCapabilityPlanner {
    private VulkanSkyCapabilityPlanner() {
    }

    public static VulkanSkyCapabilityPlan plan(PlanInput input) {
        PlanInput safe = input == null ? PlanInput.defaults() : input;
        QualityTier tier = safe.qualityTier() == null ? QualityTier.MEDIUM : safe.qualityTier();

        boolean hdriExpected = safe.hdriAvailable();
        boolean hdriActive = hdriExpected;

        boolean proceduralExpected = safe.proceduralRequested();
        boolean proceduralActive = false;
        boolean atmosphereExpected = safe.atmosphereRequested();
        boolean atmosphereActive = false;
        boolean dynamicTimeOfDayExpected = safe.dynamicTimeOfDayRequested();
        boolean dynamicTimeOfDayActive = false;
        boolean volumetricCloudsExpected = safe.volumetricCloudsRequested();
        boolean volumetricCloudsActive = false;
        boolean cloudShadowProjectionExpected = safe.cloudShadowProjectionRequested();
        boolean cloudShadowProjectionActive = false;
        boolean aerialPerspectiveExpected = safe.aerialPerspectiveRequested();
        boolean aerialPerspectiveActive = false;

        String modeId = resolveModeId(
                safe.configuredMode(),
                hdriExpected,
                proceduralExpected,
                atmosphereExpected,
                dynamicTimeOfDayExpected,
                volumetricCloudsExpected,
                cloudShadowProjectionExpected,
                aerialPerspectiveExpected
        );

        List<String> active = new ArrayList<>();
        if (hdriActive) {
            active.add("vulkan.sky.hdri_skybox");
        }
        if (proceduralActive) {
            active.add("vulkan.sky.procedural_sky");
        }
        if (atmosphereActive) {
            active.add("vulkan.sky.atmosphere");
        }
        if (dynamicTimeOfDayActive) {
            active.add("vulkan.sky.dynamic_time_of_day");
        }
        if (volumetricCloudsActive) {
            active.add("vulkan.sky.volumetric_clouds");
        }
        if (cloudShadowProjectionActive) {
            active.add("vulkan.sky.cloud_shadow_projection");
        }
        if (aerialPerspectiveActive) {
            active.add("vulkan.sky.aerial_perspective");
        }

        List<String> pruned = new ArrayList<>();
        if (proceduralExpected && !proceduralActive) {
            pruned.add("vulkan.sky.procedural_sky");
        }
        if (atmosphereExpected && !atmosphereActive) {
            pruned.add("vulkan.sky.atmosphere");
        }
        if (dynamicTimeOfDayExpected && !dynamicTimeOfDayActive) {
            pruned.add("vulkan.sky.dynamic_time_of_day");
        }
        if (volumetricCloudsExpected && !volumetricCloudsActive) {
            pruned.add("vulkan.sky.volumetric_clouds");
        }
        if (cloudShadowProjectionExpected && !cloudShadowProjectionActive) {
            pruned.add("vulkan.sky.cloud_shadow_projection");
        }
        if (aerialPerspectiveExpected && !aerialPerspectiveActive) {
            pruned.add("vulkan.sky.aerial_perspective");
        }

        List<String> signals = List.of(
                "resolvedMode=" + modeId,
                "tier=" + tier.name().toLowerCase(Locale.ROOT),
                "hdriExpected=" + hdriExpected,
                "hdriActive=" + hdriActive,
                "proceduralExpected=" + proceduralExpected,
                "proceduralActive=" + proceduralActive,
                "atmosphereExpected=" + atmosphereExpected,
                "atmosphereActive=" + atmosphereActive,
                "dynamicTimeOfDayExpected=" + dynamicTimeOfDayExpected,
                "dynamicTimeOfDayActive=" + dynamicTimeOfDayActive,
                "volumetricCloudsExpected=" + volumetricCloudsExpected,
                "volumetricCloudsActive=" + volumetricCloudsActive,
                "cloudShadowProjectionExpected=" + cloudShadowProjectionExpected,
                "cloudShadowProjectionActive=" + cloudShadowProjectionActive,
                "aerialPerspectiveExpected=" + aerialPerspectiveExpected,
                "aerialPerspectiveActive=" + aerialPerspectiveActive
        );

        return new VulkanSkyCapabilityPlan(
                modeId,
                hdriExpected,
                hdriActive,
                proceduralExpected,
                proceduralActive,
                atmosphereExpected,
                atmosphereActive,
                dynamicTimeOfDayExpected,
                dynamicTimeOfDayActive,
                volumetricCloudsExpected,
                volumetricCloudsActive,
                cloudShadowProjectionExpected,
                cloudShadowProjectionActive,
                aerialPerspectiveExpected,
                aerialPerspectiveActive,
                active,
                pruned,
                signals
        );
    }

    private static String resolveModeId(
            String configuredMode,
            boolean hdriExpected,
            boolean proceduralExpected,
            boolean atmosphereExpected,
            boolean dynamicTimeOfDayExpected,
            boolean volumetricCloudsExpected,
            boolean cloudShadowProjectionExpected,
            boolean aerialPerspectiveExpected
    ) {
        if (configuredMode != null && !configuredMode.isBlank()) {
            return configuredMode.toLowerCase(Locale.ROOT);
        }
        if (atmosphereExpected || dynamicTimeOfDayExpected || volumetricCloudsExpected
                || cloudShadowProjectionExpected || aerialPerspectiveExpected) {
            return "atmosphere";
        }
        if (proceduralExpected) {
            return "procedural";
        }
        if (hdriExpected) {
            return "hdri";
        }
        return "off";
    }

    public record PlanInput(
            QualityTier qualityTier,
            String configuredMode,
            boolean hdriAvailable,
            boolean proceduralRequested,
            boolean atmosphereRequested,
            boolean dynamicTimeOfDayRequested,
            boolean volumetricCloudsRequested,
            boolean cloudShadowProjectionRequested,
            boolean aerialPerspectiveRequested
    ) {
        public static PlanInput defaults() {
            return new PlanInput(
                    QualityTier.MEDIUM,
                    "hdri",
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }
    }
}
