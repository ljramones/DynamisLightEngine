package org.dynamislight.demos;

import java.util.Locale;
import java.util.Map;
import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.runtime.EngineApiVersions;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.spi.registry.BackendRegistry;

final class CapabilityProbeDemo implements DemoDefinition {
    private static final EngineApiVersion HOST_REQUIRED_API = new EngineApiVersion(1, 0, 0);

    @Override
    public String id() {
        return "capability-probe";
    }

    @Override
    public String description() {
        return "Runtime capability probe for backend availability and API compatibility.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        emitProbeReport(request);
        return DemoScenes.sceneWithAa("taa", true, 0.80f, 1.0f);
    }

    @Override
    public Map<String, String> backendOptions(DemoRequest request) {
        String prefix = request.backendId().toLowerCase(Locale.ROOT);
        return Map.of(prefix + ".aaPreset", "stability");
    }

    private static void emitProbeReport(DemoRequest request) {
        BackendRegistry registry = BackendRegistry.discover();
        var providers = registry.providers();
        System.out.println("capability-probe discovered-backends=" + providers.size());
        for (var provider : providers) {
            var info = provider.info();
            System.out.printf(
                    Locale.ROOT,
                    "capability-probe backend=%s display=%s version=%s api=%s%n",
                    info.backendId(),
                    sanitize(info.displayName()),
                    sanitize(info.version()),
                    provider.supportedApiVersion()
            );
        }

        var target = providers.stream()
                .filter(provider -> provider.backendId().equalsIgnoreCase(request.backendId()))
                .findFirst();
        if (target.isPresent()) {
            EngineApiVersion runtimeApi = target.get().supportedApiVersion();
            boolean compatible = EngineApiVersions.isRuntimeCompatible(HOST_REQUIRED_API, runtimeApi);
            System.out.printf(
                    Locale.ROOT,
                    "capability-probe selected=%s runtimeApi=%s hostRequired=%s compatible=%s%n",
                    request.backendId(),
                    runtimeApi,
                    HOST_REQUIRED_API,
                    compatible
            );
        } else {
            System.out.println("capability-probe selected=" + request.backendId() + " compatible=false reason=backend-not-found");
        }

        System.out.printf(
                Locale.ROOT,
                "capability-probe run width=%d height=%d quality=%s mock=%s%n",
                request.width(),
                request.height(),
                request.qualityTier(),
                request.mockContext()
        );
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
