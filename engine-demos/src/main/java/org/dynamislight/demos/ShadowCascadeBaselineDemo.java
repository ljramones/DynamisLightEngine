package org.dynamislight.demos;

import org.dynamislight.api.scene.SceneDescriptor;

final class ShadowCascadeBaselineDemo implements DemoDefinition {
    @Override
    public String id() {
        return "shadow-cascade-baseline";
    }

    @Override
    public String description() {
        return "Directional shadow cascade baseline lane tuned for clean default runs.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.shadowCascadeBaselineScene();
    }
}

