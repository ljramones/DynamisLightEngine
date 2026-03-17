package org.dynamisengine.light.impl.opengl;

import java.util.Map;
import org.dynamisengine.light.api.scene.PostProcessDesc;

/**
 * Config-parsing utilities for anti-aliasing presets, upscaler modes, reflection
 * profiles, and TSR controls.  All methods are stateless.
 */
final class OpenGlRenderingOptions {

    private OpenGlRenderingOptions() {
    }

    // ── enum types (package-private so OpenGlEngineRuntime can use them) ──

    enum AaPreset {
        PERFORMANCE,
        BALANCED,
        QUALITY,
        STABILITY
    }

    enum AaMode {
        TAA,
        TSR,
        TUUA,
        MSAA_SELECTIVE,
        HYBRID_TUUA_MSAA,
        DLAA,
        FXAA_LOW
    }

    enum UpscalerMode {
        NONE,
        FSR,
        XESS,
        DLSS
    }

    enum UpscalerQuality {
        PERFORMANCE,
        BALANCED,
        QUALITY,
        ULTRA_QUALITY
    }

    enum ReflectionMode {
        IBL_ONLY,
        SSR,
        PLANAR,
        HYBRID,
        RT_HYBRID
    }

    enum ReflectionProfile {
        PERFORMANCE,
        BALANCED,
        QUALITY,
        STABILITY
    }

    record TsrControls(
            float historyWeight,
            float responsiveMask,
            float neighborhoodClamp,
            float reprojectionConfidence,
            float sharpen,
            float antiRinging,
            float tsrRenderScale,
            float tuuaRenderScale
    ) {
    }

    // ── parsing ──

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

    static ReflectionMode parseReflectionMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReflectionMode.IBL_ONLY;
        }
        return switch (raw.trim().toLowerCase()) {
            case "ssr" -> ReflectionMode.SSR;
            case "planar" -> ReflectionMode.PLANAR;
            case "hybrid" -> ReflectionMode.HYBRID;
            case "rt_hybrid", "rt" -> ReflectionMode.RT_HYBRID;
            default -> ReflectionMode.IBL_ONLY;
        };
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

    static float parseFloatOption(Map<String, String> options, String key, float fallback, float min, float max) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Float.parseFloat(raw.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
        return Math.max(0, Math.min(5, postProcess.antiAliasing().debugView()));
    }
}
