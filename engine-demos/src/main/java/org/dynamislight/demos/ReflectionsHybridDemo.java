package org.dynamislight.demos;

import java.util.Map;
import org.dynamislight.api.scene.SceneDescriptor;

final class ReflectionsHybridDemo implements DemoDefinition {
    @Override
    public String id() {
        return "reflections-hybrid";
    }

    @Override
    public String description() {
        return "Hybrid reflections lane combining SSR, probe blend, and fallback policy.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.reflectionsHybridScene();
    }

    @Override
    public Map<String, String> backendOptions(DemoRequest request) {
        return Map.of("vulkan.reflectionsProfile", "quality");
    }
}
