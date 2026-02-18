package org.dynamislight.demos;

import java.util.Map;
import org.dynamislight.api.config.QualityTier;

record DemoRequest(
        String demoId,
        String backendId,
        QualityTier qualityTier,
        int width,
        int height,
        int seconds,
        boolean mockContext,
        String aaPreset,
        int taaDebugView,
        Map<String, String> args
) {
    String arg(String key, String fallback) {
        String value = args.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    int argInt(String key, int fallback, int min, int max) {
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed < min) {
                return min;
            }
            if (parsed > max) {
                return max;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    float argFloat(String key, float fallback, float min, float max) {
        String raw = args.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            float parsed = Float.parseFloat(raw);
            if (parsed < min) {
                return min;
            }
            if (parsed > max) {
                return max;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
