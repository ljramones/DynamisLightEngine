package org.dynamislight.impl.vulkan;

import java.util.Map;

final class VulkanRuntimeOptions {
    private VulkanRuntimeOptions() {
    }

    static Parsed parse(Map<String, String> options, int defaultMeshGeometryCacheEntries) {
        Map<String, String> safe = options == null ? Map.of() : options;
        return new Parsed(
                parseBoolean(safe, "vulkan.mockContext", true),
                parseBoolean(safe, "vulkan.windowVisible", false),
                parseBoolean(safe, "vulkan.forceDeviceLostOnRender", false),
                parseBoolean(safe, "vulkan.postOffscreen", true),
                parseBoolean(safe, "vulkan.forceInitFailure", false),
                parseIntOption(safe, "vulkan.meshGeometryCacheEntries", defaultMeshGeometryCacheEntries, 16, 4096),
                parseIntOption(safe, "vulkan.framesInFlight", 3, 2, 6),
                parseIntOption(safe, "vulkan.maxDynamicSceneObjects", 2048, 256, 8192),
                parseIntOption(safe, "vulkan.maxPendingUploadRanges", 64, 8, 2048),
                parseIntOption(safe, "vulkan.dynamicUploadMergeGapObjects", 1, 0, 32),
                parseIntOption(safe, "vulkan.dynamicObjectSoftLimit", 1536, 128, 8192),
                parseIntOption(safe, "vulkan.maxTextureDescriptorSets", 4096, 256, 32768),
                parseDoubleOption(safe, "vulkan.descriptorRingWasteWarnRatio", 0.85, 0.1, 0.99),
                parseIntOption(safe, "vulkan.descriptorRingWasteWarnMinFrames", 8, 1, 600),
                parseIntOption(safe, "vulkan.descriptorRingWasteWarnMinCapacity", 64, 1, 65536),
                parseIntOption(safe, "vulkan.descriptorRingWasteWarnCooldownFrames", 120, 0, 10000),
                parseLongOption(safe, "vulkan.descriptorRingCapPressureWarnMinBypasses", 4, 1, 1_000_000),
                parseIntOption(safe, "vulkan.descriptorRingCapPressureWarnMinFrames", 2, 1, 600),
                parseIntOption(safe, "vulkan.descriptorRingCapPressureWarnCooldownFrames", 120, 0, 10000),
                parseIntOption(safe, "vulkan.uniformUploadSoftLimitBytes", 2 * 1024 * 1024, 4096, 64 * 1024 * 1024),
                parseIntOption(safe, "vulkan.uniformUploadWarnCooldownFrames", 120, 0, 10000),
                parseIntOption(safe, "vulkan.pendingUploadRangeSoftLimit", 48, 1, 2048),
                parseIntOption(safe, "vulkan.pendingUploadRangeWarnCooldownFrames", 120, 0, 10000),
                parseIntOption(safe, "vulkan.descriptorRingActiveSoftLimit", 2048, 64, 32768),
                parseIntOption(safe, "vulkan.descriptorRingActiveWarnCooldownFrames", 120, 0, 10000),
                parseIntOption(safe, "vulkan.reflections.probeChurnWarnMinDelta", 1, 1, 64),
                parseIntOption(safe, "vulkan.reflections.probeChurnWarnMinStreak", 3, 1, 600),
                parseIntOption(safe, "vulkan.reflections.probeChurnWarnCooldownFrames", 120, 0, 10000),
                parseIntOption(safe, "vulkan.reflections.probeQualityOverlapWarnMaxPairs", 8, 0, 10000),
                parseIntOption(safe, "vulkan.reflections.probeQualityBleedRiskWarnMaxPairs", 0, 0, 10000),
                parseIntOption(safe, "vulkan.reflections.probeQualityMinOverlapPairsWhenMultiple", 1, 0, 10000),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaInstabilityRejectMin", 0.35, 0.0, 1.0),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaInstabilityConfidenceMax", 0.70, 0.0, 1.0),
                parseLongOption(safe, "vulkan.reflections.ssrTaaInstabilityDropEventsMin", 0, 0, 1_000_000),
                parseIntOption(safe, "vulkan.reflections.ssrTaaInstabilityWarnMinFrames", 3, 1, 600),
                parseIntOption(safe, "vulkan.reflections.ssrTaaInstabilityWarnCooldownFrames", 120, 0, 10000),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaRiskEmaAlpha", 0.25, 0.01, 1.0),
                parseBoolean(safe, "vulkan.reflections.ssrTaaAdaptiveEnabled", false),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaAdaptiveTemporalBoostMax", 0.12, 0.0, 0.4),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaAdaptiveSsrStrengthScaleMin", 0.70, 0.2, 1.0),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaAdaptiveStepScaleBoostMax", 0.15, 0.0, 1.0),
                parseIntOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWindowFrames", 120, 8, 10000),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendHighRatioWarnMin", 0.40, 0.0, 1.0),
                parseIntOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnMinFrames", 3, 1, 600),
                parseIntOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnCooldownFrames", 120, 0, 10000),
                parseIntOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendWarnMinSamples", 24, 1, 10000),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloMeanSeverityMax", 0.50, 0.0, 1.0),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloHighRatioMax", 0.40, 0.0, 1.0),
                parseIntOption(safe, "vulkan.reflections.ssrTaaAdaptiveTrendSloMinSamples", 24, 1, 10000),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaHistoryRejectSeverityMin", 0.75, 0.0, 1.0),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaHistoryConfidenceDecaySeverityMin", 0.45, 0.0, 1.0),
                parseIntOption(safe, "vulkan.reflections.ssrTaaHistoryRejectRiskStreakMin", 2, 1, 600),
                parseLongOption(safe, "vulkan.reflections.ssrTaaDisocclusionRejectDropEventsMin", 2, 0, 1_000_000),
                parseDoubleOption(safe, "vulkan.reflections.ssrTaaDisocclusionRejectConfidenceMax", 0.60, 0.0, 1.0),
                parseDoubleOption(safe, "vulkan.reflections.ssrEnvelopeRejectWarnMax", 0.45, 0.0, 1.0),
                parseDoubleOption(safe, "vulkan.reflections.ssrEnvelopeConfidenceWarnMin", 0.55, 0.0, 1.0),
                parseLongOption(safe, "vulkan.reflections.ssrEnvelopeDropWarnMin", 2, 0, 1_000_000),
                parseIntOption(safe, "vulkan.reflections.ssrEnvelopeWarnMinFrames", 3, 1, 600),
                parseIntOption(safe, "vulkan.reflections.ssrEnvelopeWarnCooldownFrames", 120, 0, 10000),
                parseDoubleOption(safe, "vulkan.reflections.planarEnvelopePlaneDeltaWarnMax", 0.35, 0.0, 10.0),
                parseDoubleOption(safe, "vulkan.reflections.planarEnvelopeCoverageRatioWarnMin", 0.25, 0.0, 1.0),
                parseIntOption(safe, "vulkan.reflections.planarEnvelopeWarnMinFrames", 3, 1, 600),
                parseIntOption(safe, "vulkan.reflections.planarEnvelopeWarnCooldownFrames", 120, 0, 10000),
                parseBoolean(safe, "vulkan.reflections.planarScopeIncludeAuto", true),
                parseBoolean(safe, "vulkan.reflections.planarScopeIncludeProbeOnly", false),
                parseBoolean(safe, "vulkan.reflections.planarScopeIncludeSsrOnly", false),
                parseBoolean(safe, "vulkan.reflections.planarScopeIncludeOther", true),
                parseDoubleOption(safe, "vulkan.reflections.planarPerfMaxGpuMsLow", 1.4, 0.0, 1000.0),
                parseDoubleOption(safe, "vulkan.reflections.planarPerfMaxGpuMsMedium", 2.2, 0.0, 1000.0),
                parseDoubleOption(safe, "vulkan.reflections.planarPerfMaxGpuMsHigh", 3.0, 0.0, 1000.0),
                parseDoubleOption(safe, "vulkan.reflections.planarPerfMaxGpuMsUltra", 4.2, 0.0, 1000.0),
                parseDoubleOption(safe, "vulkan.reflections.planarPerfDrawInflationWarnMax", 2.0, 1.0, 10.0),
                parseDoubleOption(safe, "vulkan.reflections.planarPerfMemoryBudgetMb", 32.0, 1.0, 4096.0),
                parseIntOption(safe, "vulkan.reflections.planarPerfWarnMinFrames", 3, 1, 600),
                parseIntOption(safe, "vulkan.reflections.planarPerfWarnCooldownFrames", 120, 0, 10000),
                parseBoolean(safe, "vulkan.reflections.planarPerfRequireGpuTimestamp", false),
                parseBoolean(safe, "vulkan.reflections.rtSingleBounceEnabled", true),
                parseBoolean(safe, "vulkan.reflections.rtMultiBounceEnabled", false),
                parseBoolean(safe, "vulkan.reflections.rtDedicatedDenoisePipelineEnabled", true),
                parseDoubleOption(safe, "vulkan.reflections.rtDenoiseStrength", 0.65, 0.0, 1.0),
                parseIntOption(safe, "vulkan.reflections.probeUpdateCadenceFrames", 1, 1, 120),
                parseIntOption(safe, "vulkan.reflections.probeMaxVisible", 64, 1, 256),
                parseDoubleOption(safe, "vulkan.reflections.probeLodDepthScale", 1.0, 0.25, 4.0),
                parseIntOption(safe, "vulkan.taaDebugView", 0, 0, 5),
                parseShadowFilterPath(safe.get("vulkan.shadow.filterPath")),
                parseBoolean(safe, "vulkan.shadow.contactShadows", false),
                parseShadowRtMode(safe.get("vulkan.shadow.rtMode")),
                parseBoolean(safe, "vulkan.shadow.rtBvhStrict", false),
                (float) parseDoubleOption(safe, "vulkan.shadow.rtDenoiseStrength", 0.65, 0.0, 1.0),
                (float) parseDoubleOption(safe, "vulkan.shadow.rtRayLength", 80.0, 1.0, 500.0),
                parseIntOption(safe, "vulkan.shadow.rtSampleCount", 2, 1, 16),
                (float) parseDoubleOption(safe, "vulkan.shadow.rtDedicatedDenoiseStrength", -1.0, -1.0, 1.0),
                (float) parseDoubleOption(safe, "vulkan.shadow.rtDedicatedRayLength", -1.0, -1.0, 500.0),
                parseIntOption(safe, "vulkan.shadow.rtDedicatedSampleCount", -1, -1, 16),
                (float) parseDoubleOption(safe, "vulkan.shadow.rtProductionDenoiseStrength", -1.0, -1.0, 1.0),
                (float) parseDoubleOption(safe, "vulkan.shadow.rtProductionRayLength", -1.0, -1.0, 500.0),
                parseIntOption(safe, "vulkan.shadow.rtProductionSampleCount", -1, -1, 16),
                (float) parseDoubleOption(safe, "vulkan.shadow.pcssSoftness", 1.0, 0.25, 2.0),
                (float) parseDoubleOption(safe, "vulkan.shadow.momentBlend", 1.0, 0.25, 1.5),
                (float) parseDoubleOption(safe, "vulkan.shadow.momentBleedReduction", 1.0, 0.25, 1.5),
                (float) parseDoubleOption(safe, "vulkan.shadow.contactStrength", 1.0, 0.25, 2.0),
                (float) parseDoubleOption(safe, "vulkan.shadow.contactTemporalMotionScale", 1.0, 0.1, 3.0),
                (float) parseDoubleOption(safe, "vulkan.shadow.contactTemporalMinStability", 0.42, 0.2, 1.0),
                parseIntOption(safe, "vulkan.shadow.maxShadowedLocalLights", 0, 0, 8),
                parseIntOption(safe, "vulkan.shadow.maxLocalShadowLayers", 0, 0, 24),
                parseIntOption(safe, "vulkan.shadow.maxShadowFacesPerFrame", 0, 0, 24),
                parseBoolean(safe, "vulkan.shadow.scheduler.enabled", true),
                parseIntOption(safe, "vulkan.shadow.scheduler.heroPeriod", 1, 1, 16),
                parseIntOption(safe, "vulkan.shadow.scheduler.midPeriod", 2, 1, 32),
                parseIntOption(safe, "vulkan.shadow.scheduler.distantPeriod", 4, 1, 64),
                parseBoolean(safe, "vulkan.shadow.directionalTexelSnapEnabled", true),
                (float) parseDoubleOption(safe, "vulkan.shadow.directionalTexelSnapScale", 1.0, 0.25, 4.0)
        );
    }

    private static boolean parseBoolean(Map<String, String> options, String key, boolean fallback) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw);
    }

    private static int parseIntOption(Map<String, String> options, String key, int fallback, int min, int max) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLongOption(Map<String, String> options, String key, long fallback, long min, long max) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Long.parseLong(raw.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDoubleOption(Map<String, String> options, String key, double fallback, double min, double max) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Double.parseDouble(raw.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String parseShadowFilterPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "pcf";
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "pcf", "pcss", "vsm", "evsm" -> normalized;
            default -> "pcf";
        };
    }

    private static String parseShadowRtMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "off";
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "off", "optional", "force", "bvh", "bvh_dedicated", "bvh_production", "rt_native", "rt_native_denoised" -> normalized;
            default -> "off";
        };
    }

    record Parsed(
            boolean mockContext,
            boolean windowVisible,
            boolean forceDeviceLostOnRender,
            boolean postOffscreenRequested,
            boolean forceInitFailure,
            int meshGeometryCacheMaxEntries,
            int framesInFlight,
            int maxDynamicSceneObjects,
            int maxPendingUploadRanges,
            int dynamicUploadMergeGapObjects,
            int dynamicObjectSoftLimit,
            int descriptorRingMaxSetCapacity,
            double descriptorRingWasteWarnRatio,
            int descriptorRingWasteWarnMinFrames,
            int descriptorRingWasteWarnMinCapacity,
            int descriptorRingWasteWarnCooldownFrames,
            long descriptorRingCapPressureWarnMinBypasses,
            int descriptorRingCapPressureWarnMinFrames,
            int descriptorRingCapPressureWarnCooldownFrames,
            int uniformUploadSoftLimitBytes,
            int uniformUploadWarnCooldownFrames,
            int pendingUploadRangeSoftLimit,
            int pendingUploadRangeWarnCooldownFrames,
            int descriptorRingActiveSoftLimit,
            int descriptorRingActiveWarnCooldownFrames,
            int reflectionProbeChurnWarnMinDelta,
            int reflectionProbeChurnWarnMinStreak,
            int reflectionProbeChurnWarnCooldownFrames,
            int reflectionProbeQualityOverlapWarnMaxPairs,
            int reflectionProbeQualityBleedRiskWarnMaxPairs,
            int reflectionProbeQualityMinOverlapPairsWhenMultiple,
            double reflectionSsrTaaInstabilityRejectMin,
            double reflectionSsrTaaInstabilityConfidenceMax,
            long reflectionSsrTaaInstabilityDropEventsMin,
            int reflectionSsrTaaInstabilityWarnMinFrames,
            int reflectionSsrTaaInstabilityWarnCooldownFrames,
            double reflectionSsrTaaRiskEmaAlpha,
            boolean reflectionSsrTaaAdaptiveEnabled,
            double reflectionSsrTaaAdaptiveTemporalBoostMax,
            double reflectionSsrTaaAdaptiveSsrStrengthScaleMin,
            double reflectionSsrTaaAdaptiveStepScaleBoostMax,
            int reflectionSsrTaaAdaptiveTrendWindowFrames,
            double reflectionSsrTaaAdaptiveTrendHighRatioWarnMin,
            int reflectionSsrTaaAdaptiveTrendWarnMinFrames,
            int reflectionSsrTaaAdaptiveTrendWarnCooldownFrames,
            int reflectionSsrTaaAdaptiveTrendWarnMinSamples,
            double reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax,
            double reflectionSsrTaaAdaptiveTrendSloHighRatioMax,
            int reflectionSsrTaaAdaptiveTrendSloMinSamples,
            double reflectionSsrTaaHistoryRejectSeverityMin,
            double reflectionSsrTaaHistoryConfidenceDecaySeverityMin,
            int reflectionSsrTaaHistoryRejectRiskStreakMin,
            long reflectionSsrTaaDisocclusionRejectDropEventsMin,
            double reflectionSsrTaaDisocclusionRejectConfidenceMax,
            double reflectionSsrEnvelopeRejectWarnMax,
            double reflectionSsrEnvelopeConfidenceWarnMin,
            long reflectionSsrEnvelopeDropWarnMin,
            int reflectionSsrEnvelopeWarnMinFrames,
            int reflectionSsrEnvelopeWarnCooldownFrames,
            double reflectionPlanarEnvelopePlaneDeltaWarnMax,
            double reflectionPlanarEnvelopeCoverageRatioWarnMin,
            int reflectionPlanarEnvelopeWarnMinFrames,
            int reflectionPlanarEnvelopeWarnCooldownFrames,
            boolean reflectionPlanarScopeIncludeAuto,
            boolean reflectionPlanarScopeIncludeProbeOnly,
            boolean reflectionPlanarScopeIncludeSsrOnly,
            boolean reflectionPlanarScopeIncludeOther,
            double reflectionPlanarPerfMaxGpuMsLow,
            double reflectionPlanarPerfMaxGpuMsMedium,
            double reflectionPlanarPerfMaxGpuMsHigh,
            double reflectionPlanarPerfMaxGpuMsUltra,
            double reflectionPlanarPerfDrawInflationWarnMax,
            double reflectionPlanarPerfMemoryBudgetMb,
            int reflectionPlanarPerfWarnMinFrames,
            int reflectionPlanarPerfWarnCooldownFrames,
            boolean reflectionPlanarPerfRequireGpuTimestamp,
            boolean reflectionRtSingleBounceEnabled,
            boolean reflectionRtMultiBounceEnabled,
            boolean reflectionRtDedicatedDenoisePipelineEnabled,
            double reflectionRtDenoiseStrength,
            int reflectionProbeUpdateCadenceFrames,
            int reflectionProbeMaxVisible,
            double reflectionProbeLodDepthScale,
            int taaDebugView,
            String shadowFilterPath,
            boolean shadowContactShadows,
            String shadowRtMode,
            boolean shadowRtBvhStrict,
            float shadowRtDenoiseStrength,
            float shadowRtRayLength,
            int shadowRtSampleCount,
            float shadowRtDedicatedDenoiseStrength,
            float shadowRtDedicatedRayLength,
            int shadowRtDedicatedSampleCount,
            float shadowRtProductionDenoiseStrength,
            float shadowRtProductionRayLength,
            int shadowRtProductionSampleCount,
            float shadowPcssSoftness,
            float shadowMomentBlend,
            float shadowMomentBleedReduction,
            float shadowContactStrength,
            float shadowContactTemporalMotionScale,
            float shadowContactTemporalMinStability,
            int shadowMaxShadowedLocalLights,
            int shadowMaxLocalLayers,
            int shadowMaxFacesPerFrame,
            boolean shadowSchedulerEnabled,
            int shadowSchedulerHeroPeriod,
            int shadowSchedulerMidPeriod,
            int shadowSchedulerDistantPeriod,
            boolean shadowDirectionalTexelSnapEnabled,
            float shadowDirectionalTexelSnapScale
    ) {
    }
}
