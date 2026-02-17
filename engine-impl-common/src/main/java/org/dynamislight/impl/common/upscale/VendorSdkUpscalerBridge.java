package org.dynamislight.impl.common.upscale;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VendorSdkUpscalerBridge implements ExternalUpscalerBridge {
    private Map<String, String> options = Map.of();
    private final Map<String, VendorUpscalerSdkProvider> providers = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "vendor-sdk-bridge";
    }

    @Override
    public boolean initialize(InitContext context) {
        options = context == null || context.backendOptions() == null
                ? Map.of()
                : Map.copyOf(context.backendOptions());
        providers.clear();
        initializeProvider("fsr");
        initializeProvider("xess");
        initializeProvider("dlss");
        return true;
    }

    @Override
    public Decision evaluate(DecisionInput input) {
        if (input == null || input.upscalerMode() == null || input.upscalerMode().isBlank()) {
            return Decision.inactive("missing upscaler mode");
        }
        String mode = input.upscalerMode().trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "fsr" -> vendorDecision("fsr", input, 0.67f, 0.58f, 0.50f, 0.92f);
            case "xess" -> vendorDecision("xess", input, 0.66f, 0.56f, 0.50f, 0.90f);
            case "dlss" -> vendorDecision("dlss", input, 0.64f, 0.54f, 0.48f, 0.88f);
            default -> Decision.inactive("unsupported vendor mode: " + mode);
        };
    }

    private Decision vendorDecision(
            String vendor,
            DecisionInput input,
            float qualityScale,
            float balancedScale,
            float perfScale,
            float sharpen
    ) {
        String quality = input == null ? "quality" : input.upscalerQuality();
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
        VendorUpscalerSdkProvider provider = providers.get(vendor);
        if (provider != null) {
            Decision providerDecision = provider.evaluate(
                    input,
                    renderScale,
                    sharpenOverride,
                    "vendor provider active: " + vendor
            );
            if (providerDecision != null && providerDecision.nativeActive()) {
                return providerDecision;
            }
            String providerDetail = providerDecision == null ? provider.detail()
                    : (providerDecision.detail() == null || providerDecision.detail().isBlank()
                    ? provider.detail()
                    : providerDecision.detail());
            String fallbackDetail = "provider inactive (" + providerDetail + "), using bridge fallback: " + vendor
                    + " renderScale=" + renderScale + " sharpen=" + sharpenOverride;
            return new Decision(true, null, null, sharpenOverride, renderScale, null, fallbackDetail);
        }
        String detail = "vendor bridge active: " + vendor + " renderScale=" + renderScale + " sharpen=" + sharpenOverride;
        return new Decision(true, null, null, sharpenOverride, renderScale, null, detail);
    }

    private void initializeProvider(String vendor) {
        String providerClass = firstNonBlank(
                options.get("dle.upscaler.vendor." + vendor + ".providerClass"),
                options.get("vulkan.upscaler.vendor." + vendor + ".providerClass"),
                options.get("opengl.upscaler.vendor." + vendor + ".providerClass")
        );
        if (providerClass == null) {
            return;
        }
        try {
            Class<?> cls = Class.forName(providerClass);
            Object instance = cls.getDeclaredConstructor().newInstance();
            if (!(instance instanceof VendorUpscalerSdkProvider provider)) {
                return;
            }
            if (!vendor.equalsIgnoreCase(provider.vendor())) {
                return;
            }
            if (provider.initialize(options)) {
                providers.put(vendor, provider);
            }
        } catch (Throwable ignored) {
            // Keep runtime fallback path active when provider class cannot be loaded.
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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
