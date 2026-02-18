package org.dynamislight.demos;

import java.util.Map;
import org.dynamislight.api.scene.SceneDescriptor;

interface DemoDefinition {
    String id();

    String description();

    SceneDescriptor buildScene(DemoRequest request);

    default Map<String, String> backendOptions(DemoRequest request) {
        return Map.of();
    }
}
