package org.dynamislight.demos;

import org.dynamislight.api.scene.SceneDescriptor;

final class MaterialBaselineDemo implements DemoDefinition {
    @Override
    public String id() {
        return "material-baseline";
    }

    @Override
    public String description() {
        return "PBR baseline sweep across roughness and metalness.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.materialBaselineScene();
    }
}

