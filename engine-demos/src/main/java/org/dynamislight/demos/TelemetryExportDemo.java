package org.dynamislight.demos;

import java.util.Map;
import java.util.Locale;
import org.dynamislight.api.scene.SceneDescriptor;

final class TelemetryExportDemo implements DemoDefinition {
    @Override
    public String id() {
        return "telemetry-export";
    }

    @Override
    public String description() {
        return "CLI telemetry export lane for stable JSONL/summary capture.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.sceneWithAa("taa", true, 0.80f, 1.0f);
    }

    @Override
    public Map<String, String> backendOptions(DemoRequest request) {
        String prefix = request.backendId().toLowerCase(Locale.ROOT);
        return Map.of(
                prefix + ".aaMode", "taa",
                prefix + ".aaPreset", "stability"
        );
    }
}
