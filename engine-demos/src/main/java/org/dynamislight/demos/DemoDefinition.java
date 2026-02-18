package org.dynamislight.demos;

import java.util.Map;
import org.dynamislight.api.scene.SceneDescriptor;

interface DemoDefinition {
    String id();

    String description();

    SceneDescriptor buildScene(DemoRequest request);

    default boolean isDynamicScene() {
        return false;
    }

    default SceneDescriptor sceneForFrame(DemoRequest request, int frameIndex, int totalFrames, double elapsedSeconds) {
        return buildScene(request);
    }

    default Map<String, String> backendOptions(DemoRequest request) {
        return Map.of();
    }
}
