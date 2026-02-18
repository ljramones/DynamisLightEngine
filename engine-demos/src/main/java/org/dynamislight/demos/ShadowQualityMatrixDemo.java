package org.dynamislight.demos;

import org.dynamislight.api.scene.SceneDescriptor;

final class ShadowQualityMatrixDemo implements DemoDefinition {
    @Override
    public String id() {
        return "shadow-quality-matrix";
    }

    @Override
    public String description() {
        return "Shadow quality matrix lane comparing low/medium/high local shadow settings.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.shadowQualityMatrixScene();
    }
}

