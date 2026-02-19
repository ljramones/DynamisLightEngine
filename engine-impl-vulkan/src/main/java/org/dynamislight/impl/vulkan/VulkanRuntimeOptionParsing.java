package org.dynamislight.impl.vulkan;

import java.util.Map;

import org.dynamislight.api.scene.AntiAliasingDesc;
import org.dynamislight.api.scene.PostProcessDesc;

final class VulkanRuntimeOptionParsing {
    private VulkanRuntimeOptionParsing() {
    }

    static ReflectionProfile parseReflectionProfile(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReflectionProfile.BALANCED;
        }
        return switch (raw.trim().toLowerCase()) {
            case "performance" -> ReflectionProfile.PERFORMANCE;
            case "quality" -> ReflectionProfile.QUALITY;
            case "stability" -> ReflectionProfile.STABILITY;
            default -> ReflectionProfile.BALANCED;
        };
    }

    static AaPreset parseAaPreset(String raw) {
        if (raw == null || raw.isBlank()) {
            return AaPreset.BALANCED;
        }
        try {
            return AaPreset.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AaPreset.BALANCED;
        }
    }

    static AaMode parseAaMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return AaMode.TAA;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return AaMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return AaMode.TAA;
        }
    }

    static UpscalerMode parseUpscalerMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return UpscalerMode.NONE;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return UpscalerMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return UpscalerMode.NONE;
        }
    }

    static UpscalerQuality parseUpscalerQuality(String raw) {
        if (raw == null || raw.isBlank()) {
            return UpscalerQuality.QUALITY;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return UpscalerQuality.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return UpscalerQuality.QUALITY;
        }
    }

    static TsrControls parseTsrControls(Map<String, String> options, String prefix) {
        return new TsrControls(
                parseFloatOption(options, prefix + "tsrHistoryWeight", 0.90f, 0.50f, 0.99f),
                parseFloatOption(options, prefix + "tsrResponsiveMask", 0.65f, 0.0f, 1.0f),
                parseFloatOption(options, prefix + "tsrNeighborhoodClamp", 0.88f, 0.50f, 1.20f),
                parseFloatOption(options, prefix + "tsrReprojectionConfidence", 0.85f, 0.10f, 1.0f),
                parseFloatOption(options, prefix + "tsrSharpen", 0.14f, 0.0f, 0.35f),
                parseFloatOption(options, prefix + "tsrAntiRinging", 0.75f, 0.0f, 1.0f),
                parseFloatOption(options, prefix + "tsrRenderScale", 0.60f, 0.50f, 1.0f),
                parseFloatOption(options, prefix + "tuuaRenderScale", 0.72f, 0.50f, 1.0f)
        );
    }

    static AaMode resolveAaMode(PostProcessDesc postProcess, AaMode fallback) {
        if (postProcess == null || postProcess.antiAliasing() == null) {
            return fallback;
        }
        String raw = postProcess.antiAliasing().mode();
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return parseAaMode(raw);
    }

    static int resolveTaaDebugView(PostProcessDesc postProcess, int fallback) {
        if (postProcess == null || postProcess.antiAliasing() == null) {
            return fallback;
        }
        AntiAliasingDesc aa = postProcess.antiAliasing();
        return Math.max(0, Math.min(5, aa.debugView()));
    }

    private static float parseFloatOption(Map<String, String> options, String key, float fallback, float min, float max) {
        String raw = options == null ? null : options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Float.parseFloat(raw.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
