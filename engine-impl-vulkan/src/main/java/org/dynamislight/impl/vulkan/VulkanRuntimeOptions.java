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
                parseIntOption(safe, "vulkan.taaDebugView", 0, 0, 4)
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
            int taaDebugView
    ) {
    }
}
