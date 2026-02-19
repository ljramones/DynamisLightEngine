package org.dynamislight.impl.vulkan;

import java.util.Map;

import org.dynamislight.api.config.QualityTier;

final class VulkanShadowRuntimeTuning {
    private VulkanShadowRuntimeTuning() {
    }

    static ShadowRenderConfig withRuntimeMomentPipelineState(ShadowRenderConfig base, VulkanContext context) {
        if (base == null || !base.momentPipelineRequested()) {
            return base;
        }
        boolean resourcesActive = context.isShadowMomentPipelineActive();
        boolean initialized = context.isShadowMomentInitialized();
        boolean active = resourcesActive && initialized;
        String runtimeFilterPath = active ? base.filterPath() : base.runtimeFilterPath();
        boolean momentFilterEstimateOnly = base.momentFilterEstimateOnly() && !active;
        if (base.momentPipelineActive() == active
                && base.runtimeFilterPath().equals(runtimeFilterPath)
                && base.momentFilterEstimateOnly() == momentFilterEstimateOnly) {
            return base;
        }
        return new ShadowRenderConfig(
                base.enabled(),
                base.strength(),
                base.bias(),
                base.normalBiasScale(),
                base.slopeBiasScale(),
                base.pcfRadius(),
                base.cascadeCount(),
                base.mapResolution(),
                base.maxShadowedLocalLights(),
                base.selectedLocalShadowLights(),
                base.primaryShadowType(),
                base.primaryShadowLightId(),
                base.atlasCapacityTiles(),
                base.atlasAllocatedTiles(),
                base.atlasUtilization(),
                base.atlasEvictions(),
                base.atlasMemoryBytesD16(),
                base.atlasMemoryBytesD32(),
                base.shadowUpdateBytesEstimate(),
                base.shadowMomentAtlasBytesEstimate(),
                base.renderedLocalShadowLights(),
                base.renderedSpotShadowLights(),
                base.renderedPointShadowCubemaps(),
                base.renderedShadowLightIdsCsv(),
                base.deferredShadowLightCount(),
                base.deferredShadowLightIdsCsv(),
                base.staleBypassShadowLightCount(),
                base.filterPath(),
                runtimeFilterPath,
                momentFilterEstimateOnly,
                base.momentPipelineRequested(),
                active,
                base.contactShadowsRequested(),
                base.rtShadowMode(),
                base.rtShadowActive(),
                base.degraded()
        );
    }

    static float effectiveShadowRtDenoiseStrength(
            String rtMode,
            float defaultStrength,
            float productionStrength,
            float dedicatedStrength
    ) {
        if ("bvh_production".equals(rtMode) && productionStrength >= 0.0f) {
            return Math.max(0.0f, Math.min(1.0f, productionStrength));
        }
        if (("bvh_dedicated".equals(rtMode) || "rt_native_denoised".equals(rtMode)) && dedicatedStrength >= 0.0f) {
            return Math.max(0.0f, Math.min(1.0f, dedicatedStrength));
        }
        return defaultStrength;
    }

    static float effectiveShadowRtRayLength(
            String rtMode,
            float defaultRayLength,
            float productionRayLength,
            float dedicatedRayLength
    ) {
        if ("bvh_production".equals(rtMode) && productionRayLength >= 0.0f) {
            return Math.max(1.0f, Math.min(500.0f, productionRayLength));
        }
        if (("bvh_dedicated".equals(rtMode) || "rt_native_denoised".equals(rtMode)) && dedicatedRayLength >= 0.0f) {
            return Math.max(1.0f, Math.min(500.0f, dedicatedRayLength));
        }
        return defaultRayLength;
    }

    static int effectiveShadowRtSampleCount(
            String rtMode,
            int defaultSamples,
            int productionSamples,
            int dedicatedSamples
    ) {
        if ("bvh_production".equals(rtMode) && productionSamples > 0) {
            return Math.max(1, Math.min(16, productionSamples));
        }
        if (("bvh_dedicated".equals(rtMode) || "rt_native_denoised".equals(rtMode)) && dedicatedSamples > 0) {
            return Math.max(1, Math.min(16, dedicatedSamples));
        }
        return defaultSamples;
    }

    static double shadowRtPerfCapForTier(
            QualityTier tier,
            double low,
            double medium,
            double high,
            double ultra
    ) {
        return switch (tier) {
            case LOW -> low;
            case MEDIUM -> medium;
            case HIGH -> high;
            case ULTRA -> ultra;
        };
    }

    static void updateShadowSchedulerTicks(Map<String, Long> lastRenderedTicks, long frameTick, String renderedShadowLightIdsCsv) {
        if (renderedShadowLightIdsCsv == null || renderedShadowLightIdsCsv.isBlank()) {
            return;
        }
        String[] ids = renderedShadowLightIdsCsv.split(",");
        for (String id : ids) {
            String normalized = id == null ? "" : id.trim();
            if (!normalized.isEmpty()) {
                lastRenderedTicks.put(normalized, frameTick);
            }
        }
    }
}
