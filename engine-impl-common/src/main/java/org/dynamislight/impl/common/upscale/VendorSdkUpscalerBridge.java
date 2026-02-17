package org.dynamislight.impl.common.upscale;

import java.util.Locale;
import java.util.Map;

public final class VendorSdkUpscalerBridge implements ExternalUpscalerBridge {
    private Map<String, String> options = Map.of();

    @Override
    public String id() {
        return "vendor-sdk-bridge";
    }

    @Override
    public boolean initialize(InitContext context) {
        options = context == null || context.backendOptions() == null
                ? Map.of()
                : Map.copyOf(context.backendOptions());
        return true;
    }

    @Override
    public Decision evaluate(DecisionInput input) {
        if (input == null || input.upscalerMode() == null || input.upscalerMode().isBlank()) {
            return Decision.inactive("missing upscaler mode");
        }
        String mode = input.upscalerMode().trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "fsr" -> vendorDecision("fsr", input.upscalerQuality(), 0.67f, 0.58f, 0.50f, 0.92f);
            case "xess" -> vendorDecision("xess", input.upscalerQuality(), 0.66f, 0.56f, 0.50f, 0.90f);
            case "dlss" -> vendorDecision("dlss", input.upscalerQuality(), 0.64f, 0.54f, 0.48f, 0.88f);
            default -> Decision.inactive("unsupported vendor mode: " + mode);
        };
    }

    private Decision vendorDecision(
            String vendor,
            String quality,
            float qualityScale,
            float balancedScale,
            float perfScale,
            float sharpen
    ) {
        float configuredScale = parseFloatOption("dle.upscaler.vendor." + vendor + ".renderScale", -1.0f);
        float renderScale;
        if (configuredScale > 0f && configuredScale <= 1.0f) {
            renderScale = configuredScale;
        } else {
            renderScale = switch (quality == null ? "quality" : quality.trim().toLowerCase(Locale.ROOT)) {
                case "ultra_quality" -> Math.min(1.0f, qualityScale + 0.06f);
                case "quality" -> qualityScale;
                case "balanced" -> balancedScale;
                case "performance" -> perfScale;
                default -> qualityScale;
            };
        }
        float sharpenOverride = parseFloatOption("dle.upscaler.vendor." + vendor + ".sharpen", sharpen);
        String detail = "vendor bridge active: " + vendor + " renderScale=" + renderScale + " sharpen=" + sharpenOverride;
        return new Decision(true, null, null, sharpenOverride, renderScale, null, detail);
    }

    private float parseFloatOption(String key, float fallback) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
