package org.dynamislight.demos;

import org.dynamislight.api.scene.SceneDescriptor;

final class ShadowLocalAtlasDemo implements DemoDefinition {
    @Override
    public String id() {
        return "shadow-local-atlas";
    }

    @Override
    public String description() {
        return "Local shadow atlas pressure lane with multiple shadow-casting local lights.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.shadowLocalAtlasScene();
    }
}

