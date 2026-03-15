package org.dynamisengine.light.demos;

import org.dynamisengine.light.api.scene.SceneDescriptor;

final class ShadowCascadeDebugDemo implements DemoDefinition {
    @Override
    public String id() {
        return "shadow-cascade-debug";
    }

    @Override
    public String description() {
        return "Directional shadow cascade stress lane for split/bias tuning.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.shadowCascadeDebugScene();
    }
}

