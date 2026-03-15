package org.dynamisengine.light.demos;

import org.dynamisengine.light.api.scene.SceneDescriptor;

final class LightsLocalArrayDemo implements DemoDefinition {
    @Override
    public String id() {
        return "lights-local-array";
    }

    @Override
    public String description() {
        return "Multi-local-light scene for attenuation and light budget behavior.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.lightsLocalArrayScene();
    }
}

