package org.dynamisengine.light.demos;

import org.dynamisengine.light.api.scene.SceneDescriptor;

final class SponzaDemo implements DemoDefinition {
    @Override
    public String id() {
        return "sponza";
    }

    @Override
    public String description() {
        return "KhronosGroup Sponza atrium with PBR materials, shadows, and post-process.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.sponzaScene();
    }
}
