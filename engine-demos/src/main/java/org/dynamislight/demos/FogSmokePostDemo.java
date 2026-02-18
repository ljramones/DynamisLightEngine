package org.dynamislight.demos;

import java.util.Map;
import org.dynamislight.api.scene.SceneDescriptor;

final class FogSmokePostDemo implements DemoDefinition {
    @Override
    public String id() {
        return "fog-smoke-post";
    }

    @Override
    public String description() {
        return "Height fog and post-process interaction lane.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.fogSmokePostScene();
    }

    @Override
    public Map<String, String> backendOptions(DemoRequest request) {
        return Map.of(
                "vulkan.fogProfile", "quality",
                "vulkan.postProfile", "quality"
        );
    }
}
