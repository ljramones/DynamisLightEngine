package org.dynamislight.impl.vulkan.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.spi.render.RenderFeatureCapabilityV2;
import org.dynamislight.spi.render.RenderFeatureMode;

/**
 * Metadata-only planner that maps Vulkan shadow policy signals to one v2 capability mode.
 */
public final class VulkanShadowCapabilityPlanner {
    private VulkanShadowCapabilityPlanner() {
    }

    public static Plan plan(PlanInput input) {
        PlanInput safe = input == null ? PlanInput.defaults() : input.normalized();
        RenderFeatureMode mode = resolveMode(safe);
        List<String> signals = collectSignals(safe, mode);
        RenderFeatureCapabilityV2 capability = VulkanShadowCapabilityDescriptorV2.withMode(mode);
        return new Plan(mode, capability, signals);
    }

    private static RenderFeatureMode resolveMode(PlanInput input) {
        if (input.distanceFieldSoftEnabled()) {
            return VulkanShadowCapabilityDescriptorV2.MODE_DISTANCE_FIELD_SOFT;
        }
        if (input.transparentReceiversEnabled()) {
            return VulkanShadowCapabilityDescriptorV2.MODE_TRANSPARENT_RECEIVERS;
        }
        if (isRtDenoisedMode(input.shadowRtMode())) {
            return input.contactShadows()
                    ? VulkanShadowCapabilityDescriptorV2.MODE_HYBRID_CASCADE_CONTACT_RT
                    : VulkanShadowCapabilityDescriptorV2.MODE_RT_DENOISED;
        }
        if (isRtRequested(input.shadowRtMode())) {
            return input.contactShadows()
                    ? VulkanShadowCapabilityDescriptorV2.MODE_HYBRID_CASCADE_CONTACT_RT
                    : VulkanShadowCapabilityDescriptorV2.MODE_RT;
        }
        if (input.areaLightShadowsEnabled()) {
            return VulkanShadowCapabilityDescriptorV2.MODE_AREA_APPROX;
        }
        if (input.spotProjectedEnabled()) {
            return VulkanShadowCapabilityDescriptorV2.MODE_SPOT_PROJECTED;
        }
        if (input.shadowCacheEnabled()) {
            return VulkanShadowCapabilityDescriptorV2.MODE_CACHED_STATIC_DYNAMIC;
        }
        if (input.maxShadowFacesPerFrame() > 0) {
            return VulkanShadowCapabilityDescriptorV2.MODE_POINT_CUBEMAP_BUDGET;
        }
        if (input.schedulerEnabled() || input.maxShadowedLocalLights() > 0 || input.maxLocalShadowLayers() > 0) {
            return VulkanShadowCapabilityDescriptorV2.MODE_LOCAL_ATLAS_CADENCE;
        }
        return switch (input.shadowFilterPath()) {
            case "vsm" -> VulkanShadowCapabilityDescriptorV2.MODE_VSM;
            case "evsm" -> VulkanShadowCapabilityDescriptorV2.MODE_EVSM;
            case "pcss" -> VulkanShadowCapabilityDescriptorV2.MODE_PCSS;
            default -> VulkanShadowCapabilityDescriptorV2.MODE_PCF;
        };
    }

    private static List<String> collectSignals(PlanInput input, RenderFeatureMode mode) {
        List<String> signals = new ArrayList<>();
        signals.add("tier=" + input.qualityTier().name().toLowerCase(Locale.ROOT));
        signals.add("filterPath=" + input.shadowFilterPath());
        signals.add("rtMode=" + input.shadowRtMode());
        if (input.contactShadows()) {
            signals.add("contactShadows=true");
        }
        if (input.schedulerEnabled()) {
            signals.add("scheduler=enabled");
        }
        if (input.maxShadowedLocalLights() > 0) {
            signals.add("maxShadowedLocalLights=" + input.maxShadowedLocalLights());
        }
        if (input.maxLocalShadowLayers() > 0) {
            signals.add("maxLocalShadowLayers=" + input.maxLocalShadowLayers());
        }
        if (input.maxShadowFacesPerFrame() > 0) {
            signals.add("maxShadowFacesPerFrame=" + input.maxShadowFacesPerFrame());
        }
        if (input.shadowCacheEnabled()) {
            signals.add("shadowCache=true");
        }
        if (input.areaLightShadowsEnabled()) {
            signals.add("areaLights=true");
        }
        if (input.spotProjectedEnabled()) {
            signals.add("spotProjected=true");
        }
        if (input.transparentReceiversEnabled()) {
            signals.add("transparentReceivers=true");
        }
        if (input.distanceFieldSoftEnabled()) {
            signals.add("distanceFieldSoft=true");
        }
        signals.add("resolvedMode=" + mode.id());
        return List.copyOf(signals);
    }

    private static boolean isRtRequested(String mode) {
        return switch (normalizeRt(mode)) {
            case "force", "bvh", "bvh_dedicated", "bvh_production", "rt_native", "rt_native_denoised" -> true;
            default -> false;
        };
    }

    private static boolean isRtDenoisedMode(String mode) {
        return switch (normalizeRt(mode)) {
            case "bvh_dedicated", "bvh_production", "rt_native_denoised" -> true;
            default -> false;
        };
    }

    private static String normalizeRt(String mode) {
        if (mode == null || mode.isBlank()) {
            return "off";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    public record Plan(RenderFeatureMode mode, RenderFeatureCapabilityV2 capability, List<String> signals) {
    }

    public record PlanInput(
            QualityTier qualityTier,
            String shadowFilterPath,
            boolean contactShadows,
            String shadowRtMode,
            int maxShadowedLocalLights,
            int maxLocalShadowLayers,
            int maxShadowFacesPerFrame,
            boolean schedulerEnabled,
            boolean shadowCacheEnabled,
            boolean areaLightShadowsEnabled,
            boolean spotProjectedEnabled,
            boolean transparentReceiversEnabled,
            boolean distanceFieldSoftEnabled
    ) {
        public PlanInput normalized() {
            String filter = normalizeFilter(shadowFilterPath);
            String rt = normalizeRt(shadowRtMode);
            return new PlanInput(
                    qualityTier == null ? QualityTier.MEDIUM : qualityTier,
                    filter,
                    contactShadows,
                    rt,
                    Math.max(0, maxShadowedLocalLights),
                    Math.max(0, maxLocalShadowLayers),
                    Math.max(0, maxShadowFacesPerFrame),
                    schedulerEnabled,
                    shadowCacheEnabled,
                    areaLightShadowsEnabled,
                    spotProjectedEnabled,
                    transparentReceiversEnabled,
                    distanceFieldSoftEnabled
            );
        }

        public static PlanInput defaults() {
            return new PlanInput(
                    QualityTier.MEDIUM,
                    "pcf",
                    false,
                    "off",
                    0,
                    0,
                    0,
                    true,
                    false,
                    false,
                    false,
                    false,
                    false
            ).normalized();
        }
    }

    private static String normalizeFilter(String filterPath) {
        if (filterPath == null || filterPath.isBlank()) {
            return "pcf";
        }
        String normalized = filterPath.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pcf", "pcss", "vsm", "evsm" -> normalized;
            default -> "pcf";
        };
    }
}
