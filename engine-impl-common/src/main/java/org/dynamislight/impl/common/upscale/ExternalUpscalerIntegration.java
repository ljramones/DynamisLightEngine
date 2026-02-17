package org.dynamislight.impl.common.upscale;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ExternalUpscalerIntegration {
    private final ExternalUpscalerBridge bridge;
    private final String providerId;
    private final String statusDetail;

    private ExternalUpscalerIntegration(ExternalUpscalerBridge bridge, String providerId, String statusDetail) {
        this.bridge = bridge;
        this.providerId = providerId == null || providerId.isBlank() ? "none" : providerId;
        this.statusDetail = statusDetail == null || statusDetail.isBlank() ? "inactive" : statusDetail;
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
            return new ExternalUpscalerIntegration(bridge, bridge.id(), "native bridge active");
        } catch (Throwable t) {
            return inactive("bridge load/init failure for " + bridgeClass + ": " + t.getClass().getSimpleName() + " " + safeMessage(t));
        }
    }

    public static ExternalUpscalerIntegration inactive(String detail) {
        return new ExternalUpscalerIntegration(null, "none", detail);
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
            return decision;
        } catch (Throwable t) {
            return ExternalUpscalerBridge.Decision.inactive(
                    "bridge evaluate() failure: " + t.getClass().getSimpleName() + " " + safeMessage(t)
            );
        }
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
