package org.dynamislight.impl.common.upscale;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public final class ExternalUpscalerIntegration {
    private final ExternalUpscalerBridge bridge;
    private final String providerId;
    private final String statusDetail;
    private final Map<String, String> vendorStatus;

    private ExternalUpscalerIntegration(ExternalUpscalerBridge bridge, String providerId, String statusDetail, Map<String, String> vendorStatus) {
        this.bridge = bridge;
        this.providerId = providerId == null || providerId.isBlank() ? "none" : providerId;
        this.statusDetail = statusDetail == null || statusDetail.isBlank() ? "inactive" : statusDetail;
        this.vendorStatus = vendorStatus == null ? Map.of() : Map.copyOf(vendorStatus);
    }

    public static ExternalUpscalerIntegration create(String backend, String optionPrefix, Map<String, String> options) {
        Map<String, String> safeOptions = options == null ? Map.of() : options;
        String prefix = optionPrefix == null ? "" : optionPrefix;
        if (!parseBoolean(safeOptions, prefix + "upscaler.nativeEnabled", true)) {
            return inactive("disabled by " + prefix + "upscaler.nativeEnabled=false");
        }
        String bridgeClass = firstNonBlank(
                safeOptions.get(prefix + "upscaler.bridgeClass"),
                safeOptions.get("dle.upscaler.bridgeClass")
        );
        if (bridgeClass == null) {
            return inactive("bridge class not configured (" + prefix + "upscaler.bridgeClass)");
        }
        try {
            loadOptionalLibraries(firstNonBlank(
                    safeOptions.get(prefix + "upscaler.bridgeLibrary"),
                    safeOptions.get("dle.upscaler.bridgeLibrary")
            ));
            Class<?> cls = Class.forName(bridgeClass);
            Object instance = cls.getDeclaredConstructor().newInstance();
            if (!(instance instanceof ExternalUpscalerBridge bridge)) {
                return inactive("configured bridge class is not an ExternalUpscalerBridge: " + bridgeClass);
            }
            boolean initialized = bridge.initialize(new ExternalUpscalerBridge.InitContext(backend, Map.copyOf(safeOptions)));
            if (!initialized) {
                return inactive("bridge initialize() returned false: " + bridgeClass);
            }
            Map<String, String> vendorStatus = resolveVendorStatus(safeOptions, prefix);
            String vendorSummary = summarizeVendorStatus(vendorStatus);
            String detail = vendorSummary == null ? "native bridge active" : ("native bridge active; " + vendorSummary);
            return new ExternalUpscalerIntegration(bridge, bridge.id(), detail, vendorStatus);
        } catch (Throwable t) {
            return inactive("bridge load/init failure for " + bridgeClass + ": " + t.getClass().getSimpleName() + " " + safeMessage(t));
        }
    }

    public static ExternalUpscalerIntegration inactive(String detail) {
        return new ExternalUpscalerIntegration(null, "none", detail, Map.of());
    }

    public boolean active() {
        return bridge != null;
    }

    public String providerId() {
        return providerId;
    }

    public String statusDetail() {
        return statusDetail;
    }

    public ExternalUpscalerBridge.Decision evaluate(ExternalUpscalerBridge.DecisionInput input) {
        if (bridge == null) {
            return ExternalUpscalerBridge.Decision.inactive(statusDetail);
        }
        try {
            ExternalUpscalerBridge.Decision decision = bridge.evaluate(input);
            if (decision == null) {
                return ExternalUpscalerBridge.Decision.inactive("bridge returned null decision");
            }
            String mode = input == null || input.upscalerMode() == null
                    ? ""
                    : input.upscalerMode().trim().toLowerCase();
            if ("fsr".equals(mode) || "xess".equals(mode) || "dlss".equals(mode)) {
                String status = vendorStatus.get(mode);
                if (status != null && !status.startsWith("ready")) {
                    return ExternalUpscalerBridge.Decision.inactive(mode + " vendor unavailable: " + status);
                }
            }
            return decision;
        } catch (Throwable t) {
            return ExternalUpscalerBridge.Decision.inactive(
                    "bridge evaluate() failure: " + t.getClass().getSimpleName() + " " + safeMessage(t)
            );
        }
    }

    private static Map<String, String> resolveVendorStatus(Map<String, String> options, String prefix) {
        Map<String, String> status = new HashMap<>();
        status.put("fsr", resolveVendorLibraryStatus(options, prefix, "fsr"));
        status.put("xess", resolveVendorLibraryStatus(options, prefix, "xess"));
        status.put("dlss", resolveVendorLibraryStatus(options, prefix, "dlss"));
        return status;
    }

    private static String resolveVendorLibraryStatus(Map<String, String> options, String prefix, String vendor) {
        String configured = firstNonBlank(
                options.get(prefix + "upscaler.vendor." + vendor + ".library"),
                options.get("dle.upscaler.vendor." + vendor + ".library")
        );
        if (configured == null) {
            return "unconfigured";
        }
        String trimmed = configured.trim();
        try {
            if (looksLikePath(trimmed)) {
                Path p = Path.of(trimmed);
                if (!Files.isRegularFile(p)) {
                    return "missing path " + trimmed;
                }
                return "ready(path)";
            }
            return "ready(name)";
        } catch (Throwable t) {
            return "invalid config " + safeMessage(t);
        }
    }

    private static String summarizeVendorStatus(Map<String, String> vendorStatus) {
        if (vendorStatus == null || vendorStatus.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(", ", "vendors[", "]");
        joiner.add("fsr=" + vendorStatus.getOrDefault("fsr", "unknown"));
        joiner.add("xess=" + vendorStatus.getOrDefault("xess", "unknown"));
        joiner.add("dlss=" + vendorStatus.getOrDefault("dlss", "unknown"));
        return joiner.toString();
    }

    private static void loadOptionalLibraries(String rawLibraries) {
        if (rawLibraries == null || rawLibraries.isBlank()) {
            return;
        }
        String[] tokens = rawLibraries.split(",");
        for (String token : tokens) {
            String trimmed = token == null ? "" : token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (looksLikePath(trimmed)) {
                Path p = Path.of(trimmed);
                if (!Files.isRegularFile(p)) {
                    throw new IllegalStateException("missing bridge library path: " + trimmed);
                }
                System.load(p.toAbsolutePath().toString());
            } else {
                System.loadLibrary(trimmed);
            }
        }
    }

    private static boolean looksLikePath(String value) {
        return value.contains("/") || value.contains("\\");
    }

    private static boolean parseBoolean(Map<String, String> options, String key, boolean fallback) {
        String raw = options.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.trim());
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

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.isBlank()) {
            return "<no-message>";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
