package org.dynamislight.demos;

import java.util.Locale;
import java.util.Map;
import org.dynamislight.api.scene.SceneDescriptor;

final class BackendCompareSmokeDemo implements DemoDefinition {
    @Override
    public String id() {
        return "backend-compare-smoke";
    }

    @Override
    public String description() {
        return "Quick backend smoke lane intended to run on Vulkan and OpenGL.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.sceneWithAa("taa", true, 0.80f, 1.0f);
    }

    @Override
    public Map<String, String> backendOptions(DemoRequest request) {
        String prefix = request.backendId().toLowerCase(Locale.ROOT);
        return Map.of(prefix + ".aaPreset", "stability");
    }
}
