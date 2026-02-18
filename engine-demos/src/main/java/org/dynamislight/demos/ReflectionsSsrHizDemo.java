package org.dynamislight.demos;

import java.util.Map;
import org.dynamislight.api.scene.SceneDescriptor;

final class ReflectionsSsrHizDemo implements DemoDefinition {
    @Override
    public String id() {
        return "reflections-ssr-hiz";
    }

    @Override
    public String description() {
        return "SSR + Hi-Z reflection lane on glossy surfaces.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.reflectionsSsrHizScene();
    }

    @Override
    public Map<String, String> backendOptions(DemoRequest request) {
        return Map.of("vulkan.reflectionsProfile", "quality");
    }
}

