package org.dynamislight.demos;

import java.util.Map;
import org.dynamislight.api.scene.SceneDescriptor;

final class ReflectionsPlanarDemo implements DemoDefinition {
    @Override
    public String id() {
        return "reflections-planar";
    }

    @Override
    public String description() {
        return "Planar reflection lane with clip-plane configuration.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.reflectionsPlanarScene();
    }

    @Override
    public Map<String, String> backendOptions(DemoRequest request) {
        return Map.of("vulkan.reflectionsProfile", "quality");
    }
}

